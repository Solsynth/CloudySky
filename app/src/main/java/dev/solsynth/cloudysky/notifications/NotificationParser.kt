package dev.solsynth.cloudysky.notifications

import org.json.JSONObject

internal fun parseNotificationItem(json: JSONObject): NotificationItem {
    val meta = json.optJSONObject("meta")
    return NotificationItem(
        id = json.optString("id"),
        topic = json.optString("topic"),
        title = json.optString("title"),
        subtitle = json.optString("subtitle"),
        content = json.optString("content"),
        actionUri = meta?.optString("action_uri").orEmpty(),
        priority = json.optInt("priority", 10),
        viewedAt = json.optString("viewed_at").takeIf { it.isNotBlank() },
        accountId = json.optString("account_id"),
        createdAt = json.optString("created_at"),
        updatedAt = json.optString("updated_at"),
        deletedAt = json.optString("deleted_at").takeIf { it.isNotBlank() },
        type = json.optString("type"),
        meta = meta,
    )
}
