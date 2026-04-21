plugins {
    kotlin("jvm") version "2.0.21"
    id("dev.memoize")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("dev.memoize:memoize-annotations:0.1.0")
    implementation("dev.memoize:memoize-runtime:0.1.0")
    testImplementation("junit:junit:4.13.2")
}
