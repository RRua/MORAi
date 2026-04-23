plugins {
    java
    id("io.github.sanadlab")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation("io.github.sanadlab:memoize-annotations:0.1.0")
    implementation("io.github.sanadlab:memoize-runtime:0.1.0")
    testImplementation("junit:junit:4.13.2")
}
