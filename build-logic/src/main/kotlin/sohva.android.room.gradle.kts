import androidx.room.gradle.RoomExtension

// Room with KSP and exported schemas. Apply after sohva.android.library.
plugins {
    id("com.google.devtools.ksp")
    id("androidx.room")
}

extensions.configure<RoomExtension> {
    // Exported schemas are the migration contract; the plugin also hands them to device tests.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    "implementation"(libs.findLibrary("androidx-room-runtime").get())
    "ksp"(libs.findLibrary("androidx-room-compiler").get())
    "androidTestImplementation"(libs.findLibrary("androidx-room-testing").get())
}
