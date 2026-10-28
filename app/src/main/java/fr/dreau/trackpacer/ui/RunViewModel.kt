package fr.dreau.trackpacer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import fr.dreau.trackpacer.PacerApp
import fr.dreau.trackpacer.domain.RunState
import fr.dreau.trackpacer.service.PacerService
import kotlinx.coroutines.flow.StateFlow

/** Thin command/observe facade over the foreground service. */
class RunViewModel(application: Application) : AndroidViewModel(application) {
    private val holder = (application as PacerApp).runStateHolder

    val state: StateFlow<RunState> = holder.state

    fun startSession() = PacerService.start(getApplication())
    fun pause() = PacerService.pause(getApplication())
    fun resume() = PacerService.resume(getApplication())
    fun endRep() = PacerService.endRep(getApplication())
    fun go() = PacerService.go(getApplication())
    fun finishSession() = PacerService.finish(getApplication())
    fun clearSession() = holder.reset()
}
