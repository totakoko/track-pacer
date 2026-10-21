package fr.dreau.trackpacer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.dreau.trackpacer.PacerApp
import fr.dreau.trackpacer.domain.CueProfile
import fr.dreau.trackpacer.domain.PaceSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as PacerApp).settingsRepository

    val settings: StateFlow<PaceSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaceSettings())

    fun setPace(secondsPerKm: Int) = viewModelScope.launch { repository.setPace(secondsPerKm) }
    fun setInterval(meters: Int) = viewModelScope.launch { repository.setInterval(meters) }
    fun setSound(on: Boolean) = viewModelScope.launch { repository.setSound(on) }
    fun setCueProfile(profile: CueProfile) = viewModelScope.launch { repository.setCueProfile(profile) }
    fun setVibration(on: Boolean) = viewModelScope.launch { repository.setVibration(on) }
    fun setShowHeartRate(on: Boolean) = viewModelScope.launch { repository.setShowHeartRate(on) }
    fun setShowDistance(on: Boolean) = viewModelScope.launch { repository.setShowDistance(on) }
    fun setKeepScreenOn(on: Boolean) = viewModelScope.launch { repository.setKeepScreenOn(on) }
}
