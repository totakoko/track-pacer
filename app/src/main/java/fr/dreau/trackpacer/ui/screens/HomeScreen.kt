package fr.dreau.trackpacer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import fr.dreau.trackpacer.R
import fr.dreau.trackpacer.domain.PaceMath
import fr.dreau.trackpacer.service.Permissions
import fr.dreau.trackpacer.ui.RunViewModel
import fr.dreau.trackpacer.ui.SettingsViewModel

@Composable
fun HomeScreen(
    settingsViewModel: SettingsViewModel,
    runViewModel: RunViewModel,
    onEditPace: () -> Unit,
    onEditInterval: () -> Unit,
    onOpenSettings: () -> Unit,
    onRun: () -> Unit,
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val runState by runViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberTransformingLazyColumnState()

    fun launchSession() {
        runViewModel.startSession()
        onRun()
    }

    // Heart-rate permission may be denied: the session still starts, just without BPM.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { launchSession() }

    val onStart = {
        val missing = Permissions.missing(context, wantHeartRate = settings.showHeartRate)
        if (missing.isEmpty()) launchSession() else permissionLauncher.launch(missing)
    }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            if (runState.isActive) {
                EdgeButton(onClick = onRun, colors = ButtonDefaults.filledTonalButtonColors()) {
                    Icon(Icons.Rounded.Timer, contentDescription = null)
                    Text(stringResource(R.string.resume_session))
                }
            } else {
                EdgeButton(onClick = onStart) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.start))
                }
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader {
                    Text(stringResource(R.string.app_name), textAlign = TextAlign.Center)
                }
            }
            item {
                Button(
                    onClick = onEditPace,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Rounded.Speed, contentDescription = null) },
                    label = { Text(stringResource(R.string.target_pace)) },
                    secondaryLabel = {
                        Text(stringResource(R.string.pace_value, PaceMath.formatPace(settings.secondsPerKm)))
                    },
                )
            }
            item {
                Button(
                    onClick = onEditInterval,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Rounded.Straighten, contentDescription = null) },
                    label = { Text(stringResource(R.string.marks)) },
                    secondaryLabel = { Text(stringResource(R.string.every_meters, settings.intervalMeters)) },
                )
            }
            item {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.settings)) },
                )
            }
            item {
                Text(
                    text = stringResource(
                        R.string.split_hint,
                        settings.intervalMeters,
                        PaceMath.formatChrono(settings.splitMs, tenths = true),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.button_hint),
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
