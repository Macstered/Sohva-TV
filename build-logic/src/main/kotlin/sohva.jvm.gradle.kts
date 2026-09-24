// Pure Kotlin modules (:core:model): no Android, no Compose, fast JVM tests (plan/03 §4.2).
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = SohvaBuild.JAVA_VERSION
    targetCompatibility = SohvaBuild.JAVA_VERSION
}

configureKotlinCompile()
