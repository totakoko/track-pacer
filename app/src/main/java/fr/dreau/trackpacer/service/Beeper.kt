package fr.dreau.trackpacer.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import fr.dreau.trackpacer.domain.SplitEvent
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Generates short sine-wave cues with [AudioTrack] in static mode.
 * Synthesised PCM is more predictable than [android.media.ToneGenerator] on watches,
 * and USAGE_MEDIA routes to Bluetooth earbuds when they are connected.
 */
class Beeper {
    private companion object {
        const val SAMPLE_RATE = 44_100
        const val FADE_MS = 6
    }

    private val preBeep = buildTrack(tone(880.0, 90))
    private val mark = buildTrack(tone(1_480.0, 230))
    private val lap = buildTrack(tone(1_480.0, 150) + silence(70) + tone(1_480.0, 150) + silence(70) + tone(1_760.0, 260))

    fun play(kind: SplitEvent.Kind) {
        val track = when (kind) {
            SplitEvent.Kind.PRE_BEEP -> preBeep
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
        listOf(preBeep, mark, lap).forEach { runCatching { it.release() } }
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

    private fun tone(freqHz: Double, durationMs: Int): ShortArray {
        val n = SAMPLE_RATE * durationMs / 1000
        val fade = SAMPLE_RATE * FADE_MS / 1000
        return ShortArray(n) { i ->
            val envelope = min(1.0, min(i, n - 1 - i).toDouble() / fade)
            (sin(2.0 * PI * freqHz * i / SAMPLE_RATE) * envelope * Short.MAX_VALUE * 0.9).toInt().toShort()
        }
    }

    private fun silence(durationMs: Int) = ShortArray(SAMPLE_RATE * durationMs / 1000)
}
