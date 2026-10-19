package fr.tpsarena.companion

import fr.tpsarena.companion.data.ApiClient
import fr.tpsarena.companion.data.Cache
import fr.tpsarena.companion.data.Settings
import fr.tpsarena.companion.data.User
import fr.tpsarena.companion.realtime.Realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface AuthState {
    data object Checking : AuthState

    data class Anonymous(val message: String? = null) : AuthState

    /** [online] false: server unreachable at start, the app shows the last player's saved data. */
    data class SignedIn(val user: User, val online: Boolean) : AuthState
}

/** Who is logged in, and whether the server answers (the PWA's App.tsx). */
class SessionManager(
    private val scope: CoroutineScope,
    private val api: ApiClient,
    private val settings: Settings,
    private val cache: Cache,
    private val realtime: Realtime,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Checking)
    val state: StateFlow<AuthState> = _state

    /** Called after every change (the app starts or stops the chat service). */
    var onChange: (AuthState) -> Unit = {}

    private var resuming: Job? = null

    init {
        api.onSessionExpired = {
            scope.launch { signOut(AuthState.Anonymous("Ta session a expiré. Reconnecte-toi.")) }
        }
    }

    /** At start, and to retry when offline: extends the session with the refresh token. */
    fun resume() {
        if (resuming?.isActive == true) return
        resuming = scope.launch {
            val next = try {
                val user = api.refreshSession()
                if (user != null) {
                    settings.lastUser = user
                    AuthState.SignedIn(user, online = true)
                } else {
                    AuthState.Anonymous()
                }
            } catch (e: Exception) {
                val last = settings.lastUser
                if (last != null && api.hasRefreshToken) AuthState.SignedIn(last, online = false) else AuthState.Anonymous(e.message)
            }
            set(next)
        }
    }

    /** Throws ApiException / NetworkException with a French message. */
    suspend fun login(server: String, identifier: String, password: String) {
        val user = api.login(server, identifier, password)
        val last = settings.lastUser
        if (last != null && last.userId != user.userId) cache.clear()
        settings.lastUser = user
        set(AuthState.SignedIn(user, online = true))
    }

    fun logout() {
        scope.launch {
            realtime.stop()
            api.logout()
            cache.clear()
            settings.lastUser = null
            set(AuthState.Anonymous())
        }
    }

    private fun signOut(next: AuthState.Anonymous) {
        if (_state.value is AuthState.Anonymous) return
        realtime.stop()
        set(next)
    }

    private fun set(next: AuthState) {
        _state.value = next
        if (next is AuthState.SignedIn && next.online) realtime.start(next.user) else realtime.stop()
        onChange(next)
    }
}
