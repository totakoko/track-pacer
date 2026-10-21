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
        val SOUND = booleanPreferencesKey("sound")
        val PRE_BEEPS = booleanPreferencesKey("pre_beeps")
        val VIBRATION = booleanPreferencesKey("vibration")
        val SHOW_HR = booleanPreferencesKey("show_hr")
        val SHOW_DISTANCE = booleanPreferencesKey("show_distance")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }

    val settings: Flow<PaceSettings> = context.dataStore.data.map { p ->
        val defaults = PaceSettings()
        PaceSettings(
            secondsPerKm = p[Keys.SECONDS_PER_KM] ?: defaults.secondsPerKm,
            intervalMeters = p[Keys.INTERVAL_METERS] ?: defaults.intervalMeters,
            soundEnabled = p[Keys.SOUND] ?: defaults.soundEnabled,
            preBeepsEnabled = p[Keys.PRE_BEEPS] ?: defaults.preBeepsEnabled,
            vibrationEnabled = p[Keys.VIBRATION] ?: defaults.vibrationEnabled,
            showHeartRate = p[Keys.SHOW_HR] ?: defaults.showHeartRate,
            showDistance = p[Keys.SHOW_DISTANCE] ?: defaults.showDistance,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
        )
    }

    suspend fun current(): PaceSettings = settings.first()

    suspend fun setPace(secondsPerKm: Int) = context.dataStore.edit {
        it[Keys.SECONDS_PER_KM] = secondsPerKm.coerceIn(
            PaceSettings.MIN_SECONDS_PER_KM, PaceSettings.MAX_SECONDS_PER_KM,
        )
    }

    suspend fun setInterval(meters: Int) = context.dataStore.edit { it[Keys.INTERVAL_METERS] = meters }
    suspend fun setSound(enabled: Boolean) = context.dataStore.edit { it[Keys.SOUND] = enabled }
    suspend fun setPreBeeps(enabled: Boolean) = context.dataStore.edit { it[Keys.PRE_BEEPS] = enabled }
    suspend fun setVibration(enabled: Boolean) = context.dataStore.edit { it[Keys.VIBRATION] = enabled }
    suspend fun setShowHeartRate(enabled: Boolean) = context.dataStore.edit { it[Keys.SHOW_HR] = enabled }
    suspend fun setShowDistance(enabled: Boolean) = context.dataStore.edit { it[Keys.SHOW_DISTANCE] = enabled }
    suspend fun setKeepScreenOn(enabled: Boolean) = context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
}
