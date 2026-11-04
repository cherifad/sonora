// Subsonic/OpenSubsonic backend for Myspoti. GPL-3.0.
package app.myspoti.subsonic

import app.myspoti.api.Account
import app.myspoti.api.Album
import app.myspoti.api.Artist
import app.myspoti.api.ArtistPage
import app.myspoti.api.BlendGroup
import app.myspoti.api.BlendInvite
import app.myspoti.api.BlendInvitation
import app.myspoti.api.BlendStory
import app.myspoti.api.BrowsePage
import app.myspoti.api.Canvas
import app.myspoti.api.Catalog
import app.myspoti.api.Category
import app.myspoti.api.Credits
import app.myspoti.api.Device
import app.myspoti.api.Devices
import app.myspoti.api.Home
import app.myspoti.api.HomeFeed
import app.myspoti.api.HomeFilter
import app.myspoti.api.Jam
import app.myspoti.api.JamAvailability
import app.myspoti.api.JamSession
import app.myspoti.api.Library
import app.myspoti.api.LibraryFilter
import app.myspoti.api.LibraryItem
import app.myspoti.api.LibrarySort
import app.myspoti.api.Lyrics
import app.myspoti.api.MediaCard
import app.myspoti.api.MediaKind
import app.myspoti.api.Page
import app.myspoti.api.Playlist
import app.myspoti.api.PlaylistAccess
import app.myspoti.api.PlaylistMember
import app.myspoti.api.PlaylistSort
import app.myspoti.api.Playlists
import app.myspoti.api.Podcasts
import app.myspoti.api.Recents
import app.myspoti.api.RowAdder
import app.myspoti.api.SavedTrack
import app.myspoti.api.Search
import app.myspoti.api.SearchResults
import app.myspoti.api.Shelf
import app.myspoti.api.TopContent
import app.myspoti.api.Track
import app.myspoti.api.TrackExtras
import app.myspoti.api.Uri
import app.myspoti.api.UserProfile
import app.myspoti.api.UserRef
import app.myspoti.api.Users
import app.myspoti.api.sortedFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalTime
import kotlin.math.absoluteValue

internal class SubsonicHome(private val client: SubsonicClient) : Home {

    override suspend fun feed(filter: HomeFilter, itemsPerShelf: Int): HomeFeed {
        val newest = client.albums("newest", itemsPerShelf)
        val frequent = client.albums("frequent", itemsPerShelf)
        val playlists = client.playlists()
        val shelves = buildList {
            // Untitled lead shelf: the Myspoti 2-column shortcut grid. The UI prepends
            // "Liked Songs" itself; playlists then albums fill the rest.
            val shortcuts = (playlists.map { it.toCard() } + newest.map { it.toCard() } + frequent.map { it.toCard() })
                .distinctBy { it.uri }
                .take(6)
            if (shortcuts.isNotEmpty()) add(Shelf(null, null, shortcuts, shortcuts.size))
            val newItems = newest.map { it.toCard() }
            if (newItems.isNotEmpty()) add(Shelf(null, "Nouveautés", newItems, newItems.size))
            val frequentItems = frequent.map { it.toCard() }
            if (frequentItems.isNotEmpty()) add(Shelf(null, "Souvent écoutés", frequentItems, frequentItems.size))
            val playlistItems = playlists.map { it.toCard() }
            if (playlistItems.isNotEmpty()) add(Shelf(null, "Vos playlists", playlistItems, playlistItems.size))
        }
        return HomeFeed(
            greeting = greeting(),
            filters = listOf(HomeFilter.ALL, HomeFilter.MUSIC),
            shelves = shelves,
        )
    }

    override suspend fun categories(): List<Category> =
        client.genres().map { Category("spotify:genre:$it", it, emptyList(), null) }

    override suspend fun category(uri: Uri): BrowsePage =
        BrowsePage(uri, uri.substringAfterLast(':'), emptyList())

    override suspend fun whatsNew(offset: Int, limit: Int): Page<Album> =
        pageOf(client.albums("newest", 100), offset, limit)

    private fun greeting(): String = when (LocalTime.now().hour) {
        in 5..11 -> "Bonjour"
        in 12..17 -> "Bon après-midi"
        else -> "Bonsoir"
    }
}

internal class SubsonicSearch(private val client: SubsonicClient) : Search {

