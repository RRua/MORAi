plugins {
    kotlin("jvm") version "1.9.10"
    application
}

group = "com.linkedlist"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.10")
    implementation("org.apache.commons:commons-lang3:3.12.0")
}

application {
    mainClass.set("com.linkedlist.app.LinkedListDemo")
}

// tasks.register("printCp") {
//     doLast {
//         val classpath = configurations.runtimeClasspath.get().files.joinToString(separator = ":")
//         println(classpath)
//     }
// }
