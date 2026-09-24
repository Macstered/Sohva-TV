// Project lint rules that plain lint cannot see (plan/05 §4.11). Build-time only.
plugins {
    id("sohva.jvm")
}

dependencies {
    compileOnly(libs.lint.api)
    compileOnly(libs.lint.checks)
    testImplementation(libs.lint.api)
    testImplementation(libs.lint.checks)
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
}
