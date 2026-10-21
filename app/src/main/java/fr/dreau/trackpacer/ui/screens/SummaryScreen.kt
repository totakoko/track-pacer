package fr.dreau.trackpacer.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import fr.dreau.trackpacer.R
import fr.dreau.trackpacer.domain.PaceMath
import fr.dreau.trackpacer.ui.RunViewModel

/** End-of-session recap: one card per block with its duration and theoretical distance. */
@Composable
fun SummaryScreen(viewModel: RunViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()
    val blocks = state.completedBlocks

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onClose) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Text(stringResource(R.string.close))
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(stringResource(R.string.summary), textAlign = TextAlign.Center) }
            }
            item {
                Text(
                    text = stringResource(R.string.pace_value, PaceMath.formatPace(state.settings.secondsPerKm)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(blocks.size) { index ->
                val block = blocks[index]
                Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.block_number, block.number),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        PaceMath.formatChrono(block.durationMs, tenths = true),
                        style = MaterialTheme.typography.numeralSmall,
                    )
                    Text(
                        stringResource(R.string.theoretical_distance, PaceMath.formatDistance(block.theoreticalMeters)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (blocks.isEmpty()) {
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
