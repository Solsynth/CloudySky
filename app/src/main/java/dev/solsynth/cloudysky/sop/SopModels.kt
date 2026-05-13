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

data class SopState(
    val enabled: Boolean = true,
    val token: String? = null,
    val subscriptionId: String? = null,
    val deviceId: String? = null,
    val lastRegisteredAt: String? = null,
    val pendingStart: Boolean = false,
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
    val status: SopListenerStatus = SopListenerStatus.Idle,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val deviceId: String? = null,
    val subscriptionId: String? = null,
    val error: String? = null,
)
