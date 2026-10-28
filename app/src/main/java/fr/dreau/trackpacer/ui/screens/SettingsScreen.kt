package fr.dreau.trackpacer.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import fr.dreau.trackpacer.R
import fr.dreau.trackpacer.domain.PaceSettings
import fr.dreau.trackpacer.ui.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = listPadding(contentPadding)) {
            item { ListHeader { Text(stringResource(R.string.settings), textAlign = TextAlign.Center) } }
            item { Toggle(R.string.sound, settings.soundEnabled, viewModel::setSound) }
            item {
                Slider(
                    value = settings.volumeLevel,
                    onValueChange = viewModel::setVolumeLevel,
                    valueProgression = PaceSettings.MIN_VOLUME_LEVEL..PaceSettings.MAX_VOLUME_LEVEL,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = settings.soundEnabled,
                    segmented = true,
                )
            }
            item {
                Toggle(
                    R.string.boost_volume, settings.boostMediaVolume, viewModel::setBoostMediaVolume,
                    secondary = R.string.boost_volume_hint, enabled = settings.soundEnabled,
                )
            }
            item {
                Button(
                    onClick = viewModel::previewCues,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = settings.soundEnabled,
                    icon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                    label = { Text(stringResource(R.string.preview_cues), maxLines = 1) },
                )
            }
            item {
                Toggle(R.string.countdown_cues, settings.countdownCues, viewModel::setCountdownCues, secondary = R.string.countdown_cues_hint)
            }
            item { Toggle(R.string.vibration, settings.vibrationEnabled, viewModel::setVibration) }
            item {
                Toggle(
                    R.string.auto_start, settings.autoStart, viewModel::setAutoStart,
                    secondary = R.string.auto_start_hint, enabled = settings.isTimedRecovery,
                )
            }
            item {
                Toggle(
                    R.string.auto_recovery, settings.autoRecovery, viewModel::setAutoRecovery,
                    secondary = R.string.auto_recovery_hint, enabled = settings.hasRepDistance,
                )
            }
            item {
                Toggle(R.string.show_distance, settings.showDistance, viewModel::setShowDistance, secondary = R.string.show_distance_hint)
            }
            item {
                Toggle(R.string.show_heart_rate, settings.showHeartRate, viewModel::setShowHeartRate, secondary = R.string.show_heart_rate_hint)
            }
            item {
                Toggle(R.string.keep_screen_on, settings.keepScreenOn, viewModel::setKeepScreenOn, secondary = R.string.keep_screen_on_hint)
            }
        }
    }
}

@Composable
private fun Toggle(
    label: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    secondary: Int? = null,
    enabled: Boolean = true,
) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = secondary?.let {
            { Text(stringResource(it), style = MaterialTheme.typography.bodyExtraSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
    )
}
