package fr.dreau.trackpacer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceMathTest {

    private val s = PaceSettings(secondsPerKm = 285, intervalMeters = 100) // 4:45/km → 28.5 s per 100 m

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
    fun nextEvent_returnsPreBeepsThenMarkThenLap() {
        val e1 = PaceMath.nextEvent(0, s)
        assertEquals(SplitEvent(25_500L, 100, SplitEvent.Kind.PRE_BEEP, 3), e1)
        val e2 = PaceMath.nextEvent(e1.atMs, s)
        assertEquals(SplitEvent(26_500L, 100, SplitEvent.Kind.PRE_BEEP, 2), e2)
        val e3 = PaceMath.nextEvent(e2.atMs, s)
        assertEquals(SplitEvent(27_500L, 100, SplitEvent.Kind.PRE_BEEP, 1), e3)
        val e4 = PaceMath.nextEvent(e3.atMs, s)
        assertEquals(SplitEvent(28_500L, 100, SplitEvent.Kind.MARK), e4)

        val lap = PaceMath.nextEvent(113_000L, s)
        assertEquals(SplitEvent(114_000L, 400, SplitEvent.Kind.LAP), lap)
    }

    @Test
    fun nextEvent_skipsPreBeepsWhenDisabledOrSplitTooShort() {
        val noPre = s.copy(preBeepsEnabled = false)
        assertEquals(SplitEvent.Kind.MARK, PaceMath.nextEvent(0, noPre).kind)

        val fast = PaceSettings(secondsPerKm = 100, intervalMeters = 50) // 5 s split
        assertEquals(SplitEvent.Kind.MARK, PaceMath.nextEvent(0, fast).kind)
        assertEquals(5_000L, PaceMath.nextEvent(0, fast).atMs)
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
        assertEquals("350 m", PaceMath.formatDistance(350.7))
        assertEquals("10.50 km", PaceMath.formatDistance(10_500.0))
    }

    @Test
    fun progressInSplit_isBounded() {
        assertEquals(0f, PaceMath.progressInSplit(0, 28_500L), 1e-6f)
        assertTrue(PaceMath.progressInSplit(14_250L, 28_500L) in 0.49f..0.51f)
        assertEquals(0f, PaceMath.progressInSplit(28_500L, 28_500L), 1e-6f)
    }
}
