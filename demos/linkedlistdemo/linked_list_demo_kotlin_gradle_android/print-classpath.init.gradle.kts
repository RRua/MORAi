// print-classpath.init.gradle.kts

allprojects {
    // Check if the current project is the 'app' module
    if (project.name == "app") {
        task("printClasspath") {
            doLast {
                // Now we are safely in the 'app' project context
                val classpath = configurations.getByName("debugRuntimeClasspath").joinToString(separator = ":") { it.absolutePath }
                println(classpath)
            }
        }
    }
}