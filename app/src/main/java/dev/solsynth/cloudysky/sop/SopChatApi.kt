package dev.solsynth.cloudysky.sop

import android.util.Log
import dev.solsynth.cloudysky.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SopChatApi(
    private val authRepository: AuthRepository,
) {
    suspend fun sendReply(roomId: String, content: String, repliedMessageId: String? = null): Result<Unit> {
        return try {
            val accessToken = authRepository.accessToken()
                ?: return Result.failure(IllegalStateException("No access token"))

            val status = withContext(Dispatchers.IO) {
                val url = URL("$baseUrl/messager/chat/$roomId/messages")
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Authorization", "Bearer $accessToken")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Accept", "application/json")

                    val payload = JSONObject().apply {
                        put("content", content)
                        if (!repliedMessageId.isNullOrBlank()) {
                            put("replied_message_id", repliedMessageId)
                        }
                        put("nonce", System.currentTimeMillis().toString())
                    }

                    connection.outputStream.use { it.write(payload.toString().toByteArray()) }
                    val code = connection.responseCode
                    val body = if (code in 200..299) "" else connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    Log.d(TAG, "sendReply: status=$code roomId=$roomId")
                    code to body
                } finally {
                    connection.disconnect()
                }
            }

            if (status.first !in 200..299) {
                return Result.failure(IllegalStateException("Failed to send reply: ${status.first} ${status.second}"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "sendReply failed", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val baseUrl = "https://api.solian.app"
        private const val TAG = "CloudySkySopChatApi"
    }
}
