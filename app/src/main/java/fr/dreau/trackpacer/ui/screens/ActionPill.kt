package fr.dreau.trackpacer.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/** The one action of a screen: a compact pill that sits above the bottom edge with a margin. */
@Composable
fun ActionPill(text: String, onClick: () -> Unit, colors: ButtonColors = ButtonDefaults.buttonColors()) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(40.dp),
        colors = colors,
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

/** Room to leave under a screen's content for an [ActionPill] and its bottom margin. */
val ACTION_PILL_SPACE = 58.dp
val ACTION_PILL_MARGIN = 12.dp
