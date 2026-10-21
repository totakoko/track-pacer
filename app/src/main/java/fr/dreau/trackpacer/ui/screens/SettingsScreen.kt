package fr.dreau.trackpacer.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import fr.dreau.trackpacer.R
import fr.dreau.trackpacer.ui.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item { ListHeader { Text(stringResource(R.string.settings), textAlign = TextAlign.Center) } }
            item {
                Toggle(Icons.AutoMirrored.Rounded.VolumeUp, R.string.sound, settings.soundEnabled, viewModel::setSound)
            }
            item {
                Toggle(
                    Icons.Rounded.Timer, R.string.pre_beeps, settings.preBeepsEnabled, viewModel::setPreBeeps,
                    secondary = R.string.pre_beeps_hint,
                )
            }
            item {
                Toggle(Icons.Rounded.Vibration, R.string.vibration, settings.vibrationEnabled, viewModel::setVibration)
            }
            item {
                Toggle(Icons.Rounded.Favorite, R.string.show_heart_rate, settings.showHeartRate, viewModel::setShowHeartRate)
            }
            item {
                Toggle(
                    Icons.Rounded.Straighten, R.string.show_distance, settings.showDistance, viewModel::setShowDistance,
                    secondary = R.string.show_distance_hint,
                )
            }
            item {
                Toggle(
                    Icons.Rounded.LightMode, R.string.keep_screen_on, settings.keepScreenOn, viewModel::setKeepScreenOn,
                    secondary = R.string.keep_screen_on_hint,
                )
            }
        }
    }
}

@Composable
private fun Toggle(
    icon: ImageVector,
    label: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    secondary: Int? = null,
) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(stringResource(label)) },
        secondaryLabel = secondary?.let { { Text(stringResource(it)) } },
    )
}
