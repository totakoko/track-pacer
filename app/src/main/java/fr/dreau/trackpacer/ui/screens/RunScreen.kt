package fr.dreau.trackpacer.ui.screens

import android.app.Activity
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import fr.dreau.trackpacer.R
import fr.dreau.trackpacer.domain.PaceMath
import fr.dreau.trackpacer.domain.RunPhase
import fr.dreau.trackpacer.ui.LocalAmbientMode
import fr.dreau.trackpacer.ui.RunViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val IMMINENT_MS = 3_000L

/**
 * The chronometer screen. A ring around the bezel fills between two marks, the
 * next mark and its countdown sit above a large chrono, and the screen flashes
 * when the runner is due at a mark.
 */
@Composable
fun RunScreen(viewModel: RunViewModel, onFinished: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ambient = LocalAmbientMode.current
    val settings = state.settings

    // Live clock: per frame while interactive, 1 Hz in ambient, frozen otherwise.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.phase, ambient) {
        now = SystemClock.elapsedRealtime()
        if (state.phase != RunPhase.RUNNING) return@LaunchedEffect
        while (isActive) {
            if (ambient) delay(1_000) else withFrameMillis { }
            now = SystemClock.elapsedRealtime()
        }
    }

    LaunchedEffect(state.phase) {
        if (state.phase == RunPhase.FINISHED) onFinished()
    }

    KeepScreenOn(enabled = settings.keepScreenOn && state.phase == RunPhase.RUNNING)

    val flash = remember { Animatable(0f) }
    LaunchedEffect(state.lastMark?.atRealtime) {
        if (state.lastMark == null || ambient) return@LaunchedEffect
        flash.snapTo(1f)
        flash.animateTo(0f, tween(durationMillis = 700))
    }

    val elapsed = state.elapsedMs(now)
    val running = state.phase == RunPhase.RUNNING
    val stopped = state.phase == RunPhase.STOPPED
    val paused = state.phase == RunPhase.PAUSED
    val nextMark = PaceMath.nextMarkMeters(elapsed, settings)
    val msToNext = PaceMath.msToNextMark(elapsed, settings)
    val imminent = running && msToNext <= IMMINENT_MS
    val progress = if (running) PaceMath.progressInSplit(elapsed, settings.splitMs) else 1f
    val accent = if (state.lastMark?.isLap == true) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

    ScreenScaffold { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (!ambient) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    colors = ProgressIndicatorDefaults.colors(
                        indicatorColor = if (imminent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    strokeWidth = 7.dp,
                )
                if (flash.value > 0f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(accent.copy(alpha = 0.35f * flash.value)),
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.block_number, state.blockNumber),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = when {
                        running -> stringResource(R.string.next_mark, nextMark, (msToNext + 999) / 1000)
                        paused -> stringResource(R.string.block_paused)
                        stopped -> stringResource(R.string.block_stopped)
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (imminent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    fontWeight = if (imminent) FontWeight.Bold else null,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = PaceMath.formatChrono(elapsed, tenths = !ambient),
                    style = MaterialTheme.typography.numeralLarge,
                    color = if (ambient) Color.White else MaterialTheme.colorScheme.onBackground,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (settings.showHeartRate) {
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = stringResource(R.string.heart_rate),
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = state.heartRateBpm?.toString() ?: "--",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    if (settings.showHeartRate && settings.showDistance) {
                        Text("  ·  ", style = MaterialTheme.typography.labelMedium)
                    }
                    if (settings.showDistance) {
                        Text(
                            text = PaceMath.formatDistance(
                                PaceMath.theoreticalDistanceMeters(elapsed, settings.secondsPerKm),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                if (state.degraded && !ambient) {
                    Text(
                        text = stringResource(R.string.degraded_hint),
                        style = MaterialTheme.typography.bodyExtraSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(44.dp))
            }

            if (!ambient && state.isActive) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Row(
                        modifier = Modifier.padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when {
                            running -> {
                                FilledIconButton(onClick = viewModel::pause) {
                                    Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.pause))
                                }
                                StopButton(onClick = viewModel::stopBlock)
                            }
                            paused -> {
                                FilledIconButton(onClick = viewModel::resume) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.resume))
                                }
                                StopButton(onClick = viewModel::stopBlock)
                            }
                            else -> {
                                FilledIconButton(onClick = viewModel::newBlock) {
                                    Icon(Icons.Rounded.Replay, contentDescription = stringResource(R.string.new_block))
                                }
                                FilledTonalIconButton(onClick = viewModel::finishSession) {
                                    Icon(Icons.Rounded.Flag, contentDescription = stringResource(R.string.finish))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.stop))
    }
}

/** Keeps the display on during a rep; ambient mode still takes over on palm cover. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = (context as? Activity)?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
