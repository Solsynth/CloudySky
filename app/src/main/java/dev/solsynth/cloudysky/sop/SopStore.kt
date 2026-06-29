package dev.solsynth.cloudysky.sop

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SopStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        FILE_NAME,
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<SopState> = _state.asStateFlow()

    fun load(): SopState = _state.value

    fun save(state: SopState) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, state.enabled)
            .putString(KEY_MODE, state.mode.name)
            .putLong(KEY_POLLING_INTERVAL, state.dynamicConfig.pollingIntervalMs)
            .putLong(KEY_STREAM_TIMEOUT, state.dynamicConfig.streamTimeoutMs)
            .putBoolean(KEY_AUTO_START_ON_BOOT, state.autoStartOnBoot)
            .putBoolean(KEY_SILENT_MODE, state.silentMode)
            .putString(KEY_TOKEN, state.token)
            .putString(KEY_SUBSCRIPTION_ID, state.subscriptionId)
            .putString(KEY_DEVICE_ID, state.deviceId)
            .putString(KEY_LAST_REGISTERED_AT, state.lastRegisteredAt)
            .putString(KEY_LAST_SEEN_NOTIFICATION_ID, state.lastSeenNotificationId)
            .putBoolean(KEY_PENDING_START, state.pendingStart)
            .putString(KEY_ACCESS_TOKEN_HASH, state.accessTokenHash)
            .apply()
        _state.value = state
    }

    fun setEnabled(enabled: Boolean) {
        save(_state.value.copy(enabled = enabled))
    }

    fun clearRegistration() {
        save(
            _state.value.copy(
                token = null,
                subscriptionId = null,
                deviceId = null,
                lastRegisteredAt = null,
            )
        )
    }

    fun setPendingStart(pendingStart: Boolean) {
        save(_state.value.copy(pendingStart = pendingStart))
    }

    fun setAccessTokenHash(hash: String?) {
        save(_state.value.copy(accessTokenHash = hash))
    }

    fun clearIfSessionChanged(currentTokenHash: String?) {
        val stored = _state.value.accessTokenHash
        if (currentTokenHash != null && stored != null && stored != currentTokenHash) {
            clearRegistration()
            save(_state.value.copy(accessTokenHash = currentTokenHash))
        } else if (currentTokenHash != null && stored == null) {
            save(_state.value.copy(accessTokenHash = currentTokenHash))
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        _state.value = SopState()
    }

    private fun loadState(): SopState {
        val modeName = prefs.getString(KEY_MODE, SopListenerMode.Stream.name) ?: SopListenerMode.Stream.name
        val mode = try { SopListenerMode.valueOf(modeName) } catch (_: Exception) { SopListenerMode.Stream }
        return SopState(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            mode = mode,
            dynamicConfig = SopDynamicConfig(
                pollingIntervalMs = prefs.getLong(KEY_POLLING_INTERVAL, 5 * 60 * 1000),
                streamTimeoutMs = prefs.getLong(KEY_STREAM_TIMEOUT, 10 * 60 * 1000),
            ),
            autoStartOnBoot = prefs.getBoolean(KEY_AUTO_START_ON_BOOT, true),
            silentMode = prefs.getBoolean(KEY_SILENT_MODE, false),
            token = prefs.getString(KEY_TOKEN, null),
            subscriptionId = prefs.getString(KEY_SUBSCRIPTION_ID, null),
            deviceId = prefs.getString(KEY_DEVICE_ID, null),
            lastRegisteredAt = prefs.getString(KEY_LAST_REGISTERED_AT, null),
            lastSeenNotificationId = prefs.getString(KEY_LAST_SEEN_NOTIFICATION_ID, null),
            pendingStart = prefs.getBoolean(KEY_PENDING_START, false),
            accessTokenHash = prefs.getString(KEY_ACCESS_TOKEN_HASH, null),
        )
    }

    private companion object {
        const val FILE_NAME = "sop_state"
        const val KEY_ENABLED = "enabled"
        const val KEY_MODE = "mode"
        const val KEY_POLLING_INTERVAL = "polling_interval_ms"
        const val KEY_STREAM_TIMEOUT = "stream_timeout_ms"
        const val KEY_AUTO_START_ON_BOOT = "auto_start_on_boot"
        const val KEY_SILENT_MODE = "silent_mode"
        const val KEY_TOKEN = "token"
        const val KEY_SUBSCRIPTION_ID = "subscription_id"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_LAST_REGISTERED_AT = "last_registered_at"
        const val KEY_LAST_SEEN_NOTIFICATION_ID = "last_seen_notification_id"
        const val KEY_PENDING_START = "pending_start"
        const val KEY_ACCESS_TOKEN_HASH = "access_token_hash"
    }
}
