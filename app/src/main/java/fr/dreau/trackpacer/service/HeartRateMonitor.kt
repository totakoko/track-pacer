package fr.dreau.trackpacer.service

import android.content.Context
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.unregisterMeasureCallback

/**
 * Live heart rate from Health Services' MeasureClient (foreground-only measurement,
 * which is exactly what a track session needs).
 */
class HeartRateMonitor(context: Context, private val onBpm: (Int?) -> Unit) {
    private val client: MeasureClient = HealthServices.getClient(context).measureClient
    private var registered = false

    private val callback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            Log.d(TAG, "HR availability: $availability")
        }

        override fun onDataReceived(data: DataPointContainer) {
            val bpm = data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value
            onBpm(bpm?.takeIf { it > 0 }?.toInt())
        }

        override fun onRegistered() {
            registered = true
        }

        override fun onRegistrationFailed(throwable: Throwable) {
            Log.w(TAG, "HR registration failed", throwable)
            onBpm(null)
        }
    }

    fun start() {
        runCatching { client.registerMeasureCallback(DataType.HEART_RATE_BPM, callback) }
            .onFailure { Log.w(TAG, "HR start failed", it) }
    }

    suspend fun stop() {
        if (!registered) return
        registered = false
        runCatching { client.unregisterMeasureCallback(DataType.HEART_RATE_BPM, callback) }
            .onFailure { Log.w(TAG, "HR stop failed", it) }
        onBpm(null)
    }

    private companion object {
        const val TAG = "HeartRateMonitor"
    }
}
