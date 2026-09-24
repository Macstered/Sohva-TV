// Enforces the module dependency table of plan/03 §4.3 (ModuleRules) on the root project:
//   ./gradlew checkModuleDependencies
// Production configurations only; test code may use test helpers.

val check = tasks.register<ModuleDependencyTask>("checkModuleDependencies") {
    group = "verification"
    description = "Fails when a module depends on a module plan/03 §4.3 does not allow."
}

gradle.projectsEvaluated {
    // Folders such as :core or :feature group modules and have no build file of their own.
    val modules = subprojects.filter { it.buildFile.exists() }
    check.configure {
        modules.forEach { module ->
            val deps = module.configurations
                .filter { c ->
                    val n = c.name.lowercase()
                    (n.endsWith("implementation") || n.endsWith("api") || n == "compileonly") && !n.contains("test")
                }
                .flatMap { c -> c.dependencies.withType(ProjectDependency::class.java).map { it.path } }
                .toSortedSet()
            declared.put(module.path, deps.joinToString(","))
        }
    }
}
