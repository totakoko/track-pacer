package fr.dreau.trackpacer.ui.screens

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Picker
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.rememberPickerState
import fr.dreau.trackpacer.R
import java.util.Locale

/**
 * Minutes and seconds side by side, confirmed with the edge button. Used for the pace
 * and the recovery. [zeroLabel], when given, names the 0:00 value in the hint line.
 */
@Composable
fun DurationPicker(
    title: String,
    initialSeconds: Int,
    minMinutes: Int,
    maxMinutes: Int,
    suffix: String? = null,
    zeroLabel: String? = null,
    hint: (seconds: Int) -> String = { "" },
    onConfirm: (seconds: Int) -> Unit,
) {
    val initial = remember { initialSeconds.coerceIn(minMinutes * 60, maxMinutes * 60 + 59) }
    val minutesState = rememberPickerState(
        initialNumberOfOptions = maxMinutes - minMinutes + 1,
        initiallySelectedIndex = (initial / 60 - minMinutes).coerceIn(0, maxMinutes - minMinutes),
        shouldRepeatOptions = false,
    )
    val secondsState = rememberPickerState(
        initialNumberOfOptions = 60,
        initiallySelectedIndex = initial % 60,
    )
    val minutesLabel = stringResource(R.string.minutes)
    val secondsLabel = stringResource(R.string.seconds)

    fun selectedSeconds(): Int = (minutesState.selectedOptionIndex + minMinutes) * 60 + secondsState.selectedOptionIndex

    val seconds = selectedSeconds()
    PickerScaffold(
        title = title,
        hint = if (zeroLabel != null && seconds == 0) zeroLabel else hint(seconds),
        onConfirm = { onConfirm(selectedSeconds()) },
    ) {
        Picker(
            state = minutesState,
            contentDescription = { minutesLabel },
            modifier = Modifier.width(56.dp).height(100.dp),
        ) { index -> WheelOption(String.format(Locale.ROOT, "%d", index + minMinutes)) }
        Text(":", style = MaterialTheme.typography.numeralMedium)
        Picker(
            state = secondsState,
            contentDescription = { secondsLabel },
            modifier = Modifier.width(56.dp).height(100.dp),
        ) { index -> WheelOption(String.format(Locale.ROOT, "%02d", index)) }
        if (suffix != null) {
            Text(suffix, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
