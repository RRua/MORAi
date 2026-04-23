plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "io.github.sanadlab"
version = "0.1.0"

dependencies {
    implementation(project(":memoize-annotations"))
    implementation(libs.ksp.api)
    testImplementation(libs.junit)
}

kotlin {
    jvmToolchain(17)
}
