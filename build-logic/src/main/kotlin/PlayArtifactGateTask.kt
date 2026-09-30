import com.android.aapt.Resources.XmlElement
import com.android.aapt.Resources.XmlNode
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.util.zip.ZipFile
import javax.inject.Inject

/** Inspect the actual shipping artifacts; no signing key or emulator is needed in CI. */
@CacheableTask
abstract class PlayArtifactGateTask : DefaultTask() {
    @get:Inject abstract val exec: ExecOperations

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val bundle: RegularFileProperty

    @get:Input abstract val aapt2Path: Property<String>
    @get:OutputFile abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val apk = apkDirectory.get().asFile.singleApk()
        val output = ByteArrayOutputStream()
        exec.exec {
            commandLine(aapt2Path.get(), "dump", "badging", apk.absolutePath)
            standardOutput = output
        }
        val badging = output.toString(Charsets.UTF_8)
        requireArtifact("package: name='$PACKAGE'" in badging, "APK application ID")
        requireArtifact("application-debuggable" !in badging, "APK must not be debuggable")
        requireArtifact(INSTALL_PERMISSION !in badging, "APK must not request package installation")
        // aapt2 badging only reports the touch launcher on some SDK versions; inspect the TV category.
        output.reset()
        exec.exec {
            commandLine(aapt2Path.get(), "dump", "xmltree", "--file", "AndroidManifest.xml", apk.absolutePath)
            standardOutput = output
        }
        requireArtifact("android.intent.category.LEANBACK_LAUNCHER" in output.toString(Charsets.UTF_8), "APK needs a TV launcher")
        val apkVersion = Regex("versionCode='(\\d+)'").find(badging)?.groupValues?.get(1)
            ?: throw GradleException("Play APK has no version code")
        ZipFile(apk).use { zip ->
            requireEntry(zip, "assets/dexopt/baseline.prof")
            requireEntry(zip, "assets/dexopt/baseline.profm")
        }
        ZipFile(bundle.get().asFile).use { zip ->
            val manifest = zip.getInputStream(requireEntry(zip, "base/manifest/AndroidManifest.xml")).use {
                XmlNode.parseFrom(it).element
            }
            requireArtifact(manifest.attribute("package", "") == PACKAGE, "bundle application ID")
            requireArtifact(manifest.attribute("versionCode") == apkVersion, "APK and bundle versions differ")
            val children = manifest.elements()
            requireArtifact(children.none { it.name == "uses-permission" && it.attribute("name") == INSTALL_PERMISSION }, "bundle must not request package installation")
            requireArtifact(children.any { it.name == "uses-feature" && it.attribute("name") == "android.software.leanback" && it.attribute("required") == "true" }, "bundle must require Android TV")
            requireArtifact(children.any { it.name == "uses-feature" && it.attribute("name") == "android.hardware.touchscreen" && it.attribute("required") == "false" }, "bundle must not require touch")
            val app = children.single { it.name == "application" }
            requireArtifact(app.attribute("debuggable") != "true", "bundle must not be debuggable")
            requireArtifact(app.attributeList.any { it.name == "banner" }, "bundle needs a TV banner")
            requireArtifact(app.descendants().any { it.name == "category" && it.attribute("name") == "android.intent.category.LEANBACK_LAUNCHER" }, "bundle needs a TV launcher")
            requireArtifact(app.elements().any { it.name == "profileable" && it.attribute("shell") == "true" }, "bundle must be profileable")
            requireArtifact(app.descendants().filter { it.name == "provider" }.none {
                it.attribute("authorities")?.startsWith("com.streammate.tv") == true
            }, "bundle provider authority must use the Play application ID")
            requireEntry(zip, "base/dex/classes.dex")
            requireEntry(zip, "BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof")
            requireEntry(zip, "BUNDLE-METADATA/com.android.tools.build.profiles/baseline.profm")
        }
        report.get().asFile.writeText("package=$PACKAGE\nversionCode=$apkVersion\napk=${apk.name}\nbundle=${bundle.get().asFile.name}\nmanifest=pass\nprofiles=pass\n")
        logger.lifecycle("Play APK and bundle pass: $PACKAGE, build $apkVersion, TV manifest and generated profiles.")
    }

    private fun requireEntry(zip: ZipFile, path: String) = zip.getEntry(path)?.takeIf { it.size > 0 }
        ?: throw GradleException("Play artifact ${zip.name} is missing $path")

    private fun requireArtifact(pass: Boolean, message: String) {
        if (!pass) throw GradleException("Play artifact gate: $message")
    }

    private fun XmlElement.elements(): List<XmlElement> = childList.filter { it.hasElement() }.map { it.element }

    private fun XmlElement.descendants(): Sequence<XmlElement> = sequence {
        for (child in elements()) {
            yield(child)
            yieldAll(child.descendants())
        }
    }

    private fun XmlElement.attribute(name: String, namespace: String = ANDROID): String? =
        attributeList.firstOrNull { it.name == name && it.namespaceUri == namespace }?.let { attr ->
            attr.value.takeIf { it.isNotEmpty() } ?: when {
                attr.compiledItem.prim.hasBooleanValue() -> attr.compiledItem.prim.booleanValue.toString()
                attr.compiledItem.prim.hasIntDecimalValue() -> attr.compiledItem.prim.intDecimalValue.toString()
                else -> null
            }
        }

    private companion object {
        const val PACKAGE = "fi.luontra.sohvatv"
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        const val INSTALL_PERMISSION = "android.permission.REQUEST_INSTALL_PACKAGES"
    }
}
