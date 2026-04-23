plugins {
    kotlin("jvm") version "2.0.21"
    id("io.github.sanadlab")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("io.github.sanadlab:memoize-annotations:0.1.0")
    implementation("io.github.sanadlab:memoize-runtime:0.1.0")
    testImplementation("junit:junit:4.13.2")
}
