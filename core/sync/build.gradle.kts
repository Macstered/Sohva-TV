plugins {
    id("sohva.android.library")
}

android {
    namespace = "com.sohva.tv.core.sync"

    // The import harness and scenarios run both on the JVM (Robolectric) and on the emulator.
    sourceSets {
        getByName("test") { kotlin.directories.add("src/sharedTest/kotlin") }
        getByName("androidTest") { kotlin.directories.add("src/sharedTest/kotlin") }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:net"))
    implementation(project(":core:data"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.moshi)
    implementation(libs.androidx.room.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kxml2)
    testImplementation(libs.androidx.work.testing)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.room.runtime)
}
