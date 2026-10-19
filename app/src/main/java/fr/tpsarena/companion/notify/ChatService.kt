package fr.tpsarena.companion.notify

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import fr.tpsarena.companion.CompanionApp

/**
 * Foreground service that keeps the process (and so the chat WebSocket held by Realtime) alive
 * while the app is in the background, so friends' messages still raise notifications. It holds
 * nothing itself. Type remoteMessaging: it relays text messages.
 *
 * Started only from the foreground (Android 12+ forbids starting it from the background) and
 * not sticky: if the system kills it, notifications stop until the app is opened again.
 */
class ChatService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notifier = (application as CompanionApp).notifier
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
        try {
            ServiceCompat.startForeground(this, Notifier.ID_SERVICE, notifier.serviceNotification(), type)
        } catch (e: Exception) {
            Log.w(TAG, "cannot go to the foreground", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "ChatService"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ChatService::class.java))
            } catch (e: Exception) {
                // Background start refused (ForegroundServiceStartNotAllowedException): next time.
                Log.w(TAG, "cannot start", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChatService::class.java))
        }
    }
}
