package fr.dreau.trackpacer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceMathTest {

    // 4:45/km → 28.5 s per 100 m, free rep, no countdown.
    private val s = PaceSettings(secondsPerKm = 285, intervalMeters = 100, repMeters = 0, recoverySeconds = 0)

    @Test
    fun splitDuration_isMetersTimesSecondsPerKm() {
        assertEquals(28_500L, s.splitMs)
        assertEquals(114_000L, PaceMath.splitDurationMs(285, 400))
    }

    @Test
    fun nextMark_progressesEveryInterval() {
        assertEquals(100, PaceMath.nextMarkMeters(0, s))
        assertEquals(100, PaceMath.nextMarkMeters(28_499, s))
        assertEquals(200, PaceMath.nextMarkMeters(28_500, s))
        assertEquals(400, PaceMath.nextMarkMeters(100_000, s))
    }

    @Test
    fun msToNextMark_countsDown() {
        assertEquals(28_500L, PaceMath.msToNextMark(0, s))
        assertEquals(1L, PaceMath.msToNextMark(28_499, s))
        assertEquals(28_500L, PaceMath.msToNextMark(28_500, s))
    }

    @Test
    fun marks_thenLapEvery400m_withoutCountdown() {
        assertEquals(SplitEvent(28_500L, 100, SplitEvent.Kind.MARK), PaceMath.nextRepEvent(0, s))
        assertEquals(SplitEvent(57_000L, 200, SplitEvent.Kind.MARK), PaceMath.nextRepEvent(28_500L, s))
        assertEquals(SplitEvent(114_000L, 400, SplitEvent.Kind.LAP), PaceMath.nextRepEvent(113_000L, s))
    }

    @Test
    fun countdown_ticksThreeTwoOneBeforeEachMark() {
        val c = s.copy(countdownCues = true)
        val e1 = PaceMath.nextRepEvent(0, c)!!
        assertEquals(SplitEvent(25_500L, 100, SplitEvent.Kind.COUNT_BEEP, 3), e1)
        val e2 = PaceMath.nextRepEvent(e1.atMs, c)!!
        assertEquals(SplitEvent(26_500L, 100, SplitEvent.Kind.COUNT_BEEP, 2), e2)
        val e3 = PaceMath.nextRepEvent(e2.atMs, c)!!
        assertEquals(SplitEvent(27_500L, 100, SplitEvent.Kind.COUNT_BEEP, 1), e3)
        assertEquals(SplitEvent(28_500L, 100, SplitEvent.Kind.MARK), PaceMath.nextRepEvent(e3.atMs, c))
    }

    @Test
    fun countdown_dropsTicksTooCloseToThePreviousMark() {
        // 5 s split: the 3 s tick would sit 2 s after the previous mark, so the countdown starts at 2.
        val fast = PaceSettings(secondsPerKm = 100, intervalMeters = 50, repMeters = 0, countdownCues = true)
        assertEquals(SplitEvent(3_000L, 50, SplitEvent.Kind.COUNT_BEEP, 2), PaceMath.nextRepEvent(0, fast))
    }

    @Test
    fun lapCue_isEvery400mOnTrackAndEvery5kmOnRoad() {
        // 500 m marks at 5:30/km: 2 km is a multiple of 400 but must stay a plain mark.
        val road = PaceSettings(secondsPerKm = 330, intervalMeters = 500, repMeters = 0)
        assertEquals(SplitEvent(660_000L, 2_000, SplitEvent.Kind.MARK), PaceMath.nextRepEvent(659_000L, road))
        assertEquals(SplitEvent(1_650_000L, 5_000, SplitEvent.Kind.LAP), PaceMath.nextRepEvent(1_649_000L, road))
    }

    @Test
    fun distanceRep_endsOnTheLastMark_thenStaysSilent() {
        // 400 m rep with 100 m marks: the rep end replaces the 400 m lap cue, then nothing until the press.
        val rep = s.copy(repMeters = 400, countdownCues = true)
        assertEquals(SplitEvent(28_500L, 100, SplitEvent.Kind.MARK), PaceMath.nextRepEvent(27_500L, rep))
        assertEquals(SplitEvent(111_000L, 400, SplitEvent.Kind.COUNT_BEEP, 3), PaceMath.nextRepEvent(100_000L, rep))
        assertEquals(SplitEvent(114_000L, 400, SplitEvent.Kind.REP_END), PaceMath.nextRepEvent(113_000L, rep))
        assertNull(PaceMath.nextRepEvent(114_000L, rep))
        assertEquals(114_000L, rep.repEndMs)
    }

    @Test
    fun distanceRep_offTheMarks_endsBetweenThem() {
        // 1500 m with 400 m cues: marks at 400, 800, 1200, then the end at 1500, never 1600.
        val rep = PaceSettings(secondsPerKm = 240, intervalMeters = 400, repMeters = 1_500)
        assertEquals(SplitEvent(288_000L, 1_200, SplitEvent.Kind.LAP), PaceMath.nextRepEvent(200_000L, rep))
        assertEquals(SplitEvent(360_000L, 1_500, SplitEvent.Kind.REP_END), PaceMath.nextRepEvent(288_000L, rep))
        assertNull(PaceMath.nextRepEvent(360_000L, rep))
    }

    @Test
    fun recoveryTimer_alwaysCountsDown_thenEndsThenStaysSilent() {
        val manual = PaceSettings(recoverySeconds = 90)
        assertEquals(SplitEvent(87_000L, 0, SplitEvent.Kind.COUNT_BEEP, 3), PaceMath.nextRecoveryEvent(0, manual))
        assertEquals(SplitEvent(89_000L, 0, SplitEvent.Kind.COUNT_BEEP, 1), PaceMath.nextRecoveryEvent(88_000L, manual))
        assertEquals(SplitEvent(90_000L, 0, SplitEvent.Kind.MARK), PaceMath.nextRecoveryEvent(89_000L, manual))
        assertNull(PaceMath.nextRecoveryEvent(90_000L, manual))

        val auto = manual.copy(autoStart = true)
        assertEquals(SplitEvent(90_000L, 0, SplitEvent.Kind.LAP), PaceMath.nextRecoveryEvent(89_500L, auto))

        assertNull(PaceMath.nextRecoveryEvent(0, manual.copy(recoverySeconds = 0)))
    }

    @Test
    fun block_deltaAgainstTarget() {
        val rep = Block(1, 95_100L, 285, targetMs = 93_200L)
        assertEquals(1_900L, rep.deltaMs)
        assertEquals("+1.9s", PaceMath.formatDelta(rep.deltaMs!!))
        assertEquals("−0.8s", PaceMath.formatDelta(-800L))
        assertNull(Block(1, 95_100L, 285).deltaMs)
    }

    @Test
    fun theoreticalDistance_followsPace() {
        assertEquals(1000.0, PaceMath.theoreticalDistanceMeters(285_000L, 285), 1e-9)
        assertEquals(350.0, PaceMath.theoreticalDistanceMeters(99_750L, 285), 1e-9)
    }

    @Test
    fun formatting() {
        assertEquals("4:45", PaceMath.formatPace(285))
        assertEquals("10:05", PaceMath.formatPace(605))
        assertEquals("02:14.3", PaceMath.formatChrono(134_350L, tenths = true))
        assertEquals("02:14", PaceMath.formatChrono(134_350L, tenths = false))
        assertEquals("350m", PaceMath.formatDistance(350.7))
        assertEquals("10.50km", PaceMath.formatDistance(10_500.0))
        assertEquals("1.25km", PaceMath.formatDistance(1_250.0, kmFromMeters = 1_000))
        assertEquals("1km", PaceMath.formatInterval(1_000))
        assertEquals("1.5km", PaceMath.formatInterval(1_500))
        assertEquals("21.1km", PaceMath.formatInterval(21_100))
        assertEquals("500m", PaceMath.formatInterval(500))
        assertEquals("1", PaceMath.formatCountdown(1L))
        assertEquals("42", PaceMath.formatCountdown(41_300L))
        assertEquals("05:30", PaceMath.formatCountdown(329_001L))
        assertEquals("1:30", PaceMath.formatDuration(90))
    }

    @Test
    fun repDistanceChoices_growTheirStepWithTheDistance() {
        val c = PaceSettings.REP_DISTANCE_CHOICES
        assertEquals(listOf(0, 50, 100, 150, 200, 300), c.take(6))
        assertTrue(3_000 in c && 3_200 in c && 3_100 !in c)
        assertTrue(5_000 in c && 5_500 in c && 5_200 !in c)
        assertTrue(10_000 in c && 11_000 in c && 10_500 !in c)
        assertEquals(99_000, c.last())
        assertEquals(c.sorted(), c)
    }

    @Test
    fun progressInSplit_isBounded() {
        assertEquals(0f, PaceMath.progressInSplit(0, 28_500L), 1e-6f)
        assertTrue(PaceMath.progressInSplit(14_250L, 28_500L) in 0.49f..0.51f)
        assertEquals(0f, PaceMath.progressInSplit(28_500L, 28_500L), 1e-6f)
    }
}
