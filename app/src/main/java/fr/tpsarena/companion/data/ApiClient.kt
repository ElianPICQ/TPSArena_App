package fr.tpsarena.companion.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.serializer
import fr.tpsarena.companion.realtime.TokenSource
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Client of the web-bff, reached through the site's nginx (`<server>/api/...`), like the PWA
// (TPSArena_Site/app/src/lib/api.ts).
//
// Session: the access token (15 min) stays in memory. The web-bff puts the refresh token in an
// HttpOnly cookie (`tpsarena_refresh`, Path=/api/auth, Secure). A browser keeps it; here the app
// keeps its value itself (SessionStore) and sends it back on /auth/* only, whatever the scheme:
// the Secure flag would otherwise stop it on a site served over plain HTTP.
//
// Auth revokes every session of the player when a rotated refresh token is reused (presumed
// theft), so refreshes go one at a time (refreshMutex) and the new token is stored before use.

/** An error answer of the API (`{"error", "message"}`), with its French message. */
open class ApiException(val status: Int, val code: String) : Exception(Labels.messageFor(code, status))

/** The server could not be reached (offline, Wi-Fi cut, wrong address…). */
class NetworkException(message: String = Labels.messageFor("unreachable")) : Exception(message)

/** The session is over (refresh token expired, revoked or missing): log in again. */
class SessionExpiredException : ApiException(401, "invalid_refresh_token")

