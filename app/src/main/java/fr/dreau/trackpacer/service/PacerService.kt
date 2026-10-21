package fr.dreau.trackpacer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import fr.dreau.trackpacer.PacerApp
import fr.dreau.trackpacer.R
import fr.dreau.trackpacer.data.RunStateHolder
import fr.dreau.trackpacer.domain.Block
import fr.dreau.trackpacer.domain.MarkHit
import fr.dreau.trackpacer.domain.PaceMath
import fr.dreau.trackpacer.domain.PaceSettings
import fr.dreau.trackpacer.domain.RunPhase
import fr.dreau.trackpacer.domain.RunState
import fr.dreau.trackpacer.domain.SplitEvent
import fr.dreau.trackpacer.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service owning the chronometer and the cue scheduler.
 *
 * Runs as a `health` foreground service with a partial wake lock so that cues fire
 * on time even when the watch drops into ambient mode. The UI never receives ticks:
 * it reads [RunStateHolder] and derives the live chrono from elapsedRealtime().
 */
class PacerService : LifecycleService() {

    private lateinit var stateHolder: RunStateHolder
    private lateinit var beeper: Beeper
    private lateinit var haptics: Haptics
    private var heartRate: HeartRateMonitor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var ongoingActivity: OngoingActivity? = null
    private var schedulerJob: Job? = null
    private var settings: PaceSettings = PaceSettings()
    private var foregroundStarted = false
    private var lastButtonPressRealtime = 0L

    override fun onCreate() {
        super.onCreate()
        stateHolder = (application as PacerApp).runStateHolder
        beeper = Beeper()
        haptics = Haptics(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startSession()
            ACTION_PAUSE -> pauseBlock()
            ACTION_RESUME -> resumeBlock()
            ACTION_STOP_BLOCK -> stopBlock()
            ACTION_NEW_BLOCK -> startBlock(stateHolder.state.value.blockNumber + 1)
            ACTION_FINISH -> finishSession()
            ACTION_BUTTON -> onPhysicalButton()
        }
        return START_NOT_STICKY
    }

    private fun startSession() {
        if (stateHolder.state.value.isActive) return // double tap on Start
        // startForeground() must be called promptly after startForegroundService(): do it
        // before any suspending work.
        stateHolder.update { RunState() }
        promoteToForeground()
        acquireWakeLock()
        lifecycleScope.launch {
            settings = (application as PacerApp).settingsRepository.current()
            beeper.setVolume(settings.volumeGain)
            stateHolder.update { it.copy(settings = settings) }
            if (settings.showHeartRate && Permissions.isGranted(this@PacerService, Permissions.heartRate)) {
                heartRate = HeartRateMonitor(this@PacerService) { bpm ->
                    stateHolder.update { it.copy(heartRateBpm = bpm) }
                }.also { it.start() }
            }
            startBlock(1)
        }
    }

    private fun startBlock(number: Int) {
        if (!foregroundStarted) promoteToForeground() // permissions may have been granted since
        val start = SystemClock.elapsedRealtime()
        stateHolder.update {
            it.copy(
                phase = RunPhase.RUNNING,
                blockNumber = number,
                blockStartRealtime = start,
                frozenElapsedMs = 0L,
                lastMark = null,
            )
        }
        launchScheduler(start)
    }

    private fun launchScheduler(blockStart: Long) {
        schedulerJob?.cancel()
        updateOngoingStatus(blockStart)
        schedulerJob = lifecycleScope.launch(Dispatchers.Default) { runScheduler(blockStart) }
    }

    private fun pauseBlock() {
        if (stateHolder.state.value.phase != RunPhase.RUNNING) return
        schedulerJob?.cancel()
        schedulerJob = null
        val now = SystemClock.elapsedRealtime()
        stateHolder.update { it.copy(phase = RunPhase.PAUSED, frozenElapsedMs = now - it.blockStartRealtime) }
        updateOngoing(Status.Builder().addTemplate(getString(R.string.ongoing_paused)).build())
    }

    /** Resume by shifting the virtual start so the chrono continues from the frozen value. */
    private fun resumeBlock() {
        val state = stateHolder.state.value
        if (state.phase != RunPhase.PAUSED) return
        val start = SystemClock.elapsedRealtime() - state.frozenElapsedMs
        stateHolder.update { it.copy(phase = RunPhase.RUNNING, blockStartRealtime = start) }
        launchScheduler(start)
    }

    /**
     * Side button: one press toggles pause (or starts the next block when stopped);
     * two presses within [DOUBLE_PRESS_MS] end the block. A block shorter than one
     * second is never ended this way, so a double press on "stopped" cannot record a
     * phantom block.
     */
    private fun onPhysicalButton() {
        val now = SystemClock.elapsedRealtime()
        val state = stateHolder.state.value
        val isDouble = now - lastButtonPressRealtime <= DOUBLE_PRESS_MS
        lastButtonPressRealtime = now
        if (isDouble && (state.phase == RunPhase.RUNNING || state.phase == RunPhase.PAUSED)) {
            if (state.elapsedMs(now) >= 1_000L) {
                stopBlock()
                lastButtonPressRealtime = 0L
            }
            return
        }
        when (state.phase) {
            RunPhase.RUNNING -> pauseBlock()
            RunPhase.PAUSED -> resumeBlock()
            RunPhase.STOPPED -> startBlock(state.blockNumber + 1)
            RunPhase.IDLE, RunPhase.FINISHED -> Unit
        }
    }

