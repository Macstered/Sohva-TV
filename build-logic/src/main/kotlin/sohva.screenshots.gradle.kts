// JVM screenshot tests with Roborazzi on Robolectric (decision: no emulator needed for goldens).
//   ./gradlew :<module>:recordRoborazziDebug   record goldens
//   ./gradlew :<module>:verifyRoborazziDebug   compare against them (CI)
plugins {
    id("io.github.takahirom.roborazzi")
}

dependencies {
    "testImplementation"(libs.findLibrary("junit").get())
    "testImplementation"(libs.findLibrary("robolectric").get())
    "testImplementation"(libs.findLibrary("androidx-test-ext-junit").get())
    "testImplementation"(libs.findLibrary("androidx-compose-ui-test-junit4").get())
    "testImplementation"(libs.findLibrary("roborazzi").get())
    "testImplementation"(libs.findLibrary("roborazzi-compose").get())
    "testImplementation"(libs.findLibrary("roborazzi-junit-rule").get())
    "debugImplementation"(libs.findLibrary("androidx-compose-ui-test-manifest").get())
}
