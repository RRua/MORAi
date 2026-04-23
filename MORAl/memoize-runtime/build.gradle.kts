plugins {
    `java-library`
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

group = "io.github.sanadlab"
version = "0.1.0"

dependencies {
    implementation(project(":memoize-annotations"))
    testImplementation(libs.junit)
}
