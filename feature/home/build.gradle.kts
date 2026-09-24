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
    implementation(project(":ui:design"))
}
