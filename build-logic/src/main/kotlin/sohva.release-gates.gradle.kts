import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.internal.os.OperatingSystem

// Size and method-size gates on the release APK (plan/05 §4.6, §4.8). Apply to :app after
// sohva.android.application. `checkReleaseGates` runs both; CI runs it on every push.

val components = extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
val androidExt = extensions.getByType(ApplicationExtension::class.java)
val dexdumpName = if (OperatingSystem.current().isWindows) "dexdump.exe" else "dexdump"
val dexdump = components.sdkComponents.sdkDirectory.map { sdk ->
    sdk.dir("build-tools/${androidExt.buildToolsVersion}").file(dexdumpName).asFile.absolutePath
}

components.onVariants(components.selector().withBuildType("release")) { variant ->
    val apkDir = variant.artifacts.get(SingleArtifact.APK)
    val size = tasks.register<ApkSizeGateTask>("checkReleaseApkSize") {
        group = "verification"
        description = "Fails when the release APK exceeds its size budget."
        apkDirectory.set(apkDir)
        baselineFile.set(rootProject.layout.projectDirectory.file("config/size-baseline.txt"))
        report.set(layout.buildDirectory.file("reports/release-gates/apk-size.txt"))
    }
    val methods = tasks.register<MethodSizeGateTask>("checkReleaseMethodSizes") {
        group = "verification"
        description = "Fails when a release method is too large for ART's ahead-of-time compiler."
        apkDirectory.set(apkDir)
        dexdumpPath.set(dexdump)
        report.set(layout.buildDirectory.file("reports/release-gates/method-sizes.txt"))
    }
    tasks.register("checkReleaseGates") {
        group = "verification"
        description = "Runs every release gate that needs no device."
        dependsOn(size, methods)
    }
}
