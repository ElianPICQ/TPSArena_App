package fr.tpsarena.companion.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

// Last data shown, kept on the phone for offline use (the PWA's lib/cache.ts). One file per
// player and per screen; everything is deleted on logout or when another player logs in.

class Cache(private val root: File) {
    data class Entry<T>(val savedAt: Long, val data: T)

    @Serializable
    private class Stored(val savedAt: Long, val data: String)

    fun <T> read(userId: String, key: String, serializer: KSerializer<T>): Entry<T>? = try {
        val stored = json.decodeFromString<Stored>(file(userId, key).readText())
        Entry(stored.savedAt, json.decodeFromString(serializer, stored.data))
    } catch (_: Exception) {
        null // Missing, unreadable or from an older format: no offline data for this screen.
    }

    fun <T> write(userId: String, key: String, serializer: KSerializer<T>, data: T) {
        try {
            val target = file(userId, key)
            target.parentFile?.mkdirs()
            val tmp = File(target.path + ".tmp")
            tmp.writeText(json.encodeToString(Stored.serializer(), Stored(System.currentTimeMillis(), json.encodeToString(serializer, data))))
            if (!tmp.renameTo(target)) tmp.delete()
        } catch (_: IOException) {
            // Disk full: no offline data for this screen.
        }
    }

    fun clear() {
        root.deleteRecursively()
    }

    // Keys come from the app (`chat.<uuid>`, `board.xp.top`…); anything else is replaced.
    private fun file(userId: String, key: String) = File(File(root, safe(userId)), safe(key) + ".json")

    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
