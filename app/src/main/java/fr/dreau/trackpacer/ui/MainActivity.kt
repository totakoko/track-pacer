package fr.dreau.trackpacer.ui

import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.ambient.AmbientLifecycleObserver
import fr.dreau.trackpacer.PacerApp
import fr.dreau.trackpacer.ui.theme.TrackPacerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var isAmbient by mutableStateOf(false)
    private var resumed = false
    private var sessionActive = false

    /**
     * Ambient mode suspends the CPU between the service's cues, which makes the on-screen
     * seconds stutter. While this activity is visible in ambient during a session, keep
     * the CPU up: the display already costs far more than it does.
     */
    private var ambientLock: PowerManager.WakeLock? = null

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isAmbient = true
            updateAmbientLock()
        }

        override fun onExitAmbient() {
            isAmbient = false
            updateAmbientLock()
        }

        override fun onUpdateAmbient() = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback))
        ambientLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TrackPacer:ambientUi")
            .apply { setReferenceCounted(false) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as PacerApp).runStateHolder.state.collect {
                    sessionActive = it.isActive
                    updateAmbientLock()
                }
            }
        }
        setContent {
            CompositionLocalProvider(LocalAmbientMode provides isAmbient) {
                TrackPacerTheme {
                    PacerNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        updateAmbientLock()
    }

    override fun onPause() {
        resumed = false
        updateAmbientLock()
        super.onPause()
    }

    override fun onDestroy() {
        ambientLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun updateAmbientLock() {
        val lock = ambientLock ?: return
        if (isAmbient && resumed && sessionActive) {
            lock.acquire(AMBIENT_LOCK_TIMEOUT_MS)
        } else if (lock.isHeld) {
            lock.release()
        }
    }

    private companion object {
        const val AMBIENT_LOCK_TIMEOUT_MS = 4L * 60 * 60 * 1000
    }
}
