package fr.dreau.trackpacer.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import fr.dreau.trackpacer.R
import fr.dreau.trackpacer.domain.PaceMath
import fr.dreau.trackpacer.ui.RunViewModel

/** End-of-session recap: one card per rep (duration, theoretical distance), recoveries as a line between. */
@Composable
fun SummaryScreen(viewModel: RunViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()
    val blocks = state.completedBlocks
    val settings = state.settings

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onClose) {
                Text(stringResource(R.string.close), maxLines = 1)
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = listPadding(contentPadding)) {
            item {
                ListHeader { Text(stringResource(R.string.summary), textAlign = TextAlign.Center) }
            }
            item {
                Text(
                    text = stringResource(R.string.pace_value, PaceMath.formatPace(settings.secondsPerKm)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(blocks.size) { index ->
                val block = blocks[index]
                if (block.isRecovery) {
                    Text(
                        text = stringResource(R.string.recovery_duration, PaceMath.formatChrono(block.durationMs, tenths = false)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                } else {
                    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.rep_number, block.number),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            PaceMath.formatChrono(block.durationMs, tenths = true),
                            style = MaterialTheme.typography.numeralSmall,
                        )
                        block.deltaMs?.let { delta ->
                            Text(
                                stringResource(R.string.summary_delta, PaceMath.formatChrono(block.targetMs ?: 0L, tenths = true), PaceMath.formatDelta(delta)),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (delta > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                            )
                        }
                        Text(
                            stringResource(
                                R.string.theoretical_distance,
                                PaceMath.formatDistance(
                                    block.theoreticalMeters,
                                    kmFromMeters = if (settings.isRoadInterval) 1_000 else 10_000,
                                ),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (blocks.none { !it.isRecovery }) {
                item {
                    Text(
                        stringResource(R.string.no_blocks),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
