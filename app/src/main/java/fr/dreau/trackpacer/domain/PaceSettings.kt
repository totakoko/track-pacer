package fr.dreau.trackpacer.domain

/**
 * User configuration for a pacing session.
 *
 * Pace is stored as seconds per kilometre, which conveniently equals
 * milliseconds per metre (4:45/km = 285 s/km = 285 ms/m).
 */
data class PaceSettings(
    val secondsPerKm: Int = 285,
    val intervalMeters: Int = 100,
    val soundEnabled: Boolean = true,
    val preBeepsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val showHeartRate: Boolean = true,
    val showDistance: Boolean = true,
    val keepScreenOn: Boolean = true,
) {
    /** Time budget for one interval (e.g. 100 m at 4:45/km = 28 500 ms). */
    val splitMs: Long get() = PaceMath.splitDurationMs(secondsPerKm, intervalMeters)

    companion object {
        val INTERVAL_CHOICES = listOf(50, 100, 200, 300, 400)
        const val MIN_SECONDS_PER_KM = 2 * 60      // 2:00/km
        const val MAX_SECONDS_PER_KM = 12 * 60 + 59 // 12:59/km
    }
}
