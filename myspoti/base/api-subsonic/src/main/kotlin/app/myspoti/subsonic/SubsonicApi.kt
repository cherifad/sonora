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
 * The porting spike's backend: Subsonic endpoints behind the same `MyspotiApi` contract the
 * Myspoti UI already speaks. Home, search, albums, library and playback are wired; the
 * Spotify-only surfaces (podcasts, Connect, Jam, Blends, extras) are empty stubs.
 */
class SubsonicApi(
    context: Context,
    val config: SubsonicConfig,
) : MyspotiApi {

    private val client = SubsonicClient(config)
    private val catalogImpl = SubsonicCatalog(client)
    private val playlistsImpl = SubsonicPlaylists(client)

    override val player: Player = SubsonicPlayer(context.applicationContext, client, catalogImpl, playlistsImpl)
    override val catalog: Catalog = catalogImpl
    override val home: Home = SubsonicHome(client)
    override val search: Search = SubsonicSearch(client)
    override val library: Library = SubsonicLibrary(client)
    override val playlists: Playlists = playlistsImpl
    override val users: Users = SubsonicUsers(config)
    override val podcasts: Podcasts = SubsonicPodcasts()
    override val extras: TrackExtras = SubsonicExtras()
    override val devices: Devices = SubsonicDevices()
    override val jam: Jam = SubsonicJam()
    override val blends: Blends = SubsonicBlends()
    override val recents: Recents = SubsonicRecents()

    private val readyFlow = MutableStateFlow(true)
    override val ready: StateFlow<Boolean> = readyFlow

    /** The setup screen changed the server: the same client/player now talk to the new one. */
    fun updateConfig(config: SubsonicConfig) {
        client.updateConfig(config)
    }
}
