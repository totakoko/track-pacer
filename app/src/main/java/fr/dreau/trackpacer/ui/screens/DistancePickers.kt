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
import fr.dreau.trackpacer.domain.PaceMath
import fr.dreau.trackpacer.domain.PaceSettings
import fr.dreau.trackpacer.ui.SettingsViewModel

/** Cue interval: one wheel, 50 m to 1 km in 50 m steps. */
@Composable
fun IntervalScreen(viewModel: SettingsViewModel, onDone: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val step = PaceSettings.INTERVAL_STEP_METERS
    val count = (PaceSettings.MAX_INTERVAL_METERS - PaceSettings.MIN_INTERVAL_METERS) / step + 1
    val initial = remember { ((settings.intervalMeters - PaceSettings.MIN_INTERVAL_METERS) / step).coerceIn(0, count - 1) }
    val state = rememberPickerState(initialNumberOfOptions = count, initiallySelectedIndex = initial, shouldRepeatOptions = false)
    val label = stringResource(R.string.marks)

    PickerScaffold(
        title = label,
        hint = PaceMath.formatChrono(
            PaceMath.splitDurationMs(settings.secondsPerKm, PaceSettings.MIN_INTERVAL_METERS + state.selectedOptionIndex * step),
            tenths = true,
        ),
        onConfirm = {
            viewModel.setInterval(PaceSettings.MIN_INTERVAL_METERS + state.selectedOptionIndex * step)
            onDone()
        },
    ) {
        Picker(state = state, contentDescription = { label }, modifier = Modifier.width(110.dp).height(100.dp)) { index ->
            WheelOption(PaceMath.formatInterval(PaceSettings.MIN_INTERVAL_METERS + index * step))
        }
    }
}

/** Rep distance: kilometres and hundreds of metres, up to 99.9 km; 0 means a free rep. */
@Composable
fun RepDistanceScreen(viewModel: SettingsViewModel, onDone: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val initial = remember { settings.repMeters.coerceIn(0, PaceSettings.MAX_REP_KM * 1000 + 900) }
    val kmState = rememberPickerState(
        initialNumberOfOptions = PaceSettings.MAX_REP_KM + 1,
        initiallySelectedIndex = initial / 1000,
        shouldRepeatOptions = false,
    )
    val hmState = rememberPickerState(
        initialNumberOfOptions = 1000 / PaceSettings.REP_STEP_METERS,
        initiallySelectedIndex = (initial % 1000) / PaceSettings.REP_STEP_METERS,
    )
    val kmLabel = stringResource(R.string.kilometres)
    val mLabel = stringResource(R.string.metres)

    fun selectedMeters(): Int = kmState.selectedOptionIndex * 1000 + hmState.selectedOptionIndex * PaceSettings.REP_STEP_METERS

    val meters = selectedMeters()
    PickerScaffold(
        title = stringResource(R.string.rep_setting),
        hint = if (meters == 0) stringResource(R.string.rep_free_hint)
        else PaceMath.formatChrono(meters.toLong() * settings.secondsPerKm, tenths = true),
        onConfirm = {
            viewModel.setRepMeters(selectedMeters())
            onDone()
        },
    ) {
        Picker(state = kmState, contentDescription = { kmLabel }, modifier = Modifier.width(52.dp).height(100.dp)) { index ->
            WheelOption(index.toString())
        }
        Text("km", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 4.dp))
        Picker(state = hmState, contentDescription = { mLabel }, modifier = Modifier.width(68.dp).height(100.dp)) { index ->
            WheelOption((index * PaceSettings.REP_STEP_METERS).toString())
        }
        Text("m", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Title, the wheels in a row, a one-line hint under them, and the confirm edge button. */
@Composable
internal fun PickerScaffold(title: String, hint: String, onConfirm: () -> Unit, wheels: @Composable () -> Unit) {
    ScreenScaffold { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { wheels() }
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            EdgeButton(onClick = onConfirm) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.confirm))
            }
        }
    }
}

@Composable
internal fun WheelOption(text: String) {
    Box(contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.numeralMedium)
    }
}
