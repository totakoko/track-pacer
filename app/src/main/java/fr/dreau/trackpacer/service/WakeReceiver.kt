package fr.dreau.trackpacer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * Target of the exact alarm that wakes the CPU shortly before a cue cluster.
 * The system holds a wake lock only while onReceive() runs, so a short bridging lock
 * is taken here until [PacerService] takes its own.
 */
class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(PowerManager::class.java)
        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PacerService:bridge").acquire(BRIDGE_MS)
        // The service is in the foreground, so this is allowed; never crash the receiver if it is not.
        runCatching { context.startService(Intent(context, PacerService::class.java).setAction(PacerService.ACTION_WAKE)) }
    }

    private companion object {
        /** onStartCommand() follows within milliseconds; measured on a Pixel Watch 2 from doze. */
        const val BRIDGE_MS = 2_000L
    }
}
