package fr.dreau.trackpacer.ui.screens

import androidx.annotation.StringRes
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
import fr.dreau.trackpacer.domain.CueProfile
import fr.dreau.trackpacer.ui.SettingsViewModel

/** Pick how early the watch warns before each mark. */
@Composable
fun CueProfileScreen(viewModel: SettingsViewModel, onDone: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()
    val profiles = CueProfile.entries

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(stringResource(R.string.cue_profile), textAlign = TextAlign.Center) }
            }
            items(profiles.size) { index ->
                val profile = profiles[index]
                RadioButton(
                    selected = settings.cueProfile == profile,
                    onSelect = {
                        viewModel.setCueProfile(profile)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(profile.titleRes())) },
                    secondaryLabel = { Text(stringResource(profile.descriptionRes())) },
                )
            }
        }
    }
}

@StringRes
fun CueProfile.titleRes(): Int = when (this) {
    CueProfile.MARK_ONLY -> R.string.cue_mark_only
    CueProfile.COUNTDOWN_3 -> R.string.cue_countdown_3
    CueProfile.EARLY_10 -> R.string.cue_early_10
    CueProfile.EARLY_10_COUNTDOWN_3 -> R.string.cue_early_10_countdown_3
    CueProfile.EARLY_15_5 -> R.string.cue_early_15_5
}

@StringRes
fun CueProfile.descriptionRes(): Int = when (this) {
    CueProfile.MARK_ONLY -> R.string.cue_mark_only_hint
    CueProfile.COUNTDOWN_3 -> R.string.cue_countdown_3_hint
    CueProfile.EARLY_10 -> R.string.cue_early_10_hint
    CueProfile.EARLY_10_COUNTDOWN_3 -> R.string.cue_early_10_countdown_3_hint
    CueProfile.EARLY_15_5 -> R.string.cue_early_15_5_hint
}
