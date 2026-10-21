package fr.dreau.trackpacer.domain

/**
 * When the watch warns before a mark. Offsets are milliseconds before the mark.
 * Offsets of 5 s or more are "early" cues (lower pitch, time to correct the stride);
 * shorter ones are the "countdown" (precision at the mark).
 */
enum class CueProfile(val offsetsMs: LongArray) {
    /** Only the mark itself. */
    MARK_ONLY(longArrayOf()),
    /** 3-2-1 then the mark. Precise, little time to react. */
    COUNTDOWN_3(longArrayOf(3_000, 2_000, 1_000)),
    /** One early cue 10 s before, then the mark. */
    EARLY_10(longArrayOf(10_000)),
    /** Early cue 10 s before, then 3-2-1 and the mark. Default. */
    EARLY_10_COUNTDOWN_3(longArrayOf(10_000, 3_000, 2_000, 1_000)),
    /** Early cues 15 s and 5 s before, then the mark. */
    EARLY_15_5(longArrayOf(15_000, 5_000));

    companion object {
        /** Cues at or beyond this offset are "early" cues. */
        const val EARLY_THRESHOLD_MS = 5_000L

        val DEFAULT = EARLY_10_COUNTDOWN_3

        fun fromName(name: String?): CueProfile = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
