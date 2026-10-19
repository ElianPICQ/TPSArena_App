package fr.tpsarena.companion.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fr.tpsarena.companion.MainActivity
import fr.tpsarena.companion.R
import fr.tpsarena.companion.realtime.Alert

/**
 * System notifications for the socket's alerts while the app is in the background (in the
 * foreground they are in-app toasts). There is no push (no FCM on the LAN): they only arrive
 * while the socket is open, which ChatService keeps up in the background.
 */
class Notifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** Unread count per friend while their notification is shown. */
    private val unread = mutableMapOf<String, MutableList<String>>()

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_MESSAGES, context.getString(R.string.channel_messages), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_messages_description) },
                NotificationChannel(CHANNEL_SOCIAL, context.getString(R.string.channel_social), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_social_description) },
                NotificationChannel(CHANNEL_SERVICE, context.getString(R.string.channel_service), NotificationManager.IMPORTANCE_MIN)
                    .apply {
                        description = context.getString(R.string.channel_service_description)
                        setShowBadge(false)
                    },
            ),
        )
    }

    fun canNotify(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission") // checked by canNotify()
    fun post(alert: Alert) {
        if (!canNotify()) return
        val builder = NotificationCompat.Builder(context, CHANNEL_SOCIAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF2EE3EB.toInt())
            .setAutoCancel(true)
            .setContentIntent(openIntent(alert.route))
        val id: Int
        when (alert) {
            is Alert.Message -> {
                val friend = alert.message.fromUserId
                val lines = unread.getOrPut(friend) { mutableListOf() }.apply { add(alert.message.body) }
                id = chatId(friend)
                builder.setChannelId(CHANNEL_MESSAGES)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setContentTitle(alert.senderName ?: "Nouveau message")
                    .setContentText(alert.message.body)
                    .setNumber(lines.size)
                    .setStyle(NotificationCompat.InboxStyle().also { style -> lines.takeLast(6).forEach(style::addLine) })
            }
            is Alert.FriendRequest, is Alert.FriendAdded -> {
                id = ID_FRIENDS
                builder.setCategory(NotificationCompat.CATEGORY_SOCIAL).setContentTitle(alert.text)
            }
            is Alert.Progress -> {
                id = ID_PROGRESS
                builder.setCategory(NotificationCompat.CATEGORY_EVENT).setContentTitle(alert.text)
            }
        }
        manager.notify(id, builder.build())
    }

    /** The conversation is on screen: its notification is read. */
    fun cancelChat(friendId: String) {
        unread.remove(friendId)
        manager.cancel(chatId(friendId))
    }

    fun cancelAll() {
        unread.clear()
        manager.cancelAll()
    }

    fun serviceNotification() = NotificationCompat.Builder(context, CHANNEL_SERVICE)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(0xFF2EE3EB.toInt())
        .setContentTitle(context.getString(R.string.service_title))
        .setContentText(context.getString(R.string.service_text))
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setContentIntent(openIntent(null))
        .build()

    private fun openIntent(route: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (route != null) putExtra(MainActivity.EXTRA_ROUTE, route) }
        // One PendingIntent per route, or they would share the first one's extras.
        return PendingIntent.getActivity(
            context,
            route?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun chatId(friendId: String) = 1000 + (friendId.hashCode() and 0x7fff)

    companion object {
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_SOCIAL = "social"
        const val CHANNEL_SERVICE = "service"
        const val ID_SERVICE = 1
        private const val ID_FRIENDS = 2
        private const val ID_PROGRESS = 3
    }
}
