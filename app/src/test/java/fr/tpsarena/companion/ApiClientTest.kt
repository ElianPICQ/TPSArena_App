package fr.tpsarena.companion

import fr.tpsarena.companion.data.ApiClient
import fr.tpsarena.companion.data.ApiException
import fr.tpsarena.companion.data.NetworkException
import fr.tpsarena.companion.data.PlayerStats
import fr.tpsarena.companion.data.SessionExpiredException
import fr.tpsarena.companion.data.SessionStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class ApiClientTest {
    private class MemoryStore : SessionStore {
        override var serverUrl: String? = null
        override var refreshToken: String? = null
    }

    private lateinit var server: MockWebServer
    private val store = MemoryStore()
    private var now = 1_000_000L
    private lateinit var api: ApiClient
    private val requests = CopyOnWriteArrayList<RecordedRequest>()

    private fun session(token: String, refresh: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .addHeader("Set-Cookie", "tpsarena_refresh=$refresh; HttpOnly; Max-Age=2592000; Path=/api/auth; SameSite=strict; Secure")
        .setBody("""{"access_token":"$token","expires_in":900,"user":{"user_id":"u1","username":"neo"}}""")

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private val stats = """{"user_id":"u1","matches_played":3,"wins":1,"kills":10,"deaths":5,"kd_ratio":2.0,"level":2,"xp":300}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store.serverUrl = "http://${server.hostName}:${server.port}"
        api = ApiClient(OkHttpClient(), store, now = { now })
    }

    @After
    fun tearDown() = server.shutdown()

    private fun route(handler: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return handler(request)
            }
        }
    }

    @Test
    fun loginStoresServerAndRefreshCookie() = runBlocking {
        route { session("a1", "r1") }
        val user = api.login("http://${server.hostName}:${server.port}/", "neo", "secret")
        assertEquals("neo", user.username)
        assertEquals("r1", store.refreshToken)
        assertEquals("http://${server.hostName}:${server.port}", store.serverUrl)
        val sent = requests.single()
        assertEquals("/api/auth/login", sent.path)
        assertEquals("""{"identifier":"neo","password":"secret"}""", sent.body.readUtf8())
    }

    @Test
    fun errorCodesAreTranslated() = runBlocking {
        route { json(401, """{"error":"invalid_credentials","message":"Invalid."}""") }
        try {
            api.login(store.serverUrl!!, "neo", "bad")
            fail()
        } catch (e: ApiException) {
            assertEquals("invalid_credentials", e.code)
            assertEquals("Identifiant ou mot de passe incorrect.", e.message)
        }
    }

    @Test
    fun nonJsonErrorsFallBackOnTheStatus() = runBlocking {
        route { MockResponse().setResponseCode(502).setBody("<html>Bad Gateway</html>") }
        try {
            api.login(store.serverUrl!!, "neo", "x")
            fail()
        } catch (e: ApiException) {
            assertEquals("bad_gateway", e.code)
        }
        route { MockResponse().setResponseCode(429) }
        try {
            api.login(store.serverUrl!!, "neo", "x")
            fail()
        } catch (e: ApiException) {
            assertEquals("rate_limited", e.code)
        }
    }

    @Test
    fun unreachableServerIsANetworkError() = runBlocking {
        server.shutdown()
        try {
            api.login(store.serverUrl!!, "neo", "x")
            fail()
        } catch (e: NetworkException) {
            assertEquals("Impossible de joindre le serveur. Vérifie ta connexion au réseau.", e.message)
        }
    }

    @Test
    fun expiredTokenIsRefreshedThenTheCallRetried() = runBlocking {
        val calls = AtomicInteger()
        route { r ->
            when (r.path) {
                "/api/auth/login" -> session("a1", "r1")
                "/api/auth/refresh" -> session("a2", "r2")
                else -> if (calls.getAndIncrement() == 0) json(401, """{"error":"token_expired"}""") else json(200, stats)
            }
        }
        api.login(store.serverUrl!!, "neo", "x")
        val result = api.get<PlayerStats>("/players/u1/stats")
        assertEquals(10, result.kills)
        val paths = requests.map { it.path }
        assertEquals(listOf("/api/auth/login", "/api/players/u1/stats", "/api/auth/refresh", "/api/players/u1/stats"), paths)
        assertEquals("Bearer a1", requests[1].getHeader("Authorization"))
        assertEquals("tpsarena_refresh=r1", requests[2].getHeader("Cookie"))
        assertEquals("Bearer a2", requests[3].getHeader("Authorization"))
        assertEquals("r2", store.refreshToken)
        // The cookie only goes to /auth/*.
        assertNull(requests[1].getHeader("Cookie"))
    }

    @Test
    fun concurrentCallsShareOneRefresh() = runBlocking {
        val refreshes = AtomicInteger()
        route { r ->
            when (r.path) {
                "/api/auth/refresh" -> {
                    refreshes.incrementAndGet()
                    Thread.sleep(100)
                    session("a${refreshes.get()}", "r${refreshes.get()}")
                }
                else -> json(200, stats)
            }
        }
        store.refreshToken = "r0"
        (1..5).map { async(kotlinx.coroutines.Dispatchers.IO) { api.get<PlayerStats>("/players/u1/stats") } }.awaitAll()
        assertEquals(1, refreshes.get())
    }

    @Test
    fun tokenNearExpiryIsRefreshedBeforeUse() = runBlocking {
        route { r -> if (r.path == "/api/auth/refresh") session("a2", "r2") else if (r.path == "/api/auth/login") session("a1", "r1") else json(200, stats) }
        api.login(store.serverUrl!!, "neo", "x")
        now += 850_000 // 14 min 10 s: inside the one-minute margin of the 15 min token
        api.get<PlayerStats>("/players/u1/stats")
        assertEquals(listOf("/api/auth/login", "/api/auth/refresh", "/api/players/u1/stats"), requests.map { it.path })
    }

    @Test
    fun refusedRefreshEndsTheSession() = runBlocking {
        route { r ->
            if (r.path == "/api/auth/refresh") {
                json(401, """{"error":"invalid_refresh_token"}""")
                    .addHeader("Set-Cookie", "tpsarena_refresh=\"\"; expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; Path=/api/auth")
            } else {
                json(200, stats)
            }
        }
        var expired = false
        api.onSessionExpired = { expired = true }
        store.refreshToken = "old"
        try {
            api.get<PlayerStats>("/players/u1/stats")
            fail()
        } catch (_: SessionExpiredException) {
        }
        assertTrue(expired)
        assertNull(store.refreshToken)
        assertFalse(api.hasRefreshToken)
    }

    @Test
    fun refreshWithoutTokenSkipsTheServer() = runBlocking {
        route { fail("no request expected"); MockResponse() }
        assertNull(api.refreshSession())
    }

    @Test
    fun logoutSendsTheCookieAndForgetsIt() = runBlocking {
        route { MockResponse().setResponseCode(204).addHeader("Set-Cookie", "tpsarena_refresh=\"\"; Max-Age=0; Path=/api/auth") }
        store.refreshToken = "r9"
        api.logout()
        assertEquals("tpsarena_refresh=r9", requests.single().getHeader("Cookie"))
        assertNull(store.refreshToken)
    }

    @Test
    fun webSocketUrlFollowsTheScheme() {
        store.serverUrl = "https://192.168.1.20:8443"
        assertEquals("wss://192.168.1.20:8443/ws", api.webSocketUrl())
        store.serverUrl = "http://192.168.1.20:8080"
        assertEquals("ws://192.168.1.20:8080/ws", api.webSocketUrl())
    }

    @Test
    fun normalizesServerAddresses() {
        assertEquals("https://192.168.1.20:8443", ApiClient.normalizeServerUrl(" 192.168.1.20:8443/ "))
        assertEquals("https://192.168.1.20", ApiClient.normalizeServerUrl("192.168.1.20"))
        assertEquals("http://192.168.1.20:8080", ApiClient.normalizeServerUrl("http://192.168.1.20:8080/app/"))
        assertEquals("http://10.0.2.2", ApiClient.normalizeServerUrl("http://10.0.2.2:80"))
        assertNull(ApiClient.normalizeServerUrl(""))
        assertNull(ApiClient.normalizeServerUrl("ht tp://bad"))
    }

    @Test
    fun parsesSetCookieHeaders() {
        assertEquals("tpsarena_refresh" to "abc", ApiClient.parseSetCookie("tpsarena_refresh=abc; HttpOnly; Path=/api/auth; Secure"))
        assertEquals("tpsarena_refresh" to null, ApiClient.parseSetCookie("tpsarena_refresh=\"\"; Max-Age=0; Path=/api/auth"))
        assertEquals(
            "tpsarena_refresh" to null,
            ApiClient.parseSetCookie("tpsarena_refresh=abc; expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/api/auth"),
        )
        assertNull(ApiClient.parseSetCookie("garbage"))
    }
}
