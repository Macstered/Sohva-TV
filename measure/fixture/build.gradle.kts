// Measurement builds only (Lab and benchmarkRelease): writes the owner-scale synthetic guide into
// the app's own database so the benchmarks measure the real screens at owner scale (plan/07 §6.1).
plugins {
    id("sohva.android.library")
}

android {
    namespace = "com.sohva.tv.measure.fixture"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
}
