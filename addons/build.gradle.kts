plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sohva.tv.addons"
    compileSdk = 36
    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    testOptions.targetSdk = 36
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    lint.abortOnError = true
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
// Both variants export the same schema. Serialize export when a combined Lab/
// unit-test build schedules them together, so neither reads a half-written file.
tasks.matching { it.name == "kspReleaseKotlin" }.configureEach { mustRunAfter("kspDebugKotlin") }
kotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }

// Explicit opt-in only: the property is a LOCAL FILE PATH, never a configured URL.
tasks.withType<Test>().configureEach {
    systemProperty("sohva.addon.liveInput", providers.gradleProperty("addonLiveInput").orElse("").get())
    // Separate unauthenticated link-service probe: never reads configured addon URLs.
    systemProperty("sohva.stremio.linkProbe", providers.gradleProperty("addonStremioLinkProbe").orElse("false").get())
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
}
