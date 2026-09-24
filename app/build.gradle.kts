import java.util.Properties

plugins {
    id("sohva.android.application")
    id("sohva.android.compose")
    id("sohva.release-gates")
}

// The permanent release identity (plan/00 §6). The keystore and its passwords stay in the
// ignored .local/ folder; without the file the release is built unsigned, which is enough for CI
// to catch R8, resource and manifest breakage (plan/05 §3.5).
val signingFile: File = rootProject.file(".local/streammate-signing/keystore.properties")
val signingProps: Properties? = signingFile.takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

// Trakt client credentials stay in the ignored .local/ folder; builds without them (CI, public
// clones) compile a Trakt-less app (plan/05 §3.4, §4.12).
val traktConfigured: Boolean = rootProject.file(".local/trakt/trakt-credentials.properties").isFile

android {
    namespace = "com.sohva.tv.app"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.streammate.tv"
        // Beta 23 is build 57; the rebuild starts at 100 / 0.2.0-beta.1 (decision A3).
        versionCode = 100
        versionName = "0.2.0-beta.1"
        buildConfigField("boolean", "TRAKT_CONFIGURED", traktConfigured.toString())
    }

    signingConfigs {
        if (signingProps != null) {
            create("release") {
                val keys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
                val missing = keys.filter { signingProps.getProperty(it).isNullOrBlank() }
                check(missing.isEmpty()) { "keystore.properties is missing $missing" }
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BUILD_KIND", "\"DEBUG\"")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            buildConfigField("String", "BUILD_KIND", "\"RELEASE\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // Release code as a separately installable package for measurements and the Lab suites.
        // Minified like release, so what is measured is what ships (plan/05 §4.5).
        create("lab") {
            buildConfigField("String", "BUILD_KIND", "\"LAB\"")
            initWith(getByName("release"))
            applicationIdSuffix = ".lab"
            versionNameSuffix = "-lab"
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
        // Screenshots and demos with fictional content from the demo source set.
        create("demo") {
            buildConfigField("String", "BUILD_KIND", "\"DEMO\"")
            initWith(getByName("debug"))
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            matchingFallbacks += "debug"
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.profileinstaller)
}
