# Build-Time Bytecode Transformation

This is the core mechanism that makes `@Memoize` transparent. The Gradle plugin transforms `.class` files at build time using ASM bytecode manipulation.

Source: [`memoize-gradle-plugin/src/main/kotlin/dev/memoize/plugin/`](https://github.com)

## Plugin Registration

The `MemoizePlugin` auto-detects the project type and registers the appropriate transformation:

```kotlin
// MemoizePlugin.kt
class MemoizePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Try Android first
        if (applyAndroid(project)) return
        // Fall back to JVM
        applyJvm(project)
    }
}
```

### Android Mode (AGP detected)

Uses the AGP Instrumentation API. Runs during the `transformDebugClassesWithAsm` task:

```kotlin
androidComponents.onVariants { variant ->
    variant.instrumentation.transformClassesWith(
        MemoizeClassVisitorFactory::class.java,
        InstrumentationScope.PROJECT
    ) {}
    variant.instrumentation.setAsmFramesComputationMode(
        FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
    )
}
```

### JVM Mode (no AGP)

Registers a post-compilation `doLast` action on `JavaCompile` and `compileKotlin` tasks. Walks the classes directory and transforms `.class` files in-place using `JvmBytecodeTransformer`:

```kotlin
compileTask.doLast {
    val classesDir = compileTask.destinationDirectory.asFile.get()
    transformClassesInDirectory(classesDir, project)
}
```

The `JvmBytecodeTransformer` does a quick string scan for annotation descriptors before parsing, so unrelated classes are skipped with near-zero overhead.

## Class Filtering

The factory determines which classes to instrument:

```kotlin
// MemoizeClassVisitorFactory.kt
override fun isInstrumentable(classData: ClassData): Boolean {
    val className = classData.className
    return !className.startsWith("android.") &&
           !className.startsWith("androidx.") &&
           !className.startsWith("kotlin.") &&
           !className.startsWith("java.") &&
           !className.startsWith("dev.memoize.runtime.") &&
           !className.startsWith("dev.memoize.annotations.")
}
```

All project classes pass through the visitor. Classes without `@Memoize` or `@CacheInvalidate` annotations are passed through unmodified (the visitor detects this in Phase 1 and short-circuits).

## Two-Pass Transformation Strategy

The transformation uses two passes because of a chicken-and-egg problem: constructors appear before other methods in bytecode, but we need to know all memoized methods to generate constructor initialization code.

```
Pass 1 (Tree API - ClassNode):
  ├── Read entire class into memory
  ├── Scan ALL methods for annotations → collect metadata
  ├── Add fields: __memoCacheManager, __memoDispatcher_*
  ├── Patch constructors: insert initialization before RETURN
  └── Serialize modified ClassNode to byte[]

Pass 2 (Visitor API - AdviceAdapter):
  ├── Re-read the modified bytes
  ├── For @Memoize methods: inject cache-check at entry, cache-store at exit
  ├── For @CacheInvalidate methods: inject invalidateAll() at exit
  └── Write to output ClassVisitor
```

## Phase 1: Annotation Scanning

The tree API provides full access to the class structure:

```kotlin
// In MemoizeClassVisitor.visitEnd()
for (method in classNode.methods) {
    val allAnnotations = (method.visibleAnnotations.orEmpty()) +
                         (method.invisibleAnnotations.orEmpty())
    for (ann in allAnnotations) {
        when (ann.desc) {
            "Ldev/memoize/annotations/Memoize;" -> {
                // Read maxSize parameter
                var maxSize = 128
                if (ann.values != null) {
                    var i = 0
                    while (i < ann.values.size - 1) {
                        if (ann.values[i] == "maxSize") maxSize = ann.values[i+1] as Int
                        i += 2
                    }
                }
                memoizedMethods.add(MemoMethodInfo(method.name, method.desc, maxSize))
            }
            "Ldev/memoize/annotations/CacheInvalidate;" -> {
                invalidateMethods.add(method.name)
            }
        }
    }
}
```

Annotation values in the ASM tree model are stored as alternating name-value pairs: `["maxSize", 64, "recordStats", false]`.

## Phase 2: Field Addition

For a class with `search(int)` (maxSize=64) and `length()` (maxSize=128) memoized:

```kotlin
// One MemoCacheManager field per class
classNode.fields.add(FieldNode(
    ACC_PRIVATE | ACC_SYNTHETIC, "__memoCacheManager",
    "Ldev/memoize/runtime/MemoCacheManager;", null, null
))

// One MemoDispatcher field per memoized method (with hash suffix)
classNode.fields.add(FieldNode(
    ACC_PRIVATE | ACC_SYNTHETIC, "__memoDispatcher_search_297da",
    "Ldev/memoize/runtime/MemoDispatcher;", null, null
))
classNode.fields.add(FieldNode(
    ACC_PRIVATE | ACC_SYNTHETIC, "__memoDispatcher_length_8aec2",
    "Ldev/memoize/runtime/MemoDispatcher;", null, null
))
```

**Overload-safe field naming:** Each field name includes a 5-hex-digit hash derived from the method name + JVM descriptor. This ensures overloaded methods get unique fields:

```
search(int)    → methodKey("search", "(I)Z")     → "search_297da"
search(String) → methodKey("search", "(Ljava/lang/String;)Z") → "search_c71de"
length()       → methodKey("length", "()I")       → "length_8aec2"
```

Fields are marked `ACC_SYNTHETIC` so they don't appear in IDE auto-complete.

## Phase 3: Constructor Patching

Before every `RETURN` instruction in every `<init>` method, the following bytecode is inserted (shown as equivalent Java):

```java
// Initialization inserted before constructor returns:
this.__memoCacheManager = new MemoCacheManager();

// MemoDispatcher.create() is called with ALL annotation parameters:
this.__memoDispatcher_search_297da = MemoDispatcher.create(
    "search_297da", 64, -1L, "LRU", "CONCURRENT", false
);
this.__memoCacheManager.register("search_297da", this.__memoDispatcher_search_297da);

this.__memoDispatcher_length_8aec2 = MemoDispatcher.create(
    "length_8aec2", 128, -1L, "LRU", "CONCURRENT", false
);
this.__memoCacheManager.register("length_8aec2", this.__memoDispatcher_length_8aec2);
```

The 6 parameters to `MemoDispatcher.create()` are read directly from the `@Memoize` annotation: `maxSize`, `expireAfterWrite`, `eviction`, `threadSafety`, `recordStats`.

This is implemented by finding all `RETURN` opcodes in the constructor's instruction list and inserting `InsnList` nodes before each:

```kotlin
private fun patchConstructor(method: MethodNode, internalName: String, ...) {
    val returnInsns = mutableListOf<AbstractInsnNode>()
    var node = method.instructions.first
    while (node != null) {
        if (node.opcode == Opcodes.RETURN) returnInsns.add(node)
        node = node.next
    }
    for (ret in returnInsns) {
        val initInsns = InsnList()
        // ... build initialization bytecode ...
        method.instructions.insertBefore(ret, initInsns)
    }
}
```

## Phase 4: Method Body Instrumentation

### @Memoize Methods

The `MemoizeMethodAdapter` (extending ASM's `AdviceAdapter`) injects at two points:

#### At Method Entry (`onMethodEnter`)

For `@Memoize public boolean search(int key)`:

```java
// 1. Build cache key from arguments
CacheKeyWrapper __key = this.__memoDispatcher_search.buildKey(
    new Object[]{ Integer.valueOf(key) }  // box primitive
);

// 2. Check cache
Object __cached = this.__memoDispatcher_search.getIfCached(__key);

// 3. If cache hit, unbox and return
if (__cached != null) {
    return ((Boolean) MemoDispatcher.unwrap(__cached)).booleanValue();
}

// 4. Cache miss -- fall through to original method body
```

The actual bytecode for this:

```
 0: aload_0
 1: getfield      __memoDispatcher_search
 4: iconst_1                          // array size = 1
 6: anewarray     java/lang/Object
 9: dup
10: iconst_0                          // index 0
12: iload_1                           // load arg 'key'
13: invokestatic  Integer.valueOf(int) // box
16: aastore
17: invokevirtual MemoDispatcher.buildKey
20: astore_2                          // store key in local
21: aload_0
22: getfield      __memoDispatcher_search
25: aload_2
26: invokevirtual MemoDispatcher.getIfCached
29: dup
30: ifnull        43                  // jump to miss
33: invokestatic  MemoDispatcher.unwrap
36: checkcast     java/lang/Boolean
39: invokevirtual Boolean.booleanValue
42: ireturn                           // return cached value
43: pop                               // miss: discard null
    // ... original method body follows ...
```

#### Before Method Return (`onMethodExit`)

Before each return instruction, the return value is duplicated, boxed, and stored in the cache:

```java
// Return value is on the stack (e.g., 'true' as an int 1)
// Duplicate it (one copy for the actual return, one for caching)
boolean __returnValue = /* on stack */;

// Box and store
this.__memoDispatcher_search.putInCache(__key, Boolean.valueOf(__returnValue));

// Original return instruction executes
return __returnValue;
```

#### Primitive Type Handling

The adapter handles all 8 Java primitive types plus object/array types:

| Return Type | Boxing Call | Unboxing Call | Return Opcode |
|-------------|------------|---------------|---------------|
| `boolean` | `Boolean.valueOf(z)` | `Boolean.booleanValue()` | `IRETURN` |
| `byte` | `Byte.valueOf(b)` | `Byte.byteValue()` | `IRETURN` |
| `char` | `Character.valueOf(c)` | `Character.charValue()` | `IRETURN` |
| `short` | `Short.valueOf(s)` | `Short.shortValue()` | `IRETURN` |
| `int` | `Integer.valueOf(i)` | `Integer.intValue()` | `IRETURN` |
| `long` | `Long.valueOf(l)` | `Long.longValue()` | `LRETURN` |
| `float` | `Float.valueOf(f)` | `Float.floatValue()` | `FRETURN` |
| `double` | `Double.valueOf(d)` | `Double.doubleValue()` | `DRETURN` |
| `Object` / arrays | no-op | `checkcast` | `ARETURN` |

For 2-slot types (`long`, `double`), `DUP2` is used instead of `DUP`.

### @CacheInvalidate Methods

The `InvalidateMethodAdapter` injects a single call before each return:

```java
// Injected before each RETURN in @CacheInvalidate methods:
if (this.__memoCacheManager != null) {
    this.__memoCacheManager.invalidateAll();
}
```

The null check handles the edge case where `@CacheInvalidate` appears on a class with no `@Memoize` methods (the manager field would be uninitialized).

Exception paths (`ATHROW`) do NOT trigger invalidation -- if a mutation throws, the state may not have changed.

## Verifying the Transformation

After building, you can inspect the transformed bytecode:

```bash
# Build the APK
./gradlew assembleDebug

# Find the transformed class
find build -name "LinkedList.class" -path "*transformDebug*"

# Inspect with javap
javap -p -c <path_to_transformed_class>
```

Expected output shows:
- `__memoCacheManager` and `__memoDispatcher_*` fields
- Constructor with `new MemoCacheManager()` and `new MemoDispatcher(...)` calls
- Memoized methods with cache-check at entry and cache-store before returns
- Invalidate methods with `invalidateAll()` before returns
