package dev.solsynth.cloudysky.sop

import android.util.Log
import okhttp3.internal.http2.StreamResetException
import dev.solsynth.cloudysky.notifications.NotificationItem
import dev.solsynth.cloudysky.notifications.parseNotificationItem
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SopStreamClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) {
    private val factory = EventSources.createFactory(okHttpClient)

    fun connect(token: String, listener: Listener): EventSource {
        val request = Request.Builder()
            .url("${SopApi.streamUrl}?token=$token")
            .header("Accept", "text/event-stream")
            .build()

        return factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                Log.d(TAG, "stream opened code=${response.code}")
                listener.onOpen()
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                when (type) {
                    "ready" -> listener.onReady(data)
                    "notification" -> parseNotification(data)?.let(listener::onNotification)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d(TAG, "stream closed")
                listener.onClosed()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                if (t is StreamResetException && t.message?.contains("CANCEL") == true) {
                    Log.d(TAG, "stream cancelled normally")
                    listener.onClosed()
                    return
                }
                Log.e(TAG, "stream failed code=${response?.code}", t)
                listener.onFailure(t, response?.code)
            }
        })
    }

    private fun parseNotification(body: String): NotificationItem? {
        return runCatching {
            parseNotificationItem(JSONObject(body))
        }.getOrNull()
    }

    interface Listener {
        fun onOpen()
        fun onReady(payload: String)
        fun onNotification(notification: NotificationItem)
        fun onClosed()
        fun onFailure(error: Throwable?, code: Int?)
    }

    private companion object {
        const val TAG = "CloudySkySopStream"
    }
}
