package fr.dreau.trackpacer.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Runtime permissions, resolved per OS version (Wear OS 6 / API 36 moved heart rate to a health permission). */
object Permissions {
    private const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"

    val heartRate: String
        get() = if (Build.VERSION.SDK_INT >= 36) READ_HEART_RATE else Manifest.permission.BODY_SENSORS

    /** Needed for the `health` foreground-service type and for Health Services. */
    const val ACTIVITY_RECOGNITION = Manifest.permission.ACTIVITY_RECOGNITION

    const val POST_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS

    fun forSession(wantHeartRate: Boolean): Array<String> = buildList {
        add(ACTIVITY_RECOGNITION)
        add(POST_NOTIFICATIONS)
        if (wantHeartRate) add(heartRate)
    }.toTypedArray()

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun missing(context: Context, wantHeartRate: Boolean): Array<String> =
        forSession(wantHeartRate).filterNot { isGranted(context, it) }.toTypedArray()
}
