import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension

// Apply after sohva.android.application or sohva.android.library.
plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.findByType(ApplicationExtension::class.java)?.buildFeatures?.compose = true
extensions.findByType(LibraryExtension::class.java)?.buildFeatures?.compose = true

composeCompiler {
    // Read-only collections and :core:model types are declared stable here, because the JVM
    // module is compiled without the Compose plugin (plan/05 §4.10).
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
}

dependencies {
    val bom = platform(libs.findLibrary("androidx-compose-bom").get())
    "implementation"(bom)
    "androidTestImplementation"(bom)
    "implementation"(libs.findLibrary("androidx-compose-runtime").get())
    "implementation"(libs.findLibrary("androidx-compose-ui").get())
    "implementation"(libs.findLibrary("androidx-compose-foundation").get())
}
