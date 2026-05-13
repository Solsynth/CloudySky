package dev.solsynth.cloudysky.sop

import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import dev.solsynth.cloudysky.auth.AuthRepository
import dev.solsynth.cloudysky.notifications.NotificationItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SopRepository(context: Context) {
    private val appContext = context.applicationContext
    private val authRepository = AuthRepository(appContext)
    private val store = SopStore(appContext)
    private val api = SopApi()

    private val _listenerState = MutableStateFlow(snapshot(status = SopListenerStatus.Idle))
    val listenerState: StateFlow<SopListenerSnapshot> = _listenerState.asStateFlow()

    fun currentState(): SopState = store.load()

    fun setEnabled(enabled: Boolean) {
        store.setEnabled(enabled)
        if (!enabled) {
            store.setPendingStart(false)
        }
        _listenerState.value = snapshot(
            status = if (enabled) SopListenerStatus.Idle else SopListenerStatus.Disabled,
            error = null,
        )
    }

    fun clearAll() {
        store.clearAll()
        _listenerState.value = snapshot(status = SopListenerStatus.Idle, error = null)
    }

    fun clearRegistration() {
        store.clearRegistration()
    }

    fun requestStart() {
        store.setPendingStart(true)
    }

    fun consumePendingStart(): Boolean {
        val pending = store.load().pendingStart
        if (pending) {
            store.setPendingStart(false)
        }
        return pending
    }

    fun hasPendingStart(): Boolean = store.load().pendingStart

    suspend fun ensureRegistration(): Result<SopRegistration> {
        val current = store.load()
        current.token?.takeIf { it.isNotBlank() }?.let { token ->
            val subscriptionId = current.subscriptionId.orEmpty()
            val deviceId = current.deviceId.orEmpty()
            val lastRegisteredAt = current.lastRegisteredAt.orEmpty()
            return Result.success(
                SopRegistration(
                    token = token,
                    subscription = SopSubscription(
                        id = subscriptionId,
                        accountId = "",
                        deviceId = deviceId,
                        deviceToken = token,
                        provider = 2,
                        createdAt = lastRegisteredAt,
                        updatedAt = lastRegisteredAt,
                    )
                )
            )
        }

        _listenerState.value = snapshot(status = SopListenerStatus.Registering, error = null)
        return try {
            val accessToken = authRepository.accessToken()
                ?: return Result.failure(IllegalStateException("No access token"))
            val registration = withContext(Dispatchers.IO) { api.register(accessToken) }
                ?: return Result.failure(IllegalStateException("Failed to register SOP subscription"))

            store.save(
                current.copy(
                    token = registration.token,
                    subscriptionId = registration.subscription.id,
                    deviceId = registration.subscription.deviceId,
                    lastRegisteredAt = registration.subscription.updatedAt,
                )
            )
            _listenerState.value = snapshot(status = SopListenerStatus.Idle, error = null)
            Result.success(registration)
        } catch (e: Exception) {
            _listenerState.value = snapshot(status = SopListenerStatus.Failed, error = e.message)
            Result.failure(e)
        }
    }

    suspend fun getSopNotifications(offset: Int = 0, take: Int = 20): Result<List<NotificationItem>> {
        val registration = ensureRegistration().getOrElse { return Result.failure(it) }
        return try {
            val items = withContext(Dispatchers.IO) {
                api.getNotifications(registration.token, take = take, offset = offset)
            } ?: return Result.failure(IllegalStateException("Failed to load SOP notifications"))
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun updateStatus(status: SopListenerStatus, error: String? = null) {
        _listenerState.value = snapshot(status = status, error = error)
    }

    private fun snapshot(status: SopListenerStatus, error: String? = null): SopListenerSnapshot {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val notificationsEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        return SopListenerSnapshot(
            enabled = store.load().enabled,
            status = status,
            isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(appContext.packageName),
            hasNotificationPermission = notificationsEnabled,
            deviceId = store.load().deviceId,
            subscriptionId = store.load().subscriptionId,
            error = error,
        )
    }
}
