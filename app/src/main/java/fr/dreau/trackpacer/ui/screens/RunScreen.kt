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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import fr.dreau.trackpacer.R
import fr.dreau.trackpacer.domain.PaceMath
import fr.dreau.trackpacer.domain.PaceSettings
import fr.dreau.trackpacer.domain.RunPhase
import fr.dreau.trackpacer.domain.RunState
import fr.dreau.trackpacer.ui.LocalAmbientMode
import fr.dreau.trackpacer.ui.RunViewModel
import fr.dreau.trackpacer.ui.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val IMMINENT_MS = 3_000L

/** Interactive refresh period: four updates a second keep the ring smooth without a 60 Hz redraw. */
private const val INTERACTIVE_TICK_MS = 250L
private const val AMBIENT_TICK_MS = 1_000L

private const val CONTROLS_PAGE = 0
private const val METRICS_PAGE = 1
private const val SETTINGS_PAGE = 2

/**
 * The session screen, three pages wide. The numbers page (middle) carries one pill for the
 * action you need at the line: Recovery, Go, or Resume. Nothing else on it reacts to touch.
 * Controls (Pause / Resume, Finish) are a swipe to the right; settings a swipe to the left.
 */
@Composable
fun RunScreen(viewModel: RunViewModel, settingsViewModel: SettingsViewModel, onFinished: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ambient = LocalAmbientMode.current
    val settings = state.settings
    val ticking = state.phase == RunPhase.RUNNING || state.phase == RunPhase.RECOVERY

    // Live clock: 4 Hz while interactive, 1 Hz in ambient, frozen otherwise.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(ticking, ambient) {
        now = SystemClock.elapsedRealtime()
        if (!ticking) return@LaunchedEffect
        val period = if (ambient) AMBIENT_TICK_MS else INTERACTIVE_TICK_MS
        while (isActive) {
            // Align on the period so displayed seconds change on time rather than up to one period late.
            delay(period - SystemClock.elapsedRealtime() % period)
            now = SystemClock.elapsedRealtime()
        }
    }

    LaunchedEffect(state.phase) {
        if (state.phase == RunPhase.FINISHED) onFinished()
    }

    KeepScreenOn(enabled = settings.keepScreenOn && ticking)

    val flash = remember { Animatable(0f) }
    LaunchedEffect(state.lastMark?.atRealtime) {
        if (state.lastMark == null || ambient) return@LaunchedEffect
        flash.snapTo(1f)
        flash.animateTo(0f, tween(durationMillis = 700))
    }

    val pagerState = rememberPagerState(initialPage = METRICS_PAGE) { 3 }
    // After a control tap the phase changes: bring the numbers back without a swipe.
    LaunchedEffect(state.phase) {
        if (pagerState.currentPage != METRICS_PAGE) pagerState.animateScrollToPage(METRICS_PAGE)
    }

    if (ambient) {
        MetricsPage(state, now, flash.value, ambient = true, onEndRep = {}, onGo = {}, onResume = {})
        return
    }

    HorizontalPagerScaffold(pagerState = pagerState) {
        HorizontalPager(state = pagerState) { page ->
            AnimatedPage(pageIndex = page, pagerState = pagerState) {
                when (page) {
                    METRICS_PAGE -> MetricsPage(
                        state, now, flash.value, ambient = false,
                        onEndRep = viewModel::endRep, onGo = viewModel::go, onResume = viewModel::resume,
                    )
                    CONTROLS_PAGE -> ControlsPage(
                        state.phase,
                        onPause = viewModel::pause, onResume = viewModel::resume, onFinish = viewModel::finishSession,
                    )
                    else -> SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }
}

@Composable
private fun MetricsPage(
    state: RunState,
    now: Long,
    flashAlpha: Float,
    ambient: Boolean,
    onEndRep: () -> Unit,
    onGo: () -> Unit,
    onResume: () -> Unit,
) {
    val settings = state.settings
    val elapsed = state.elapsedMs(now)
    val running = state.phase == RunPhase.RUNNING
    val recovery = state.phase == RunPhase.RECOVERY
    val paused = state.phase == RunPhase.PAUSED
    val inRep = running || (paused && state.pausedFrom == RunPhase.RUNNING)
    val inRecovery = recovery || (paused && state.pausedFrom == RunPhase.RECOVERY)
    val msToNext = PaceMath.msToNextMark(elapsed, settings)
    val repDue = inRep && settings.hasRepDistance && elapsed >= settings.repEndMs
    val imminent = running && !repDue && msToNext <= IMMINENT_MS
    val progress = if (running && !repDue) PaceMath.progressInSplit(elapsed, settings.splitMs) else 0f
    val timedRecovery = inRecovery && settings.isTimedRecovery
    val recoveryLeft = settings.recoveryMs - elapsed
    val overdue = timedRecovery && recoveryLeft <= 0
    val flashColor = if (state.lastMark?.isLap == true) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val white = Color.White
    val mainColor = when {
        ambient -> white
        imminent || overdue || repDue -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onBackground
    }

    ScreenScaffold { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (!ambient && running) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    colors = ProgressIndicatorDefaults.colors(
                        indicatorColor = if (imminent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    strokeWidth = 6.dp,
                )
            }
            if (!ambient && flashAlpha > 0f) {
                Box(Modifier.fillMaxSize().background(flashColor.copy(alpha = 0.35f * flashAlpha)))
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Below the time text, above the action pill and its margin.
                    .padding(top = contentPadding.calculateTopPadding() + 6.dp, bottom = if (ambient) 0.dp else ACTION_PILL_SPACE)
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // 1. Status, only when there is one: in a plain rep the chrono comes first.
                val header = when {
                    repDue -> stringResource(R.string.rep_due)
                    recovery -> stringResource(R.string.recovery).uppercase()
                    paused -> stringResource(R.string.paused).uppercase()
                    else -> null
                }
                if (header != null) {
                    Text(
                        text = header,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            ambient -> white
                            repDue -> MaterialTheme.colorScheme.tertiary
                            recovery -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }

                // 2. The big number: chrono in a rep, countdown in a timed recovery.
                val main = when {
                    timedRecovery && !overdue -> PaceMath.formatCountdown(recoveryLeft)
                    timedRecovery -> "+" + PaceMath.formatChrono(-recoveryLeft, tenths = false)
                    else -> PaceMath.formatChrono(elapsed, tenths = false)
                }
                Text(text = main, style = MaterialTheme.typography.numeralLarge, color = mainColor, maxLines = 1)

                // 3. Context, one line: distance in a rep; the rep just done in recovery.
                Spacer(Modifier.height(2.dp))
                val context = when {
                    inRep && settings.showDistance -> distanceLine(elapsed, settings)
                    inRecovery -> state.lastRep?.let { rep ->
                        val delta = rep.deltaMs
                        if (delta != null) PaceMath.formatChrono(rep.durationMs, tenths = true) + " · " + PaceMath.formatDelta(delta)
                        else PaceMath.formatChrono(rep.durationMs, tenths = true) +
                            if (settings.showDistance) " · " + distanceText(rep.durationMs, settings) else ""
                    }
                    else -> null
                }
                // Heart rate rides on the same line: a fifth row would not fit above the pill.
                val heart = if (settings.showHeartRate && !ambient) " · \u2665 " + (state.heartRateBpm?.toString() ?: "--") else ""
                if (context != null || heart.isNotEmpty()) {
                    Text(
                        text = (context ?: "") + heart,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (ambient) white else MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                    )
                }

                // 4. What is coming, right under the distance: "16 s → 100 m".
                if (running && !repDue) {
                    Text(
                        text = stringResource(
                            R.string.next_cue,
                            countdownText(msToNext),
                            PaceMath.formatInterval(PaceMath.nextMarkMeters(elapsed, settings)),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            ambient -> white
                            imminent -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        maxLines = 1,
                    )
                }

                if (state.degraded && !ambient) {
                    Text(
                        text = stringResource(R.string.degraded_hint),
                        style = MaterialTheme.typography.bodyExtraSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }

            if (!ambient && state.isActive) {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = ACTION_PILL_MARGIN), contentAlignment = Alignment.BottomCenter) {
                    PrimaryAction(running, recovery, onEndRep, onGo, onResume)
                }
            }
        }
    }
}

/** The single control on the numbers page: end the rep, start the next one, or resume. */
@Composable
private fun PrimaryAction(running: Boolean, recovery: Boolean, onEndRep: () -> Unit, onGo: () -> Unit, onResume: () -> Unit) {
    when {
        running -> ActionPill(
            stringResource(R.string.end_rep),
            onClick = onEndRep,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        )
        recovery -> ActionPill(stringResource(R.string.go), onClick = onGo)
        else -> ActionPill(stringResource(R.string.resume_short), onClick = onResume, colors = ButtonDefaults.filledTonalButtonColors())
    }
}

/** "212 / 400 m" during a measured rep, "212 m" otherwise: one string, so it cannot misalign. */
@Composable
private fun distanceLine(elapsedMs: Long, settings: PaceSettings): String {
    val meters = PaceMath.theoreticalDistanceMeters(elapsedMs, settings.secondsPerKm)
    return if (settings.hasRepDistance && settings.repMeters < 1_000 && !settings.isRoadInterval) {
        "${meters.toInt().coerceAtMost(settings.repMeters)} / ${settings.repMeters} m"
    } else if (settings.hasRepDistance) {
        distanceText(elapsedMs, settings) + " / " + PaceMath.formatInterval(settings.repMeters)
    } else {
        distanceText(elapsedMs, settings)
    }
}

@Composable
private fun distanceText(elapsedMs: Long, settings: PaceSettings): String =
    PaceMath.formatDistance(
        PaceMath.theoreticalDistanceMeters(elapsedMs, settings.secondsPerKm),
        kmFromMeters = if (settings.isRoadInterval) 1_000 else 10_000,
    )

@Composable
private fun countdownText(ms: Long): String {
    val value = PaceMath.formatCountdown(ms)
    return if (ms < 60_000) "$value s" else value
}

/** Left page: the rare actions, out of reach of an accidental tap. */
@Composable
private fun ControlsPage(phase: RunPhase, onPause: () -> Unit, onResume: () -> Unit, onFinish: () -> Unit) {
    ScreenScaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            if (phase == RunPhase.PAUSED) {
                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.resume), maxLines = 1) },
                )
            } else {
                Button(
                    onClick = onPause,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    label = { Text(stringResource(R.string.pause), maxLines = 1) },
                )
            }
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                label = { Text(stringResource(R.string.finish), maxLines = 1) },
            )
        }
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