    override suspend fun all(query: String, limit: Int): SearchResults {
        val root = client.call(
            "search3",
            mapOf(
                "query" to query,
                "artistCount" to limit.toString(),
                "albumCount" to limit.toString(),
                "songCount" to limit.toString(),
            ),
        ).optJSONObject("searchResult3")
        val artists = root?.array("artist").orEmpty().map { client.artistOf(it) }
        val albums = root?.array("album").orEmpty().map { client.albumOf(it) }
        val tracks = root?.array("song").orEmpty().map { client.trackOf(it) }
        return SearchResults(
            tracks = pageOf(tracks, 0, tracks.size),
            artists = pageOf(artists, 0, artists.size),
            albums = pageOf(albums, 0, albums.size),
            playlists = emptyPage(),
        )
    }

    override suspend fun tracks(query: String, offset: Int, limit: Int): Page<Track> {
        val root = client.call("search3", mapOf("query" to query, "songCount" to "100"))
            .optJSONObject("searchResult3")
        return pageOf(root?.array("song").orEmpty().map { client.trackOf(it) }, offset, limit)
    }

    override suspend fun artists(query: String, offset: Int, limit: Int): Page<Artist> {
        val root = client.call("search3", mapOf("query" to query, "artistCount" to "100"))
            .optJSONObject("searchResult3")
        return pageOf(root?.array("artist").orEmpty().map { client.artistOf(it) }, offset, limit)
    }

    override suspend fun albums(query: String, offset: Int, limit: Int): Page<Album> {
        val root = client.call("search3", mapOf("query" to query, "albumCount" to "100"))
            .optJSONObject("searchResult3")
        return pageOf(root?.array("album").orEmpty().map { client.albumOf(it) }, offset, limit)
    }

    override suspend fun playlists(query: String, offset: Int, limit: Int): Page<Playlist> = emptyPage()

    override suspend fun shows(query: String, offset: Int, limit: Int): Page<MediaCard> = emptyPage()

    override suspend fun episodes(query: String, offset: Int, limit: Int): Page<app.myspoti.api.Episode> = emptyPage()

    override suspend fun audiobooks(query: String, offset: Int, limit: Int): Page<MediaCard> = emptyPage()

    override suspend fun users(query: String, offset: Int, limit: Int): Page<MediaCard> = emptyPage()

    override suspend fun genres(query: String, offset: Int, limit: Int): Page<MediaCard> =
        pageOf(
            client.genres()
                .filter { it.contains(query, ignoreCase = true) }
                .map { MediaCard("spotify:genre:$it", MediaKind.GENRE, it, null, emptyList()) },
            offset,
            limit,
        )

    override suspend fun suggestions(query: String, limit: Int): List<String> = emptyList()

    override suspend fun recent(): List<MediaCard> = emptyList()

    override suspend fun clearRecent() = Unit
}

internal class SubsonicCatalog(private val client: SubsonicClient) : Catalog {

    override suspend fun track(uri: Uri): Track {
        val id = uri.substringAfterLast(':')
        val song = client.call("getSong", mapOf("id" to id)).optJSONObject("song")
            ?: error("Song not found")
        return client.trackOf(song)
    }

    override suspend fun album(uri: Uri): Album {
        val id = uri.substringAfterLast(':')
        val album = client.call("getAlbum", mapOf("id" to id)).optJSONObject("album")
            ?: error("Album not found")
        return client.albumOf(album)
    }

    override suspend fun albumTracks(uri: Uri, offset: Int, limit: Int): Page<Track> {
        val id = uri.substringAfterLast(':')
        val album = client.call("getAlbum", mapOf("id" to id)).optJSONObject("album")
            ?: error("Album not found")
        val image = client.imageOf(album.optString("coverArt").takeIf { it.isNotBlank() }, 300)
        val name = album.optString("name")
        val tracks = album.array("song").map { client.trackOf(it, albumId = id, albumName = name, albumImages = image) }
        return pageOf(tracks, offset, limit)
    }

    override suspend fun artist(uri: Uri): Artist {
        val id = uri.substringAfterLast(':')
        val artist = client.call("getArtist", mapOf("id" to id)).optJSONObject("artist")
            ?: error("Artist not found")
        return client.artistOf(artist)
    }

    override suspend fun artistTopTracks(uri: Uri): List<Track> {
        val id = uri.substringAfterLast(':')
        val artist = client.call("getArtist", mapOf("id" to id)).optJSONObject("artist") ?: return emptyList()
        val album = artist.array("album").firstOrNull() ?: return emptyList()
        return albumTracks(
            "spotify:album:${album.optString("id")}",
            0,
            10,
        ).items
    }

    override suspend fun artistPage(uri: Uri): ArtistPage {
        val artist = artist(uri)
        val albums = discography(uri, 0, 50).items
        return ArtistPage(
            artist = artist,
            biography = null,
            headerImages = artist.images,
            topTracks = artistTopTracks(uri).map { app.myspoti.api.TopTrack(it, null) },
            popularReleases = albums,
            appearsOn = emptyList(),
            related = emptyList(),
        )
    }

