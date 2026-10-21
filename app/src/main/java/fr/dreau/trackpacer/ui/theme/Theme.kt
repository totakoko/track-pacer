package fr.dreau.trackpacer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/** Warm amber accent: high contrast on the AMOLED black background, readable in sunlight. */
private val PacerColorScheme = ColorScheme(
    primary = Color(0xFFFFB955),
    primaryDim = Color(0xFFD9962F),
    primaryContainer = Color(0xFF5C3D00),
    onPrimary = Color(0xFF2B1A00),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFF9CD4FF),
    secondaryDim = Color(0xFF6FB3E6),
    secondaryContainer = Color(0xFF123B55),
    onSecondary = Color(0xFF00223A),
    onSecondaryContainer = Color(0xFFCCE8FF),
    tertiary = Color(0xFFFF8A80),
    tertiaryDim = Color(0xFFE06A60),
    tertiaryContainer = Color(0xFF5A1A14),
    onTertiary = Color(0xFF3A0500),
    onTertiaryContainer = Color(0xFFFFDAD6),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3A0000),
    errorContainer = Color(0xFF7A1F1F),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun TrackPacerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PacerColorScheme, content = content)
}
