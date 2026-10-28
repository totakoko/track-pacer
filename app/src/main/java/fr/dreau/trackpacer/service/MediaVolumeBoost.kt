package fr.dreau.trackpacer.service

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log

/**
 * Raises the media stream to its maximum for the duration of a session and restores
 * the previous level afterwards. Skipped when a Bluetooth output is connected: full
 * volume in earbuds is painful, and the app gain is already enough there.
 */
class MediaVolumeBoost(context: Context) {
    private val audio: AudioManager = context.getSystemService(AudioManager::class.java)
    private var savedLevel: Int? = null

    fun boost() {
        if (savedLevel != null || hasBluetoothOutput()) return
        runCatching {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (current >= max) return
            savedLevel = current
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
        }.onFailure { Log.w(TAG, "Could not raise media volume", it) }
    }

    fun restore() {
        val level = savedLevel ?: return
        savedLevel = null
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0) }
            .onFailure { Log.w(TAG, "Could not restore media volume", it) }
    }

    private fun hasBluetoothOutput(): Boolean =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in BLUETOOTH_TYPES }

    private companion object {
        const val TAG = "MediaVolumeBoost"
        val BLUETOOTH_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )
    }
}
