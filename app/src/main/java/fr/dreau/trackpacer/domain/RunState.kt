package fr.dreau.trackpacer.domain

/**
 * RUNNING: a paced rep, cues fire. RECOVERY: a chrono between two reps, silent except for
 * its own end. PAUSED freezes either of them and remembers which one in [RunState.pausedFrom].
 */
enum class RunPhase { IDLE, RUNNING, RECOVERY, PAUSED, FINISHED }

/**
 * One completed chronometer block: a paced rep (actual time, from Start/Go to the runner's
 * press) or the recovery that followed one. [targetMs] is the rep's theoretical time when it
 * had a distance, so the summary can show how far off the pace the runner was.
 */
data class Block(
    val number: Int,
    val durationMs: Long,
    val secondsPerKm: Int,
    val isRecovery: Boolean = false,
    val targetMs: Long? = null,
) {
    val theoreticalMeters: Double get() = PaceMath.theoreticalDistanceMeters(durationMs, secondsPerKm)
    val deltaMs: Long? get() = targetMs?.let { durationMs - it }
}

/**
 * Snapshot of the pacing session, written by the service and observed by the UI.
 * Time is measured with SystemClock.elapsedRealtime() so the UI can derive the live
 * chronometer without the service pushing a tick every frame.
 */
data class RunState(
    val phase: RunPhase = RunPhase.IDLE,
    val settings: PaceSettings = PaceSettings(),
    /** 1-based index of the current (or, during recovery, the last) rep. */
    val repNumber: Int = 0,
    /** Virtual elapsedRealtime() of the block start (shifted forward after each pause). */
    val blockStartRealtime: Long = 0L,
    /** Frozen chrono value while PAUSED. */
    val frozenElapsedMs: Long = 0L,
    /** Phase to return to on resume. */
    val pausedFrom: RunPhase = RunPhase.RUNNING,
    val completedBlocks: List<Block> = emptyList(),
    val heartRateBpm: Int? = null,
    /** Last mark hit, used by the UI for the flash cue. */
    val lastMark: MarkHit? = null,
    /** True if the foreground service could not be promoted (degraded timing in ambient mode). */
    val degraded: Boolean = false,
) {
    fun elapsedMs(nowRealtime: Long): Long = when (phase) {
        RunPhase.RUNNING, RunPhase.RECOVERY -> (nowRealtime - blockStartRealtime).coerceAtLeast(0L)
        RunPhase.PAUSED -> frozenElapsedMs
        else -> 0L
    }

    /** The paced rep that just ended, shown during recovery. */
    val lastRep: Block? get() = completedBlocks.lastOrNull { !it.isRecovery }

    val isActive: Boolean get() = phase == RunPhase.RUNNING || phase == RunPhase.RECOVERY || phase == RunPhase.PAUSED
}

data class MarkHit(val meters: Int, val isLap: Boolean, val atRealtime: Long)
