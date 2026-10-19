package fr.tpsarena.companion.data

import android.content.Context
import kotlinx.serialization.json.Json

/** What the API client needs to persist: the server and the session's refresh token. */
interface SessionStore {
    /** Site origin as typed on the login screen, normalized (`https://192.168.1.20:8443`). */
    var serverUrl: String?

    /** Value of the web-bff's `tpsarena_refresh` cookie (see ApiClient). */
    var refreshToken: String?
}

/** App preferences (SharedPreferences: small, read synchronously from OkHttp threads). */
class Settings(context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences("tps-arena", Context.MODE_PRIVATE)

    override var serverUrl: String?
        get() = prefs.getString(SERVER_URL, null)
        set(value) = prefs.edit().putString(SERVER_URL, value).apply()

    // commit(), not apply(): a rotated refresh token must be on disk before the process can die,
    // or the next start would send the old one and Auth would revoke every session.
    override var refreshToken: String?
        get() = prefs.getString(REFRESH_TOKEN, null)
        set(value) {
            prefs.edit().putString(REFRESH_TOKEN, value).commit()
        }

    /** The last player logged in, to open the app offline on their saved data. */
    var lastUser: User?
        get() = prefs.getString(LAST_USER, null)?.let { runCatching { json.decodeFromString<User>(it) }.getOrNull() }
        set(value) = prefs.edit().putString(LAST_USER, value?.let { json.encodeToString(it) }).apply()

    /** Keep the chat connected in the background (foreground service) for notifications. */
    var backgroundChat: Boolean
        get() = prefs.getBoolean(BACKGROUND_CHAT, true)
        set(value) = prefs.edit().putBoolean(BACKGROUND_CHAT, value).apply()

    /** Whether the notification permission was already asked once (Android 13+). */
    var askedNotifications: Boolean
        get() = prefs.getBoolean(ASKED_NOTIFICATIONS, false)
        set(value) = prefs.edit().putBoolean(ASKED_NOTIFICATIONS, value).apply()

    private companion object {
        const val SERVER_URL = "server_url"
        const val REFRESH_TOKEN = "refresh_token"
        const val LAST_USER = "last_user"
        const val BACKGROUND_CHAT = "background_chat"
        const val ASKED_NOTIFICATIONS = "asked_notifications"
        val json = Json { ignoreUnknownKeys = true }
    }
}
