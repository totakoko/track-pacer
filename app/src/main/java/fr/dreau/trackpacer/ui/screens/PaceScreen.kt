package fr.dreau.trackpacer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.dreau.trackpacer.R
import fr.dreau.trackpacer.domain.PaceMath
import fr.dreau.trackpacer.domain.PaceSettings
import fr.dreau.trackpacer.ui.SettingsViewModel

/** Target pace editor: minutes and seconds per kilometre; the hint shows the rep's target time. */
@Composable
fun PaceScreen(viewModel: SettingsViewModel, onDone: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    DurationPicker(
        title = stringResource(R.string.target_pace),
        initialSeconds = settings.secondsPerKm,
        minMinutes = PaceSettings.MIN_SECONDS_PER_KM / 60,
        maxMinutes = PaceSettings.MAX_SECONDS_PER_KM / 60,
        suffix = stringResource(R.string.per_km),
        hint = { secondsPerKm ->
            if (settings.hasRepDistance) {
                PaceMath.formatInterval(settings.repMeters) + " · " +
                    PaceMath.formatChrono(settings.repMeters.toLong() * secondsPerKm, tenths = true)
            } else {
                PaceMath.formatInterval(settings.intervalMeters) + " · " +
                    PaceMath.formatChrono(settings.intervalMeters.toLong() * secondsPerKm, tenths = true)
            }
        },
    ) { seconds ->
        viewModel.setPace(seconds)
        onDone()
    }
}
