plugins {
    id("sohva.android.library")
    id("sohva.android.compose")
}

android {
    namespace = "com.sohva.tv.feature.profiles"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":ui:design"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
