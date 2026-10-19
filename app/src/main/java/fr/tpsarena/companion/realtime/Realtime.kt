package fr.tpsarena.companion.realtime

import fr.tpsarena.companion.data.FriendMessage
import fr.tpsarena.companion.data.Labels
import fr.tpsarena.companion.data.NetworkException
import fr.tpsarena.companion.data.Presence
import fr.tpsarena.companion.data.SessionExpiredException
import fr.tpsarena.companion.data.User
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

// Real-time WebSocket for friends' presence, the chat and notifications, through the site's
// nginx (`/ws`). Same protocol as the game (backend/services/realtime/PROTOCOL.md), with
// `client: "mobile"`: friends see the player « Sur mobile ». Port of the PWA's lib/realtime.ts.

/** What the socket needs from the session (ApiClient). */
interface TokenSource {
    /** A valid access token. Throws SessionExpiredException or NetworkException. */
    suspend fun accessToken(): String
    fun accessExpiresAt(): Long?
    fun dropAccessToken()
    fun expireSession()
    fun webSocketUrl(): String?
}

enum class ConnectionStatus { IDLE, CONNECTING, CONNECTED, DISCONNECTED }

/** In-app banner; [route] is where tapping it goes. */
data class Toast(val id: Long, val text: String, val route: String? = null)

/** Something worth a system notification when the app is in the background. */
sealed interface Alert {
    val text: String
    val route: String

    data class Message(val message: FriendMessage, val senderName: String?, override val text: String, override val route: String) : Alert
    data class FriendRequest(override val text: String, override val route: String) : Alert
    data class FriendAdded(override val text: String, override val route: String) : Alert
    data class Progress(override val text: String, override val route: String) : Alert
}

data class RealtimeState(
    val status: ConnectionStatus = ConnectionStatus.IDLE,
    /** Friends' presence, known only once connected. */
    val presence: Map<String, Presence> = emptyMap(),
    /** Messages received or sent since the app started, per friend (oldest to newest). */
    val messages: Map<String, List<FriendMessage>> = emptyMap(),
    val unread: Map<String, Int> = emptyMap(),
    /** Goes up on every friendship or request change: the friend list is reloaded. */
    val friendsVersion: Int = 0,
    /** Pending incoming friend requests (badge of the Friends tab). */
    val incoming: Int = 0,
    val toasts: List<Toast> = emptyList(),
)

/** Routes of the app (Navigation Compose), also used by toasts and notifications. */
object Routes {
    const val PROFILE = "profile"
    const val ACHIEVEMENTS = "achievements"
    const val HISTORY = "history"
    const val LEADERBOARDS = "leaderboards"
    const val FRIENDS = "friends"
    fun match(id: String) = "match/$id"
    fun chat(id: String) = "chat/$id"
}

