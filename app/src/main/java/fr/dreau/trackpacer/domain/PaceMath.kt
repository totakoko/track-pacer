package fr.dreau.trackpacer.domain

import java.util.Locale

/** Pure pacing arithmetic. No Android dependency, fully unit-testable. */
object PaceMath {
    const val LAP_METERS = 400

    /** Countdown beeps fire this many seconds before each mark. */
    val PRE_BEEP_OFFSETS_MS = longArrayOf(3_000L, 2_000L, 1_000L)

    /** Pre-beeps are only meaningful if the split is long enough to hear them apart. */
    private const val MIN_SPLIT_FOR_PRE_BEEPS_MS = 6_000L

    fun splitDurationMs(secondsPerKm: Int, intervalMeters: Int): Long =
        intervalMeters.toLong() * secondsPerKm

    /** 1-based index of the next mark not yet reached at [elapsedMs]. */
    fun nextSplitIndex(elapsedMs: Long, splitMs: Long): Int =
        (elapsedMs / splitMs).toInt() + 1

    /** Distance (m) of the next mark the runner should reach. */
    fun nextMarkMeters(elapsedMs: Long, settings: PaceSettings): Int =
        nextSplitIndex(elapsedMs, settings.splitMs) * settings.intervalMeters

    /** Milliseconds until the next mark. Always > 0. */
    fun msToNextMark(elapsedMs: Long, settings: PaceSettings): Long =
        nextSplitIndex(elapsedMs, settings.splitMs) * settings.splitMs - elapsedMs

    /** 0f..1f progression inside the current interval. */
    fun progressInSplit(elapsedMs: Long, splitMs: Long): Float =
        ((elapsedMs % splitMs).toFloat() / splitMs).coerceIn(0f, 1f)

    /** Where the runner *should* be, given the elapsed time and the target pace. */
    fun theoreticalDistanceMeters(elapsedMs: Long, secondsPerKm: Int): Double =
        elapsedMs.toDouble() / secondsPerKm

    fun isLapMark(distanceMeters: Int): Boolean = distanceMeters % LAP_METERS == 0

    /**
     * Next audible/haptic event strictly after [elapsedMs].
     * Candidates are the pre-beeps (if enabled and the split is long enough) and the mark itself.
     */
    fun nextEvent(elapsedMs: Long, settings: PaceSettings): SplitEvent {
        val splitMs = settings.splitMs
        val index = nextSplitIndex(elapsedMs, splitMs)
        val markAt = index * splitMs
        val meters = index * settings.intervalMeters
        if (settings.preBeepsEnabled && splitMs >= MIN_SPLIT_FOR_PRE_BEEPS_MS) {
            for (offset in PRE_BEEP_OFFSETS_MS) {
                val at = markAt - offset
                if (at > elapsedMs) {
                    return SplitEvent(at, meters, SplitEvent.Kind.PRE_BEEP, (offset / 1000).toInt())
                }
            }
        }
        val kind = if (isLapMark(meters)) SplitEvent.Kind.LAP else SplitEvent.Kind.MARK
        return SplitEvent(markAt, meters, kind)
    }

    fun formatPace(secondsPerKm: Int): String =
        String.format(Locale.ROOT, "%d:%02d", secondsPerKm / 60, secondsPerKm % 60)

    /** mm:ss or mm:ss.t; hours roll into minutes (99:59 max is plenty for a track block). */
    fun formatChrono(ms: Long, tenths: Boolean): String {
        val totalTenths = ms / 100
        val minutes = totalTenths / 600
        val seconds = (totalTenths / 10) % 60
        val tenth = totalTenths % 10
        return if (tenths) {
            String.format(Locale.ROOT, "%02d:%02d.%d", minutes, seconds, tenth)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatDistance(meters: Double): String =
        if (meters >= 10_000) String.format(Locale.ROOT, "%.2f km", meters / 1000)
        else String.format(Locale.ROOT, "%d m", meters.toInt())
}

/** A scheduled cue: when it fires (block-relative ms), which mark it belongs to, and its nature. */
data class SplitEvent(
    val atMs: Long,
    val markMeters: Int,
    val kind: Kind,
    /** For PRE_BEEP: seconds remaining before the mark (3, 2 or 1). */
    val secondsBefore: Int = 0,
) {
    enum class Kind { PRE_BEEP, MARK, LAP }
}
