package dev.solsynth.cloudysky.sop

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.solsynth.cloudysky.MainActivity
import dev.solsynth.cloudysky.R
import dev.solsynth.cloudysky.notifications.NotificationItem

class SopNotifier(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    fun buildServiceNotification(status: String): Notification {
        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(status)
            .setOngoing(true)
            .build()
    }

    fun showNotification(item: NotificationItem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, item.actionUri.takeIf { it.isNotBlank() }?.let(Uri::parse), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, EVENTS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(item.title.ifBlank { "New notification" })
            .setContentText(item.content.ifBlank { item.subtitle })
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.content.ifBlank { item.subtitle }))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(item.id.hashCode(), notification)
    }

    companion object {
        const val SERVICE_CHANNEL_ID = "sop_listener_service"
        const val EVENTS_CHANNEL_ID = "sop_events"
        const val SERVICE_NOTIFICATION_ID = 1001
    }
}
