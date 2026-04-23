plugins {
    alias(libs.plugins.android.library)
    id("io.github.sanadlab")
}

android {
    namespace = "com.memoize.bench.lib"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    api("io.github.sanadlab:memoize-annotations:0.1.0")
    api("io.github.sanadlab:memoize-runtime:0.1.0")
}
