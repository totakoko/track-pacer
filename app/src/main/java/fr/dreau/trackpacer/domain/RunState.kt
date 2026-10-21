package fr.dreau.trackpacer.domain

enum class RunPhase { IDLE, RUNNING, STOPPED, FINISHED }

/** One completed chronometer block (start → stop). */
data class Block(val number: Int, val durationMs: Long, val secondsPerKm: Int) {
    val theoreticalMeters: Double get() = PaceMath.theoreticalDistanceMeters(durationMs, secondsPerKm)
}

/**
 * Snapshot of the pacing session, written by the service and observed by the UI.
 * Time is measured with SystemClock.elapsedRealtime() so the UI can derive the live
 * chronometer without the service pushing a tick every frame.
 */
data class RunState(
    val phase: RunPhase = RunPhase.IDLE,
    val settings: PaceSettings = PaceSettings(),
    val blockNumber: Int = 0,
    /** elapsedRealtime() at the start of the current block. */
    val blockStartRealtime: Long = 0L,
    /** Frozen chrono value when the block is STOPPED. */
    val frozenElapsedMs: Long = 0L,
    val completedBlocks: List<Block> = emptyList(),
    val heartRateBpm: Int? = null,
    /** Last mark hit, used by the UI for the flash cue. */
    val lastMark: MarkHit? = null,
    /** True if the foreground service could not be promoted (degraded timing in ambient mode). */
    val degraded: Boolean = false,
) {
    fun elapsedMs(nowRealtime: Long): Long = when (phase) {
        RunPhase.RUNNING -> (nowRealtime - blockStartRealtime).coerceAtLeast(0L)
        RunPhase.STOPPED -> frozenElapsedMs
        else -> 0L
    }

    val isActive: Boolean get() = phase == RunPhase.RUNNING || phase == RunPhase.STOPPED
}

data class MarkHit(val meters: Int, val isLap: Boolean, val atRealtime: Long)
