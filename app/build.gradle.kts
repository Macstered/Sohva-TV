import java.util.Properties

plugins {
    id("sohva.android.application")
    id("sohva.android.compose")
    id("sohva.release-gates")
    id("sohva.baselineprofile.consumer")
}

// The permanent release identity (plan/00 §6). The keystore and its passwords stay in the
// ignored .local/ folder; without the file the release is built unsigned, which is enough for CI
// to catch R8, resource and manifest breakage (plan/05 §3.5).
val signingFile: File = rootProject.file(".local/streammate-signing/keystore.properties")
val signingProps: Properties? = signingFile.takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

// Google Play's upload key (docs/play/README.md §2), when the owner has made one; also only in .local/.
val playUploadFile: File = rootProject.file(".local/play-upload/keystore.properties")
val playUploadProps: Properties? = playUploadFile.takeIf { it.isFile }?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

// Trakt client credentials stay in the ignored .local/ folder; builds without them (CI, public
// clones) compile an app whose Accounts panel says Trakt is not configured (spec 51 FR-01).
val traktFile: File = rootProject.file(".local/trakt/trakt-credentials.properties")
val traktProps: Properties? = traktFile.takeIf { it.isFile }?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

/** Printable ASCII without quotes or backslashes (FR-01); anything else is left out. */
fun traktValue(key: String): String = traktProps?.getProperty(key)?.trim()?.takeIf { v -> v.isNotEmpty() && v.all { it in '!'..'~' && it != '"' && it != '\\' } }.orEmpty()

android {
    namespace = "com.sohva.tv.app"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.streammate.tv"
        // Beta 23 is build 57; the rebuild started at 100 / 0.2.0-beta.1 (decision A3); 100 to 103 went to the owner's devices only; 104 was 0.2.0-beta.1, 105 0.2.0-beta.2, 106 to 111 went to the owner's Shield only; 112 was 0.2.0-beta.3.
        versionCode = 121
        versionName = "0.2.0-beta.11"
        // The emulator's update test (tools/update_e2e.py) builds two local releases above these;
        // they never leave the emulator (decision "Updater test").
        providers.gradleProperty("sohva.versionCode").orNull?.let { versionCode = it.toInt() }
        providers.gradleProperty("sohva.versionName").orNull?.let { versionName = it }
        buildConfigField("String", "TRAKT_CLIENT_ID", "\"${traktValue("TRAKT_CLIENT_ID")}\"")
        buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"${traktValue("TRAKT_CLIENT_SECRET")}\"")
        // The public release feed (spec 72 §7.1). The emulator's update test builds a release against a
        // local feed with -Psohva.updateFeed; nothing published is built that way (decision "Updater test").
        val feed = providers.gradleProperty("sohva.updateFeed").getOrElse("https://api.github.com/repos/Macstered/Sohva-TV/releases?per_page=10")
        buildConfigField("String", "UPDATE_FEED", "\"$feed\"")
    }

    signingConfigs {
        if (playUploadProps != null) {
            create("playUpload") {
                val keys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
                val missing = keys.filter { playUploadProps.getProperty(it).isNullOrBlank() }
                check(missing.isEmpty()) { "play-upload/keystore.properties is missing $missing" }
                storeFile = rootProject.file(playUploadProps.getProperty("storeFile"))
                storePassword = playUploadProps.getProperty("storePassword")
                keyAlias = playUploadProps.getProperty("keyAlias")
                keyPassword = playUploadProps.getProperty("keyPassword")
            }
        }
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
        // Google Play: release code and profiles, without the in-app updater, which Play's policy
        // forbids (an app on Play updates only through Play). Its own application ID, set below:
        // com.streammate.tv was taken on Play (decision "Play application ID"). Signed with the Play
        // upload key when .local/play-upload/keystore.properties exists, else the release key (local tests).
        create("play") {
            initWith(getByName("release"))
            buildConfigField("String", "BUILD_KIND", "\"PLAY\"")
            signingConfig = signingConfigs.findByName("playUpload") ?: signingConfigs.findByName("release")
            matchingFallbacks += "release"
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

// Google Play's own application ID (decision "Play application ID"); the GitHub build keeps
// com.streammate.tv, which its installs and in-app updater depend on.
androidComponents {
    // The profile plugin copies release's source-set manifest to its generated build type.
    // Restore the benchmark-only fixture manifest after that copy; it never enters release/Play.
    finalizeDsl { extension ->
        extension.sourceSets.getByName("benchmarkRelease").manifest.srcFile("src/benchmarkRelease/AndroidManifest.xml")
    }
    onVariants(selector().withBuildType("play")) { variant ->
        variant.applicationId.set("fi.luontra.sohvatv")
    }
}

// The guide-grid spike (M0) and the owner-scale fixture live only in the measurement builds: Lab and benchmarkRelease.
// The baseline-profile plugin creates benchmarkRelease after this script runs.
configurations.configureEach {
    if (name == "benchmarkReleaseImplementation") {
        dependencies.add(project.dependencies.create(project(":spike:guidegrid")))
        dependencies.add(project.dependencies.create(project(":measure:fixture")))
        dependencies.add(project.dependencies.create(libs.androidx.room.runtime.get()))
    }
}

dependencies {
    "labImplementation"(project(":spike:guidegrid"))
    "labImplementation"(project(":measure:fixture"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:net"))
    implementation(project(":core:sync"))
    implementation(project(":ui:design"))
    implementation(project(":feature:home"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:sport"))
    implementation(project(":feature:discover"))
    implementation(project(":feature:trakt"))
    implementation(project(":feature:live"))
    implementation(project(":feature:player"))
    implementation(project(":feature:channels"))
    implementation(project(":feature:library"))
    implementation(project(":feature:organize"))
    implementation(project(":feature:search"))
    implementation(project(":feature:profiles"))
    implementation(project(":core:player"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.tracing)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.core)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)

    // The beta 23 upgrade test writes beta 23's settings file as beta 23 did (decision A1).
    androidTestImplementation(libs.androidx.datastore.preferences)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.okhttp.mockwebserver)
    // Answers the document pickers in the backup test (spec 71 §11).
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
