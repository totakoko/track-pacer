package fr.dreau.trackpacer.data

import fr.dreau.trackpacer.domain.RunState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide session state shared between the foreground service (writer)
 * and the UI (reader). Avoids a bound-service/Binder round trip for a
 * single-process app.
 */
class RunStateHolder {
    private val _state = MutableStateFlow(RunState())
    val state: StateFlow<RunState> = _state

    fun update(transform: (RunState) -> RunState) = _state.update(transform)
    fun reset() = _state.update { RunState() }
}
