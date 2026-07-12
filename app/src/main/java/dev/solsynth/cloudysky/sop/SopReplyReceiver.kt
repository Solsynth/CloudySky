package dev.solsynth.cloudysky.sop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.app.RemoteInput
import dev.solsynth.cloudysky.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SopReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_REPLY) return

        val roomId = intent.getStringExtra(EXTRA_ROOM_ID).orEmpty()
        val repliedMessageId = intent.getStringExtra(EXTRA_MESSAGE_ID)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val results = RemoteInput.getResultsFromIntent(intent)
        val replyText = results?.getCharSequence(KEY_REPLY_TEXT)?.toString().orEmpty()

        if (roomId.isBlank() || replyText.isBlank()) return

        val appContext = context.applicationContext
        val authRepository = AuthRepository(appContext)
        val api = SopChatApi(appContext, authRepository)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val result = api.sendReply(roomId = roomId, content = replyText, repliedMessageId = repliedMessageId)
            result.onSuccess {
                Log.d(TAG, "reply sent roomId=$roomId notificationId=$notificationId")
            }.onFailure { error ->
                Log.e(TAG, "reply failed roomId=$roomId", error)
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "dev.solsynth.cloudysky.sop.REPLY"
        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val KEY_REPLY_TEXT = "reply_text"
        private const val TAG = "CloudySkySopReply"
    }
}
