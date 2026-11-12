plugins {
    id("sohva.android.library")
    id("sohva.android.compose")
}

android {
    namespace = "com.sohva.tv.feature.sport"
}

// Only the reviewed snapshot is an input. The research database stays in .local and is never
// copied into an APK. A missing build input must fail instead of packaging a stale asset.
abstract class SportDictionaryTask : Exec() {
    @get:OutputDirectory abstract val assetDirectory: DirectoryProperty
}
val compileSportDictionary by tasks.registering(SportDictionaryTask::class) {
    val source = rootProject.file("docs/sport-dictionary/reviewed.json")
    inputs.file(source)
    inputs.files(rootProject.fileTree("tools/team_dictionary") { include("*.py", "schema.sql") })
    inputs.file(rootProject.file("tools/compile_sport_dictionary.py"))
    assetDirectory.set(layout.buildDirectory.dir("generated/dictionary"))
    commandLine("python", rootProject.file("tools/compile_sport_dictionary.py"), "--source", source,
        "--output", assetDirectory.get().asFile)
}
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(compileSportDictionary) { it.assetDirectory }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:net"))
    implementation(project(":ui:design"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.moshi)
    implementation(libs.androidx.room.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.sqlite.jdbc)
}
