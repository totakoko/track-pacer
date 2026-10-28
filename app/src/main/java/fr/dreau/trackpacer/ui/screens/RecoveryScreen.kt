package fr.dreau.trackpacer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.dreau.trackpacer.R
import fr.dreau.trackpacer.domain.PaceSettings
import fr.dreau.trackpacer.ui.SettingsViewModel

/** Recovery between reps: 0:00 is a free chrono, anything else a timer with cues at the end. */
@Composable
fun RecoveryScreen(viewModel: SettingsViewModel, onDone: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    DurationPicker(
        title = stringResource(R.string.recovery_setting),
        initialSeconds = settings.recoverySeconds,
        minMinutes = 0,
        maxMinutes = PaceSettings.MAX_RECOVERY_MINUTES,
        zeroLabel = stringResource(R.string.recovery_free),
    ) { seconds ->
        viewModel.setRecoverySeconds(seconds)
        onDone()
    }
}
