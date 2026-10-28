package fr.dreau.trackpacer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.dreau.trackpacer.domain.PaceSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pace_settings")

/** Persists [PaceSettings] with Preferences DataStore. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val SECONDS_PER_KM = intPreferencesKey("seconds_per_km")
        val INTERVAL_METERS = intPreferencesKey("interval_meters")
        val REP_METERS = intPreferencesKey("rep_meters")
        val RECOVERY_SECONDS = intPreferencesKey("recovery_seconds")
        val COUNTDOWN_CUES = booleanPreferencesKey("countdown_cues")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val AUTO_RECOVERY = booleanPreferencesKey("auto_recovery")
        val SOUND = booleanPreferencesKey("sound")
        val VOLUME_LEVEL = intPreferencesKey("volume_level")
        val BOOST_MEDIA_VOLUME = booleanPreferencesKey("boost_media_volume")
        val VIBRATION = booleanPreferencesKey("vibration")
        val SHOW_HR = booleanPreferencesKey("show_hr")
        val SHOW_DISTANCE = booleanPreferencesKey("show_distance")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }

    val settings: Flow<PaceSettings> = context.dataStore.data.map { p ->
        val d = PaceSettings()
        PaceSettings(
            secondsPerKm = p[Keys.SECONDS_PER_KM] ?: d.secondsPerKm,
            intervalMeters = p[Keys.INTERVAL_METERS] ?: d.intervalMeters,
            repMeters = p[Keys.REP_METERS] ?: d.repMeters,
            recoverySeconds = p[Keys.RECOVERY_SECONDS] ?: d.recoverySeconds,
            countdownCues = p[Keys.COUNTDOWN_CUES] ?: d.countdownCues,
            autoStart = p[Keys.AUTO_START] ?: d.autoStart,
            autoRecovery = p[Keys.AUTO_RECOVERY] ?: d.autoRecovery,
            soundEnabled = p[Keys.SOUND] ?: d.soundEnabled,
            volumeLevel = p[Keys.VOLUME_LEVEL] ?: d.volumeLevel,
            boostMediaVolume = p[Keys.BOOST_MEDIA_VOLUME] ?: d.boostMediaVolume,
            vibrationEnabled = p[Keys.VIBRATION] ?: d.vibrationEnabled,
            showHeartRate = p[Keys.SHOW_HR] ?: d.showHeartRate,
            showDistance = p[Keys.SHOW_DISTANCE] ?: d.showDistance,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: d.keepScreenOn,
        )
    }

    suspend fun current(): PaceSettings = settings.first()

    suspend fun setPace(secondsPerKm: Int) = context.dataStore.edit {
        it[Keys.SECONDS_PER_KM] = secondsPerKm.coerceIn(PaceSettings.MIN_SECONDS_PER_KM, PaceSettings.MAX_SECONDS_PER_KM)
    }
    suspend fun setInterval(meters: Int) = context.dataStore.edit {
        it[Keys.INTERVAL_METERS] = meters.coerceIn(PaceSettings.MIN_INTERVAL_METERS, PaceSettings.MAX_INTERVAL_METERS)
    }
    suspend fun setRepMeters(meters: Int) = context.dataStore.edit { it[Keys.REP_METERS] = meters.coerceAtLeast(0) }
    suspend fun setRecoverySeconds(seconds: Int) = context.dataStore.edit { it[Keys.RECOVERY_SECONDS] = seconds.coerceAtLeast(0) }
    suspend fun setCountdownCues(enabled: Boolean) = context.dataStore.edit { it[Keys.COUNTDOWN_CUES] = enabled }
    suspend fun setAutoStart(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_START] = enabled }
    suspend fun setAutoRecovery(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_RECOVERY] = enabled }
    suspend fun setSound(enabled: Boolean) = context.dataStore.edit { it[Keys.SOUND] = enabled }
    suspend fun setVolumeLevel(level: Int) = context.dataStore.edit {
        it[Keys.VOLUME_LEVEL] = level.coerceIn(PaceSettings.MIN_VOLUME_LEVEL, PaceSettings.MAX_VOLUME_LEVEL)
    }
    suspend fun setBoostMediaVolume(enabled: Boolean) = context.dataStore.edit { it[Keys.BOOST_MEDIA_VOLUME] = enabled }
    suspend fun setVibration(enabled: Boolean) = context.dataStore.edit { it[Keys.VIBRATION] = enabled }
    suspend fun setShowHeartRate(enabled: Boolean) = context.dataStore.edit { it[Keys.SHOW_HR] = enabled }
    suspend fun setShowDistance(enabled: Boolean) = context.dataStore.edit { it[Keys.SHOW_DISTANCE] = enabled }
    suspend fun setKeepScreenOn(enabled: Boolean) = context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
}
