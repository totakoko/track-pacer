package fr.dreau.trackpacer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import fr.dreau.trackpacer.R
import fr.dreau.trackpacer.domain.PaceMath
import fr.dreau.trackpacer.domain.PaceSettings
import fr.dreau.trackpacer.service.Permissions
import fr.dreau.trackpacer.ui.RunViewModel
import fr.dreau.trackpacer.ui.SettingsViewModel

/**
 * Two pages: the session as one sentence, every number a chip that opens its picker,
 * and the settings list a swipe to the left.
 *
 *     [400 m] à [4:45]
 *     ⟷ [100 m]  ⌛ [1:00]
 */
@Composable
fun HomeScreen(
    settingsViewModel: SettingsViewModel,
    runViewModel: RunViewModel,
    onEditPace: () -> Unit,
    onEditInterval: () -> Unit,
    onEditRep: () -> Unit,
    onEditRecovery: () -> Unit,
    onRun: () -> Unit,
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val runState by runViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pagerState = rememberPagerState { 2 }

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

    HorizontalPagerScaffold(pagerState = pagerState) {
        HorizontalPager(state = pagerState) { page ->
            AnimatedPage(pageIndex = page, pagerState = pagerState) {
                when (page) {
                    0 -> SentencePage(
                        settings = settings,
                        sessionActive = runState.isActive,
                        onEditPace = onEditPace, onEditInterval = onEditInterval,
                        onEditRep = onEditRep, onEditRecovery = onEditRecovery,
                        onStart = onStart, onRun = onRun,
                    )
                    else -> SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }
}

@Composable
private fun SentencePage(
    settings: PaceSettings,
    sessionActive: Boolean,
    onEditPace: () -> Unit,
    onEditInterval: () -> Unit,
    onEditRep: () -> Unit,
    onEditRecovery: () -> Unit,
    onStart: () -> Unit,
    onRun: () -> Unit,
) {
    ScreenScaffold { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Clear of the time text and of the round edge at the top; room for the pill below.
                    .padding(top = contentPadding.calculateTopPadding() + 10.dp, bottom = ACTION_PILL_SPACE),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Chip(
                        text = if (settings.hasRepDistance) PaceMath.formatInterval(settings.repMeters) else stringResource(R.string.rep_free),
                        onClick = onEditRep,
                    )
                    Text(
                        stringResource(R.string.at_pace),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 5.dp),
                    )
                    Chip(text = PaceMath.formatPace(settings.secondsPerKm), onClick = onEditPace)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Straighten,
                        contentDescription = stringResource(R.string.marks),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Chip(text = PaceMath.formatInterval(settings.intervalMeters), onClick = onEditInterval)
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Rounded.HourglassBottom,
                        contentDescription = stringResource(R.string.recovery_setting),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Chip(
                        text = if (settings.isTimedRecovery) PaceMath.formatDuration(settings.recoverySeconds) else stringResource(R.string.rep_free),
                        onClick = onEditRecovery,
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize().padding(bottom = ACTION_PILL_MARGIN), contentAlignment = Alignment.BottomCenter) {
                if (sessionActive) {
                    ActionPill(stringResource(R.string.resume_session), onClick = onRun, colors = ButtonDefaults.filledTonalButtonColors())
                } else {
                    ActionPill(stringResource(R.string.start), onClick = onStart)
                }
            }
        }
    }
}

private val CHIP_HEIGHT = 34.dp

/** One tappable value of the sentence. */
@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.height(CHIP_HEIGHT),
        contentPadding = PaddingValues(horizontal = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}
