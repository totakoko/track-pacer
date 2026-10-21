package fr.dreau.trackpacer.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import fr.dreau.trackpacer.R
import fr.dreau.trackpacer.domain.PaceMath
import fr.dreau.trackpacer.domain.PaceSettings
import fr.dreau.trackpacer.ui.SettingsViewModel

/** Choose how often the watch cues a mark (50 m … 400 m). */
@Composable
fun IntervalScreen(viewModel: SettingsViewModel, onDone: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(stringResource(R.string.marks), textAlign = TextAlign.Center) }
            }
            items(PaceSettings.INTERVAL_CHOICES.size) { index ->
                val meters = PaceSettings.INTERVAL_CHOICES[index]
                RadioButton(
                    selected = settings.intervalMeters == meters,
                    onSelect = {
                        viewModel.setInterval(meters)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.every_meters, meters)) },
                    secondaryLabel = {
                        Text(
                            PaceMath.formatChrono(
                                PaceMath.splitDurationMs(settings.secondsPerKm, meters),
                                tenths = true,
                            ),
                        )
                    },
                )
            }
        }
    }
}
