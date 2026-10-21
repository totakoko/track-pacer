package fr.dreau.trackpacer.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.wear.ambient.AmbientLifecycleObserver
import fr.dreau.trackpacer.PacerApp
import fr.dreau.trackpacer.service.PacerService
import fr.dreau.trackpacer.ui.theme.TrackPacerTheme

class MainActivity : ComponentActivity() {

    private var isAmbient by mutableStateOf(false)

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isAmbient = true
        }

        override fun onExitAmbient() {
            isAmbient = false
        }

        override fun onUpdateAmbient() = Unit
    }

    /**
     * Side button(s) drive the chrono during a session: the system reserves the long press
     * (Assistant, then power menu), so only short presses reach the app.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val isStem = keyCode == KeyEvent.KEYCODE_STEM_1 ||
            keyCode == KeyEvent.KEYCODE_STEM_2 ||
            keyCode == KeyEvent.KEYCODE_STEM_3
        if (isStem && event.repeatCount == 0) {
            val sessionActive = (application as PacerApp).runStateHolder.state.value.isActive
            if (sessionActive) {
                PacerService.physicalButton(this)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback))
        setContent {
            CompositionLocalProvider(LocalAmbientMode provides isAmbient) {
                TrackPacerTheme {
                    PacerNavHost()
                }
            }
        }
    }
}
