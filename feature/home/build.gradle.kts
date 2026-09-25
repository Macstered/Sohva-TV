plugins {
    id("sohva.android.library")
    id("sohva.android.compose")
    id("sohva.screenshots")
}

android {
    namespace = "com.sohva.tv.feature.home"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":ui:design"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.tracing)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
