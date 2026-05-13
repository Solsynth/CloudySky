package dev.solsynth.cloudysky.notifications

import android.util.Log
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class NotificationApi {
    suspend fun getNotifications(accessToken: String, take: Int = 20, offset: Int = 0): List<NotificationItem>? {
        val url = URL("$baseUrl/ring/notifications?take=$take&offset=$offset")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")

            val status = connection.responseCode
            Log.d(TAG, "getNotifications: status=$status offset=$offset take=$take")
            if (status !in 200..299) {
                return null
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseNotifications(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseNotifications(body: String): List<NotificationItem> {
        val array = JSONArray(body)
        val items = ArrayList<NotificationItem>(array.length())

        for (index in 0 until array.length()) {
            items += parseNotificationItem(array.getJSONObject(index))
        }

        return items
    }

    private companion object {
        const val baseUrl = "https://api.solian.app"
        const val TAG = "CloudySkyNotifApi"
    }
}
