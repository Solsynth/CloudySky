package dev.solsynth.cloudysky.sop

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
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

    private val _listenerState = MutableStateFlow(SopListenerSnapshot())
    val listenerState: StateFlow<SopListenerSnapshot> = _listenerState.asStateFlow()

    init {
        _listenerState.value = snapshot(status = SopListenerStatus.Idle)
    }

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

    fun setMode(mode: SopListenerMode) {
        store.save(store.load().copy(mode = mode))
        _listenerState.value = snapshot(mode = mode)
    }

    fun setDynamicConfig(config: SopDynamicConfig) {
        store.save(store.load().copy(dynamicConfig = config))
        _listenerState.value = snapshot(dynamicConfig = config)
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        store.save(store.load().copy(autoStartOnBoot = enabled))
        _listenerState.value = snapshot(autoStartOnBoot = enabled)
    }

    fun setSilentMode(enabled: Boolean) {
        store.save(store.load().copy(silentMode = enabled))
        _listenerState.value = snapshot(silentMode = enabled)
    }

    fun setLastSeenNotificationId(id: String?) {
        store.save(store.load().copy(lastSeenNotificationId = id))
    }

    fun clearAll() {
        store.clearAll()
        _listenerState.value = snapshot(status = SopListenerStatus.Idle, error = null)
    }

    suspend fun deleteSubscriptionAndClear(): Boolean {
        val state = store.load()
        val subscriptionId = state.subscriptionId
        val accessToken = authRepository.accessToken()

        val deleted = if (subscriptionId != null && accessToken != null) {
            withContext(Dispatchers.IO) { api.deleteSubscription(accessToken, subscriptionId) }
        } else {
            true
        }

        clearAll()
        return deleted
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

    fun updateRunState(runState: SopRunState) {
        _listenerState.value = snapshot(runState = runState)
    }

    private fun snapshot(
        status: SopListenerStatus? = null,
        runState: SopRunState? = null,
        mode: SopListenerMode? = null,
        dynamicConfig: SopDynamicConfig? = null,
        autoStartOnBoot: Boolean? = null,
        silentMode: Boolean? = null,
        error: String? = null,
    ): SopListenerSnapshot {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val notificationsEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        val androidDeviceId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        val current = _listenerState.value
        val state = store.load()
        return SopListenerSnapshot(
            enabled = state.enabled,
            mode = mode ?: state.mode,
            runState = runState ?: current.runState,
            dynamicConfig = dynamicConfig ?: state.dynamicConfig,
            autoStartOnBoot = autoStartOnBoot ?: state.autoStartOnBoot,
            silentMode = silentMode ?: state.silentMode,
            status = status ?: current.status,
            isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(appContext.packageName),
            hasNotificationPermission = notificationsEnabled,
            androidDeviceId = androidDeviceId,
            deviceId = state.deviceId,
            subscriptionId = state.subscriptionId,
            error = error ?: current.error,
        )
    }
}
