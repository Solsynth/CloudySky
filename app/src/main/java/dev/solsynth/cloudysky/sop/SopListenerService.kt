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
import kotlinx.coroutines.launch
import okhttp3.sse.EventSource

class SopListenerService : android.app.Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: SopRepository
    private lateinit var notifier: SopNotifier
    private val streamClient = SopStreamClient()

    private var eventSource: EventSource? = null
    private var streamJob: Job? = null
    private var reconnectAttempt = 0
    @Volatile private var stopRequested = false

    override fun onCreate() {
        super.onCreate()
        repository = SopRepository(applicationContext)
        notifier = SopNotifier(applicationContext)
        notifier.ensureChannels()
        if (!enterForeground("Connecting to SOP stream")) {
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
            else -> {
                stopRequested = false
                if (streamJob?.isActive == true || eventSource != null) {
                    return START_STICKY
                }
                startStreaming()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopRequested = true
        streamJob?.cancel()
        eventSource?.cancel()
        repository.updateStatus(SopListenerStatus.Idle)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startStreaming() {
        if (stopRequested) return
        if (!repository.currentState().enabled) {
            repository.updateStatus(SopListenerStatus.Disabled)
            stopSelf()
            return
        }

        streamJob?.cancel()
        eventSource?.cancel()
        streamJob = serviceScope.launch {
            val registration = repository.ensureRegistration().getOrElse {
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
                    enterForeground("Listening for notifications")
                }

                override fun onNotification(notification: NotificationItem) {
                    notifier.showNotification(notification)
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
        }
    }

    private fun scheduleReconnect() {
        if (stopRequested) return
        streamJob?.cancel()
        streamJob = serviceScope.launch {
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
    }

    private fun enterForeground(status: String): Boolean {
        return try {
            startForeground(
                SopNotifier.SERVICE_NOTIFICATION_ID,
                notifier.buildServiceNotification(status)
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