    override suspend fun isrc(trackUri: Uri): String? = null

    override suspend fun discography(uri: Uri, offset: Int, limit: Int): Page<Album> {
        val id = uri.substringAfterLast(':')
        val artist = client.call("getArtist", mapOf("id" to id)).optJSONObject("artist")
        val albums = artist?.array("album").orEmpty().map { client.albumOf(it) }
        return pageOf(albums, offset, limit)
    }

    override suspend fun relatedArtists(uri: Uri): List<Artist> = emptyList()

    override suspend fun appearsOn(uri: Uri): List<Album> = emptyList()

    override suspend fun similarAlbums(trackUri: Uri, limit: Int): List<Album> = emptyList()
}

internal class SubsonicLibrary(private val client: SubsonicClient) : Library {

    private val changeFlow = MutableStateFlow(Unit)

    override suspend fun contains(uris: List<Uri>): List<Boolean> {
        val starred = client.call("getStarred2").optJSONObject("starred2")
        val ids = buildSet {
            starred?.array("song").orEmpty().forEach { add(it.optString("id")) }
            starred?.array("album").orEmpty().forEach { add(it.optString("id")) }
            starred?.array("artist").orEmpty().forEach { add(it.optString("id")) }
        }
        return uris.map { it.substringAfterLast(':') in ids }
    }

    override suspend fun add(uris: List<Uri>) {
        uris.forEach { star(it, true) }
    }

    override suspend fun remove(uris: List<Uri>) {
        uris.forEach { star(it, false) }
    }

    private suspend fun star(uri: Uri, on: Boolean) {
        val id = uri.substringAfterLast(':')
        val method = if (on) "star" else "unstar"
        val params = when {
            uri.startsWith("spotify:album:") -> mapOf("albumId" to id)
            uri.startsWith("spotify:artist:") -> mapOf("artistId" to id)
            else -> mapOf("id" to id)
        }
        runCatching { client.call(method, params) }
    }

    override suspend fun items(
        filter: LibraryFilter,
        sort: LibrarySort,
        folderUri: Uri?,
        offset: Int,
        limit: Int,
    ): Page<LibraryItem> {
        val items = when (filter) {
            LibraryFilter.ALBUMS -> client.albums("alphabeticalByName", 100).map { albumItem(it) }
            LibraryFilter.ARTISTS -> client.artists().map { artistItem(it) }
            LibraryFilter.PLAYLISTS -> client.playlists().map { playlistItem(it) }
            else -> buildList {
                addAll(client.playlists().map { playlistItem(it) })
                addAll(client.albums("alphabeticalByName", 100).map { albumItem(it) })
            }
        }.sortedBy { it.name.lowercase() }
        return pageOf(items, offset, limit)
    }

    override suspend fun likedTracks(offset: Int, limit: Int): Page<SavedTrack> {
        val songs = client.call("getStarred2").optJSONObject("starred2")?.array("song").orEmpty()
        return pageOf(songs.map { SavedTrack(client.trackOf(it), null) }, offset, limit)
    }

    override suspend fun pin(uri: Uri) = Unit

    override suspend fun unpin(uri: Uri) = Unit

    override suspend fun createFolder(name: String, parentFolderUri: Uri?): Uri = ""

    override suspend fun renameFolder(folderUri: Uri, name: String) = Unit

    override suspend fun deleteFolder(folderUri: Uri) = Unit

    override suspend fun moveToFolder(uri: Uri, folderUri: Uri?) = Unit

    override val changes: Flow<Unit> = changeFlow

    private fun albumItem(album: Album) = LibraryItem(
        uri = album.uri,
        kind = MediaKind.ALBUM,
        name = album.name,
        subtitle = album.artists.joinToString(", ") { it.name },
        images = album.images,
        pinned = false,
        addedAt = null,
    )

    private fun artistItem(artist: Artist) = LibraryItem(
        uri = artist.uri,
        kind = MediaKind.ARTIST,
        name = artist.name,
        subtitle = null,
        images = artist.images,
        pinned = false,
        addedAt = null,
    )

    private fun playlistItem(playlist: Playlist) = LibraryItem(
        uri = playlist.uri,
        kind = MediaKind.PLAYLIST,
        name = playlist.name,
        subtitle = playlist.owner,
        images = playlist.images,
        pinned = false,
        addedAt = null,
    )
}

internal class SubsonicPlaylists(private val client: SubsonicClient) : Playlists {

