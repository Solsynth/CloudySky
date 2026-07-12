package dev.solsynth.cloudysky.sop

import android.util.Log
import dev.solsynth.cloudysky.BuildConfig
import dev.solsynth.cloudysky.notifications.NotificationItem
import dev.solsynth.cloudysky.notifications.parseNotificationItem
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
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            val requestBody = JSONObject()
                .put("device_name", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                .put("app_id", BuildConfig.APPLICATION_ID)
                .toString()
            connection.outputStream.bufferedWriter().use { it.write(requestBody) }

            val status = connection.responseCode
            if (status !in 200..299) {
                val errorBody = connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                val responseHeaders = connection.headerFields
                    .filterKeys { name -> name != null && name !in SENSITIVE_RESPONSE_HEADERS }
                    .entries
                    .joinToString(", ") { (name, values) -> "$name=${values?.joinToString()}" }
                Log.d(
                    TAG,
                    "register failed: status=$status message=${connection.responseMessage} " +
                        "headers=[$responseHeaders] body=$errorBody"
                )
                return null
            }

            Log.d(TAG, "register: status=$status")

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseRegistration(body)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun deleteSubscription(accessToken: String, subscriptionId: String): Boolean {
        val url = URL("$baseUrl/ring/notifications/subscription/$subscriptionId")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")

            val status = connection.responseCode
            Log.d(TAG, "deleteSubscription: status=$status subscriptionId=$subscriptionId")
            status in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "deleteSubscription failed", e)
            false
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
            items += parseNotificationItem(array.getJSONObject(index))
        }

        return items
    }

    companion object {
        const val baseUrl = "https://api.solian.app"
        const val streamUrl = "$baseUrl/ring/notifications/sop/stream"
        private const val TAG = "CloudySkySopApi"
        private val SENSITIVE_RESPONSE_HEADERS = setOf("Set-Cookie", "Cookie", "Authorization")
    }
}
