package fr.tpsarena.companion

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import fr.tpsarena.companion.data.ApiClient
import fr.tpsarena.companion.data.Cache
import fr.tpsarena.companion.data.Settings
import fr.tpsarena.companion.notify.ChatService
import fr.tpsarena.companion.notify.Notifier
import fr.tpsarena.companion.realtime.Realtime
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** The app's singletons: they outlive the activity, so the chat stays connected in the background. */
class CompanionApp : Application() {
    val scope = MainScope()

    lateinit var settings: Settings
        private set
    lateinit var api: ApiClient
        private set
    lateinit var cache: Cache
        private set
    lateinit var realtime: Realtime
        private set
    lateinit var session: SessionManager
        private set
    lateinit var notifier: Notifier
        private set

    /** True while an activity of the app is visible. */
    @Volatile var inForeground = false
        private set

    override fun onCreate() {
        super.onCreate()
        val http = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        settings = Settings(this)
        api = ApiClient(http, settings)
        cache = Cache(File(filesDir, "cache"))
        notifier = Notifier(this).apply { createChannels() }
        realtime = Realtime(scope, api, { url, listener -> http.newWebSocket(okhttp3.Request.Builder().url(url).build(), listener) })
        realtime.onAlert = { alert -> if (!inForeground) notifier.post(alert) }
        session = SessionManager(scope, api, settings, cache, realtime)
        session.onChange = { updateChatService() }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                inForeground = true
                realtime.setVisible(true)
                realtime.shownChat?.let(notifier::cancelChat)
                val state = session.state.value
                if (state is AuthState.SignedIn && !state.online) session.resume()
                realtime.wake()
                updateChatService()
            }

            override fun onStop(owner: LifecycleOwner) {
                inForeground = false
                realtime.setVisible(false)
                if (!backgroundChatWanted()) realtime.pause()
            }
        })

        // Network back (Wi-Fi reconnected): retry what failed, like the PWA's `online` event.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch {
                        val state = session.state.value
                        if (state is AuthState.SignedIn && !state.online) session.resume()
                        if (inForeground || backgroundChatWanted()) realtime.wake()
                    }
                }
            })
        }

        session.resume()
    }

    private fun backgroundChatWanted(): Boolean {
        val state = session.state.value
        return settings.backgroundChat && notifier.canNotify() && state is AuthState.SignedIn && state.online
    }

    /** Starts or stops the background chat service to match the session and the setting. */
    fun updateChatService() {
        if (backgroundChatWanted()) {
            // Only from the foreground: Android 12+ refuses a background start.
            if (inForeground) ChatService.start(this)
        } else {
            ChatService.stop(this)
            if (session.state.value !is AuthState.SignedIn) notifier.cancelAll()
        }
    }
}
