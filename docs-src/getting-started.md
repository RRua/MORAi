# Getting Started

## Prerequisites

- Android Gradle Plugin 8.0+
- Kotlin 2.0+ (for KSP validation) or Java 11+
- Gradle 8.0+

## Installation

### Step 1: Add the plugin to your settings

Add the MORAl as an included build (for local development) or configure the plugin repository.

**Kotlin DSL** (`settings.gradle.kts`):

```kotlin
pluginManagement {
    // Option A: Local development (includeBuild)
    includeBuild("path/to/MORAl")

    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        mavenLocal()  // If published to mavenLocal
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}
```

**Groovy DSL** (`settings.gradle`):

```groovy
pluginManagement {
    // Option A: Local development (includeBuild)
    includeBuild 'path/to/MORAl'

    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        mavenLocal()  // If published to mavenLocal
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}
```

### Step 2: Apply the plugin and add dependencies

**Kotlin DSL** (`app/build.gradle.kts`):

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("io.github.sanadlab")  // Add this
}

dependencies {
    implementation("io.github.sanadlab:memoize-annotations:0.1.0")
    implementation("io.github.sanadlab:memoize-runtime:0.1.0")
}
```

**Groovy DSL** (`app/build.gradle`):

```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'io.github.sanadlab'  // Add this
}

dependencies {
    implementation 'io.github.sanadlab:memoize-annotations:0.1.0'
    implementation 'io.github.sanadlab:memoize-runtime:0.1.0'
}
```

### Step 3: Annotate your methods

Mark expensive reads with `@Memoize`, and mutators with `@CacheInvalidate` so
caches stay coherent with the underlying state.

#### 3.1 &nbsp; The minimal pattern

```java
import io.github.sanadlab.annotations.Memoize;
import io.github.sanadlab.annotations.CacheInvalidate;

public class MyRepository {

    @Memoize(maxSize = 256)
    public UserProfile loadProfile(int userId) {
        return db.queryProfile(userId);              // expensive — cached
    }

    @CacheInvalidate("loadProfile")
    public void updateProfile(int userId, String name) {
        db.update(userId, name);                     // mutates → wipes loadProfile cache
    }
}
```

No call-site changes needed. The build-time transformation wires everything up.

#### 3.2 &nbsp; Selective full-flush across multiple methods

Legacy `value` form lists cache names to wipe entirely. Every other cache on
the instance is untouched:

```java
@Memoize  public List<Doc>    listDocs(String folder) { ... }
@Memoize  public int          docCount()               { ... }
@Memoize  public List<String> listTags(int docId)      { ... }

@CacheInvalidate({"listDocs", "docCount"})
public void addDoc(Doc doc) {
    db.insert(doc);
    // listTags cache is *not* cleared — tags didn't change.
}
```

#### 3.3 &nbsp; Single-entry eviction when you know which row changed

A full flush is wasteful if only one cached row is stale. Use
`@InvalidateCacheEntry` (single target) or the structured
`@CacheInvalidate(targets = ...)` form (multiple targets). Here's the former:

```java
import io.github.sanadlab.annotations.InvalidateCacheEntry;

@Memoize
public UserProfile loadProfile(int userId) { ... }

@InvalidateCacheEntry(method = "loadProfile", keys = {0})
public void updateProfile(int userId, String name) {
    db.update(userId, name);
    // Only loadProfile's entry for this userId is evicted.
}
```

`keys = {0}` means "take parameter index 0 of the enclosing method
(`userId`) and rebuild the target's cache key from it."

#### 3.4 &nbsp; Heterogeneous targets on one mutator

Real mutators often affect several caches differently: one gets its row
evicted, another has to be flushed whole. Use `targets`:

```java
import io.github.sanadlab.annotations.CacheInvalidate;
import io.github.sanadlab.annotations.Invalidation;

@Memoize public Document getDocument(int id)      { ... }
@Memoize public int      getDocumentCount()       { ... }

// Adding a document: the new row invalidates *only* getDocument(doc.id),
// but the count cache must be flushed.
@CacheInvalidate(targets = {
    @Invalidation(method = "getDocument",      keyBuilder = "docKey"),
    @Invalidation(method = "getDocumentCount", allEntries = true)
})
public void addDocument(Document doc) {
    db.insert(doc);
}

private Object docKey(Document doc) {
    return doc.id;    // scalar — the transform wraps it as the target's arg tuple
}
```

Three per-target modes:

| Mode | Written as | Effect |
|------|------------|--------|
| Full flush | `allEntries = true` | Evict every entry of the named cache. |
| Param-index key | `keys = {0, 2}` | Take enclosing args at indices 0 and 2, box them, use as the target's cache key. |
| Builder method | `keyBuilder = "docKey"` | Call the named helper, use its return value as the target's key. |

#### 3.5 &nbsp; Key builders with ambient / extra state

The helper named by `keyBuilder` is plain Java — it can read static fields,
instance fields, thread-locals, call other methods, etc. When the helper needs
information that *isn't* in the mutating method's parameter list, pass it
through `this` or a static:

```java
public class DocumentStore {
    private int currentUserId;   // set elsewhere (e.g., per request)

    @Memoize public Document getDocument(int id) { ... }

    @CacheInvalidate(targets = {
        @Invalidation(method = "getDocument", keyBuilder = "docKey")
    })
    public void addDocument(Document doc) {
        db.insert(doc, currentUserId);
    }

