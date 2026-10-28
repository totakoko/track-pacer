package fr.dreau.trackpacer.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import kotlinx.coroutines.launch

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

/**
 * Home is the root: a drag to the right from its left edge closes the app for good. The
 * platform gesture is off in the theme (see styles.xml) and the pager leaves its left edge
 * zone to us on the first page. The page follows the finger, then either leaves or snaps back.
 * A running session keeps its foreground service and watch-face indicator; Finish frees it.
 */
@Composable
private fun Modifier.exitOnEdgeDrag(): Modifier {
    val activity = LocalActivity.current
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    return this
        .pointerInput(Unit) {
            val edge = size.width * EXIT_EDGE_FRACTION
            awaitEachGesture {
                // Only a touch that lands in the edge zone is ours; anything else stays with the pager.
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.position.x > edge) return@awaitEachGesture
                val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                    if (over > 0f) change.consume()
                } ?: return@awaitEachGesture
                scope.launch { offset.snapTo((slop.position.x - down.position.x).coerceAtLeast(0f)) }
                val completed = horizontalDrag(slop.id) { change ->
                    val delta = change.positionChange().x
                    change.consume()
                    scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) }
                }
                if (completed && offset.value >= size.width * EXIT_THRESHOLD_FRACTION) {
                    scope.launch {
                        offset.animateTo(size.width.toFloat(), tween(150))
                        activity?.finishAndRemoveTask()
                    }
                } else {
                    scope.launch { offset.animateTo(0f, tween(200)) }
                }
            }
        }
        .graphicsLayer { translationX = offset.value }
}

private const val EXIT_EDGE_FRACTION = 0.2f
private const val EXIT_THRESHOLD_FRACTION = 0.4f

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
        Box(modifier = Modifier.fillMaxSize().exitOnEdgeDrag()) {
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
