package dev.solsynth.cloudysky.sop

import android.content.Context
import android.content.Intent
import android.app.ForegroundServiceStartNotAllowedException
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import dev.solsynth.cloudysky.notifications.NotificationItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.sse.EventSource

class SopListenerService : android.app.Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: SopRepository
    private lateinit var notifier: SopNotifier
    private lateinit var logger: SopNotificationLogger
    private val streamClient = SopStreamClient()
    private val api = SopApi()

    private var eventSource: EventSource? = null
    private var activeJob: Job? = null
    private var pollingJob: Job? = null
    private var timeoutJob: Job? = null
    private var reconnectAttempt = 0
    @Volatile private var stopRequested = false
    @Volatile private var currentRunState = SopRunState.Idle

    override fun onCreate() {
        super.onCreate()
        repository = SopRepository(applicationContext)
        notifier = SopNotifier(applicationContext)
        logger = SopNotificationLogger(applicationContext)
        notifier.ensureChannels()
        if (!enterForeground("Starting notification listener")) {
            repository.requestStart()
            repository.updateStatus(SopListenerStatus.Failed, "Foreground start blocked by system")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRequested = true
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_NOTIFICATION -> {
                updateForegroundNotification()
                return START_STICKY
            }
            else -> {
                stopRequested = false
                if (activeJob?.isActive == true || pollingJob?.isActive == true) {
                    return START_STICKY
                }
                startBasedOnMode()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopRequested = true
        activeJob?.cancel()
        pollingJob?.cancel()
        timeoutJob?.cancel()
        eventSource?.cancel()
        repository.updateStatus(SopListenerStatus.Idle)
        repository.updateRunState(SopRunState.Idle)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startBasedOnMode() {
        if (stopRequested) return
        if (!repository.currentState().enabled) {
            repository.updateStatus(SopListenerStatus.Disabled)
            stopSelf()
            return
        }

        val state = repository.currentState()
        when (state.mode) {
            SopListenerMode.Stream -> enterActiveState()
            SopListenerMode.Polling -> enterIdleState()
            SopListenerMode.Dynamic -> enterIdleState()
        }
    }

    private fun enterIdleState() {
        if (stopRequested) return
        Log.d(TAG, "Entering idle state (polling)")
        val previousState = currentRunState
        currentRunState = SopRunState.Idle
        repository.updateRunState(SopRunState.Idle)
        repository.updateStatus(SopListenerStatus.Idle)
        enterForeground("Polling for notifications")

        if (previousState == SopRunState.Active) {
            logger.logModeSwitch(SopRunState.Active, SopRunState.Idle)
        }

        activeJob?.cancel()
        eventSource?.cancel()
        timeoutJob?.cancel()

        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            val state = repository.currentState()
            val intervalMs = state.dynamicConfig.pollingIntervalMs

            while (isActive && !stopRequested) {
                pollForNotifications()
                delay(intervalMs)
            }
        }
    }

    private fun enterActiveState() {
        if (stopRequested) return
        Log.d(TAG, "Entering active state (streaming)")
        val previousState = currentRunState
        currentRunState = SopRunState.Active
        repository.updateRunState(SopRunState.Active)
        enterForeground("Listening for notifications")

        if (previousState == SopRunState.Idle) {
            logger.logModeSwitch(SopRunState.Idle, SopRunState.Active)
        }

        pollingJob?.cancel()
        timeoutJob?.cancel()

        startStreaming()
    }

    private fun pollForNotifications() {
        if (stopRequested) return
        serviceScope.launch(Dispatchers.IO) {
            try {
                val registration = repository.ensureRegistration().getOrElse {
                    if (it is kotlinx.coroutines.CancellationException) return@launch
                    Log.e(TAG, "Polling registration failed", it)
                    return@launch
                }

                val notifications = api.getNotifications(registration.token, take = 5)
                    ?: return@launch

                val lastSeenId = repository.currentState().lastSeenNotificationId

                if (lastSeenId == null) {
                    notifications.firstOrNull()?.let {
                        repository.setLastSeenNotificationId(it.id)
                    }
                    logger.logPolling(0)
                    return@launch
                }

                val lastIndex = notifications.indexOfFirst { it.id == lastSeenId }
                val newNotifications = if (lastIndex == -1) {
                    emptyList()
                } else {
                    notifications.take(lastIndex)
                }

                logger.logPolling(newNotifications.size)

                if (newNotifications.isNotEmpty()) {
                    val newestId = newNotifications.first().id
                    repository.setLastSeenNotificationId(newestId)
                    Log.d(TAG, "Polling found ${newNotifications.size} new notifications")

                    newNotifications.reversed().forEach { notification ->
                        logger.logNotification(notification.title, notification.topic)
                        notifier.showNotification(notification)
                    }

                    val state = repository.currentState()
                    if (state.mode == SopListenerMode.Dynamic) {
                        launch(Dispatchers.Main) {
                            enterActiveState()
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Service stopped, expected
            } catch (e: Exception) {
                Log.e(TAG, "Polling failed", e)
            }
        }
    }

    private fun startStreaming() {
        if (stopRequested) return
        if (!repository.currentState().enabled) {
            repository.updateStatus(SopListenerStatus.Disabled)
            stopSelf()
            return
        }

        activeJob?.cancel()
        eventSource?.cancel()
        activeJob = serviceScope.launch {
            try {
                val registration = repository.ensureRegistration().getOrElse {
                    if (it is kotlinx.coroutines.CancellationException) return@launch
                    repository.updateStatus(SopListenerStatus.Failed, it.message)
                    scheduleReconnect()
                    return@launch
                }

                repository.updateStatus(SopListenerStatus.Connecting)
                eventSource = streamClient.connect(registration.token, object : SopStreamClient.Listener {
                    override fun onOpen() {
                        reconnectAttempt = 0
                        repository.updateStatus(SopListenerStatus.Connecting)
                    }

                    override fun onReady(payload: String) {
                        Log.d(TAG, "stream ready: $payload")
                        repository.updateStatus(SopListenerStatus.Connected)
                        resetStreamTimeout()
                    }

                    override fun onNotification(notification: NotificationItem) {
                        logger.logNotification(notification.title, notification.topic)
                        notifier.showNotification(notification)
                        repository.setLastSeenNotificationId(notification.id)
                        resetStreamTimeout()
                    }

                    override fun onClosed() {
                        if (stopRequested) return
                        repository.updateStatus(SopListenerStatus.Reconnecting)
                        scheduleReconnect()
                    }

                    override fun onFailure(error: Throwable?, code: Int?) {
                        if (stopRequested) return
                        if (code == 401 || code == 403) {
                            repository.clearRegistration()
                        }
                        repository.updateStatus(SopListenerStatus.Reconnecting, error?.message)
                        scheduleReconnect()
                    }
                })
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Service stopped, expected
            }
        }
    }

    private fun resetStreamTimeout() {
        val state = repository.currentState()
        if (state.mode != SopListenerMode.Dynamic) return

        timeoutJob?.cancel()
        timeoutJob = serviceScope.launch {
            delay(state.dynamicConfig.streamTimeoutMs)
            Log.d(TAG, "Stream timeout, returning to idle state")
            enterIdleState()
        }
    }

    private fun scheduleReconnect() {
        if (stopRequested) return
        val state = repository.currentState()

        if (state.mode == SopListenerMode.Polling) {
            enterIdleState()
            return
        }

        activeJob?.cancel()
        activeJob = serviceScope.launch {
            if (stopRequested) return@launch
            reconnectAttempt += 1
            val backoffMillis = when (reconnectAttempt) {
                1 -> 1_000L
                2 -> 2_000L
                3 -> 5_000L
                4 -> 10_000L
                else -> 30_000L
            }
            delay(backoffMillis)
            startStreaming()
        }
    }

    companion object {
        private const val TAG = "CloudySkySopService"
        private const val ACTION_START = "dev.solsynth.cloudysky.sop.START"
        private const val ACTION_STOP = "dev.solsynth.cloudysky.sop.STOP"
        private const val ACTION_UPDATE_NOTIFICATION = "dev.solsynth.cloudysky.sop.UPDATE_NOTIFICATION"

        fun start(context: Context) {
            val intent = Intent(context, SopListenerService::class.java).setAction(ACTION_START)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure { error ->
                SopRepository(context.applicationContext).requestStart()
                Log.e(TAG, "failed to start foreground service", error)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SopListenerService::class.java).setAction(ACTION_STOP)
            runCatching {
                context.startService(intent)
            }.onFailure { error ->
                Log.e(TAG, "failed to stop service", error)
            }
        }

        fun updateNotification(context: Context) {
            val intent = Intent(context, SopListenerService::class.java).setAction(ACTION_UPDATE_NOTIFICATION)
            runCatching {
                context.startService(intent)
            }.onFailure { error ->
                Log.e(TAG, "failed to update notification", error)
            }
        }
    }

    private fun updateForegroundNotification() {
        val status = when {
            currentRunState == SopRunState.Active -> "Listening for notifications"
            else -> "Polling for notifications"
        }
        enterForeground(status)
    }

    private fun enterForeground(status: String): Boolean {
        val silent = repository.currentState().silentMode
        return try {
            startForeground(
                SopNotifier.SERVICE_NOTIFICATION_ID,
                notifier.buildServiceNotification(status, silent)
            )
            true
        } catch (error: ForegroundServiceStartNotAllowedException) {
            Log.e(TAG, "foreground start not allowed", error)
            false
        } catch (error: IllegalStateException) {
            Log.e(TAG, "foreground start failed", error)
            false
        }
    }
}