    // Reads instance state; takes only what it receives from the enclosing method.
    private Object docKey(Document doc) {
        return currentUserId + ":" + doc.id;
    }
}
```

When the helper *does* want extra enclosing-method parameters, declare them on
its signature &mdash; the transform auto-forwards the first N parameters of
the mutating method (N = helper's arity):

```java
@CacheInvalidate(targets = {
    @Invalidation(method = "getDocument", keyBuilder = "docKey")
})
public void addDocumentAs(Document doc, int userId) {
    db.insert(doc, userId);
}

// Both (doc, userId) are forwarded automatically because docKey takes 2 args
// and addDocumentAs has ≥ 2 declared parameters.
private Object docKey(Document doc, int userId) {
    return userId + ":" + doc.id;
}
```

To forward a different subset or reorder, set `keyBuilderArgs` explicitly:

```java
@CacheInvalidate(targets = {
    // Pass only the 3rd enclosing arg to the builder, ignore the first two.
    @Invalidation(method = "getTags",
                  keyBuilder = "tagKey",
                  keyBuilderArgs = {2})
})
public void updateTags(String auditLabel, long ts, int docId) { ... }

private Object tagKey(int docId) { return docId; }
```

Key builders can be `static` and can return `Object[]` to supply a multi-part
key tuple verbatim; anything else is auto-wrapped into a one-element array.

#### 3.6 &nbsp; Legacy and structured can be combined

`value` (full-flush names) and `targets` (structured directives) on the same
annotation both fire &mdash; legacy names first, then each structured
directive in order:

```java
@CacheInvalidate(
    value = {"getTags"},                    // wipe every getTags entry
    targets = {
        @Invalidation(method = "getDocument", keys = {0})   // evict one row
    }
)
public void markStale(int id) { ... }
```

### Step 4: Build and verify

```bash
# Build your project
./gradlew assembleDebug

# Verify transformation (optional)
javap -p app/build/intermediates/classes/debug/transformDebugClassesWithAsm/dirs/com/example/MyRepository.class
```

You should see `__memoCacheManager` and `__memoDispatcher_*` fields in the output.

## Quick Example

### Java

```java
import io.github.sanadlab.annotations.Memoize;
import io.github.sanadlab.annotations.CacheInvalidate;

public class LinkedList {
    private Node head;

    @Memoize(maxSize = 64)
    public boolean search(int key) {
        Node current = head;
        while (current != null) {
            if (current.data == key) return true;
            current = current.next;
        }
        return false;
    }

    @Memoize
    public int length() {
        int count = 0;
        Node current = head;
        while (current != null) { count++; current = current.next; }
        return count;
    }

    // Selective: only invalidates search and length
    @CacheInvalidate({"search", "length"})
    public void insert(int data) {
        Node newNode = new Node(data);
        // ... insert logic ...
    }

    // Full: invalidates all caches on the instance
    @CacheInvalidate
    public void clear() {
        head = null;
    }
}
```

### Kotlin

```kotlin
import io.github.sanadlab.annotations.Memoize
import io.github.sanadlab.annotations.CacheInvalidate

class LinkedList : Iterable<Node> {
    private var head: Node? = null

    @Memoize(maxSize = 64)
    fun search(key: Int): Boolean {
        var current = head
        while (current != null) {
            if (current.data == key) return true
            current = current.next
        }
        return false
    }

    @Memoize
    fun length(): Int {
        var count = 0
        var current = head
        while (current != null) { count++; current = current.next }
        return count
    }

    @CacheInvalidate(["search", "length"])
    fun insert(data: Int) { /* ... */ }

    @CacheInvalidate
    fun clear() { head = null }
}
```

## JVM (Non-Android) Projects

The plugin also works with plain JVM projects (Java, Kotlin JVM, Kotlin Multiplatform JVM targets). No AGP required.

### Setup

**Kotlin DSL** (`settings.gradle.kts`):

```kotlin
pluginManagement {
    includeBuild("path/to/MORAl")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}
```

**Kotlin DSL** (`build.gradle.kts`):

```kotlin
plugins {
    java  // or kotlin("jvm")
    id("io.github.sanadlab")
}

dependencies {
    implementation("io.github.sanadlab:memoize-annotations:0.1.0")
    implementation("io.github.sanadlab:memoize-runtime:0.1.0")
}
```

**Groovy DSL** (`build.gradle`):

```groovy
plugins {
    id 'java'  // or id 'org.jetbrains.kotlin.jvm'
    id 'io.github.sanadlab'
}

dependencies {
    implementation 'io.github.sanadlab:memoize-annotations:0.1.0'
    implementation 'io.github.sanadlab:memoize-runtime:0.1.0'
}
```

The plugin detects that AGP is absent and instead registers a post-compilation task that transforms `.class` files in-place. No configuration difference from the developer's perspective.

### Kotlin Multiplatform

For KMP projects, the plugin works on **JVM targets** only. Apply it to the JVM source set:

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.github.sanadlab")
}

kotlin {
    jvm()  // Plugin transforms JVM bytecode
    // JS, Native targets are unaffected (no JVM bytecode)
}

dependencies {
    jvmMainImplementation("io.github.sanadlab:memoize-annotations:0.1.0")
    jvmMainImplementation("io.github.sanadlab:memoize-runtime:0.1.0")
}
```

## Building from Source

```bash
# Clone and build the library
cd MORAl
./gradlew build

# Publish to mavenLocal for consumption by other projects
./gradlew publishToMavenLocal

# Build and test the test app
cd memoize-test-android
./gradlew assembleDebug testDebugUnitTest
```