class ApiClient(
    private val http: OkHttpClient,
    private val store: SessionStore,
    private val now: () -> Long = System::currentTimeMillis,
) : TokenSource {
    private data class Access(val token: String, val expiresAt: Long)

    @Volatile private var access: Access? = null
    private val refreshMutex = Mutex()

    @Volatile var onSessionExpired: () -> Unit = {}

    val hasRefreshToken: Boolean get() = store.refreshToken != null

    // --- Session ---

    suspend fun login(serverUrl: String, identifier: String, password: String): User {
        val origin = normalizeServerUrl(serverUrl) ?: throw ApiException(0, "invalid_server_url")
        store.serverUrl = origin
        val body = buildJsonObject("identifier" to identifier, "password" to password)
        return applySession(decode(request("/auth/login", "POST", body), Session.serializer()))
    }

    /**
     * Extends the session with the refresh token. Returns the player, or null if they must log
     * in again. Throws NetworkException if the server is unreachable (the session may still be valid).
     */
    suspend fun refreshSession(): User? = refreshMutex.withLock { refreshLocked() }

    private suspend fun refreshLocked(): User? {
        if (store.refreshToken == null) {
            access = null
            return null
        }
        return try {
            applySession(decode(request("/auth/refresh", "POST", null), Session.serializer()))
        } catch (e: ApiException) {
            if (e.status != 401) throw e
            access = null
            store.refreshToken = null
            null
        }
    }

    /** Revokes the refresh token and forgets it. Never throws. */
    suspend fun logout() {
        access = null
        refreshMutex.withLock {
            try {
                if (store.refreshToken != null) request("/auth/logout", "POST", null)
            } catch (_: Exception) {
                // Offline: the token expires on its own; the local session is closed anyway.
            }
            store.refreshToken = null
        }
    }

    private fun applySession(session: Session): User {
        access = Access(session.accessToken, now() + session.expiresIn * 1000 - EXPIRY_MARGIN_MS)
        return session.user
    }

    /** A valid access token (refreshed if needed). Throws SessionExpiredException or NetworkException. */
    override suspend fun accessToken(): String {
        access?.takeIf { it.expiresAt > now() }?.let { return it.token }
        val token = refreshMutex.withLock {
            // Another caller may have refreshed while this one waited for the lock.
            access?.takeIf { it.expiresAt > now() }?.token ?: run {
                refreshLocked()
                access?.token
            }
        }
        if (token == null) {
            onSessionExpired()
            throw SessionExpiredException()
        }
        return token
    }

    /** When the current access token must be replaced (margin included), or null without one. */
    override fun accessExpiresAt(): Long? = access?.expiresAt

    /** The server refused the token: the next call asks for a new one. */
    override fun dropAccessToken() {
        access = null
    }

    /** Session revoked or account deleted (WebSocket closed): back to the login screen. */
    override fun expireSession() {
        access = null
        store.refreshToken = null
        onSessionExpired()
    }

    // --- Authenticated calls ---

    suspend fun <T> get(path: String, serializer: KSerializer<T>): T = decode(authed(path, "GET", null), serializer)

    suspend inline fun <reified T> get(path: String): T = get(path, serializer())

    /** POST (optional JSON body) or DELETE; the answer is ignored. */
    suspend fun send(method: String, path: String, body: JsonElement? = null) {
        authed(path, method, body)
    }

    suspend fun <T> send(method: String, path: String, body: JsonElement?, serializer: KSerializer<T>): T =
        decode(authed(path, method, body), serializer)

    /** A 401 (token expired in between) refreshes the token and tries once more. */
    private suspend fun authed(path: String, method: String, body: JsonElement?): String {
        val token = accessToken()
        return try {
            request(path, method, body, token)
        } catch (e: ApiException) {
            if (e.status != 401 || e is SessionExpiredException) throw e
            if (access?.token == token) access = null
            request(path, method, body, accessToken())
        }
    }

    // --- HTTP ---

    /** `wss://host:port/ws` (or `ws://` for a site served over HTTP), null without a server. */
    override fun webSocketUrl(): String? {
        val origin = store.serverUrl?.toHttpUrlOrNull() ?: return null
        val scheme = if (origin.isHttps) "wss" else "ws"
        return "$scheme://${origin.host}:${origin.port}/ws"
    }

    private fun apiUrl(path: String): HttpUrl {
        val origin = store.serverUrl ?: throw NetworkException(Labels.messageFor("no_server"))
        return "$origin/api$path".toHttpUrlOrNull() ?: throw NetworkException(Labels.messageFor("no_server"))
    }

    /** The answer's body (empty for a 204). Throws ApiException or NetworkException. */
    private suspend fun request(path: String, method: String, body: JsonElement?, bearer: String? = null): String {
        val builder = Request.Builder()
            .url(apiUrl(path))
            .header("Accept", "application/json")
            .header("Cache-Control", "no-store")
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        if (path.startsWith("/auth/")) store.refreshToken?.let { builder.header("Cookie", "$REFRESH_COOKIE=$it") }
        val requestBody = body?.toString()?.toRequestBody(JSON_TYPE)
        builder.method(method, requestBody ?: if (method == "POST") EMPTY_BODY else null)

        val response = try {
            http.newCall(builder.build()).await()
        } catch (e: IOException) {
            throw networkError(e)
        }
        return response.use { handle(it, path) }
    }

    private fun handle(response: Response, path: String): String {
        if (path.startsWith("/auth/")) storeRefreshCookie(response)
        val text = try {
            response.body.string()
        } catch (e: IOException) {
            throw networkError(e)
        }
        if (!response.isSuccessful) {
            val code = runCatching {
                ((json.parseToJsonElement(text) as? JsonObject)?.get("error") as? JsonPrimitive)?.contentOrNull
            }.getOrNull()
            throw ApiException(response.code, code ?: fallbackCode(response.code))
        }
        return text
    }

    private fun <T> decode(text: String, serializer: KSerializer<T>): T {
        return try {
            json.decodeFromString(serializer, text)
        } catch (_: SerializationException) {
            // Not our JSON: an intermediary's error page.
            throw ApiException(502, "bad_gateway")
        } catch (_: IllegalArgumentException) {
            throw ApiException(502, "bad_gateway")
        }
    }

    /** Keeps the cookie's new value (login, rotation on refresh), or forgets it when the server deletes it. */
    private fun storeRefreshCookie(response: Response) {
        for (header in response.headers("Set-Cookie")) {
            val cookie = parseSetCookie(header) ?: continue
            if (cookie.first != REFRESH_COOKIE) continue
            store.refreshToken = cookie.second
        }
    }

    private fun networkError(e: IOException): NetworkException = when {
        e is SSLHandshakeException || e is SSLPeerUnverifiedException || e.cause is CertificateException ->
            NetworkException(Labels.messageFor("untrusted_certificate"))
        else -> NetworkException()
    }

    companion object {
        const val REFRESH_COOKIE = "tpsarena_refresh"
        private const val EXPIRY_MARGIN_MS = 60_000L
        private val JSON_TYPE = "application/json".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)

        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun fallbackCode(status: Int): String = when {
            status == 422 -> "validation"
            status == 429 -> "rate_limited"
            status >= 500 -> "bad_gateway"
            else -> "unknown"
        }

        /**
         * `name` and value of a Set-Cookie header; the value is null when the cookie is deleted
         * (empty, `Max-Age=0` or an `Expires` in the past, which is what Starlette's delete_cookie sends).
         */
        fun parseSetCookie(header: String, now: Long = System.currentTimeMillis()): Pair<String, String?>? {
            val parts = header.split(';').map { it.trim() }
            val nameValue = parts.firstOrNull()?.takeIf { '=' in it } ?: return null
            val name = nameValue.substringBefore('=').trim()
            var value: String? = nameValue.substringAfter('=').trim().removeSurrounding("\"")
            for (attribute in parts.drop(1)) {
                val key = attribute.substringBefore('=').trim().lowercase()
                val arg = attribute.substringAfter('=', "").trim()
                if (key == "max-age" && (arg.toLongOrNull() ?: 1) <= 0) value = null
                if (key == "expires") {
                    val expires = runCatching {
                        java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US).parse(arg)
                    }.getOrNull()
                    if (expires != null && expires.time <= now) value = null
                }
            }
            return name to value?.takeIf { it.isNotEmpty() }
        }

        /**
         * Site origin from what the player typed: `192.168.1.20:8443` → `https://192.168.1.20:8443`.
         * No scheme means HTTPS (the site's default when it has its certificate). Null if invalid.
         */
        fun normalizeServerUrl(input: String): String? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            val url = withScheme.toHttpUrlOrNull() ?: return null
            val default = (url.isHttps && url.port == 443) || (!url.isHttps && url.port == 80)
            val host = if (':' in url.host) "[${url.host}]" else url.host
            return "${url.scheme}://$host${if (default) "" else ":${url.port}"}"
        }

        fun buildJsonObject(vararg fields: Pair<String, String>): JsonObject =
            JsonObject(fields.associate { (k, v) -> k to JsonPrimitive(v) })
    }
}

/** Suspends until the call answers; cancelling the coroutine cancels the call. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response) { _, r, _ -> r.close() }
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
}
