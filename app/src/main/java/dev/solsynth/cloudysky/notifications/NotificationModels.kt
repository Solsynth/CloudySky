package dev.solsynth.cloudysky.notifications

import org.json.JSONObject

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
    val type: String = "",
    val meta: JSONObject? = null,
) {
    val isViewed: Boolean get() = viewedAt != null

    val senderName: String get() = meta?.optString("sender_name").orEmpty()
    val roomName: String get() = meta?.optString("room_name").orEmpty()
    val roomId: String get() = meta?.optString("room_id").orEmpty()
    val userId: String get() = meta?.optString("user_id").orEmpty()
    val actionUriValue: String get() = meta?.optString("action_uri").orEmpty()
    val pfpId: String get() = meta?.optString("pfp").orEmpty()
    val imageId: String get() = meta?.optString("image").orEmpty()
    val imageIds: List<String> get() = meta?.optJSONArray("images")?.let { array ->
        buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isNotBlank()) add(value)
            }
        }
    }.orEmpty()
}

data class NotificationUiState(
    val notifications: List<NotificationItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
)
