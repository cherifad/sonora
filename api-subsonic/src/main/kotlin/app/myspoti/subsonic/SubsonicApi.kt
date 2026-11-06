// Subsonic/OpenSubsonic backend for Myspoti. GPL-3.0.
package app.myspoti.subsonic

import android.content.Context
import app.myspoti.api.Blends
import app.myspoti.api.Catalog
import app.myspoti.api.Devices
import app.myspoti.api.Home
import app.myspoti.api.Jam
import app.myspoti.api.Library
import app.myspoti.api.MyspotiApi
import app.myspoti.api.Player
import app.myspoti.api.Playlists
import app.myspoti.api.Podcasts
import app.myspoti.api.Recents
import app.myspoti.api.Search
import app.myspoti.api.TrackExtras
import app.myspoti.api.Users
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The Subsonic backend behind the `MyspotiApi` contract.
 * Home, search, albums, artists, library, playlists CRUD, lyrics, recents and playback
 * are wired to Subsonic/OpenSubsonic; Spotify-only surfaces (podcasts, Connect, Jam, Blends)
 * remain empty stubs.
 */
class SubsonicApi(
    context: Context,
    val config: SubsonicConfig,
) : MyspotiApi {

    private val client = SubsonicClient(config)
    private val catalogImpl = SubsonicCatalog(client)
    private val playlistsImpl = SubsonicPlaylists(client)
    private val playerImpl = SubsonicPlayer(context.applicationContext, client, catalogImpl, playlistsImpl)

    override val player: Player = playerImpl
    override val catalog: Catalog = catalogImpl
    override val home: Home = SubsonicHome(client, context)
    override val search: Search = SubsonicSearch(client)
    override val library: Library = SubsonicLibrary(client)
    override val playlists: Playlists = playlistsImpl
    override val users: Users = SubsonicUsers(config)
    override val podcasts: Podcasts = SubsonicPodcasts()
    override val extras: TrackExtras = SubsonicExtras(client)
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

    /** The setup screen changed the server: the same client/player now talk to the new one. */
    fun updateConfig(config: SubsonicConfig) {
        client.updateConfig(config)
        playerImpl.restoreServerQueueIfIdle()
    }
}
