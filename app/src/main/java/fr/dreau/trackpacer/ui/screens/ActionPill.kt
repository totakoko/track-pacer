package fr.dreau.trackpacer.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/**
 * The one action of a screen: a compact pill above the bottom edge. The margin keeps its
 * rounded ends inside the round display (a 110 dp pill needs ~17 dp from the bottom).
 */
@Composable
fun ActionPill(
    text: String,
    onClick: () -> Unit,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        colors = colors,
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

/** Room to leave under a screen's content for an [ActionPill] and its bottom margin. */
val ACTION_PILL_SPACE = 62.dp
val ACTION_PILL_MARGIN = 22.dp
