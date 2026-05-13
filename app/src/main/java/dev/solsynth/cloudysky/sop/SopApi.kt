package dev.solsynth.cloudysky.sop

import android.util.Log
import dev.solsynth.cloudysky.notifications.NotificationItem
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SopApi {
    suspend fun register(accessToken: String): SopRegistration? {
        val url = URL("$baseUrl/ring/notifications/sop/subscription")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.doOutput = false
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")

            val status = connection.responseCode
            Log.d(TAG, "register: status=$status")
            if (status !in 200..299) return null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseRegistration(body)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun getNotifications(token: String, take: Int = 20, offset: Int = 0): List<NotificationItem>? {
        val url = URL("$baseUrl/ring/notifications/sop?take=$take&offset=$offset")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "SOP $token")
            connection.setRequestProperty("Accept", "application/json")

            val status = connection.responseCode
            Log.d(TAG, "getNotifications: status=$status offset=$offset take=$take")
            if (status !in 200..299) return null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseNotifications(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRegistration(body: String): SopRegistration {
        val json = JSONObject(body)
        val subscriptionJson = json.getJSONObject("subscription")
        return SopRegistration(
            token = json.getString("token"),
            subscription = SopSubscription(
                id = subscriptionJson.optString("id"),
                accountId = subscriptionJson.optString("account_id"),
                deviceId = subscriptionJson.optString("device_id"),
                deviceToken = subscriptionJson.optString("device_token"),
                provider = subscriptionJson.optInt("provider", 0),
                createdAt = subscriptionJson.optString("created_at"),
                updatedAt = subscriptionJson.optString("updated_at"),
            )
        )
    }

    private fun parseNotifications(body: String): List<NotificationItem> {
        val array = JSONArray(body)
        val items = ArrayList<NotificationItem>(array.length())

        for (index in 0 until array.length()) {
            val json = array.getJSONObject(index)
            val meta = json.optJSONObject("meta")
            items += NotificationItem(
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
            )
        }

        return items
    }

    companion object {
        const val baseUrl = "https://api.solian.app"
        const val streamUrl = "$baseUrl/ring/notifications/sop/stream"
        private const val TAG = "CloudySkySopApi"
    }
}
