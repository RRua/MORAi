plugins {
    java
    id("dev.memoize")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation("dev.memoize:memoize-annotations:0.1.0")
    implementation("dev.memoize:memoize-runtime:0.1.0")
    testImplementation("junit:junit:4.13.2")
}
