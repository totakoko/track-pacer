package fr.dreau.trackpacer.service

import android.annotation.SuppressLint
import android.app.AlarmManager
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
 * Session flow: rep (paced, cues) → recovery (plain chrono, silent) → rep → … → finish.
 * The UI never receives ticks: it reads [RunStateHolder] and derives the live chrono
 * from elapsedRealtime().
 *
 * Power: the CPU is only kept awake while a cue is imminent. When the next cue is far
 * away an exact alarm re-arms the scheduler [WAKE_AHEAD_MS] before it and the partial
 * wake lock is released, so the SoC can suspend between marks. Recovery and pause hold
 * no lock at all. Without the exact-alarm permission the lock is held for the whole rep.
 */
class PacerService : LifecycleService() {

    private lateinit var stateHolder: RunStateHolder
    private lateinit var beeper: Beeper
    private lateinit var haptics: Haptics
    private lateinit var volumeBoost: MediaVolumeBoost
    private lateinit var alarmManager: AlarmManager
    private var heartRate: HeartRateMonitor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var ongoingActivity: OngoingActivity? = null
    private var schedulerJob: Job? = null
    private var settings: PaceSettings = PaceSettings()
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        stateHolder = (application as PacerApp).runStateHolder
        beeper = Beeper()
        haptics = Haptics(this)
        volumeBoost = MediaVolumeBoost(this)
        alarmManager = getSystemService(AlarmManager::class.java)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:cues")
            .apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startSession()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_END_REP -> endRep()
            ACTION_GO -> go()
            ACTION_FINISH -> finishSession()
            ACTION_WAKE -> onWake()
        }
        return START_NOT_STICKY
    }

    private fun startSession() {
        if (stateHolder.state.value.isActive) return // double tap on Start
        // startForeground() must be called promptly after startForegroundService(): do it
        // before any suspending work.
        stateHolder.update { RunState() }
        promoteToForeground()
        lifecycleScope.launch {
            settings = (application as PacerApp).settingsRepository.current()
            beeper.setVolume(settings.volumeGain)
            if (settings.soundEnabled && settings.boostMediaVolume) volumeBoost.boost()
            stateHolder.update { it.copy(settings = settings) }
            if (settings.showHeartRate && Permissions.isGranted(this@PacerService, Permissions.heartRate)) {
                heartRate = HeartRateMonitor(this@PacerService) { bpm ->
                    stateHolder.update { it.copy(heartRateBpm = bpm) }
                }.also { it.start() }
            }
            startRep(1)
        }
    }

    private fun startRep(number: Int) {
        if (!foregroundStarted) promoteToForeground() // permissions may have been granted since
        val start = SystemClock.elapsedRealtime()
        stateHolder.update {
            it.copy(
                phase = RunPhase.RUNNING,
                repNumber = number,
                blockStartRealtime = start,
                frozenElapsedMs = 0L,
                lastMark = null,
            )
        }
        updateOngoing(stopwatchStatus(R.string.ongoing_rep, start))
        armScheduler(start, recovery = false)
    }

    /** End the paced rep and start the recovery chrono right away: one tap at the line. */
    private fun endRep() {
        val now = SystemClock.elapsedRealtime()
        val state = stateHolder.state.value
        val duration = when {
            state.phase == RunPhase.RUNNING -> now - state.blockStartRealtime
            state.phase == RunPhase.PAUSED && state.pausedFrom == RunPhase.RUNNING -> state.frozenElapsedMs
            else -> return
        }
        disarmScheduler()
        stateHolder.update {
            it.copy(
                phase = RunPhase.RECOVERY,
                blockStartRealtime = now,
                frozenElapsedMs = 0L,
                lastMark = null,
                completedBlocks = it.completedBlocks +
                    Block(it.repNumber, duration, settings.secondsPerKm, targetMs = settings.repEndMs.takeIf { t -> t > 0 }),
            )
        }
        startRecoveryTimer(now)
    }

    /** Timed recovery: countdown on the watch face and cues (then auto start) at the end. */
    private fun startRecoveryTimer(start: Long) {
        if (settings.isTimedRecovery) {
            updateOngoing(timerStatus(start + settings.recoveryMs))
            armScheduler(start, recovery = true)
        } else {
            updateOngoing(stopwatchStatus(R.string.ongoing_recovery, start))
        }
    }

    /** Close the recovery and launch the next paced rep. */
    private fun go() {
        val now = SystemClock.elapsedRealtime()
        val state = stateHolder.state.value
        val recovery = when {
            state.phase == RunPhase.RECOVERY -> now - state.blockStartRealtime
            state.phase == RunPhase.PAUSED && state.pausedFrom == RunPhase.RECOVERY -> state.frozenElapsedMs
            else -> return
        }
        stateHolder.update {
            it.copy(completedBlocks = it.completedBlocks + Block(it.repNumber, recovery, settings.secondsPerKm, isRecovery = true))
        }
        startRep(state.repNumber + 1)
    }

    private fun pause() {
        val state = stateHolder.state.value
        if (state.phase != RunPhase.RUNNING && state.phase != RunPhase.RECOVERY) return
        disarmScheduler()
        val now = SystemClock.elapsedRealtime()
        stateHolder.update {
            it.copy(phase = RunPhase.PAUSED, pausedFrom = state.phase, frozenElapsedMs = now - it.blockStartRealtime)
        }
        updateOngoing(Status.Builder().addTemplate(getString(R.string.ongoing_paused)).build())
    }

    /** Resume by shifting the virtual start so the chrono continues from the frozen value. */
    private fun resume() {
        val state = stateHolder.state.value
        if (state.phase != RunPhase.PAUSED) return
        val start = SystemClock.elapsedRealtime() - state.frozenElapsedMs
        stateHolder.update { it.copy(phase = state.pausedFrom, blockStartRealtime = start) }
        if (state.pausedFrom == RunPhase.RUNNING) {
            updateOngoing(stopwatchStatus(R.string.ongoing_rep, start))
            armScheduler(start, recovery = false)
        } else {
            startRecoveryTimer(start)
        }
    }

    private fun finishSession() {
        val state = stateHolder.state.value
        if (state.phase == RunPhase.RUNNING || (state.phase == RunPhase.PAUSED && state.pausedFrom == RunPhase.RUNNING)) {
            endRep() // records the rep; the recovery it opens is discarded below
        }
        disarmScheduler()
        stateHolder.update { it.copy(phase = RunPhase.FINISHED, heartRateBpm = null) }
        volumeBoost.restore()
        lifecycleScope.launch {
            heartRate?.stop()
            heartRate = null
            ServiceCompat.stopForeground(this@PacerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
            stopSelf()
        }
    }

    // --- Cue scheduler -------------------------------------------------------

    private fun armScheduler(blockStart: Long, recovery: Boolean) {
        schedulerJob?.cancel()
        cancelWakeAlarm()
        holdWakeLock()
        schedulerJob = lifecycleScope.launch(Dispatchers.Default) { runScheduler(blockStart, recovery) }
    }

    private fun nextEvent(elapsed: Long, recovery: Boolean): SplitEvent? =
        if (recovery) PaceMath.nextRecoveryEvent(elapsed, settings) else PaceMath.nextRepEvent(elapsed, settings)

    private fun disarmScheduler() {
        schedulerJob?.cancel()
        schedulerJob = null
        cancelWakeAlarm()
        releaseWakeLock()
    }

    /** The alarm fired: a cue is [WAKE_AHEAD_MS] away, take the CPU back. */
    private fun onWake() {
        val state = stateHolder.state.value
        when (state.phase) {
            RunPhase.RUNNING -> armScheduler(state.blockStartRealtime, recovery = false)
            RunPhase.RECOVERY -> if (settings.isTimedRecovery) armScheduler(state.blockStartRealtime, recovery = true)
            else -> Unit
        }
    }

    /**
     * Sleeps until each cue and fires it. Drift-free: every wait is recomputed from the
     * clock. A long wait hands over to an exact alarm and lets the CPU suspend; the
     * loop is restarted by [onWake].
     */
    private suspend fun runScheduler(blockStart: Long, recovery: Boolean) {
        while (currentCoroutineContext().isActive) {
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - blockStart
            val event = nextEvent(elapsed, recovery)
            if (event == null) { // timed recovery over: nothing left to cue
                releaseWakeLock()
                return
            }
            val wait = event.atMs - elapsed
            if (wait > SLEEP_THRESHOLD_MS && canUseExactAlarms()) {
                scheduleWakeAlarm(now + wait - WAKE_AHEAD_MS)
                releaseWakeLock()
                return
            }
            holdWakeLock()
            delay(wait)
            if (!currentCoroutineContext().isActive) return
            fire(event, blockStart, recovery)
        }
    }

    private fun fire(event: SplitEvent, blockStart: Long, recovery: Boolean) {
        val now = SystemClock.elapsedRealtime()
        Log.d(TAG, "cue ${event.kind} planned +${event.atMs} ms, fired +${now - blockStart} ms (late ${now - blockStart - event.atMs} ms)")
        if (settings.soundEnabled) beeper.play(event.kind)
        if (settings.vibrationEnabled) haptics.play(event.kind)
        if (event.isPreCue) return
        stateHolder.update {
            it.copy(lastMark = MarkHit(event.markMeters, event.kind == SplitEvent.Kind.LAP, now))
        }
        if (recovery && settings.autoStart) lifecycleScope.launch { go() }
        if (event.kind == SplitEvent.Kind.REP_END && settings.autoRecovery) lifecycleScope.launch { endRep() }
    }

    private fun canUseExactAlarms(): Boolean = alarmManager.canScheduleExactAlarms()

    // USE_EXACT_ALARM (manifest) grants exact alarms without a user prompt on API 33+;
    // lint only knows SCHEDULE_EXACT_ALARM. canUseExactAlarms() still guards every call.
    @SuppressLint("MissingPermission")
    private fun scheduleWakeAlarm(triggerAtRealtime: Long) {
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtRealtime, wakePendingIntent())
        } catch (e: SecurityException) {
            // Permission revoked mid-session: fall back to keeping the CPU awake.
            Log.w(TAG, "Exact alarm refused, holding the wake lock instead", e)
            val state = stateHolder.state.value
            val recovery = state.phase == RunPhase.RECOVERY
            if (state.phase == RunPhase.RUNNING || (recovery && settings.isTimedRecovery)) {
                holdWakeLock()
                schedulerJob = lifecycleScope.launch(Dispatchers.Default) { runSchedulerAwake(state.blockStartRealtime, recovery) }
            }
        }
    }

    /** Fallback loop that never releases the wake lock. */
    private suspend fun runSchedulerAwake(blockStart: Long, recovery: Boolean) {
        while (currentCoroutineContext().isActive) {
            val elapsed = SystemClock.elapsedRealtime() - blockStart
            val event = nextEvent(elapsed, recovery) ?: return releaseWakeLock()
            holdWakeLock()
            delay(event.atMs - elapsed)
            if (!currentCoroutineContext().isActive) return
            fire(event, blockStart, recovery)
        }
    }

    private fun cancelWakeAlarm() = alarmManager.cancel(wakePendingIntent())

    private fun wakePendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        this, 0,
        Intent(this, WakeReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Non-reference-counted: each call refreshes the timeout. */
    private fun holdWakeLock() {
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
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

    private fun stopwatchStatus(templateRes: Int, startRealtime: Long): Status =
        Status.Builder()
            .addTemplate(getString(templateRes))
            .addPart("time", Status.StopwatchPart(startRealtime))
            .build()

    private fun timerStatus(endRealtime: Long): Status =
        Status.Builder()
            .addTemplate(getString(R.string.ongoing_recovery))
            .addPart("time", Status.TimerPart(endRealtime))
            .build()

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

    override fun onDestroy() {
        disarmScheduler()
        volumeBoost.restore()
        beeper.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PacerService"
        private const val CHANNEL_ID = "pacer_session"
        private const val NOTIFICATION_ID = 1

        /** Gaps longer than this let the CPU suspend between cues. */
        private const val SLEEP_THRESHOLD_MS = 20_000L
        /** How early the alarm wakes the CPU before the cue it protects. */
        private const val WAKE_AHEAD_MS = 3_000L
        /** Safety net: the lock is refreshed at every loop iteration, which is always shorter. */
        private const val WAKE_LOCK_TIMEOUT_MS = 60_000L

        const val ACTION_START = "fr.dreau.trackpacer.START"
        const val ACTION_PAUSE = "fr.dreau.trackpacer.PAUSE"
        const val ACTION_RESUME = "fr.dreau.trackpacer.RESUME"
        const val ACTION_END_REP = "fr.dreau.trackpacer.END_REP"
        const val ACTION_GO = "fr.dreau.trackpacer.GO"
        const val ACTION_FINISH = "fr.dreau.trackpacer.FINISH"
        const val ACTION_WAKE = "fr.dreau.trackpacer.WAKE"

        fun start(context: Context) = send(context, ACTION_START, foreground = true)
        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun endRep(context: Context) = send(context, ACTION_END_REP)
        fun go(context: Context) = send(context, ACTION_GO)
        fun finish(context: Context) = send(context, ACTION_FINISH)

        private fun send(context: Context, action: String, foreground: Boolean = false) {
            val intent = Intent(context, PacerService::class.java).setAction(action)
            if (foreground) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        }
    }
}
