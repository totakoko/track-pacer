package fr.dreau.trackpacer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Picker
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.rememberPickerState
import fr.dreau.trackpacer.R
import fr.dreau.trackpacer.domain.PaceSettings
import fr.dreau.trackpacer.ui.SettingsViewModel
import java.util.Locale

private const val MIN_MINUTES = PaceSettings.MIN_SECONDS_PER_KM / 60
private const val MAX_MINUTES = PaceSettings.MAX_SECONDS_PER_KM / 60

/** Target pace editor: minutes and seconds per kilometre, side by side. */
@Composable
fun PaceScreen(viewModel: SettingsViewModel, onDone: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val initial = remember { settings.secondsPerKm }
    val minutesState = rememberPickerState(
        initialNumberOfOptions = MAX_MINUTES - MIN_MINUTES + 1,
        initiallySelectedIndex = (initial / 60 - MIN_MINUTES).coerceIn(0, MAX_MINUTES - MIN_MINUTES),
        shouldRepeatOptions = false,
    )
    val secondsState = rememberPickerState(
        initialNumberOfOptions = 60,
        initiallySelectedIndex = initial % 60,
    )

    val minutesLabel = stringResource(R.string.minutes)
    val secondsLabel = stringResource(R.string.seconds)

    fun selectedSecondsPerKm(): Int =
        (minutesState.selectedOptionIndex + MIN_MINUTES) * 60 + secondsState.selectedOptionIndex

    ScreenScaffold { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.target_pace), style = MaterialTheme.typography.titleSmall)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Picker(
                    state = minutesState,
                    contentDescription = { minutesLabel },
                    modifier = Modifier.width(56.dp).height(100.dp),
                ) { index ->
                    PickerOption(String.format(Locale.ROOT, "%d", index + MIN_MINUTES))
                }
                Text(":", style = MaterialTheme.typography.numeralMedium)
                Picker(
                    state = secondsState,
                    contentDescription = { secondsLabel },
                    modifier = Modifier.width(56.dp).height(100.dp),
                ) { index ->
                    PickerOption(String.format(Locale.ROOT, "%02d", index))
                }
                Text(
                    stringResource(R.string.per_km),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Spacer(Modifier.height(48.dp))
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            EdgeButton(onClick = {
                viewModel.setPace(selectedSecondsPerKm())
                onDone()
            }) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.confirm))
            }
        }
    }
}

@Composable
private fun PickerOption(text: String) {
    Box(contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.numeralMedium)
    }
}
