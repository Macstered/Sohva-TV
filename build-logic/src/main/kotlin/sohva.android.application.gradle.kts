import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application")
}

extensions.configure<ApplicationExtension> {
    compileSdk = SohvaBuild.COMPILE_SDK
    defaultConfig {
        minSdk = SohvaBuild.MIN_SDK
        targetSdk = SohvaBuild.TARGET_SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["timeout_msec"] = "300000"
    }
    compileOptions {
        sourceCompatibility = SohvaBuild.JAVA_VERSION
        targetCompatibility = SohvaBuild.JAVA_VERSION
        isCoreLibraryDesugaringEnabled = true
    }
    androidResources {
        localeFilters += SohvaBuild.LOCALES
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = true
        disable += SohvaBuild.LINT_DISABLED
    }
    // The Play dependency block is encrypted differently on every build; leaving it out keeps
    // sideloaded APKs byte-reproducible (plan/05 §3.5).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    "coreLibraryDesugaring"(libs.findLibrary("desugar-jdk-libs").get())
    // The project's own rules (plan/05 §4.11), in every module's lint.
    "lintChecks"(project(":lint-checks"))
}

configureKotlinCompile()
guardDeviceTasks()
