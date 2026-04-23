# Getting Started

## Prerequisites

- Android Gradle Plugin 8.0+
- Kotlin 2.0+ (for KSP validation) or Java 11+
- Gradle 8.0+

## Installation

### Step 1: Add the plugin to your settings

Add the memoize-lib as an included build (for local development) or configure the plugin repository.

**Kotlin DSL** (`settings.gradle.kts`):

```kotlin
pluginManagement {
    // Option A: Local development (includeBuild)
    includeBuild("path/to/memoize-lib")

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
    includeBuild 'path/to/memoize-lib'

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
    id("dev.memoize")  // Add this
}

dependencies {
    implementation("dev.memoize:memoize-annotations:0.1.0")
    implementation("dev.memoize:memoize-runtime:0.1.0")
}
```

**Groovy DSL** (`app/build.gradle`):

```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'dev.memoize'  // Add this
}

dependencies {
    implementation 'dev.memoize:memoize-annotations:0.1.0'
    implementation 'dev.memoize:memoize-runtime:0.1.0'
}
```

### Step 3: Annotate your methods

```java
import dev.memoize.annotations.Memoize;
import dev.memoize.annotations.CacheInvalidate;

public class MyRepository {

    @Memoize(maxSize = 256)
    public UserProfile loadProfile(int userId) {
        // Expensive database/network call
        return db.queryProfile(userId);
    }

    @CacheInvalidate("loadProfile")
    public void updateProfile(int userId, String name) {
        db.update(userId, name);
        // Only the loadProfile cache is cleared
    }
}
```

That's it. No call-site changes needed. The build-time transformation handles everything.

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
import dev.memoize.annotations.Memoize;
import dev.memoize.annotations.CacheInvalidate;

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
import dev.memoize.annotations.Memoize
import dev.memoize.annotations.CacheInvalidate

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
    includeBuild("path/to/memoize-lib")
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
    id("dev.memoize")
}

dependencies {
    implementation("dev.memoize:memoize-annotations:0.1.0")
    implementation("dev.memoize:memoize-runtime:0.1.0")
}
```

**Groovy DSL** (`build.gradle`):

```groovy
plugins {
    id 'java'  // or id 'org.jetbrains.kotlin.jvm'
    id 'dev.memoize'
}

dependencies {
    implementation 'dev.memoize:memoize-annotations:0.1.0'
    implementation 'dev.memoize:memoize-runtime:0.1.0'
}
```

The plugin detects that AGP is absent and instead registers a post-compilation task that transforms `.class` files in-place. No configuration difference from the developer's perspective.

### Kotlin Multiplatform

For KMP projects, the plugin works on **JVM targets** only. Apply it to the JVM source set:

```kotlin
plugins {
    kotlin("multiplatform")
    id("dev.memoize")
}

kotlin {
    jvm()  // Plugin transforms JVM bytecode
    // JS, Native targets are unaffected (no JVM bytecode)
}

dependencies {
    jvmMainImplementation("dev.memoize:memoize-annotations:0.1.0")
    jvmMainImplementation("dev.memoize:memoize-runtime:0.1.0")
}
```

## Building from Source

```bash
# Clone and build the library
cd memoize-lib
./gradlew build

# Publish to mavenLocal for consumption by other projects
./gradlew publishToMavenLocal

# Build and test the test app
cd memoize-test-android
./gradlew assembleDebug testDebugUnitTest
```
