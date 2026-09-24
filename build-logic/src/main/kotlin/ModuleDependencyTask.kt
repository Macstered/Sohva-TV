import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * The module dependency table of plan/03 section 4.3. A key ending in `*` matches a family of
 * modules; the value lists the project dependencies a module may declare, `*` meaning any.
 */
object ModuleRules {
    val allowed: Map<String, Set<String>> = mapOf(
        ":core:model" to emptySet(),
        ":core:net" to setOf(":core:model"),
        ":core:data" to setOf(":core:model"),
        ":core:sync" to setOf(":core:model", ":core:net", ":core:data"),
        ":core:player" to setOf(":core:model", ":core:data"),
        ":ui:design" to setOf(":core:model"),
        ":feature:player" to setOf(":ui:design", ":core:model", ":core:data", ":core:sync", ":core:player"),
        ":feature:sport" to setOf(":ui:design", ":core:model", ":core:data", ":core:sync", ":core:net"),
        ":feature:discover" to setOf(":ui:design", ":core:model", ":core:data", ":core:sync", ":core:net"),
        ":feature:trakt" to setOf(":ui:design", ":core:model", ":core:data", ":core:sync", ":core:net"),
        ":feature:*" to setOf(":ui:design", ":core:model", ":core:data", ":core:sync"),
        ":spike:*" to setOf(":ui:design", ":core:model"),
        ":benchmark" to emptySet(),
        ":lint-checks" to emptySet(),
        ":app" to setOf("*"),
    )

    fun rulesFor(path: String): Set<String>? = allowed[path]
        ?: allowed.entries.firstOrNull { (pattern, _) -> pattern.endsWith("*") && path.startsWith(pattern.dropLast(1)) }?.value
}

/** Fails when a module declares a project dependency the table does not allow. */
abstract class ModuleDependencyTask : DefaultTask() {
    /** Module path to its production project dependencies, comma-separated. */
    @get:Input
    abstract val declared: MapProperty<String, String>

    @TaskAction
    fun check() {
        val modules = declared.get()
        val problems = modules.flatMap { (module, list) ->
            val deps = list.split(',').filter { it.isNotEmpty() }
            val rules = ModuleRules.rulesFor(module) ?: return@flatMap listOf("$module is not in the dependency table (plan/03 4.3); add it")
            if ("*" in rules) emptyList() else deps.filter { it !in rules }.map { "$module may not depend on $it" }
        }
        if (problems.isNotEmpty()) throw GradleException("Module dependency rules broken:\n  " + problems.joinToString("\n  "))
        logger.lifecycle("Module dependencies follow plan/03 4.3 (${modules.size} modules).")
    }
}
