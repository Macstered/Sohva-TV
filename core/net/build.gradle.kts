plugins {
    id("sohva.jvm")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    api(libs.okhttp)
    api(libs.okio)
    implementation(libs.moshi)
    // Encoding only; R8 removes the readers (spec 11 §7).
    implementation(libs.zxing.core)
    // The XMLTV reader uses the platform's XmlPullParser on Android; kXML2 only supplies the
    // interfaces to compile against and the parser for JVM tests (bundling it clashed in beta 23).
    compileOnly(libs.kxml2)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kxml2)
}
