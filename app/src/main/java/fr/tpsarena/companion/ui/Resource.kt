package fr.tpsarena.companion.ui

import fr.tpsarena.companion.data.ApiClient
import fr.tpsarena.companion.data.Cache
import fr.tpsarena.companion.data.NetworkException
import fr.tpsarena.companion.data.SessionExpiredException
import fr.tpsarena.companion.data.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer

/** What a signed-in screen needs: the player, whether the server answered, and the data sources. */
data class SessionContext(val user: User, val online: Boolean, val api: ApiClient, val cache: Cache)

data class ResourceState<T>(
    val data: T? = null,
    /** When the data shown was saved, if it comes from the cache (offline); null when fresh. */
    val cachedAt: Long? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * A screen's data (the PWA's lib/resource.ts): shows the last saved version right away, then
 * replaces it with the server's. Offline or on error, the saved version stays, with its date.
 */
class Resource<T>(
    private val scope: CoroutineScope,
    private val ctx: SessionContext,
    private val key: String,
    private val serializer: KSerializer<T>,
    private val load: suspend () -> T,
) {
    private val _state = MutableStateFlow(initial())
    val state: StateFlow<ResourceState<T>> = _state
    private var job: Job? = null

    init {
        reload()
    }

    fun reload() {
        job?.cancel()
        _state.value = initial()
        if (!ctx.online) {
            val saved = _state.value
            _state.value = saved.copy(loading = false, error = if (saved.data == null) OFFLINE_EMPTY else null)
            return
        }
        job = scope.launch {
            try {
                val data = load()
                ctx.cache.write(ctx.user.userId, key, serializer, data)
                _state.value = ResourceState(data, cachedAt = null, loading = false, error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (_: SessionExpiredException) {
                // The app goes back to the login screen.
            } catch (e: Exception) {
                val s = _state.value
                // With saved data, being offline is not an error: the date is enough.
                val error = if (e is NetworkException && s.data != null) null else e.message ?: "Erreur inattendue."
                _state.value = s.copy(loading = false, error = error)
            }
        }
    }

    private fun initial(): ResourceState<T> {
        val saved = ctx.cache.read(ctx.user.userId, key, serializer)
        return ResourceState(saved?.data, saved?.savedAt, loading = true, error = null)
    }

    companion object {
        const val OFFLINE_EMPTY = "Hors ligne, et rien n’a encore été gardé pour cet écran."
    }
}