    /** Sleeps until each cue and fires it. Drift-free: every wait is recomputed from the clock. */
    private suspend fun runScheduler(blockStart: Long) {
        while (currentCoroutineContext().isActive) {
            val elapsed = SystemClock.elapsedRealtime() - blockStart
            val event = PaceMath.nextEvent(elapsed, settings)
            delay(event.atMs - elapsed)
            if (!currentCoroutineContext().isActive) return
            fire(event)
        }
    }

    private fun fire(event: SplitEvent) {
        if (settings.soundEnabled) beeper.play(event.kind)
        if (settings.vibrationEnabled) haptics.play(event.kind)
        if (!event.isPreCue) {
            stateHolder.update {
                it.copy(lastMark = MarkHit(event.markMeters, event.kind == SplitEvent.Kind.LAP, SystemClock.elapsedRealtime()))
            }
        }
    }

    private fun stopBlock() {
        schedulerJob?.cancel()
        schedulerJob = null
        val now = SystemClock.elapsedRealtime()
        stateHolder.update { s ->
            val duration = when (s.phase) {
                RunPhase.RUNNING -> now - s.blockStartRealtime
                RunPhase.PAUSED -> s.frozenElapsedMs
                else -> return@update s
            }
            s.copy(
                phase = RunPhase.STOPPED,
                frozenElapsedMs = duration,
                completedBlocks = s.completedBlocks + Block(s.blockNumber, duration, settings.secondsPerKm),
            )
        }
        updateOngoing(Status.Builder().addTemplate(getString(R.string.ongoing_stopped)).build())
    }

    private fun finishSession() {
        if (stateHolder.state.value.phase in setOf(RunPhase.RUNNING, RunPhase.PAUSED)) stopBlock()
        stateHolder.update { it.copy(phase = RunPhase.FINISHED, heartRateBpm = null) }
        lifecycleScope.launch {
            heartRate?.stop()
            heartRate = null
            releaseWakeLock()
            ServiceCompat.stopForeground(this@PacerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
            stopSelf()
        }
    }

    // --- Foreground plumbing -------------------------------------------------

    private fun promoteToForeground() {
        createChannel()
        val notification = buildNotification()
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
            foregroundStarted = true
            stateHolder.update { it.copy(degraded = false) }
        } catch (e: Exception) {
            // Typically SecurityException when ACTIVITY_RECOGNITION was denied: keep running
            // in-process; cues may be delayed if the watch goes to sleep.
            Log.w(TAG, "Foreground promotion failed, running degraded", e)
            stateHolder.update { it.copy(degraded = true) }
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.ongoing_running))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        ongoingActivity = OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_notification)
            .setTouchIntent(contentIntent)
            .setStatus(Status.Builder().addTemplate(getString(R.string.ongoing_running)).build())
            .build()
            .also { if (canPostNotifications()) it.apply(applicationContext) }

        return builder.build()
    }

    private fun updateOngoingStatus(blockStartRealtime: Long) {
        updateOngoing(
            Status.Builder()
                .addTemplate("#time#")
                .addPart("time", Status.StopwatchPart(blockStartRealtime))
                .build(),
        )
    }

    /** The ongoing-activity indicator on the watch face is best effort: never let it break a session. */
    private fun updateOngoing(status: Status) {
        if (!canPostNotifications()) return
        try {
            ongoingActivity?.update(this, status)
        } catch (e: SecurityException) {
            Log.w(TAG, "Ongoing activity update refused", e)
        }
    }

    private fun canPostNotifications(): Boolean = Permissions.isGranted(this, Permissions.POST_NOTIFICATIONS)

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:session").also {
            it.acquire(MAX_SESSION_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        schedulerJob?.cancel()
        releaseWakeLock()
        beeper.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PacerService"
        private const val CHANNEL_ID = "pacer_session"
        private const val NOTIFICATION_ID = 1
        private const val MAX_SESSION_MS = 3L * 60 * 60 * 1000
        private const val DOUBLE_PRESS_MS = 450L

        const val ACTION_START = "fr.dreau.trackpacer.START"
        const val ACTION_PAUSE = "fr.dreau.trackpacer.PAUSE"
        const val ACTION_RESUME = "fr.dreau.trackpacer.RESUME"
        const val ACTION_BUTTON = "fr.dreau.trackpacer.BUTTON"
        const val ACTION_STOP_BLOCK = "fr.dreau.trackpacer.STOP_BLOCK"
        const val ACTION_NEW_BLOCK = "fr.dreau.trackpacer.NEW_BLOCK"
        const val ACTION_FINISH = "fr.dreau.trackpacer.FINISH"

        fun start(context: Context) = send(context, ACTION_START, foreground = true)
        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun physicalButton(context: Context) = send(context, ACTION_BUTTON)
        fun stopBlock(context: Context) = send(context, ACTION_STOP_BLOCK)
        fun newBlock(context: Context) = send(context, ACTION_NEW_BLOCK)
        fun finish(context: Context) = send(context, ACTION_FINISH)

        private fun send(context: Context, action: String, foreground: Boolean = false) {
            val intent = Intent(context, PacerService::class.java).setAction(action)
            if (foreground) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        }
    }
}
