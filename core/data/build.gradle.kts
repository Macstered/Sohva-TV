plugins {
    id("sohva.android.library")
    id("sohva.android.room")
}

android {
    namespace = "com.sohva.tv.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    // Streaming JSON for the backup payload (spec 71 §9: no tree).
    implementation(libs.moshi)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.sqlite.jdbc)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
