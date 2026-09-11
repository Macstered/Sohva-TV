import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseSigningPropertiesFile = rootProject.file(".local/streammate-signing/keystore.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use(::load)
        listOf("storeFile", "storePassword", "keyAlias", "keyPassword").forEach { name ->
            require(!getProperty(name).isNullOrBlank()) {
                "Missing $name in ${releaseSigningPropertiesFile.path}"
            }
        }
    }
}

android {
    // Opt-in Lab instrumentation is separate from the ordinary debug regression suite.
    testBuildType = providers.gradleProperty("sohvaTestBuildType").orElse("debug").get().also {
        require(it in setOf("debug", "lab", "release")) { "Only debug, lab or release instrumentation is supported" }
    }
    if (testBuildType == "lab") {
        sourceSets.getByName("androidTest").setRoot("src/androidTestLab")
        sourceSets.getByName("androidTest").assets.srcDir(layout.buildDirectory.dir("generated/addonPlaybackFixtures"))
    }
    // Explicit opt-in, disposable-emulator-only tests for the exact signed APK.
    // Never mix the ordinary debug suite's data-clearing fixtures into release.
    if (testBuildType == "release") {
        sourceSets.getByName("androidTest").setRoot("src/androidTestRelease")
    }
    namespace = "com.streammate.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.streammate.tv"
        minSdk = 23
        targetSdk = 36
        // Every distributed APK gets a new code; never reuse a released beta.
        versionCode = 14
        versionName = "0.1.0-beta.13"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // AGP leaves the instrumentation timeout at a year, so one hung test
        // stalls the whole suite instead of failing. Five minutes is far longer
        // than any test here needs and short enough to keep a hang reportable.
        testInstrumentationRunnerArguments["timeout_msec"] = "300000"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            if (releaseSigningPropertiesFile.isFile) {
                storeFile = rootProject.file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        create("demo") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            matchingFallbacks += listOf("debug")
        }

        release {
            isMinifyEnabled = false
            // The signing identity lives outside the repository, so CI and any
            // fresh clone have no keystore. Assigning the empty signing config
            // there fails validateSigningRelease; leaving it unset produces an
            // unsigned APK, which is enough to catch manifest, resource and
            // shrinker breakage. Local builds with the identity present are
            // signed exactly as before.
            if (releaseSigningPropertiesFile.isFile) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        create("lab") {
            initWith(getByName("release"))
            applicationIdSuffix = ".lab"
            versionNameSuffix = "-lab"
            // Persistent local development identity; never needs the production key.
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    dependenciesInfo {
        // Google Play encrypts this optional signing-block payload differently
        // on every APK build. Keep sideloaded release APKs byte-reproducible;
        // App Bundle dependency metadata remains available for a future store build.
        includeInApk = false
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// AGP 8.13.2 trims the non-debuggable test APK's desugared library independently
// of the app. Its partial j$.time classes shadow the app's complete classes and
// crash Application.onCreate before tests run. Keep the TEST copy complete;
// neither the Lab APK nor ordinary debug/release packaging is changed.
afterEvaluate {
    if (android.testBuildType in setOf("lab", "release")) {
        val variant = android.testBuildType.replaceFirstChar(Char::uppercaseChar)
        tasks.named<com.android.build.gradle.internal.tasks.L8DexDesugarLibTask>("l8DexDesugarLib${variant}AndroidTest") {
            keepRulesConfigurations.add("-keep class j$.** { *; }")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)

    implementation(project(":core"))
    implementation(project(":iptv"))
    implementation(project(":sportmate"))
    // Lazy Discover host owns the independent stores; no startup initializer.
    implementation(project(":addons"))
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.zxing.core)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kxml2)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.okhttp.mockwebserver)
}
