package fr.dreau.trackpacer

import android.app.Application
import fr.dreau.trackpacer.data.RunStateHolder
import fr.dreau.trackpacer.data.SettingsRepository

/** Tiny manual service locator — the app is small enough not to need Hilt. */
class PacerApp : Application() {
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val runStateHolder: RunStateHolder by lazy { RunStateHolder() }
}
