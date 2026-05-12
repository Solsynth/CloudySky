package dev.solsynth.cloudysky.notifications

data class NotificationItem(
    val id: String,
    val topic: String,
    val title: String,
    val subtitle: String = "",
    val content: String,
    val actionUri: String = "",
    val priority: Int = 10,
    val viewedAt: String? = null,
    val accountId: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
) {
    val isViewed: Boolean get() = viewedAt != null
}

data class NotificationUiState(
    val notifications: List<NotificationItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
)
