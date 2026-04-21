// print-classpath.init.gradle.kts
allprojects {
    tasks.register("printClasspath") {
        doLast {
            val classpath = configurations.getByName("runtimeClasspath").files.joinToString(separator = System.getProperty("path.separator"))
            println(classpath)
        }
    }
}
