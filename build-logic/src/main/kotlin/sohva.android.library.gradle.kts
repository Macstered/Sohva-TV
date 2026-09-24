import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
}

extensions.configure<LibraryExtension> {
    compileSdk = SohvaBuild.COMPILE_SDK
    defaultConfig {
        minSdk = SohvaBuild.MIN_SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // AGP's default is a year; one hung test used to stall the whole suite (plan/05 §3.3).
        testInstrumentationRunnerArguments["timeout_msec"] = "300000"
    }
    compileOptions {
        sourceCompatibility = SohvaBuild.JAVA_VERSION
        targetCompatibility = SohvaBuild.JAVA_VERSION
        isCoreLibraryDesugaringEnabled = true
    }
    testOptions {
        targetSdk = SohvaBuild.TARGET_SDK
        unitTests.isIncludeAndroidResources = true
    }
    lint {
        targetSdk = SohvaBuild.TARGET_SDK
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = false
        disable += SohvaBuild.LINT_DISABLED
    }
}

dependencies {
    "coreLibraryDesugaring"(libs.findLibrary("desugar-jdk-libs").get())
}

configureKotlinCompile()
guardDeviceTasks()
