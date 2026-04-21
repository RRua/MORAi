pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        mavenLocal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "memoize-lib"

include(":memoize-annotations")
include(":memoize-runtime")
include(":memoize-ksp")
include(":memoize-gradle-plugin")
// memoize-test-android is a separate build that consumes the plugin via includeBuild
