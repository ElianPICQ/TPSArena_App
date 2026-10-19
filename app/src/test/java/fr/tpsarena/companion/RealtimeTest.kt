package fr.tpsarena.companion

import fr.tpsarena.companion.data.NetworkException
import fr.tpsarena.companion.data.User
import fr.tpsarena.companion.realtime.Alert
import fr.tpsarena.companion.realtime.ConnectionStatus
import fr.tpsarena.companion.realtime.Realtime
import fr.tpsarena.companion.realtime.TokenSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeTest {
    private class FakeSocket(val listener: WebSocketListener) : WebSocket {
        val sent = mutableListOf<String>()
        var closed: Int? = null
        override fun request() = Request.Builder().url("http://localhost/ws").build()
        override fun queueSize() = 0L
        override fun send(text: String): Boolean = sent.add(text)
        override fun send(bytes: ByteString) = false
        override fun close(code: Int, reason: String?): Boolean {
            closed = code
            return true
        }
        override fun cancel() {
            closed = 1006
        }

        fun open() = listener.onOpen(this, Response.Builder().request(request()).protocol(okhttp3.Protocol.HTTP_1_1).code(101).message("").build())
        fun receive(text: String) = listener.onMessage(this, text)
        fun serverClose(code: Int, reason: String = "") = listener.onClosed(this, code, reason)
        fun types() = sent.map { Json.parseToJsonElement(it).jsonObject["type"]!!.jsonPrimitive.content }
    }

    private class FakeTokens : TokenSource {
        var token = "t1"
        var expiresAt: Long? = 900_000L
        var dropped = 0
        var expired = 0
        var unreachable = false
        override suspend fun accessToken(): String {
            if (unreachable) throw NetworkException()
            return token
        }
        override fun accessExpiresAt() = expiresAt
        override fun dropAccessToken() {
            dropped++
        }
        override fun expireSession() {
            expired++
        }
        override fun webSocketUrl() = "ws://localhost/ws"
    }

    private val me = User("me", "neo")
    private val friend = "f1"
    private val sockets = mutableListOf<FakeSocket>()
    private val tokens = FakeTokens()
    private val alerts = mutableListOf<Alert>()

    private fun TestScope.realtime(): Realtime =
        Realtime(backgroundScope, tokens, { _, listener -> FakeSocket(listener).also { sockets += it } }, now = { testScheduler.currentTime })
            .also { it.onAlert = { a -> alerts += a } }

    private fun TestScope.connected(rt: Realtime): FakeSocket {
        rt.start(me)
        val socket = sockets.last()
        socket.open()
        runCurrent()
        socket.receive("""{"type":"hello","user_id":"me","heartbeat_interval":20,"friends":[{"user_id":"$friend","status":"online","activity":"in_match"}]}""")
        runCurrent()
        return socket
    }

    private fun message(id: String, from: String, to: String) =
        """{"id":"$id","from_user_id":"$from","to_user_id":"$to","body":"salut $id","created_at":"2026-10-16T12:00:0${id}Z"}"""

    @Test
    fun authenticatesAsMobileThenGetsPresence() = runTest {
        val rt = realtime()
        val socket = connected(rt)
        val auth = Json.parseToJsonElement(socket.sent.first()).jsonObject
        assertEquals("auth", auth["type"]!!.jsonPrimitive.content)
        assertEquals("t1", auth["token"]!!.jsonPrimitive.content)
        assertEquals("mobile", auth["client"]!!.jsonPrimitive.content)
        val state = rt.state.value
        assertEquals(ConnectionStatus.CONNECTED, state.status)
        assertEquals("in_match", state.presence[friend]?.activity)
        assertEquals(1, state.friendsVersion)
    }

    @Test
    fun sendsHeartbeatsAndReauthenticatesBeforeExpiry() = runTest {
        val rt = realtime()
        val socket = connected(rt)
        advanceTimeBy(20_001)
        assertEquals(listOf("auth", "heartbeat"), socket.types())
        tokens.token = "t2"
        tokens.expiresAt = null // next turn in 60 s
        advanceTimeBy(900_000)
        val reauth = socket.sent.map { Json.parseToJsonElement(it).jsonObject }.first { it["type"]!!.jsonPrimitive.content == "auth" && it["token"]!!.jsonPrimitive.content == "t2" }
        // Only the first auth says which client this is.
        assertNull(reauth["client"])
        rt.stop()
    }

    @Test
    fun incomingMessageCountsAsUnreadAndAlerts() = runTest {
        val rt = realtime()
        val socket = connected(rt)
        rt.setFriends(mapOf(friend to "Trinity"), incoming = 0)
        socket.receive("""{"type":"friend_message","message":${message("1", friend, "me")}}""")
        runCurrent()
        val state = rt.state.value
        assertEquals(1, state.unread[friend])
        assertEquals(listOf("salut 1"), state.messages[friend]?.map { it.body })
        assertEquals("Message de Trinity", state.toasts.single().text)
        assertEquals("chat/$friend", state.toasts.single().route)
        assertEquals("Message de Trinity", (alerts.single() as Alert.Message).text)
        // Same message again (redelivered on reconnect): not duplicated.
        socket.receive("""{"type":"friend_message","message":${message("1", friend, "me")}}""")
        runCurrent()
        assertEquals(1, rt.state.value.messages[friend]?.size)
        // Toasts go away after 4.5 s.
        advanceTimeBy(4_600)
        assertTrue(rt.state.value.toasts.isEmpty())
    }

    @Test
    fun openConversationIsNotUnread() = runTest {
        val rt = realtime()
        val socket = connected(rt)
        socket.receive("""{"type":"friend_message","message":${message("1", friend, "me")}}""")
        runCurrent()
        rt.openChat(friend)
        assertNull(rt.state.value.unread[friend])
        assertTrue(rt.state.value.toasts.isEmpty())
        socket.receive("""{"type":"friend_message","message":${message("2", friend, "me")}}""")
        runCurrent()
        assertNull(rt.state.value.unread[friend])
        assertEquals(1, alerts.size)
    }

    @Test
    fun openConversationInTheBackgroundIsUnread() = runTest {
        val rt = realtime()
        val socket = connected(rt)
        rt.openChat(friend)
        rt.setVisible(false)
        socket.receive("""{"type":"friend_message","message":${message("1", friend, "me")}}""")
        runCurrent()
        assertEquals(1, rt.state.value.unread[friend])
        assertEquals(1, alerts.size)
        // Back in the foreground on that conversation: read.
        rt.setVisible(true)
        assertNull(rt.state.value.unread[friend])
    }

    @Test
    fun sentMessageResolvesOnItsAck() = runTest {
        val rt = realtime()
        val socket = connected(rt)
        val sending = async { rt.sendMessage(friend, "yo") }
        runCurrent()
        val sent = Json.parseToJsonElement(socket.sent.last()).jsonObject
        assertEquals("friend_message", sent["type"]!!.jsonPrimitive.content)
        assertEquals(friend, sent["to"]!!.jsonPrimitive.content)
        val clientId = sent["client_id"]!!.jsonPrimitive.content
        socket.receive("""{"type":"friend_message_sent","client_id":"$clientId","message":${message("5", "me", friend)}}""")
        runCurrent()
        assertEquals("5", sending.await().id)
        assertEquals(listOf("5"), rt.state.value.messages[friend]?.map { it.id })
    }

    @Test
    fun socketErrorRejectsTheOldestPendingSend() = runTest {
        val rt = realtime()
        val socket = connected(rt)
        val sending = async { runCatching { rt.sendMessage(friend, "yo") } }
        runCurrent()
        socket.receive("""{"type":"error","error":"not_friends","message":"Not friends."}""")
        runCurrent()
        assertEquals("Vous n’êtes plus amis : message non envoyé.", sending.await().exceptionOrNull()?.message)
    }

    @Test
    fun sendTimesOutWithoutAnswer() = runTest {
        val rt = realtime()
        connected(rt)
        val sending = async { runCatching { rt.sendMessage(friend, "yo") } }
        advanceTimeBy(10_001)
        assertEquals("Pas de réponse du chat. Réessaie.", sending.await().exceptionOrNull()?.message)
        rt.stop()
    }

    @Test
    fun cannotSendWhileDisconnected() = runTest {
        val rt = realtime()
        rt.start(me)
        try {
            rt.sendMessage(friend, "yo")
            fail()
        } catch (e: IllegalStateException) {
            assertEquals("Chat déconnecté : réessaie dans un instant.", e.message)
        }
        rt.stop()
    }

    @Test
    fun friendNotificationsReloadTheList() = runTest {
        val rt = realtime()
        val socket = connected(rt)
        socket.receive("""{"type":"notification","event":"friend.request_sent","data":{"request_id":"r","from_user_id":"$friend","to_user_id":"me"}}""")
        socket.receive("""{"type":"notification","event":"friend.removed","data":{"user_id":"me","friend_id":"$friend"}}""")
        socket.receive("""{"type":"notification","event":"progress.achievement_unlocked","data":{"achievement_id":"flawless"}}""")
        runCurrent()
        val state = rt.state.value
        assertEquals(3, state.friendsVersion)
        assertEquals(listOf("Nouvelle demande d’ami", "Succès débloqué : Intouchable"), state.toasts.map { it.text })
        assertTrue(alerts[0] is Alert.FriendRequest)
    }

    @Test
    fun revokedSessionGoesBackToLogin() = runTest {
        val rt = realtime()
        val socket = connected(rt)
        socket.serverClose(4001, "session_revoked")
        runCurrent()
        assertEquals(1, tokens.expired)
        advanceTimeBy(60_000)
        assertEquals(1, sockets.size) // no reconnect
    }

    @Test
    fun deletedAccountGoesBackToLogin() = runTest {
        val rt = realtime()
        val socket = connected(rt)
        socket.serverClose(4003)
        runCurrent()
        assertEquals(1, tokens.expired)
    }

    @Test
    fun refusedTokenIsDroppedThenReconnectsWithBackoff() = runTest {
        val rt = realtime()
        val socket = connected(rt)
        socket.serverClose(4001, "token_expired")
        runCurrent()
        assertEquals(1, tokens.dropped)
        assertEquals(ConnectionStatus.DISCONNECTED, rt.state.value.status)
        advanceTimeBy(999)
        assertEquals(1, sockets.size)
        advanceTimeBy(2)
        assertEquals(2, sockets.size)
        // Second failure in a row: 2 s.
        sockets.last().serverClose(1006)
        runCurrent()
        advanceTimeBy(1_500)
        assertEquals(2, sockets.size)
        advanceTimeBy(600)
        assertEquals(3, sockets.size)
        rt.stop()
    }

    @Test
    fun unreachableServerAtAuthClosesTheSocket() = runTest {
        val rt = realtime()
        tokens.unreachable = true
        rt.start(me)
        sockets.last().open()
        runCurrent()
        assertEquals(1006, sockets.last().closed)
        assertFalse(sockets.last().sent.any())
        rt.stop()
    }

    @Test
    fun stopForgetsEverything() = runTest {
        val rt = realtime()
        val socket = connected(rt)
        rt.stop()
        assertEquals(1000, socket.closed)
        assertEquals(ConnectionStatus.IDLE, rt.state.value.status)
        assertTrue(rt.state.value.presence.isEmpty())
    }

    @Test
    fun pauseKeepsThePlayerAndWakeReconnects() = runTest {
        val rt = realtime()
        val socket = connected(rt)
        rt.pause()
        assertEquals(1000, socket.closed)
        assertEquals(ConnectionStatus.DISCONNECTED, rt.state.value.status)
        advanceTimeBy(60_000)
        assertEquals(1, sockets.size)
        rt.wake()
        assertEquals(2, sockets.size)
        rt.stop()
    }
}
