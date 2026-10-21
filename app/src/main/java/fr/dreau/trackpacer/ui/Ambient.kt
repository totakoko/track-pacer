package fr.dreau.trackpacer.ui

import androidx.compose.runtime.compositionLocalOf

/** True while the watch shows the app in always-on (ambient) mode: dim, static, 1 Hz updates. */
val LocalAmbientMode = compositionLocalOf { false }
