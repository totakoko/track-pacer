package fr.dreau.trackpacer.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Padding for a list that ends with a full-width button: the scaffold's own values plus
 * enough room at the bottom for a stadium-shaped item to sit entirely inside the round
 * display when the list is scrolled to its end.
 */
@Composable
fun listPadding(scaffoldPadding: PaddingValues): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = scaffoldPadding.calculateStartPadding(direction),
        end = scaffoldPadding.calculateEndPadding(direction),
        top = scaffoldPadding.calculateTopPadding(),
        bottom = maxOf(scaffoldPadding.calculateBottomPadding(), 44.dp),
    )
}