    override suspend fun playlist(uri: Uri): Playlist {
        val id = uri.substringAfterLast(':')
        return client.playlists().firstOrNull { it.uri.endsWith(":$id") } ?: error("Playlist not found")
    }

    override suspend fun tracks(uri: Uri, offset: Int, limit: Int): Page<Track> {
        val id = uri.substringAfterLast(':')
        val playlist = client.call("getPlaylist", mapOf("id" to id)).optJSONObject("playlist")
            ?: error("Playlist not found")
        val image = client.imageOf(playlist.optString("coverArt").takeIf { it.isNotBlank() }, 300)
        val name = playlist.optString("name")
        val tracks = playlist.array("entry").map { client.trackOf(it, albumName = name, albumImages = image) }
        return pageOf(tracks, offset, limit)
    }

    override suspend fun sortedTracks(uri: Uri, sort: PlaylistSort, descending: Boolean): List<Track> =
        tracks(uri, 0, 500).items.sortedFor(sort, descending)

    override suspend fun mine(): List<Playlist> = client.playlists()

    override suspend fun editable(): List<Playlist> = client.playlists()

    override suspend fun create(name: String, folderUri: Uri?): Uri = error("Not supported in the Subsonic spike")

    override suspend fun rename(uri: Uri, name: String) = Unit

    override suspend fun setDescription(uri: Uri, description: String) = Unit

    override suspend fun access(uri: Uri): PlaylistAccess = PlaylistAccess.PUBLIC

    override suspend fun setAccess(uri: Uri, access: PlaylistAccess) = Unit

    override suspend fun published(uri: Uri): Boolean = false

    override suspend fun setPublished(uri: Uri, published: Boolean) = Unit

    override suspend fun setCover(uri: Uri, jpeg: ByteArray?) = Unit

    override suspend fun delete(uri: Uri) = Unit

    override suspend fun follow(uri: Uri) = Unit

    override suspend fun unfollow(uri: Uri) = Unit

    override suspend fun join(uri: Uri, invitationToken: String): PlaylistAccess = PlaylistAccess.PUBLIC

    override suspend fun collaboratorInvite(uri: Uri): String = ""

    override suspend fun members(uri: Uri): List<PlaylistMember> = emptyList()

    override suspend fun leave(uri: Uri) = Unit

    override suspend fun setMemberCollaborator(uri: Uri, username: String, collaborator: Boolean) = Unit

    override suspend fun removeMember(uri: Uri, username: String) = Unit

    override suspend fun stopCollaboration(uri: Uri) = Unit

    override suspend fun addedBy(uri: Uri): List<RowAdder> = emptyList()

    override suspend fun addTracks(uri: Uri, trackUris: List<Uri>) = Unit

    override suspend fun removeTracks(uri: Uri, uids: List<String>) = Unit

    override suspend fun moveTracks(uri: Uri, uids: List<String>, beforeUid: String?) = Unit

    override suspend fun recommended(uri: Uri, limit: Int, skip: List<Uri>): List<Track> = emptyList()
}

internal class SubsonicUsers(private val config: SubsonicConfig) : Users {

    override suspend fun me(): Account = Account(
        uri = "spotify:user:${config.username}",
        username = config.username,
        name = config.username,
        images = emptyList(),
        premium = false,
        country = null,
    )

    override suspend fun profile(userUri: Uri): UserProfile = UserProfile(
        uri = userUri,
        name = config.username,
        images = emptyList(),
        followers = null,
        following = null,
        publicPlaylists = emptyList(),
    )

    override suspend fun followers(userUri: Uri): List<UserRef> = emptyList()

    override suspend fun following(userUri: Uri): List<UserRef> = emptyList()

    override suspend fun top(range: app.myspoti.api.TimeRange, limit: Int): TopContent =
        TopContent(artists = emptyList(), tracks = emptyList())

    override suspend fun follow(userUris: List<Uri>) = Unit

    override suspend fun unfollow(userUris: List<Uri>) = Unit

    override suspend fun isFollowing(userUris: List<Uri>): List<Boolean> = userUris.map { false }

    override suspend fun isBlocked(userUri: Uri): Boolean = false

    override suspend fun blockUser(userUri: Uri) = Unit

    override suspend fun unblockUser(userUri: Uri) = Unit

    override suspend fun friendActivity(): List<app.myspoti.api.FriendActivity> = emptyList()

    override suspend fun history(): List<app.myspoti.api.HistoryEntry> = emptyList()

    override suspend fun setPrivateSession(on: Boolean) = Unit
}

