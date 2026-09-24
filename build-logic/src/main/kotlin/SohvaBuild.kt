import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** Values every module shares. Changing one here changes the whole build (plan/05 §4.2). */
object SohvaBuild {
    const val MIN_SDK: Int = 23
    const val TARGET_SDK: Int = 36
    const val COMPILE_SDK: Int = 37
    val JAVA_VERSION: JavaVersion = JavaVersion.VERSION_17
    val JVM_TARGET: JvmTarget = JvmTarget.JVM_17

    /** The seven interface languages; everything else is filtered out of the APK (plan/05 §4.7). */
    val LOCALES: List<String> = listOf("en", "fi", "es", "pt", "de", "sv", "it")

    /**
     * Lint ids that only say a newer dependency exists. Versions move once a month and at each
     * milestone, one family at a time (plan/05 §4.13), not whenever lint notices a release.
     */
    val LINT_DISABLED: Set<String> = setOf(
        "GradleDependency",
        "AndroidGradlePluginVersion",
        "NewerVersionAvailable",
    )
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Kotlin settings for every module: JVM 17 bytecode and no tolerated warnings (plan/05 §4.11). */
internal fun Project.configureKotlinCompile() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(SohvaBuild.JVM_TARGET)
            allWarningsAsErrors.set(true)
        }
    }
}

/**
 * The owner's Shield is often attached to adb next to the emulator. Gradle's install and
 * connected tasks act on every attached device, so they refuse to run unless ANDROID_SERIAL
 * names an emulator (AGENTS.md §7: never touch the owner's devices without an explicit go).
 */
internal fun Project.guardDeviceTasks() {
    val serial: Provider<String> = providers.environmentVariable("ANDROID_SERIAL")
    tasks.configureEach {
        val touchesDevice = name.startsWith("connected") ||
            name.startsWith("install") ||
            name.startsWith("uninstall")
        if (touchesDevice) {
            doFirst {
                val value = serial.orNull
                if (value == null || !value.startsWith("emulator-")) {
                    throw GradleException(
                        "Refusing '$name': set ANDROID_SERIAL=emulator-NNNN. Device tasks never run " +
                            "without an explicit emulator target (a real TV may be attached to adb).",
                    )
                }
            }
        }
    }
}
