package fr.dreau.trackpacer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.dreau.trackpacer.PacerApp
import fr.dreau.trackpacer.domain.PaceSettings
import fr.dreau.trackpacer.domain.SplitEvent
import fr.dreau.trackpacer.service.Beeper
import fr.dreau.trackpacer.service.MediaVolumeBoost
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as PacerApp).settingsRepository
    private val volumeBoost = MediaVolumeBoost(application)
    private var previewBeeper: Beeper? = null
    private var previewJob: Job? = null

    val settings: StateFlow<PaceSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaceSettings())

    fun setPace(secondsPerKm: Int) = viewModelScope.launch { repository.setPace(secondsPerKm) }
    fun setInterval(meters: Int) = viewModelScope.launch { repository.setInterval(meters) }
    fun setRepMeters(meters: Int) = viewModelScope.launch { repository.setRepMeters(meters) }
    fun setRecoverySeconds(seconds: Int) = viewModelScope.launch { repository.setRecoverySeconds(seconds) }
    fun setCountdownCues(on: Boolean) = viewModelScope.launch { repository.setCountdownCues(on) }
    fun setAutoStart(on: Boolean) = viewModelScope.launch { repository.setAutoStart(on) }
    fun setAutoRecovery(on: Boolean) = viewModelScope.launch { repository.setAutoRecovery(on) }
    fun setSound(on: Boolean) = viewModelScope.launch { repository.setSound(on) }
    fun setVolumeLevel(level: Int) = viewModelScope.launch { repository.setVolumeLevel(level) }
    fun setBoostMediaVolume(on: Boolean) = viewModelScope.launch { repository.setBoostMediaVolume(on) }
    fun setVibration(on: Boolean) = viewModelScope.launch { repository.setVibration(on) }
    fun setShowHeartRate(on: Boolean) = viewModelScope.launch { repository.setShowHeartRate(on) }
    fun setShowDistance(on: Boolean) = viewModelScope.launch { repository.setShowDistance(on) }
    fun setKeepScreenOn(on: Boolean) = viewModelScope.launch { repository.setKeepScreenOn(on) }

    /** Plays tick → mark → lap at session loudness so the user can judge sound and volume. */
    fun previewCues() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val beeper = previewBeeper ?: Beeper().also { previewBeeper = it }
            beeper.setVolume(settings.value.volumeGain)
            if (settings.value.boostMediaVolume) volumeBoost.boost()
            try {
                beeper.play(SplitEvent.Kind.COUNT_BEEP)
                delay(500)
                beeper.play(SplitEvent.Kind.MARK)
                delay(900)
                beeper.play(SplitEvent.Kind.LAP)
                delay(1_200)
            } finally {
                volumeBoost.restore()
            }
        }
    }

    override fun onCleared() {
        previewBeeper?.release()
        previewBeeper = null
        volumeBoost.restore()
    }
}