class Realtime(
    private val scope: CoroutineScope,
    private val tokens: TokenSource,
    private val openSocket: (url: String, listener: WebSocketListener) -> WebSocket,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(RealtimeState())
    val state: StateFlow<RealtimeState> = _state

    /** Called for every alert (the Notifier posts it when the app is in the background). */
    @Volatile var onAlert: (Alert) -> Unit = {}

    private var ws: WebSocket? = null
    private var user: User? = null
    /** Conversation on screen, and whether the app is visible: only then are its messages read. */
    var shownChat: String? = null
        private set
    private var visible = true
    private val activeChat: String? get() = if (visible) shownChat else null
    private var attempt = 0
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reauthJob: Job? = null
    private val pending = ArrayDeque<PendingSend>()
    private var nextToast = 1L
    private var names: Map<String, String> = emptyMap()

    private class PendingSend(val clientId: String, val result: CompletableDeferred<FriendMessage>)

    val isRunning: Boolean get() = user != null

    // Every method below runs on [scope], whose dispatcher must be single-threaded (Main):
    // socket callbacks hop onto it with [post].

    // --- Lifecycle ---

    fun start(user: User) {
        if (this.user?.userId == user.userId && ws != null) return
        stop()
        this.user = user
        connect()
    }

    /** Logout: closes the socket and forgets everything. */
    fun stop() {
        user = null
        names = emptyMap()
        clearTimers()
        reconnectJob?.cancel()
        val socket = ws
        ws = null
        socket?.close(1000, null)
        failPending("Déconnecté.")
        _state.value = RealtimeState()
    }

    /**
     * App in the background without the chat service: closes the socket but keeps the player,
     * so [wake] reconnects. Friends then see the player offline, as with the app closed.
     */
    fun pause() {
        if (user == null) return
        reconnectJob?.cancel()
        clearTimers()
        val socket = ws
        ws = null
        socket?.close(1000, null)
        failPending("Connexion au chat perdue.")
        _state.update { it.copy(status = ConnectionStatus.DISCONNECTED) }
    }

    /** Back in the foreground or network back: reconnect right away if needed. */
    fun wake() {
        if (user == null || ws != null) return
        reconnectJob?.cancel()
        connect()
    }

    private fun connect() {
        if (user == null) return
        val url = tokens.webSocketUrl() ?: return
        _state.update { it.copy(status = ConnectionStatus.CONNECTING) }
        lateinit var socket: WebSocket
        socket = openSocket(url, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                scope.launch { authenticate(socket) }
            }
            override fun onMessage(webSocket: WebSocket, text: String) = post { handleMessage(socket, text) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                post { handleClose(socket, code, reason) }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = post { handleClose(socket, code, reason) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = post { handleClose(socket, 1006, "") }
        })
        ws = socket
    }

    private fun post(block: () -> Unit) {
        scope.launch { block() }
    }

    private suspend fun authenticate(socket: WebSocket) {
        try {
            val token = tokens.accessToken()
            if (socket === ws) socket.send(authMessage(token, first = true))
        } catch (e: SessionExpiredException) {
            // The app goes back to the login screen (ApiClient's onSessionExpired).
        } catch (e: Exception) {
            socket.cancel() // Server unreachable: try again later.
        }
    }

    internal fun handleClose(socket: WebSocket, code: Int, reason: String) {
        if (socket !== ws) return
        ws = null
        clearTimers()
        failPending("Connexion au chat perdue.")
        if (user == null) return
        _state.update { it.copy(status = ConnectionStatus.DISCONNECTED) }
        if (code == 4003 || (code == 4001 && reason == "session_revoked")) {
            tokens.expireSession()
            return
        }
        if (code == 4001) tokens.dropAccessToken() // token refused: the next one is refreshed
        val wait = RECONNECT_DELAYS[minOf(attempt, RECONNECT_DELAYS.lastIndex)]
        attempt += 1
        reconnectJob = scope.launch {
            delay(wait)
            connect()
        }
    }

    private fun clearTimers() {
        heartbeatJob?.cancel()
        reauthJob?.cancel()
    }

    /** Sends `auth` again with a fresh token before the old one expires (otherwise the server closes with 4001). */
    private fun scheduleReauth(socket: WebSocket) {
        reauthJob?.cancel()
        reauthJob = scope.launch {
            while (isActive && socket === ws) {
                val expiresAt = tokens.accessExpiresAt()
                delay(if (expiresAt != null) maxOf(5_000L, expiresAt - now()) else 60_000L)
                try {
                    val token = tokens.accessToken()
                    if (socket !== ws) return@launch
                    socket.send(authMessage(token, first = false))
                } catch (_: NetworkException) {
                    // Try again at the next turn.
                } catch (_: SessionExpiredException) {
                    return@launch
                }
            }
        }
    }

    // --- Server messages ---

    internal fun handleMessage(socket: WebSocket, raw: String) {
        val message = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) {
            return
        }
        when (message.string("type")) {
            "hello" -> {
                attempt = 0
                val presence = message["friends"]?.jsonArray.orEmpty()
                    .mapNotNull { runCatching { json.decodeFromJsonElement<Presence>(it) }.getOrNull() }
                    .associateBy { it.userId }
                _state.update { it.copy(status = ConnectionStatus.CONNECTED, presence = presence, friendsVersion = it.friendsVersion + 1) }
                val every = (message["heartbeat_interval"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 } ?: 20
                heartbeatJob?.cancel()
                heartbeatJob = scope.launch {
                    while (isActive) {
                        delay(every * 1000L)
                        if (socket === ws) socket.send("""{"type":"heartbeat"}""")
                    }
                }
                scheduleReauth(socket)
            }
            "presence" -> {
                val presence = runCatching { json.decodeFromJsonElement<Presence>(message) }.getOrNull() ?: return
                _state.update { it.copy(presence = it.presence + (presence.userId to presence)) }
            }
            "friend_message" -> {
                val msg = message.friendMessage() ?: return
                addMessage(msg)
                if (msg.fromUserId != activeChat) {
                    _state.update { it.copy(unread = it.unread + (msg.fromUserId to (it.unread[msg.fromUserId] ?: 0) + 1)) }
                    val name = names[msg.fromUserId]
                    val route = Routes.chat(msg.fromUserId)
                    val text = if (name != null) "Message de $name" else "Nouveau message"
                    toast(text, route, perConversation = true)
                    onAlert(Alert.Message(msg, name, text, route))
                }
            }
            "friend_message_sent" -> {
                val msg = message.friendMessage() ?: return
                addMessage(msg)
                val clientId = message.string("client_id")
                val done = pending.firstOrNull { it.clientId == clientId } ?: return
                pending.remove(done)
                done.result.complete(msg)
            }
            "notification" -> onNotification(message.string("event").orEmpty(), message["data"] as? JsonObject ?: JsonObject(emptyMap()))
            "error" -> {
                // Errors don't carry the client_id: a socket's messages are handled in order,
                // so the oldest pending send is the one refused.
                val code = message.string("error").orEmpty()
                if (code in SEND_ERRORS && pending.isNotEmpty()) {
                    pending.removeFirst().result.completeExceptionally(
                        IllegalStateException(SOCKET_MESSAGES[code] ?: Labels.messageFor(code)),
                    )
                }
            }
        }
    }

    private fun onNotification(event: String, data: JsonObject) {
        fun bump() = _state.update { it.copy(friendsVersion = it.friendsVersion + 1) }
        when (event) {
            "friend.request_sent" -> {
                bump()
                if (data.string("to_user_id") == user?.userId) {
                    toast("Nouvelle demande d’ami", Routes.FRIENDS)
                    onAlert(Alert.FriendRequest("Nouvelle demande d’ami", Routes.FRIENDS))
                }
            }
            "friend.added" -> {
                bump()
                toast("Nouvel ami ajouté", Routes.FRIENDS)
                onAlert(Alert.FriendAdded("Nouvel ami ajouté", Routes.FRIENDS))
            }
            "friend.request_removed", "friend.removed" -> bump()
            "progress.level_up" -> {
                val text = "Niveau ${data.string("level")} atteint !"
                toast(text, Routes.PROFILE)
                onAlert(Alert.Progress(text, Routes.PROFILE))
            }
            "progress.achievement_unlocked" -> {
                val id = data.string("achievement_id").orEmpty()
                val text = "Succès débloqué : ${Labels.achievementText(id, id, "").name}"
                toast(text, Routes.ACHIEVEMENTS)
                onAlert(Alert.Progress(text, Routes.ACHIEVEMENTS))
            }
        }
    }

    private fun addMessage(msg: FriendMessage) {
        val me = user?.userId
        val friend = if (msg.fromUserId == me) msg.toUserId else msg.fromUserId
        _state.update { s ->
            val list = s.messages[friend].orEmpty()
            if (list.any { it.id == msg.id }) s else s.copy(messages = s.messages + (friend to list + msg))
        }
    }

    // --- App actions ---

    /** Sends a message; returns the message saved by the server. Throws with a French message. */
    suspend fun sendMessage(to: String, body: String): FriendMessage {
        val socket = ws
        if (socket == null || _state.value.status != ConnectionStatus.CONNECTED) {
            throw IllegalStateException("Chat déconnecté : réessaie dans un instant.")
        }
        val clientId = "${now()}-${(Math.random() * 1e9).toLong().toString(36)}"
        val send = PendingSend(clientId, CompletableDeferred())
        pending.addLast(send)
        socket.send(
            buildJsonObject {
                put("type", "friend_message")
                put("to", to)
                put("body", body)
                put("client_id", clientId)
            }.toString(),
        )
        return withTimeoutOrNull(SEND_TIMEOUT_MS) { send.result.await() } ?: run {
            pending.remove(send)
            throw IllegalStateException("Pas de réponse du chat. Réessaie.")
        }
    }

    /** Conversation on screen: its messages don't count as unread. */
    fun openChat(friendId: String?) {
        shownChat = friendId
        if (friendId != null && visible) markRead(friendId)
    }

    /** App in the foreground or not: in the background, the open conversation's messages are unread. */
    fun setVisible(visible: Boolean) {
        this.visible = visible
        if (visible) shownChat?.let(::markRead)
    }

    private fun markRead(friendId: String) {
        _state.update { s ->
            s.copy(unread = s.unread - friendId, toasts = s.toasts.filter { it.route != Routes.chat(friendId) })
        }
    }

    /** After each load of the friend list: usernames (for notifications) and incoming requests. */
    fun setFriends(names: Map<String, String>, incoming: Int) {
        this.names = names
        if (incoming != _state.value.incoming) _state.update { it.copy(incoming = incoming) }
    }

    fun dismissToast(id: Long) {
        _state.update { s -> s.copy(toasts = s.toasts.filter { it.id != id }) }
    }

    private fun toast(text: String, route: String?, perConversation: Boolean = false) {
        val id = nextToast++
        // One toast per conversation: no pile for a burst of messages.
        _state.update { s ->
            val kept = s.toasts.filter { !perConversation || it.route != route }
            s.copy(toasts = (kept + Toast(id, text, route)).takeLast(3))
        }
        scope.launch {
            delay(TOAST_MS)
            dismissToast(id)
        }
    }

    private fun failPending(text: String) {
        while (pending.isNotEmpty()) pending.removeFirst().result.completeExceptionally(IllegalStateException(text))
    }

    private fun authMessage(token: String, first: Boolean): String = buildJsonObject {
        put("type", "auth")
        put("token", token)
        if (first) put("client", "mobile")
    }.toString()

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.friendMessage(): FriendMessage? =
        runCatching { json.decodeFromJsonElement<FriendMessage>(this["message"]!!.jsonObject) }.getOrNull()

    companion object {
        private val RECONNECT_DELAYS = longArrayOf(1_000, 2_000, 5_000, 10_000, 30_000)
        private const val TOAST_MS = 4_500L
        private const val SEND_TIMEOUT_MS = 10_000L

        private val SEND_ERRORS = setOf("not_friends", "invalid_body", "invalid_recipient", "rate_limited", "internal_error")

        private val SOCKET_MESSAGES = mapOf(
            "not_friends" to "Vous n’êtes plus amis : message non envoyé.",
            "invalid_body" to "Un message fait de 1 à 500 caractères.",
            "rate_limited" to "Trop de messages d’un coup. Patiente quelques secondes.",
            "internal_error" to "Le message n’a pas pu être envoyé. Réessaie.",
        )

        private val json = Json { ignoreUnknownKeys = true }
    }
}
