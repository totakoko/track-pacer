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
            SplitEvent.Kind.EARLY_BEEP -> VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE)
            SplitEvent.Kind.COUNT_BEEP -> VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
            SplitEvent.Kind.MARK -> VibrationEffect.createWaveform(longArrayOf(0, 120, 60, 120), -1)
            SplitEvent.Kind.LAP -> VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120, 80, 250), -1)
        }
        runCatching { vibrator.vibrate(effect) }
    }
}
