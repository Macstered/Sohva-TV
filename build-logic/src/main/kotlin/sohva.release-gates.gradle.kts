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

components.onVariants { variant ->
    if (variant.buildType !in setOf("release", "play")) return@onVariants
    val kind = variant.name.replaceFirstChar(Char::uppercaseChar)
    val reportDirectory = if (variant.buildType == "release") "release-gates" else "play-gates"
    val apkDir = variant.artifacts.get(SingleArtifact.APK)
    val size = tasks.register<ApkSizeGateTask>("check${kind}ApkSize") {
        group = "verification"
        description = "Fails when the release APK exceeds its size budget."
        apkDirectory.set(apkDir)
        baselineFile.set(rootProject.layout.projectDirectory.file("config/size-baseline.txt"))
        report.set(layout.buildDirectory.file("reports/$reportDirectory/apk-size.txt"))
    }
    val methods = tasks.register<MethodSizeGateTask>("check${kind}MethodSizes") {
        group = "verification"
        description = "Fails when a release method is too large for ART's ahead-of-time compiler."
        apkDirectory.set(apkDir)
        dexdumpPath.set(dexdump)
        report.set(layout.buildDirectory.file("reports/$reportDirectory/method-sizes.txt"))
    }
    val gates = tasks.register("check${kind}Gates") {
        group = "verification"
        description = "Runs every release gate that needs no device."
        dependsOn(size, methods)
    }
    if (variant.buildType == "play") {
        val artifacts = tasks.register<PlayArtifactGateTask>("checkPlayArtifacts") {
            group = "verification"
            description = "Checks the Play APK and bundle identity, TV manifest and generated profiles."
            apkDirectory.set(apkDir)
            bundle.set(variant.artifacts.get(SingleArtifact.BUNDLE))
            aapt2Path.set(components.sdkComponents.sdkDirectory.map { sdk ->
                sdk.file("build-tools/${androidExt.buildToolsVersion}/${if (OperatingSystem.current().isWindows) "aapt2.exe" else "aapt2"}").asFile.absolutePath
            })
            report.set(layout.buildDirectory.file("reports/play-gates/artifacts.txt"))
        }
        gates.configure { dependsOn(artifacts) }
    }
}
