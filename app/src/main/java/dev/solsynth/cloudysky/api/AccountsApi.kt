package dev.solsynth.cloudysky.api

import android.util.Log
import dev.solsynth.cloudysky.auth.CurrentAccount
import java.net.HttpURLConnection
import java.net.URL

class AccountsApi {
    suspend fun getCurrentAccount(accessToken: String): CurrentAccount? {
        val connection = URL("$baseUrl/passport/accounts/me").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")

            val status = connection.responseCode
            Log.d(TAG, "getCurrentAccount: status=$status")
            if (status !in 200..299) {
                return null
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d(TAG, "getCurrentAccount: bodyLength=${body.length}")
            parseCurrentAccount(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCurrentAccount(body: String): CurrentAccount {
        val json = org.json.JSONObject(body)
        val profile = json.optJSONObject("profile")

        return CurrentAccount(
            id = json.optString("id"),
            name = json.optString("name"),
            nick = json.optString("nick"),
            language = json.optString("language"),
            bio = profile?.optString("bio").orEmpty(),
        )
    }

    private companion object {
        const val baseUrl = "https://api.solian.app"
        const val TAG = "CloudySkyApi"
    }
}
