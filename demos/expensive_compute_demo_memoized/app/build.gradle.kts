plugins {
    alias(libs.plugins.android.application)
    id("dev.memoize")
}

android {
    namespace = "com.linkedlist.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.expensive.memoized"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("dev.memoize:memoize-annotations:0.1.0")
    implementation("dev.memoize:memoize-runtime:0.1.0")
    testImplementation(libs.junit)
}