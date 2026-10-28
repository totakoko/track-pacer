package fr.dreau.trackpacer.domain

import java.util.Locale
import kotlin.math.abs

/** Pure pacing arithmetic. No Android dependency, fully unit-testable. */
object PaceMath {
    /** On the track a "lap" is 400 m; on the road (500 m / 1 km marks) the distinct cue comes every 5 km. */
    const val TRACK_LAP_METERS = 400
    const val ROAD_LAP_METERS = 5_000

    /** The 3-2-1 ticks, milliseconds before the event they announce. */
    val COUNTDOWN_OFFSETS_MS = longArrayOf(3_000, 2_000, 1_000)
    private val NO_OFFSETS = longArrayOf()

    /** A pre-cue is dropped when it would fall too close to the previous mark. */
    private const val MIN_GAP_AFTER_PREVIOUS_MARK_MS = 2_500L

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

    fun lapMeters(intervalMeters: Int): Int =
        if (intervalMeters >= PaceSettings.ROAD_INTERVAL_METERS) ROAD_LAP_METERS else TRACK_LAP_METERS

    fun isLapMark(distanceMeters: Int, intervalMeters: Int): Boolean =
        distanceMeters % lapMeters(intervalMeters) == 0

    /** Next mark cue strictly after [elapsedMs]: the optional 3-2-1, then the mark itself. */
    fun nextMarkEvent(elapsedMs: Long, settings: PaceSettings): SplitEvent {
        val splitMs = settings.splitMs
        val index = nextSplitIndex(elapsedMs, splitMs)
        val markAt = index * splitMs
        val meters = index * settings.intervalMeters
        val kind = if (isLapMark(meters, settings.intervalMeters)) SplitEvent.Kind.LAP else SplitEvent.Kind.MARK
        return cuesTowards(elapsedMs, markAt, markOffsets(settings), meters, kind)
    }

    /**
     * Next cue of a rep, strictly after [elapsedMs]: the pace marks and, when the rep has a
     * distance, its end ([SplitEvent.Kind.REP_END]), which replaces the mark it falls on.
     * Null once a measured rep is past its end: the chrono keeps running until the runner presses.
     */
    fun nextRepEvent(elapsedMs: Long, settings: PaceSettings): SplitEvent? {
        val endAt = settings.repEndMs
        if (endAt <= 0) return nextMarkEvent(elapsedMs, settings)
        if (elapsedMs >= endAt) return null
        val mark = nextMarkEvent(elapsedMs, settings)
        val end = cuesTowards(elapsedMs, endAt, markOffsets(settings), settings.repMeters, SplitEvent.Kind.REP_END)
        return if (end.atMs <= mark.atMs) end else mark
    }

    /**
     * Next cue of a timed recovery, strictly after [elapsedMs]: always a 3-2-1, then the end
     * (a lap-style signal when the next rep starts by itself, a plain mark when the runner
     * must press Go). Null once the recovery is over or when untimed.
     */
    fun nextRecoveryEvent(elapsedMs: Long, settings: PaceSettings): SplitEvent? {
        val endAt = settings.recoveryMs
        if (endAt <= 0 || elapsedMs >= endAt) return null
        val endKind = if (settings.autoStart) SplitEvent.Kind.LAP else SplitEvent.Kind.MARK
        return cuesTowards(elapsedMs, endAt, COUNTDOWN_OFFSETS_MS, 0, endKind)
    }

    private fun markOffsets(settings: PaceSettings): LongArray =
        if (settings.countdownCues) COUNTDOWN_OFFSETS_MS else NO_OFFSETS

    /** The pre-cues before [endAt], then the end itself; first one strictly after [elapsedMs]. */
    private fun cuesTowards(elapsedMs: Long, endAt: Long, offsets: LongArray, meters: Int, endKind: SplitEvent.Kind): SplitEvent {
        for (offset in offsets) {
            if (offset > endAt - MIN_GAP_AFTER_PREVIOUS_MARK_MS) continue
            val at = endAt - offset
            if (at > elapsedMs) return SplitEvent(at, meters, SplitEvent.Kind.COUNT_BEEP, (offset / 1000).toInt())
        }
        return SplitEvent(endAt, meters, endKind)
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

    /** Countdown shown as whole seconds, rounded up so "1" is still displayed right before the mark. */
    fun formatCountdown(ms: Long): String {
        val seconds = (ms + 999) / 1000
        return if (seconds >= 60) formatChrono(seconds * 1000, tenths = false) else seconds.toString()
    }

    /** "+1.9s" / "−0.8s": actual minus target, tenths. */
    fun formatDelta(deltaMs: Long): String {
        val sign = if (deltaMs < 0) "−" else "+"
        return String.format(Locale.ROOT, "%s%.1fs", sign, abs(deltaMs) / 1000.0)
    }

    /**
     * Metres below [kmFromMeters], kilometres with two decimals from there on.
     * Track sessions keep metres up to 10 km; road sessions switch at 1 km.
     */
    fun formatDistance(meters: Double, kmFromMeters: Int = 10_000): String =
        if (meters >= kmFromMeters) String.format(Locale.ROOT, "%.2fkm", meters / 1000)
        else String.format(Locale.ROOT, "%dm", meters.toInt())

    /** "100m", "1km", "1.5km", "12.3km" for the pickers and the sentence. Units are glued to save width. */
    fun formatInterval(meters: Int): String = when {
        meters < 1000 -> "${meters}m"
        meters % 1000 == 0 -> "${meters / 1000}km"
        else -> String.format(Locale.ROOT, "%.1fkm", meters / 1000.0)
    }

    /** "1:30" for the duration pickers; the caller handles 0 ("free"). */
    fun formatDuration(seconds: Int): String = formatPace(seconds)
}

/** A scheduled cue: when it fires (block-relative ms), which mark it belongs to, and its nature. */
data class SplitEvent(
    val atMs: Long,
    val markMeters: Int,
    val kind: Kind,
    /** For countdown ticks: seconds remaining before the event. */
    val secondsBefore: Int = 0,
) {
    enum class Kind { COUNT_BEEP, MARK, LAP, REP_END }

    val isPreCue: Boolean get() = kind == Kind.COUNT_BEEP
}
