package fr.dreau.trackpacer.domain

/**
 * Everything the watch needs to beep at the right time, and nothing more.
 *
 * Pace is stored as seconds per kilometre, which conveniently equals
 * milliseconds per metre (4:45/km = 285 s/km = 285 ms/m).
 */
data class PaceSettings(
    val secondsPerKm: Int = 285,
    /** Cue interval, 50 m to 1 km in 50 m steps. */
    val intervalMeters: Int = 100,
    /** Rep length; 0 = free (the rep ends when the runner presses Recovery). */
    val repMeters: Int = 400,
    /** Recovery timer between reps; 0 = free chrono, wait for Go. */
    val recoverySeconds: Int = 60,
    /** 3-2-1 ticks before each mark. The recovery end always has them. */
    val countdownCues: Boolean = false,
    /** With a timer: start the next rep by itself when the recovery ends. */
    val autoStart: Boolean = false,
    /** Switch to recovery by itself at the rep's theoretical end, instead of on the runner's press. */
    val autoRecovery: Boolean = false,
    val soundEnabled: Boolean = true,
    /** 1..4 → 25 %, 50 %, 75 %, 100 % of the media volume. */
    val volumeLevel: Int = 4,
    /** Raise the watch media volume to its maximum for the session (speaker only, never earbuds). */
    val boostMediaVolume: Boolean = true,
    val vibrationEnabled: Boolean = true,
    /** Costs a continuous optical sensor read: off by default to save the battery. */
    val showHeartRate: Boolean = false,
    val showDistance: Boolean = true,
    /** The display is the main battery drain: rely on ambient mode unless asked otherwise. */
    val keepScreenOn: Boolean = false,
) {
    /** Time budget for one interval (e.g. 100 m at 4:45/km = 28 500 ms). */
    val splitMs: Long get() = PaceMath.splitDurationMs(secondsPerKm, intervalMeters)

    val volumeGain: Float get() = volumeLevel.coerceIn(1, 4) / 4f

    val recoveryMs: Long get() = recoverySeconds * 1_000L
    val isTimedRecovery: Boolean get() = recoverySeconds > 0

    /** When the rep is due to end (block-relative ms), or 0 for a free rep. */
    val repEndMs: Long get() = if (repMeters > 0) repMeters.toLong() * secondsPerKm else 0L
    val hasRepDistance: Boolean get() = repMeters > 0

    /** Road intervals (500 m, 1 km) read better in kilometres. */
    val isRoadInterval: Boolean get() = intervalMeters >= ROAD_INTERVAL_METERS

    companion object {
        const val MIN_VOLUME_LEVEL = 1
        const val MAX_VOLUME_LEVEL = 4
        const val ROAD_INTERVAL_METERS = 500
        const val INTERVAL_STEP_METERS = 50
        const val MIN_INTERVAL_METERS = 50
        const val MAX_INTERVAL_METERS = 1_000
        /** Rep distance picker: 0 to 99 km, in 100 m steps. */
        const val MAX_REP_KM = 99
        const val REP_STEP_METERS = 100
        const val MAX_RECOVERY_MINUTES = 30
        const val MIN_SECONDS_PER_KM = 2 * 60      // 2:00/km
        const val MAX_SECONDS_PER_KM = 12 * 60 + 59 // 12:59/km
    }
}
