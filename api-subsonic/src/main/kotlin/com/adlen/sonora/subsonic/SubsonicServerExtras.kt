// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

import com.adlen.sonora.api.Track
import com.adlen.sonora.api.Uri
import org.json.JSONObject

/**
 * The server-side extras that are not playback: bookmarks, shares, scan status and the
 * server's own "now playing" list. Every call is capability-gated, so a Jellyfin bridge
 * simply returns nothing instead of failing.
 */
class SubsonicServerExtras(private val client: SubsonicClient) {

    data class Bookmark(val track: Track, val positionMs: Long, val changedAt: String?)

    data class Share(
        val id: String,
        val url: String,
        val description: String?,
        val expiresAt: Long?,
        val tracks: List<Track>,
    )

    data class ScanStatus(val scanning: Boolean, val count: Int, val lastScan: Long)

    data class RemotePlay(
        val track: Track,
        val username: String,
        val playerId: String,
        val minutesAgo: Int,
    )

    data class MusicFolder(val id: String, val name: String)

    /** The server's music folders; more than one means the library can be scoped. */
    suspend fun musicFolders(): List<MusicFolder> {
        val root = client.callOptional("getMusicFolders")?.optJSONObject("musicFolders") ?: return emptyList()
        return root.array("musicFolder").mapNotNull { folder ->
            val id = folder.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MusicFolder(id, folder.optString("name").ifBlank { id })
        }
    }

    suspend fun bookmarks(): List<Bookmark> {
        if (!supports(ServerCapabilities.Feature.BOOKMARKS)) return emptyList()
        val root = client.callOptional("getBookmarks")?.optJSONObject("bookmarks") ?: return emptyList()
        return root.array("bookmark").mapNotNull { bookmark ->
            val entry = bookmark.optJSONObject("entry") ?: return@mapNotNull null
            Bookmark(
                track = client.trackOf(entry),
                positionMs = bookmark.optLong("position", 0L),
                changedAt = bookmark.optString("changed").takeIf { it.isNotBlank() },
            )
        }
    }

    suspend fun createBookmark(trackUri: Uri, positionMs: Long) {
        if (!supports(ServerCapabilities.Feature.BOOKMARKS)) return
        val id = idOf(trackUri)
        if (id.isBlank()) return
        client.call(
            "createBookmark",
            mapOf("id" to id, "position" to positionMs.coerceAtLeast(0L).toString()),
        )
    }

    suspend fun deleteBookmark(trackUri: Uri) {
        if (!supports(ServerCapabilities.Feature.BOOKMARKS)) return
        val id = idOf(trackUri)
        if (id.isNotBlank()) client.call("deleteBookmark", mapOf("id" to id))
    }

    suspend fun shares(): List<Share> {
        if (!supports(ServerCapabilities.Feature.SHARES)) return emptyList()
        val root = client.callOptional("getShares")?.optJSONObject("shares") ?: return emptyList()
        return root.array("share").map(::shareOf)
    }

    suspend fun createShare(trackUri: Uri, expiresAtMs: Long? = null): Share? {
        if (!supports(ServerCapabilities.Feature.SHARES)) return null
        val id = idOf(trackUri)
        if (id.isBlank()) return null
        val params = buildList {
            add("id" to id)
            if (expiresAtMs != null && expiresAtMs > 0) add("expires" to expiresAtMs.toString())
        }
        val root = client.callPairs("createShare", params).optJSONObject("shares") ?: return null
        return root.array("share").firstOrNull()?.let(::shareOf)
    }

    suspend fun deleteShare(id: String) {
        if (!supports(ServerCapabilities.Feature.SHARES) || id.isBlank()) return
        client.call("deleteShare", mapOf("id" to id))
    }

    suspend fun scanStatus(): ScanStatus? {
        if (!supports(ServerCapabilities.Feature.SCAN)) return null
        val root = client.callOptional("getScanStatus")?.optJSONObject("scanStatus") ?: return null
        return ScanStatus(
            scanning = root.optBoolean("scanning", false),
            count = root.optInt("count", 0),
            lastScan = root.optLong("lastScan", 0L),
        )
    }

    suspend fun startScan() {
        if (!supports(ServerCapabilities.Feature.SCAN)) return
        client.call("startScan")
    }

    suspend fun nowPlaying(): List<RemotePlay> {
        if (!supports(ServerCapabilities.Feature.NOW_PLAYING)) return emptyList()
        val root = client.callOptional("getNowPlaying")?.optJSONObject("nowPlaying") ?: return emptyList()
        return root.array("entry").map { entry ->
            RemotePlay(
                track = client.trackOf(entry),
                username = entry.optString("username"),
                playerId = entry.optString("playerId"),
                minutesAgo = entry.optInt("minutesAgo", 0),
            )
        }
    }

    /**
     * Whether the logged-in user is a server administrator. `getUser` carries `adminRole`,
     * but most servers only allow it for admins: a refused call therefore means "not admin".
     * Null means the server could not tell us, and the caller keeps the row visible.
     */
    suspend fun isAdmin(username: String): Boolean? {
        if (username.isBlank()) return null
        return try {
            val root = client.call("getUser", mapOf("username" to username))
            root.optJSONObject("user")?.optBoolean("adminRole", false) ?: false
        } catch (e: SubsonicException.Auth) {
            false
        } catch (e: SubsonicException.NotFound) {
            null
        } catch (e: SubsonicException.Protocol) {
            null
        } catch (e: SubsonicException.Unsupported) {
            null
        } catch (e: Throwable) {
            null
        }
    }

    private fun shareOf(json: JSONObject): Share = Share(
        id = json.optString("id"),
        url = json.optString("url"),
        description = json.optString("description").takeIf { it.isNotBlank() },
        expiresAt = json.optString("expires").takeIf { it.isNotBlank() }?.let(::parseTime),
        tracks = json.array("entry").map { client.trackOf(it) },
    )

    private fun parseTime(value: String): Long? =
        value.toLongOrNull() ?: runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()

    private fun idOf(uri: Uri): String = uri.substringAfterLast(':').substringBefore('|')

    private fun supports(feature: ServerCapabilities.Feature): Boolean =
        client.capabilities?.supports(feature) != false
}
