// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

import android.content.Context
import com.adlen.sonora.api.Blends
import com.adlen.sonora.api.Catalog
import com.adlen.sonora.api.Devices
import com.adlen.sonora.api.Home
import com.adlen.sonora.api.Jam
import com.adlen.sonora.api.Library
import com.adlen.sonora.api.SonoraApi
import com.adlen.sonora.api.Player
import com.adlen.sonora.api.Playlists
import com.adlen.sonora.api.Podcasts
import com.adlen.sonora.api.Recents
import com.adlen.sonora.api.Search
import com.adlen.sonora.api.TrackExtras
import com.adlen.sonora.api.Users
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The Subsonic backend behind the `SonoraApi` contract.
 * Home, search, albums, artists, library, playlists CRUD, lyrics, recents and playback
 * are wired to Subsonic/OpenSubsonic; Spotify-only surfaces (podcasts, Connect, Jam, Blends)
 * remain empty stubs.
 */
class SubsonicApi(
    context: Context,
    val config: SubsonicConfig,
) : SonoraApi {

    private val client = SubsonicClient(config)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _capabilities = MutableStateFlow<ServerCapabilities?>(null)
    private val catalogImpl = SubsonicCatalog(client)
    private val playlistsImpl = SubsonicPlaylists(client)
    private val playerImpl = SubsonicPlayer(context.applicationContext, client, catalogImpl, playlistsImpl)

    /** The server type/version and the optional calls it advertises. */
    val capabilities: StateFlow<ServerCapabilities?> = _capabilities

    init {
        scope.launch { runCatching { client.probeCapabilities() }.onSuccess { _capabilities.value = it } }
    }

    override val player: Player = playerImpl
    override val catalog: Catalog = catalogImpl
    override val home: Home = SubsonicHome(client, context)
    override val search: Search = SubsonicSearch(client)
    override val library: Library = SubsonicLibrary(client)
    override val playlists: Playlists = playlistsImpl
    override val users: Users = SubsonicUsers(config)
    override val podcasts: Podcasts = SubsonicPodcasts()
    override val extras: TrackExtras = SubsonicExtras(client) { _capabilities.value }
    override val devices: Devices = SubsonicDevices()
    override val jam: Jam = SubsonicJam()
    override val blends: Blends = SubsonicBlends()
    override val recents: Recents = SubsonicRecents(client)

    private val readyFlow = MutableStateFlow(true)
    override val ready: StateFlow<Boolean> = readyFlow

    /**
     * Where to fetch [trackId] for offline listening. [quality] is a DownloadQuality name
     * ("SMALL", "CD", "HI_RES"); anything else keeps the original file.
     */
    fun downloadUrl(trackId: String, quality: String?): String {
        val (format, maxBitRate) = DownloadParams.of(quality)
        return client.downloadUrl(trackId, format, maxBitRate)
    }

    /** Cover art for a song, album or artist id (the `getCoverArt` id the server gave). */
    fun coverUrl(id: String, size: Int = 600): String? = client.coverUrl(id, size)

    /** True when the server answers: detects it coming back while the app is offline. */
    suspend fun ping(): Boolean = runCatching { client.call("ping") }.isSuccess

    /** Re-probe after the user changes the server; null when the server did not answer. */
    suspend fun refreshCapabilities(): ServerCapabilities? {
        val probed = runCatching { client.probeCapabilities(force = true) }.getOrNull()
        _capabilities.value = probed
        return probed
    }

    /** Unknown capabilities count as supported: a core endpoint should not vanish on a slow probe. */
    fun supports(feature: ServerCapabilities.Feature): Boolean =
        _capabilities.value?.supports(feature) ?: true

    /** The setup screen changed the server: the same client/player now talk to the new one. */
    fun updateConfig(config: SubsonicConfig) {
        client.updateConfig(config)
        _capabilities.value = null
        scope.launch { runCatching { client.probeCapabilities() }.onSuccess { _capabilities.value = it } }
        playerImpl.restoreServerQueueIfIdle()
    }
}
