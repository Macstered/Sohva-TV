import com.android.build.api.dsl.TestExtension

// The :benchmark module: Macrobenchmark journeys and baseline/startup profile generation against
// :app's release-like variants (plan/05 §4.9). Emulator results are read relatively, never as
// device figures (plan/07 §6.5).
plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

extensions.configure<TestExtension> {
    compileSdk = SohvaBuild.COMPILE_SDK
    targetProjectPath = ":app"
    defaultConfig {
        // Profile collection without root needs API 33; Macrobenchmark itself runs from 28.
        minSdk = 28
        targetSdk = SohvaBuild.TARGET_SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }
    compileOptions {
        sourceCompatibility = SohvaBuild.JAVA_VERSION
        targetCompatibility = SohvaBuild.JAVA_VERSION
    }
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    "implementation"(libs.findLibrary("androidx-benchmark-macro-junit4").get())
    "implementation"(libs.findLibrary("androidx-test-uiautomator").get())
    "implementation"(libs.findLibrary("androidx-test-ext-junit").get())
    "implementation"(libs.findLibrary("androidx-test-runner").get())
}

configureKotlinCompile()
guardDeviceTasks()
