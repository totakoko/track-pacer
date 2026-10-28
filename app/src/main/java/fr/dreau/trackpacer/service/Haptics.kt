package fr.dreau.trackpacer.service

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import fr.dreau.trackpacer.domain.SplitEvent

/** Haptic cues mirroring the audio ones, for noisy tracks or silent mode. */
class Haptics(context: Context) {
    private val vibrator: Vibrator =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator

    fun play(kind: SplitEvent.Kind) {
        if (!vibrator.hasVibrator()) return
        val effect = when (kind) {
            SplitEvent.Kind.COUNT_BEEP -> VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
            // Marks use full amplitude: a running wrist dulls anything softer.
            SplitEvent.Kind.MARK -> VibrationEffect.createWaveform(longArrayOf(0, 120, 60, 120), intArrayOf(0, 255, 0, 255), -1)
            SplitEvent.Kind.LAP, SplitEvent.Kind.REP_END -> VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120, 80, 250), intArrayOf(0, 255, 0, 255, 0, 255), -1)
        }
        runCatching { vibrator.vibrate(effect) }
    }
}
