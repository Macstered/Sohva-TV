plugins {
    id("sohva.android.library")
    id("sohva.android.compose")
    id("sohva.screenshots")
}

android {
    namespace = "com.sohva.tv.ui.design"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
