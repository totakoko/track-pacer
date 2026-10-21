package fr.dreau.trackpacer.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import fr.dreau.trackpacer.domain.SplitEvent
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Synthesised percussive "ding" cues played through [AudioTrack] in static mode.
 *
 * Each tone is a fundamental plus a few harmonics with an exponential decay, which
 * sounds like a soft marimba rather than a raw sine. Pitches sit above 1 kHz because
 * the watch speaker cannot reproduce lower notes cleanly. USAGE_MEDIA routes to
 * Bluetooth earbuds when they are connected.
 */
class Beeper(initialVolume: Float = 1f) {
    private companion object {
        const val SAMPLE_RATE = 44_100
        const val C6 = 1046.5
        const val E6 = 1318.5
        const val G6 = 1568.0
        const val C7 = 2093.0
    }

    /** Early warning: one soft, longer ding. */
    private val early = buildTrack(ding(C6, 380))
    /** Countdown: short, higher tick. */
    private val count = buildTrack(ding(E6, 110))
    /** Mark: two quick dings. */
    private val mark = buildTrack(ding(G6, 140) + silence(50) + ding(G6, 220))
    /** Lap (400 m multiple): rising three-note arpeggio. */
    private val lap = buildTrack(ding(C6, 130) + silence(30) + ding(E6, 130) + silence(30) + ding(G6, 130) + silence(30) + ding(C7, 320))

    private val tracks = listOf(early, count, mark, lap)

    init {
        setVolume(initialVolume)
    }

    /** 0f..1f gain applied on top of the media stream volume. */
    fun setVolume(volume: Float) {
        val gain = volume.coerceIn(0f, 1f)
        tracks.forEach { runCatching { it.setVolume(gain) } }
    }

    fun play(kind: SplitEvent.Kind) {
        val track = when (kind) {
            SplitEvent.Kind.EARLY_BEEP -> early
            SplitEvent.Kind.COUNT_BEEP -> count
            SplitEvent.Kind.MARK -> mark
            SplitEvent.Kind.LAP -> lap
        }
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    fun release() {
        tracks.forEach { runCatching { it.release() } }
    }

    private fun buildTrack(pcm: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }

    /**
     * Percussive tone: fundamental + 2nd/3rd harmonics, 4 ms attack, exponential decay
     * that reaches about -35 dB at the end of [durationMs].
     */
    private fun ding(freqHz: Double, durationMs: Int): ShortArray {
        val n = SAMPLE_RATE * durationMs / 1000
        val attack = SAMPLE_RATE * 4 / 1000
        val tau = n / 4.0
        return ShortArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val env = min(1.0, i.toDouble() / attack) * exp(-i / tau)
            val wave = sin(2 * PI * freqHz * t) +
                0.35 * sin(2 * PI * freqHz * 2 * t) +
                0.15 * sin(2 * PI * freqHz * 3 * t)
            (wave / 1.5 * env * Short.MAX_VALUE * 0.85).toInt().toShort()
        }
    }

    private fun silence(durationMs: Int) = ShortArray(SAMPLE_RATE * durationMs / 1000)
}
