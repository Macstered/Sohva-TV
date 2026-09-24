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
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

/** Finds the single APK AGP wrote into a variant's APK artifact directory. */
internal fun File.singleApk(): File {
    val apks = walkTopDown().filter { it.isFile && it.extension == "apk" }.toList()
    return apks.singleOrNull() ?: throw GradleException("Expected one APK in $this, found ${apks.map { it.name }}")
}

/**
 * APK size gate (plan/05 §4.8): fails above the hard ceiling, above the milestone budget, or when
 * the APK grew more than the allowed step over the committed baseline. The baseline file changes
 * only together with a docs/decisions.md entry naming the cause.
 */
@CacheableTask
abstract class ApkSizeGateTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselineFile: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val apk = apkDirectory.get().asFile.singleApk()
        val size = apk.length()
        val baseline = SizeBaseline.parse(baselineFile.get().asFile)
        val dexBytes = ZipFile(apk).use { zip ->
            zip.entries().asSequence().filter { it.name.matches(Regex("classes\\d*\\.dex")) }.sumOf { it.size }
        }
        val lines = listOf(
            "apk=${apk.name}",
            "apkBytes=$size",
            "dexBytesUncompressed=$dexBytes",
            "baselineBytes=${baseline.apkBytes}",
            "milestoneBudgetBytes=${baseline.milestoneBudgetBytes}",
            "ceilingBytes=${SizeBaseline.CEILING_BYTES}",
        )
        report.get().asFile.writeText(lines.joinToString("\n", postfix = "\n"))
        logger.lifecycle("APK size: $size bytes (dex $dexBytes uncompressed; baseline ${baseline.apkBytes})")

        val failures = buildList {
            if (size > SizeBaseline.CEILING_BYTES) add("above the 10 MB ceiling")
            if (size > baseline.milestoneBudgetBytes) add("above the milestone budget ${baseline.milestoneBudgetBytes}")
            if (size > baseline.apkBytes + SizeBaseline.MAX_GROWTH_BYTES) {
                add("grew ${size - baseline.apkBytes} bytes over the baseline (limit ${SizeBaseline.MAX_GROWTH_BYTES})")
            }
        }
        if (failures.isNotEmpty()) {
            throw GradleException("APK size gate: ${apk.name} is $size bytes, ${failures.joinToString("; ")}.")
        }
    }
}

internal data class SizeBaseline(val apkBytes: Long, val milestoneBudgetBytes: Long) {
    companion object {
        const val CEILING_BYTES: Long = 10L * 1024 * 1024
        const val MAX_GROWTH_BYTES: Long = 200L * 1024

        fun parse(file: File): SizeBaseline {
            val values = file.readLines()
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim().toLong() }
            return SizeBaseline(
                apkBytes = values["apkBytes"] ?: throw GradleException("$file has no apkBytes"),
                milestoneBudgetBytes = values["milestoneBudgetBytes"] ?: throw GradleException("$file has no milestoneBudgetBytes"),
            )
        }
    }
}

/**
 * Method-size gate (plan/05 §4.6, lessons 3.3): ART does not compile a method above 10,000 dex
 * code units ahead of time, so it runs interpreted after every start. Fails any app method above
 * 95 % of that limit (plan/05 §3.10 gates app methods) and reports the largest ones, library
 * methods above the limit included.
 */
@CacheableTask
abstract class MethodSizeGateTask : DefaultTask() {
    @get:Inject
    abstract val exec: ExecOperations

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirectory: DirectoryProperty

    @get:Input
    abstract val dexdumpPath: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val apk = apkDirectory.get().asFile.singleApk()
        val work = temporaryDir.apply { deleteRecursively(); mkdirs() }
        val dexFiles = ZipFile(apk).use { zip ->
            zip.entries().asSequence().filter { it.name.matches(Regex("classes\\d*\\.dex")) }.map { entry ->
                File(work, entry.name).also { out -> zip.getInputStream(entry).use { it.copyTo(out.outputStream()) } }
            }.toList()
        }
        val methods = dexFiles.flatMap { dex ->
            val out = ByteArrayOutputStream()
            exec.exec {
                commandLine(dexdumpPath.get(), dex.absolutePath)
                standardOutput = out
            }
            DexdumpParser.methodSizes(out.toString(Charsets.UTF_8))
        }
        val largest = methods.sortedByDescending { it.codeUnits }
        val appMethods = methods.count { it.isAppCode() }
        report.get().asFile.writeText(
            buildString {
                appendLine("methods=${methods.size}")
                appendLine("appMethods=$appMethods")
                appendLine("limit=$LIMIT")
                largest.filter { it.codeUnits > LIMIT && !it.isAppCode() }.forEach {
                    appendLine("libraryAboveLimit=${it.codeUnits} ${it.owner}.${it.name}")
                }
                largest.take(20).forEach { appendLine("${it.codeUnits} ${it.owner}.${it.name}") }
            },
        )
        val top = largest.firstOrNull()
        val topApp = largest.firstOrNull { it.isAppCode() }
        logger.lifecycle(
            "Method sizes: ${methods.size} methods ($appMethods app); largest ${top?.codeUnits} (${top?.owner}.${top?.name}); " +
                "largest app ${topApp?.codeUnits} (${topApp?.owner}.${topApp?.name})",
        )
        // Only app code can be split; library methods above the limit are reported, not failed.
        val tooBig = largest.filter { it.codeUnits > LIMIT && it.isAppCode() }
        if (tooBig.isNotEmpty()) {
            throw GradleException(
                "Method-size gate: ${tooBig.size} method(s) above $LIMIT code units, first " +
                    "${tooBig.first().owner}.${tooBig.first().name} (${tooBig.first().codeUnits}). " +
                    "Split the function or pass a state object instead of many parameters.",
            )
        }
    }

    private companion object {
        const val LIMIT = 9_500
    }
}

internal data class DexMethodSize(val owner: String, val name: String, val codeUnits: Int) {
    fun isAppCode(): Boolean = owner.startsWith("com.sohva.tv.") || owner.startsWith("com.streammate.tv.")
}

/** Reads `dexdump` text output: class descriptor, method name, then "insns size : N 16-bit code units". */
internal object DexdumpParser {
    private val classLine = Regex("""^\s*Class descriptor\s*:\s*'L([^;]+);'""")
    private val nameLine = Regex("""^\s*name\s*:\s*'([^']*)'""")
    private val insnsLine = Regex("""^\s*insns size\s*:\s*(\d+) 16-bit code units""")

    fun methodSizes(text: String): List<DexMethodSize> {
        var owner = ""
        var name = ""
        val result = ArrayList<DexMethodSize>()
        text.lineSequence().forEach { line ->
            classLine.find(line)?.let { owner = it.groupValues[1].replace('/', '.'); return@forEach }
            nameLine.find(line)?.let { name = it.groupValues[1]; return@forEach }
            insnsLine.find(line)?.let { result += DexMethodSize(owner, name, it.groupValues[1].toInt()) }
        }
        return result
    }
}