// --- Not part of the Home/album/play spike -----------------------------------

internal class SubsonicPodcasts : Podcasts {
    override suspend fun show(uri: Uri): app.myspoti.api.Show = error("Podcasts are not supported yet")
    override suspend fun audioUrl(uri: Uri): app.myspoti.api.EpisodeAudio = error("Podcasts are not supported yet")
    override suspend fun episodes(uri: Uri, offset: Int, limit: Int): Page<app.myspoti.api.Episode> = emptyPage()
    override suspend fun episode(uri: Uri): app.myspoti.api.Episode = error("Podcasts are not supported yet")
    override suspend fun audiobook(uri: Uri): app.myspoti.api.Audiobook = error("Audiobooks are not supported yet")
    override suspend fun chapters(uri: Uri, offset: Int, limit: Int): Page<app.myspoti.api.Episode> = emptyPage()
    override suspend fun savedEpisodes(offset: Int, limit: Int): Page<app.myspoti.api.Episode> = emptyPage()
    override suspend fun setEpisodeSaved(uri: Uri, saved: Boolean) = Unit
}

internal class SubsonicExtras : TrackExtras {
    override suspend fun lyrics(trackUri: Uri): Lyrics? = null
    override suspend fun canvas(trackUri: Uri): Canvas? = null
    override suspend fun credits(trackUri: Uri): Credits = Credits(emptyList())
    override suspend fun radio(seedUri: Uri): Uri? = null
    override suspend fun setExcludedFromTaste(trackUri: Uri, excluded: Boolean) = Unit
    override suspend fun isExcludedFromTaste(trackUris: List<Uri>): List<Boolean> = trackUris.map { false }
    override suspend fun setArtistBanned(artistUri: Uri, banned: Boolean) = Unit
    override suspend fun isArtistBanned(artistUris: List<Uri>): List<Boolean> = artistUris.map { false }
}

internal class SubsonicDevices : Devices {
    override val devices = MutableStateFlow<List<Device>>(emptyList())
    override suspend fun refresh() = Unit
    override suspend fun transfer(deviceId: String, play: Boolean) = Unit
    override suspend fun pullHere() = Unit
}

internal class SubsonicJam : Jam {
    override suspend fun availability(): JamAvailability = JamAvailability.UNKNOWN
    override val session = MutableStateFlow<JamSession?>(null)
    override suspend fun refresh() = Unit
    override suspend fun start(): JamSession = error("Jam is not supported")
    override suspend fun join(joinToken: String): JamSession = error("Jam is not supported")
    override suspend fun leave() = Unit
    override suspend fun end() = Unit
    override suspend fun inviteLink(): String? = null
    override suspend fun removeMember(userUri: Uri) = Unit
    override suspend fun setQueueOnly(on: Boolean) = Unit
    override suspend fun setParticipantVolumeControl(on: Boolean) = Unit
}

internal class SubsonicBlends : app.myspoti.api.Blends {
    override suspend fun invite(): BlendInvite = error("Blends are not supported")
    override suspend fun invitation(linkOrId: String): BlendInvitation = error("Blends are not supported")
    override suspend fun join(linkOrId: String): Uri = error("Blends are not supported")
    override suspend fun group(playlist: Uri): BlendGroup = error("Blends are not supported")
    override suspend fun story(playlist: Uri): BlendStory? = null
    override suspend fun rename(playlist: Uri, name: String) = Unit
    override suspend fun leave(playlist: Uri) = Unit
}

internal class SubsonicRecents : Recents {
    override suspend fun recentEntries(offset: Int, limit: Int): Page<app.myspoti.api.RecentEntry> = emptyPage()
}

private suspend fun SubsonicClient.albums(type: String, size: Int): List<Album> {
    val root = call("getAlbumList2", mapOf("type" to type, "size" to size.toString()))
    return root.optJSONObject("albumList2")?.array("album").orEmpty().map { albumOf(it) }
}

private suspend fun SubsonicClient.artists(): List<Artist> {
    val root = call("getArtists")
    return root.optJSONObject("artists")?.array("index").orEmpty()
        .flatMap { it.array("artist") }
        .map { artistOf(it) }
}

private suspend fun SubsonicClient.playlists(): List<Playlist> {
    val root = call("getPlaylists")
    return root.optJSONObject("playlists")?.array("playlist").orEmpty().map { playlistOf(it) }
}

private suspend fun SubsonicClient.genres(): List<String> {
    val root = call("getGenres")
    return root.optJSONObject("genres")?.array("genre").orEmpty()
        .mapNotNull { it.optString("value").takeIf { value -> value.isNotBlank() } }
}
