plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.android.aapt2.proto)
    implementation(libs.protobuf.java)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.room.gradle.plugin)
    implementation(libs.roborazzi.gradle.plugin)
    implementation(libs.baselineprofile.gradle.plugin)
}
