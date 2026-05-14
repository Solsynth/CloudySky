package dev.solsynth.cloudysky.sop

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.core.app.RemoteInput
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import dev.solsynth.cloudysky.MainActivity
import dev.solsynth.cloudysky.R
import dev.solsynth.cloudysky.notifications.NotificationItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL

class SopNotifier(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val imageCacheDir = File(context.cacheDir, "sop-notification-images").apply { mkdirs() }

    fun ensureChannels() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL_ID,
                "SOP listener",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                EVENTS_CHANNEL_ID,
                "Notifications",
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    fun buildServiceNotification(status: String, silent: Boolean = false): Notification {
        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(status)
            .setOngoing(true)
            .setSilent(silent)
            .setPriority(if (silent) NotificationCompat.PRIORITY_MIN else NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showNotification(item: NotificationItem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, item.actionUriValue.takeIf { it.isNotBlank() }?.let(Uri::parse), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val groupKey = notificationGroupKey(item)

        val notification = when {
            item.topic.contains("messages", ignoreCase = true) || item.type == "messages.new" -> buildMessageNotification(item, pendingIntent, groupKey)
            item.imageIds.isNotEmpty() || item.imageId.isNotBlank() || item.pfpId.isNotBlank() -> buildMediaNotification(item, pendingIntent, groupKey)
            else -> buildDefaultNotification(item, pendingIntent, groupKey)
        }

        notificationManager.notify(item.id.hashCode(), notification)
        postGroupSummary(groupKey, item)
    }

    private fun buildDefaultNotification(item: NotificationItem, pendingIntent: PendingIntent, groupKey: String): Notification {
        val body = item.content.ifBlank { item.subtitle.ifBlank { item.title } }
        return NotificationCompat.Builder(context, EVENTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(item.title.ifBlank { "New notification" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setGroup(groupKey)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun buildMessageNotification(item: NotificationItem, pendingIntent: PendingIntent, groupKey: String): Notification {
        val avatarBitmap = runBlocking {
            withTimeoutOrNull(10_000) {
                loadBitmap(item.pfpId.ifBlank { item.imageIds.firstOrNull().orEmpty() })
            }
        }
        val leadBitmap = runBlocking {
            withTimeoutOrNull(10_000) {
                loadBitmap(item.imageIds.firstOrNull().orEmpty())
            }
        }
        val sender = Person.Builder()
            .setName(item.senderName.ifBlank { item.title.ifBlank { "Sender" } })
            .apply {
                if (avatarBitmap != null) {
                    setIcon(IconCompat.createWithBitmap(circleCrop(avatarBitmap)))
                }
            }
            .build()
        val style = NotificationCompat.MessagingStyle(sender)
            .setConversationTitle(item.roomName.ifBlank { item.title.ifBlank { "Chat" } })
            .addMessage(item.content.ifBlank { item.subtitle }, System.currentTimeMillis(), sender)

        val replyIntent = Intent(context, SopReplyReceiver::class.java).apply {
            action = SopReplyReceiver.ACTION_REPLY
            putExtra(SopReplyReceiver.EXTRA_ROOM_ID, item.roomId)
            putExtra(SopReplyReceiver.EXTRA_MESSAGE_ID, item.id)
            putExtra(SopReplyReceiver.EXTRA_NOTIFICATION_ID, item.id.hashCode())
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            item.id.hashCode(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val remoteInput = RemoteInput.Builder(SopReplyReceiver.KEY_REPLY_TEXT)
            .setLabel("Reply")
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        return NotificationCompat.Builder(context, EVENTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(item.roomName.ifBlank { item.title.ifBlank { "Message" } })
            .setContentText(item.content.ifBlank { item.subtitle })
            .setSubText(richMediaSummary(item))
            .setLargeIcon(avatarBitmap?.let(::circleCrop))
            .setStyle(if (leadBitmap != null) NotificationCompat.BigPictureStyle().bigPicture(leadBitmap).bigLargeIcon(avatarBitmap?.let(::circleCrop)) else style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .addAction(replyAction)
            .setGroup(groupKey)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun buildMediaNotification(item: NotificationItem, pendingIntent: PendingIntent, groupKey: String): Notification {
        val leadBitmap = runBlocking {
            withTimeoutOrNull(10_000) {
                loadMediaLeadBitmap(item)
            }
        }
        val avatarBitmap = runBlocking {
            withTimeoutOrNull(10_000) {
                loadBitmap(item.pfpId)
            }
        }
        val style = if (leadBitmap != null) {
            NotificationCompat.BigPictureStyle()
                .bigPicture(leadBitmap)
                .bigLargeIcon(avatarBitmap?.let(::circleCrop))
        } else {
            NotificationCompat.BigPictureStyle()
        }
        val content = item.content.ifBlank { item.subtitle.ifBlank { item.title } }
        return NotificationCompat.Builder(context, EVENTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(item.title.ifBlank { "New notification" })
            .setContentText(content)
            .setSubText(richMediaSummary(item))
            .setLargeIcon(avatarBitmap?.let(::circleCrop))
            .setStyle(style)
            .setGroup(groupKey)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private suspend fun loadMediaLeadBitmap(item: NotificationItem): Bitmap? {
        val identifiers = buildList {
            addAll(item.imageIds)
            if (item.imageId.isNotBlank()) add(item.imageId)
            if (item.pfpId.isNotBlank()) add(item.pfpId)
        }.distinct().take(4)

        if (identifiers.isEmpty()) return null

        val bitmaps = mutableListOf<Bitmap>()
        for (identifier in identifiers) {
            val bitmap = loadBitmap(identifier) ?: continue
            bitmaps += bitmap
        }

        return when {
            bitmaps.isEmpty() -> null
            bitmaps.size == 1 -> bitmaps.first()
            else -> createCollage(bitmaps)
        }
    }

    private suspend fun loadBitmap(identifier: String?): Bitmap? {
        if (identifier.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            loadBitmapFromCacheOrNetwork(identifier)
        }
    }

    private fun loadBitmapFromCacheOrNetwork(identifier: String): Bitmap? {
        val cacheFile = cacheFileFor(identifier)
        if (cacheFile.exists()) {
            decodeBitmap(cacheFile)?.let { return it }
            cacheFile.delete()
        }

        return runCatching {
            val url = URL(resolveAttachmentUrl(identifier))
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.inputStream.use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                }
                decodeBitmap(cacheFile)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun decodeBitmap(file: File): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(file)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val size = info.size
                    val maxDimension = 512
                    val width = size.width.coerceAtMost(maxDimension)
                    val height = size.height.coerceAtMost(maxDimension)
                    decoder.setTargetSize(width, height)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                null
            }
        }.getOrNull()
    }

    private fun cacheFileFor(identifier: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(identifier.toByteArray())
        val name = digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
        return File(imageCacheDir, "$name.img")
    }

    private fun richMediaLeadIdentifier(item: NotificationItem): String? {
        return item.imageIds.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: item.imageId.takeIf { it.isNotBlank() }
            ?: item.pfpId.takeIf { it.isNotBlank() }
    }

    private fun richMediaSummary(item: NotificationItem): String? {
        val count = buildList {
            if (item.pfpId.isNotBlank()) add("avatar")
            if (item.imageId.isNotBlank()) add("image")
            addAll(item.imageIds)
        }.size
        return if (count > 1) "$count media items" else null
    }

    private fun notificationGroupKey(item: NotificationItem): String {
        return when {
            item.roomId.isNotBlank() -> "room:${item.roomId}"
            item.userId.isNotBlank() -> "user:${item.userId}"
            item.topic.isNotBlank() -> "topic:${item.topic}"
            item.type.isNotBlank() -> "type:${item.type}"
            else -> "all"
        }
    }

    private fun postGroupSummary(groupKey: String, latestItem: NotificationItem) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val activeCount = notificationManager.activeNotifications.count {
            it.notification.group == groupKey && !it.notification.flags.and(Notification.FLAG_GROUP_SUMMARY).equals(Notification.FLAG_GROUP_SUMMARY)
        }

        val summaryId = groupKey.hashCode()
        if (activeCount <= 1) {
            notificationManager.cancel(summaryId)
            return
        }

        val summaryText = when {
            latestItem.roomName.isNotBlank() -> latestItem.roomName
            latestItem.title.isNotBlank() -> latestItem.title
            else -> context.getString(R.string.app_name)
        }

        val summary = NotificationCompat.Builder(context, EVENTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(summaryText)
            .setContentText("$activeCount new items")
            .setStyle(NotificationCompat.InboxStyle().setSummaryText(summaryText))
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(summaryId, summary)
    }

    private fun resolveAttachmentUrl(identifier: String): String {
        return if (identifier.startsWith("http://") || identifier.startsWith("https://")) {
            identifier
        } else {
            "${SopApi.baseUrl}/drive/files/$identifier"
        }
    }

    private fun circleCrop(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        val x = (source.width - size) / 2
        val y = (source.height - size) / 2
        val square = Bitmap.createBitmap(source, x, y, size, size)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(square, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return output
    }

    private fun createCollage(bitmaps: List<Bitmap>): Bitmap {
        val size = 1024
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val cells = listOf(
            Rect(0, 0, size / 2, size / 2),
            Rect(size / 2, 0, size, size / 2),
            Rect(0, size / 2, size / 2, size),
            Rect(size / 2, size / 2, size, size)
        )

        bitmaps.take(4).forEachIndexed { index, bitmap ->
            val cell = cells[index]
            val sourceRect = centerCropRect(bitmap.width, bitmap.height)
            canvas.drawBitmap(bitmap, sourceRect, cell, paint)
        }

        return output
    }

    private fun centerCropRect(width: Int, height: Int): Rect {
        val size = minOf(width, height)
        val left = (width - size) / 2
        val top = (height - size) / 2
        return Rect(left, top, left + size, top + size)
    }

    companion object {
        const val SERVICE_CHANNEL_ID = "sop_listener_service"
        const val EVENTS_CHANNEL_ID = "sop_events_high"
        const val SERVICE_NOTIFICATION_ID = 1001
    }
}
