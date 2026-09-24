// Throw-away: decides the guide grid's structure before M2 (roadmap M0). Deleted or promoted then.
plugins {
    id("sohva.android.library")
    id("sohva.android.compose")
}

android {
    namespace = "com.sohva.tv.spike.guidegrid"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":ui:design"))
    implementation(libs.androidx.tracing)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
