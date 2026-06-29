package dev.solsynth.cloudysky.sop

data class SopSubscription(
    val id: String,
    val accountId: String,
    val deviceId: String,
    val deviceToken: String,
    val provider: Int,
    val createdAt: String,
    val updatedAt: String,
)

data class SopRegistration(
    val token: String,
    val subscription: SopSubscription,
)

enum class SopListenerMode {
    Stream,
    Polling,
    Dynamic,
}

data class SopDynamicConfig(
    val pollingIntervalMs: Long = 5 * 60 * 1000,
    val streamTimeoutMs: Long = 10 * 60 * 1000,
)

enum class SopRunState {
    Idle,
    Active,
}

data class SopState(
    val enabled: Boolean = true,
    val mode: SopListenerMode = SopListenerMode.Stream,
    val dynamicConfig: SopDynamicConfig = SopDynamicConfig(),
    val autoStartOnBoot: Boolean = true,
    val silentMode: Boolean = false,
    val token: String? = null,
    val subscriptionId: String? = null,
    val deviceId: String? = null,
    val lastRegisteredAt: String? = null,
    val lastSeenNotificationId: String? = null,
    val pendingStart: Boolean = false,
    val accessTokenHash: String? = null,
)

enum class SopListenerStatus {
    Disabled,
    Idle,
    Registering,
    Connecting,
    Connected,
    Reconnecting,
    Failed,
}

data class SopListenerSnapshot(
    val enabled: Boolean = true,
    val mode: SopListenerMode = SopListenerMode.Stream,
    val runState: SopRunState = SopRunState.Idle,
    val dynamicConfig: SopDynamicConfig = SopDynamicConfig(),
    val autoStartOnBoot: Boolean = true,
    val silentMode: Boolean = false,
    val status: SopListenerStatus = SopListenerStatus.Idle,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val androidDeviceId: String? = null,
    val deviceId: String? = null,
    val subscriptionId: String? = null,
    val error: String? = null,
)

enum class SopLogEntryType {
    Notification,
    ModeSwitch,
    Polling,
}

data class SopLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val type: SopLogEntryType = SopLogEntryType.Notification,
    val title: String = "",
    val topic: String = "",
    val runState: SopRunState? = null,
)
