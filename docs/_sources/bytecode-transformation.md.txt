# Build-Time Bytecode Transformation

This is the core mechanism that makes `@Memoize` transparent. The Gradle plugin transforms `.class` files at build time using ASM bytecode manipulation.

Source: [`memoize-gradle-plugin/src/main/kotlin/io/github/sanadlab/plugin/`](https://github.com)

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
           !className.startsWith("io.github.sanadlab.runtime.") &&
           !className.startsWith("io.github.sanadlab.annotations.")
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
            "Lio/github/sanadlab/annotations/Memoize;" -> {
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
            "Lio/github/sanadlab/annotations/CacheInvalidate;" -> {
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
    "Lio/github/sanadlab/runtime/MemoCacheManager;", null, null
))

// One MemoDispatcher field per memoized method (with hash suffix)
classNode.fields.add(FieldNode(
    ACC_PRIVATE | ACC_SYNTHETIC, "__memoDispatcher_search_297da",
    "Lio/github/sanadlab/runtime/MemoDispatcher;", null, null
))
classNode.fields.add(FieldNode(
    ACC_PRIVATE | ACC_SYNTHETIC, "__memoDispatcher_length_8aec2",
    "Lio/github/sanadlab/runtime/MemoDispatcher;", null, null
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

### @CacheInvalidate Methods — Legacy Form

The `InvalidateMethodAdapter` injects one of two calls before each return, depending on the annotation's `value`.

#### Bare `@CacheInvalidate` → `invalidateAll()`

**Source:**
```java
@CacheInvalidate
public void reset() {
    this.base = 0;
}
```

**Transformed (equivalent Java):**
```java
public void reset() {
    this.base = 0;
    if (this.__memoCacheManager != null) {
        this.__memoCacheManager.invalidateAll();
    }
}
```

#### `@CacheInvalidate({"name1", "name2"})` → `invalidate(String[])`

The user-facing names are resolved at build time to the `name_XXXXX` methodKeys — that's how overloaded methods get flushed together.

**Source:**
```java
@CacheInvalidate({"compute", "format"})
public void setBase(int newBase) {
    this.base = newBase;
}
```

**Transformed:**
```java
public void setBase(int newBase) {
    this.base = newBase;
    if (this.__memoCacheManager != null) {
        this.__memoCacheManager.invalidate(new String[]{
            "compute_297da",     // all overloads of compute(...)
            "compute_c71de",
            "format_b8a01"
        });
    }
}
```

The null check handles the edge case where `@CacheInvalidate` appears on a class with no `@Memoize` methods (the manager field would be uninitialized).

Exception paths (`ATHROW`) do NOT trigger invalidation — if a mutation throws, the state may not have changed.

### @InvalidateCacheEntry Methods

The `InvalidateEntryMethodAdapter` evicts exactly one row from one target cache. It boxes the enclosing-method parameters named by `keys` into an `Object[]` and calls `manager.invalidateEntry(methodKey, args)`. The runtime rebuilds the target's `CacheKeyWrapper` from `args` and removes that entry.

**Source:**
```java
@Memoize
public UserProfile loadProfile(int userId) { ... }

@InvalidateCacheEntry(method = "loadProfile", keys = {0})
public void updateProfile(int userId, String name) {
    db.update(userId, name);
}
```

**Transformed:**
```java
public void updateProfile(int userId, String name) {
    db.update(userId, name);
    if (this.__memoCacheManager != null) {
        this.__memoCacheManager.invalidateEntry(
            "loadProfile_297da",
            new Object[]{ Integer.valueOf(userId) }
        );
    }
}
```

Primitive parameters are auto-boxed to match the target cache's key shape. Out-of-range indices in `keys` are silently clamped at transform time so a typo doesn't crash the build — the resulting key simply misses at runtime.

### @CacheInvalidate Methods — Structured `targets`

When `@CacheInvalidate(targets = { @Invalidation(...), ... })` is used, `InvalidateMethodAdapter` emits one self-contained invalidation block per `@Invalidation` directive. Each block reloads the manager and null-guards independently. Legacy `value` (if also specified) fires first; structured directives follow in declared order.

Three per-target modes exist. Below, each is shown side-by-side: source on the left, generated code on the right.

#### Mode 1: `allEntries = true` (FLUSH)

Same effect as legacy `@CacheInvalidate("method")`, but inside the structured list.

**Source:**
```java
@Invalidation(method = "getDocumentCount", allEntries = true)
```

**Generated (per-target block):**
```java
if (this.__memoCacheManager != null) {
    this.__memoCacheManager.invalidate(new String[]{ "getDocumentCount_b8a01" });
}
```

#### Mode 2: `keys = {...}` (KEYS) — param-index keyed eviction

Semantically identical to `@InvalidateCacheEntry`, embedded as one directive among others on the same mutator.

**Source:**
```java
@Invalidation(method = "getDocument", keys = {0})
public void updateDocument(int id, String content) { ... }
```

**Generated:**
```java
if (this.__memoCacheManager != null) {
    this.__memoCacheManager.invalidateEntry(
        "getDocument_297da",
        new Object[]{ Integer.valueOf(id) }   // enclosing param at index 0, boxed
    );
}
```

#### Mode 3: `keyBuilder = "..."` (KEY_BUILDER) — helper-built keys

The transform calls your named helper and forwards its return as the target's argument tuple. Two sub-paths depending on the helper's return type.

**3a. Helper returns `Object[]` — passed through verbatim.**

**Source:**
```java
@CacheInvalidate(targets = {
    @Invalidation(method = "taggedByTenant",
                  keyBuilder = "tenantTagKey",
                  keyBuilderArgs = {0})
})
public void refreshTenantDoc(int docId) { ... }

private Object[] tenantTagKey(int docId) {
    return new Object[]{ currentTenantId, docId };  // reads instance field
}
```

**Generated:**
```java
public void refreshTenantDoc(int docId) {
    // ... original body ...
    if (this.__memoCacheManager != null) {
        this.__memoCacheManager.invalidateEntry(
            "taggedByTenant_ab12c",
            this.tenantTagKey(docId)   // Object[] return used as the args tuple
        );
    }
}
```

**3b. Helper returns a scalar (or boxed primitive) — auto-wrapped into a one-element `Object[]`.**

**Source:**
```java
@CacheInvalidate(targets = {
    @Invalidation(method = "getDocument", keyBuilder = "docKey")
})
public void addDocument(Document doc) {
    db.insert(doc);
}

private Object docKey(Document doc) {
    return doc.id;   // scalar
}
```

**Generated:**
```java
public void addDocument(Document doc) {
    db.insert(doc);
    if (this.__memoCacheManager != null) {
        Object[] __key = new Object[1];
        __key[0] = this.docKey(doc);   // auto-wrap
        this.__memoCacheManager.invalidateEntry("getDocument_297da", __key);
    }
}
```

#### Default `keyBuilderArgs` auto-forwards

When `keyBuilderArgs = {}` (the default) and the helper declares N parameters, the transform forwards the first N enclosing-method parameters in order. This lets `keyBuilder = "docKey"` "just work" without spelling out indices in the common "pass the args through" case.

**Source:**
```java
@CacheInvalidate(targets = {
    @Invalidation(method = "taggedByTenant", keyBuilder = "autoForwardKey")
})
public void reassignTag(int tenantId, int docId, String reason) { ... }

private Object[] autoForwardKey(int tenantId, int docId) {
    return new Object[]{ tenantId, docId };
}
```

**Generated:**
```java
public void reassignTag(int tenantId, int docId, String reason) {
    // ... original body ...
    if (this.__memoCacheManager != null) {
        // autoForwardKey has arity 2 → first 2 enclosing params forwarded.
        // `reason` (arg index 2) is NOT forwarded.
        this.__memoCacheManager.invalidateEntry(
            "taggedByTenant_ab12c",
            this.autoForwardKey(tenantId, docId)
        );
    }
}
```

#### Helper visibility → invoke opcode

The transform picks the right JVM invoke opcode based on the helper's access modifiers — this matters because `INVOKEVIRTUAL` is illegal on private methods per JVMS.

| Helper modifier | Emitted opcode |
|---|---|
| `static` | `INVOKESTATIC` |
| `private` (instance) | `INVOKESPECIAL` |
| any other (instance) | `INVOKEVIRTUAL` |

#### Heterogeneous directives on one mutator

All three modes (and legacy `value`) can coexist. Each emits its own self-contained block in source order.

**Source:**
```java
@CacheInvalidate(
    value = {"legacyCache"},
    targets = {
        @Invalidation(method = "getDocumentCount", allEntries = true),
        @Invalidation(method = "getDocument",     keys = {0}),
        @Invalidation(method = "taggedByTenant",  keyBuilder = "tenantTagKey")
    }
)
public void touchAllThree(int docId) { ... }
```

**Generated (outline):**
```java
public void touchAllThree(int docId) {
    // ... original body ...

    // Legacy value first
    if (this.__memoCacheManager != null) {
        this.__memoCacheManager.invalidate(new String[]{ "legacyCache_..." });
    }

    // Structured target 1: FLUSH
    if (this.__memoCacheManager != null) {
        this.__memoCacheManager.invalidate(new String[]{ "getDocumentCount_b8a01" });
    }

    // Structured target 2: KEYS
    if (this.__memoCacheManager != null) {
        this.__memoCacheManager.invalidateEntry(
            "getDocument_297da",
            new Object[]{ Integer.valueOf(docId) }
        );
    }

    // Structured target 3: KEY_BUILDER
    if (this.__memoCacheManager != null) {
        this.__memoCacheManager.invalidateEntry(
            "taggedByTenant_ab12c",
            this.tenantTagKey(docId)
        );
    }
}
```

Each block reloads the manager and null-guards independently. The cost is a few extra `GETFIELD` instructions per target — negligible since invalidation paths aren't hot.

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
