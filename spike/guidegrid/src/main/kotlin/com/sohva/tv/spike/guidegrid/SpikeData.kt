package com.sohva.tv.spike.guidegrid

import androidx.compose.runtime.Immutable
import com.sohva.tv.core.model.text.Initials
import kotlin.random.Random

/** One programme, already formatted: labels are made once per data change, never per press. */
@Immutable
data class SpikeProgramme(
    val startMinute: Int,
    val endMinute: Int,
    val title: String,
    val time: String,
    val genre: Int,
)

/** One guide row, formatted. [programmes] are sorted and do not overlap. */
@Immutable
data class SpikeRow(
    val index: Int,
    val number: String,
    val name: String,
    val feed: String,
    val initials: String,
    val programmes: List<SpikeProgramme>,
)

/**
 * The synthetic guide of the spike: [CHANNELS] rows made on demand from their index, so no
 * catalogue-sized list exists (GUIDE-NFR-10). Minutes are relative to the window start; the
 * visible window is [WINDOW_MINUTES] long and "now" sits [NOW_MINUTE] into it.
 */
object SpikeData {
    const val CHANNELS: Int = 56_000
    const val WINDOW_MINUTES: Int = 180
    const val NOW_MINUTE: Int = 45
    private const val WINDOW_START_CLOCK: Int = 19 * 60 + 30 // 19.30

    private val names = listOf("Northstar", "Meridian", "Pulse", "Summit", "Harbor", "Lumen", "Cobalt", "Ember")
    private val suffixes = listOf("One", "Two", "Plus", "Max", "Live", "HD", "World", "Prime")
    private val titlesA = listOf("Signal", "Harbor", "North", "Glass", "Silent", "Hidden", "Morning", "Studio")
    private val titlesB = listOf("at Dawn", "Routes", "Horizon", "Kitchen", "Weather", "Lighthouse", "Report", "Eleven")
    private val durations = intArrayOf(15, 25, 30, 45, 55, 60, 90)

    fun row(index: Int): SpikeRow {
        val random = Random(index)
        val name = "${names[index % names.size]} ${suffixes[(index / names.size) % suffixes.size]} ${index + 1}"
        var minute = -random.nextInt(0, 60)
        val programmes = ArrayList<SpikeProgramme>(8)
        while (minute < WINDOW_MINUTES) {
            val end = minute + durations[random.nextInt(durations.size)]
            val title = "${titlesA[random.nextInt(titlesA.size)]} ${titlesB[random.nextInt(titlesB.size)]}"
            programmes += SpikeProgramme(minute, end, title, "${clock(minute)}–${clock(end)}", random.nextInt(4))
            minute = end
        }
        return SpikeRow(index, "${index + 1}", name, "HD · General", Initials.of(name), programmes)
    }

    private fun clock(minute: Int): String {
        val m = ((WINDOW_START_CLOCK + minute) % (24 * 60) + 24 * 60) % (24 * 60)
        return "%02d.%02d".format(m / 60, m % 60)
    }

    /** The block showing [minute] (or the last one before it), for keeping the time across rows. */
    fun blockAt(row: SpikeRow, minute: Float): Int {
        val visible = visibleBlocks(row)
        val hit = visible.indexOfFirst { row.programmes[it].endMinute > minute }
        return if (hit >= 0) visible[hit] else visible.lastOrNull() ?: -1
    }

    /** Indexes of the programmes inside the window. */
    fun visibleBlocks(row: SpikeRow): List<Int> = row.programmes.indices.filter {
        row.programmes[it].endMinute > 0 && row.programmes[it].startMinute < WINDOW_MINUTES
    }
}
