// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

import android.content.Context
import com.adlen.sonora.api.Account
import com.adlen.sonora.api.Album
import com.adlen.sonora.api.Artist
import com.adlen.sonora.api.ArtistPage
import com.adlen.sonora.api.BlendGroup
import com.adlen.sonora.api.BlendInvite
import com.adlen.sonora.api.BlendInvitation
import com.adlen.sonora.api.BlendStory
import com.adlen.sonora.api.BrowsePage
import com.adlen.sonora.api.Canvas
import com.adlen.sonora.api.Catalog
import com.adlen.sonora.api.Category
import com.adlen.sonora.api.Credits
import com.adlen.sonora.api.Device
import com.adlen.sonora.api.Devices
import com.adlen.sonora.api.Home
import com.adlen.sonora.api.HomeFeed
import com.adlen.sonora.api.HomeFilter
import com.adlen.sonora.api.Image
import com.adlen.sonora.api.Jam
import com.adlen.sonora.api.JamAvailability
import com.adlen.sonora.api.JamSession
import com.adlen.sonora.api.Library
import com.adlen.sonora.api.LibraryFilter
import com.adlen.sonora.api.LibraryItem
import com.adlen.sonora.api.LibrarySort
import com.adlen.sonora.api.Lyrics
import com.adlen.sonora.api.LyricsLine
import com.adlen.sonora.api.MediaCard
import com.adlen.sonora.api.MediaKind
import com.adlen.sonora.api.Page
import com.adlen.sonora.api.Playlist
import com.adlen.sonora.api.PlaylistAccess
import com.adlen.sonora.api.PlaylistMember
import com.adlen.sonora.api.PlaylistSort
import com.adlen.sonora.api.Playlists
import com.adlen.sonora.api.Podcasts
import com.adlen.sonora.api.RecentEntry
import com.adlen.sonora.api.RecentItem
import com.adlen.sonora.api.Recents
import com.adlen.sonora.api.RowAdder
import com.adlen.sonora.api.SavedTrack
import com.adlen.sonora.api.Search
import com.adlen.sonora.api.SearchResults
import com.adlen.sonora.api.Shelf
import com.adlen.sonora.api.TopContent
import com.adlen.sonora.api.Track
import com.adlen.sonora.api.TrackExtras
import com.adlen.sonora.api.Uri
import com.adlen.sonora.api.UserProfile
import com.adlen.sonora.api.UserRef
import com.adlen.sonora.api.Users
import com.adlen.sonora.api.sortedFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.time.LocalTime

/** How long a full-list endpoint (artists, playlists, starred) is trusted before re-asking. */
private const val CACHE_MS = 30_000L

internal class SubsonicHome(
    private val client: SubsonicClient,
    /** The app's context: the shelves and the greeting are words, not server data. */
    context: Context,
) : Home {

    private val res = context.applicationContext.resources

    override suspend fun feed(filter: HomeFilter, itemsPerShelf: Int): HomeFeed {
        val newest = client.albums("newest", itemsPerShelf)
        val frequent = client.albums("frequent", itemsPerShelf)
        val recent = client.albums("recent", itemsPerShelf)
        val random = client.randomSongs(itemsPerShelf)
        val playlists = client.playlists()
        val shelves = buildList {
            // Untitled lead shelf: the Sonora 2-column shortcut grid. The UI prepends
            // "Liked Songs" itself; playlists then albums fill the rest.
            val shortcuts = (playlists.map { it.toCard() } + newest.map { it.toCard() } + frequent.map { it.toCard() })
                .distinctBy { it.uri }
                .take(6)
            if (shortcuts.isNotEmpty()) add(Shelf(null, null, shortcuts, shortcuts.size))
            val forYou = random.map { it.toCard() }
            if (forYou.isNotEmpty()) add(Shelf(null, res.getString(R.string.home_for_you), forYou, forYou.size))
            val newItems = newest.map { it.toCard() }
            if (newItems.isNotEmpty()) add(Shelf(null, res.getString(R.string.home_new_releases), newItems, newItems.size))
            val recentItems = recent.map { it.toCard() }
            if (recentItems.isNotEmpty()) add(Shelf(null, res.getString(R.string.home_recently_played), recentItems, recentItems.size))
            val frequentItems = frequent.map { it.toCard() }
            if (frequentItems.isNotEmpty()) add(Shelf(null, res.getString(R.string.home_frequently_played), frequentItems, frequentItems.size))
            val playlistItems = playlists.map { it.toCard() }
            if (playlistItems.isNotEmpty()) add(Shelf(null, res.getString(R.string.home_your_playlists), playlistItems, playlistItems.size))
        }
        return HomeFeed(
            greeting = greeting(),
            filters = listOf(HomeFilter.ALL, HomeFilter.MUSIC),
            shelves = shelves,
        )
    }

    override suspend fun categories(): List<Category> =
        client.genres().map { Category("spotify:genre:$it", it, emptyList(), null) }

    override suspend fun category(uri: Uri): BrowsePage {
        val genre = uri.substringAfterLast(':')
        val albums = client.genreAlbums(genre, 50)
        val songs = client.genreSongs(genre, 50)
        val shelves = buildList {
            if (albums.isNotEmpty()) {
                add(Shelf(null, res.getString(R.string.genre_albums), albums.map { it.toCard() }, albums.size))
            }
            if (songs.isNotEmpty()) {
                add(Shelf(null, res.getString(R.string.genre_songs), songs.map { it.toCard() }, songs.size))
            }
        }
        return BrowsePage(uri, genre, shelves)
    }

    override suspend fun whatsNew(offset: Int, limit: Int): Page<Album> =
        serverPage(client.albums("newest", limit, offset), offset, limit)

    private fun greeting(): String = when (LocalTime.now().hour) {
        in 5..11 -> res.getString(R.string.home_greeting_morning)
        in 12..17 -> res.getString(R.string.home_greeting_afternoon)
        else -> res.getString(R.string.home_greeting_evening)
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
            tracks = serverPage(tracks, 0, limit),
            artists = serverPage(artists, 0, limit),
            albums = serverPage(albums, 0, limit),
            playlists = playlists(query, 0, limit),
        )
    }

    override suspend fun tracks(query: String, offset: Int, limit: Int): Page<Track> {
        val root = client.call(
            "search3",
            mapOf("query" to query, "songCount" to limit.toString(), "songOffset" to offset.toString()),
        ).optJSONObject("searchResult3")
        return serverPage(root?.array("song").orEmpty().map { client.trackOf(it) }, offset, limit)
    }

    override suspend fun artists(query: String, offset: Int, limit: Int): Page<Artist> {
        val root = client.call(
            "search3",
            mapOf("query" to query, "artistCount" to limit.toString(), "artistOffset" to offset.toString()),
        ).optJSONObject("searchResult3")
        return serverPage(root?.array("artist").orEmpty().map { client.artistOf(it) }, offset, limit)
    }

    override suspend fun albums(query: String, offset: Int, limit: Int): Page<Album> {
        val root = client.call(
            "search3",
            mapOf("query" to query, "albumCount" to limit.toString(), "albumOffset" to offset.toString()),
        ).optJSONObject("searchResult3")
        return serverPage(root?.array("album").orEmpty().map { client.albumOf(it) }, offset, limit)
    }

    /**
     * `search3` has no playlist result. The list is small enough to fetch and filter locally,
     * and the UI's Playlists tab is then not a permanent empty page.
     */
    override suspend fun playlists(query: String, offset: Int, limit: Int): Page<Playlist> {
        if (query.isBlank()) return emptyPage()
        val all = client.playlists().filter { it.name.contains(query, ignoreCase = true) }
        return pageOf(all, offset, limit)
    }

    override suspend fun shows(query: String, offset: Int, limit: Int): Page<MediaCard> = emptyPage()

    override suspend fun episodes(query: String, offset: Int, limit: Int): Page<com.adlen.sonora.api.Episode> = emptyPage()

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
        val artistJson = client.call("getArtist", mapOf("id" to id)).optJSONObject("artist") ?: return emptyList()
        val artistName = artistJson.optString("name").takeIf { it.isNotBlank() }
        if (artistName != null) {
            val top = client.callOptional("getTopSongs", mapOf("artist" to artistName, "count" to "10"))
                ?.optJSONObject("topSongs")?.array("song").orEmpty()
                .map { client.trackOf(it) }
            if (top.isNotEmpty()) return top
        }
        val album = artistJson.array("album").firstOrNull() ?: return emptyList()
        return albumTracks(
            "spotify:album:${album.optString("id")}",
            0,
            10,
        ).items
    }

    override suspend fun artistPage(uri: Uri): ArtistPage {
        val id = uri.substringAfterLast(':')
        val artist = artist(uri)
        val albums = discography(uri, 0, 50).items
        val info = client.callOptional("getArtistInfo2", mapOf("id" to id))?.optJSONObject("artistInfo2")
        val bio = info?.optString("biography")?.takeIf { it.isNotBlank() }
        val infoImages = listOfNotNull(
            info?.optString("largeImageUrl")?.takeIf { it.isNotBlank() }?.let { Image(it) },
            info?.optString("mediumImageUrl")?.takeIf { it.isNotBlank() }?.let { Image(it) },
            info?.optString("smallImageUrl")?.takeIf { it.isNotBlank() }?.let { Image(it) },
        )
        val related = info?.array("similarArtist").orEmpty().map { client.artistOf(it) }
        return ArtistPage(
            artist = if (artist.images.isEmpty() && infoImages.isNotEmpty()) artist.copy(images = infoImages) else artist,
            biography = bio,
            headerImages = infoImages.ifEmpty { artist.images },
            topTracks = artistTopTracks(uri).map { com.adlen.sonora.api.TopTrack(it, null) },
            popularReleases = albums,
            appearsOn = emptyList(),
            related = related,
        )
    }

    override suspend fun isrc(trackUri: Uri): String? = null

    override suspend fun discography(uri: Uri, offset: Int, limit: Int): Page<Album> {
        val id = uri.substringAfterLast(':')
        val artist = client.call("getArtist", mapOf("id" to id)).optJSONObject("artist")
        val albums = artist?.array("album").orEmpty().map { client.albumOf(it) }
        return pageOf(albums, offset, limit)
    }

    override suspend fun relatedArtists(uri: Uri): List<Artist> {
        val id = uri.substringAfterLast(':')
        val info = client.callOptional("getArtistInfo2", mapOf("id" to id))?.optJSONObject("artistInfo2")
        return info?.array("similarArtist").orEmpty().map { client.artistOf(it) }
    }

    override suspend fun appearsOn(uri: Uri): List<Album> = emptyList()

    override suspend fun similarAlbums(trackUri: Uri, limit: Int): List<Album> {
        val id = trackUri.substringAfterLast(':').substringBefore('|')
        if (id.isBlank()) return emptyList()
        val root = client.callOptional(
            "getSimilarSongs2",
            mapOf("id" to id, "count" to (limit * 3).coerceAtMost(100).toString()),
        ) ?: return emptyList()
        return root.optJSONObject("similarSongs2")?.array("song").orEmpty()
            .mapNotNull { song ->
                val albumId = song.optString("albumId").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                // The similar-song row already carries the album fields: no getAlbum per row.
                client.albumOf(
                    JSONObject().apply {
                        put("id", albumId)
                        put("name", song.optString("album"))
                        put("artist", song.optString("artist"))
                        put("artistId", song.optString("artistId"))
                        put("coverArt", song.optString("coverArt"))
                    },
                )
            }
            .distinctBy { it.uri }
            .take(limit)
    }
}

internal class SubsonicLibrary(private val client: SubsonicClient) : Library {

    private val changeFlow = MutableStateFlow(Unit)
    private val cacheLock = Any()
    private var artistsCache: Pair<Long, List<Artist>>? = null
    private var playlistsCache: Pair<Long, List<Playlist>>? = null
    private var starredCache: Pair<Long, StarredSnapshot>? = null

    private data class StarredSnapshot(val ids: Set<String>, val songs: List<SavedTrack>)

    override suspend fun contains(uris: List<Uri>): List<Boolean> {
        val ids = starred().ids
        return uris.map { it.substringAfterLast(':') in ids }
    }

    override suspend fun add(uris: List<Uri>) {
        uris.forEach { star(it, true) }
        invalidateStarred()
        changeFlow.tryEmit(Unit)
    }

    override suspend fun remove(uris: List<Uri>) {
        uris.forEach { star(it, false) }
        invalidateStarred()
        changeFlow.tryEmit(Unit)
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
    ): Page<LibraryItem> = when (filter) {
        // The server sorts and slices albums: no 100-item ceiling, no client re-sort.
        LibraryFilter.ALBUMS -> {
            val page = client.albums("alphabeticalByName", limit, offset).map { albumItem(it) }
            serverPage(page, offset, limit)
        }
        LibraryFilter.ARTISTS -> pageOf(artistsCached().map { artistItem(it) }, offset, limit)
        LibraryFilter.PLAYLISTS -> pageOf(playlistsCached().map { playlistItem(it) }, offset, limit)
        else -> {
            // Playlists first, then albums, so a page can cross the boundary.
            val playlists = playlistsCached().map { playlistItem(it) }
            val fromPlaylists = if (offset < playlists.size) playlists.drop(offset).take(limit) else emptyList()
            val albumOffset = (offset - playlists.size).coerceAtLeast(0)
            val albumLimit = limit - fromPlaylists.size
            val albums = if (albumLimit > 0) {
                client.albums("alphabeticalByName", albumLimit, albumOffset).map { albumItem(it) }
            } else {
                emptyList()
            }
            serverPage(fromPlaylists + albums, offset, limit)
        }
    }

    override suspend fun likedTracks(offset: Int, limit: Int): Page<SavedTrack> =
        pageOf(starred().songs, offset, limit)

    private suspend fun artistsCached(): List<Artist> {
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            artistsCache?.let { (at, value) -> if (now - at < CACHE_MS) return value }
        }
        val value = client.artists()
        synchronized(cacheLock) { artistsCache = now to value }
        return value
    }

    private suspend fun playlistsCached(): List<Playlist> {
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            playlistsCache?.let { (at, value) -> if (now - at < CACHE_MS) return value }
        }
        val value = client.playlists()
        synchronized(cacheLock) { playlistsCache = now to value }
        return value
    }

    private suspend fun starred(): StarredSnapshot {
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            starredCache?.let { (at, value) -> if (now - at < CACHE_MS) return value }
        }
        val root = client.call("getStarred2").optJSONObject("starred2")
        val ids = buildSet {
            root?.array("song").orEmpty().forEach { add(it.optString("id")) }
            root?.array("album").orEmpty().forEach { add(it.optString("id")) }
            root?.array("artist").orEmpty().forEach { add(it.optString("id")) }
        }
        val songs = root?.array("song").orEmpty().map { SavedTrack(client.trackOf(it), null) }
        val snapshot = StarredSnapshot(ids, songs)
        synchronized(cacheLock) { starredCache = now to snapshot }
        return snapshot
    }

    private fun invalidateStarred() {
        synchronized(cacheLock) { starredCache = null }
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

    /** `getPlaylist` has no offset/size: fetch it once, then page it locally. */
    private val playlistCache = HashMap<String, Pair<Long, JSONObject>>()

    private suspend fun playlistJson(id: String): JSONObject {
        val now = System.currentTimeMillis()
        synchronized(playlistCache) {
            playlistCache[id]?.let { (at, value) -> if (now - at < CACHE_MS) return value }
        }
        val value = client.call("getPlaylist", mapOf("id" to id)).optJSONObject("playlist")
            ?: throw SubsonicException.NotFound("Playlist not found")
        synchronized(playlistCache) { playlistCache[id] = now to value }
        return value
    }

    private fun invalidatePlaylist(id: String) {
        synchronized(playlistCache) { playlistCache.remove(id) }
    }

    override suspend fun playlist(uri: Uri): Playlist {
        val id = uri.substringAfterLast(':')
        return client.playlistOf(playlistJson(id))
    }

    override suspend fun tracks(uri: Uri, offset: Int, limit: Int): Page<Track> {
        val id = uri.substringAfterLast(':')
        val playlist = playlistJson(id)
        val image = client.imageOf(playlist.optString("coverArt").takeIf { it.isNotBlank() }, 300)
        val name = playlist.optString("name")
        val tracks = playlist.array("entry").map { client.trackOf(it, albumName = name, albumImages = image) }
        return pageOf(tracks, offset, limit)
    }

    override suspend fun sortedTracks(uri: Uri, sort: PlaylistSort, descending: Boolean): List<Track> =
        tracks(uri, 0, Int.MAX_VALUE).items.sortedFor(sort, descending)

    override suspend fun mine(): List<Playlist> = client.playlists()

    override suspend fun editable(): List<Playlist> = client.playlists()

    override suspend fun create(name: String, folderUri: Uri?): Uri {
        val res = client.call("createPlaylist", mapOf("name" to name))
        val createdId = res.optJSONObject("playlist")?.optString("id")?.takeIf { it.isNotBlank() }
        if (createdId != null) return "spotify:playlist:$createdId"
        val found = client.playlists().lastOrNull { it.name == name }?.uri
        return found ?: error("Playlist created but id not returned")
    }

    override suspend fun rename(uri: Uri, name: String) {
        val id = uri.substringAfterLast(':')
        client.call("updatePlaylist", mapOf("playlistId" to id, "name" to name))
        invalidatePlaylist(id)
    }

    override suspend fun setDescription(uri: Uri, description: String) {
        val id = uri.substringAfterLast(':')
        client.call("updatePlaylist", mapOf("playlistId" to id, "comment" to description))
        invalidatePlaylist(id)
    }

    override suspend fun access(uri: Uri): PlaylistAccess = PlaylistAccess.PUBLIC

    override suspend fun setAccess(uri: Uri, access: PlaylistAccess) {
        val id = uri.substringAfterLast(':')
        val pub = access == PlaylistAccess.PUBLIC
        runCatching { client.call("updatePlaylist", mapOf("playlistId" to id, "public" to pub.toString())) }
        invalidatePlaylist(id)
    }

    override suspend fun published(uri: Uri): Boolean = false

    override suspend fun setPublished(uri: Uri, published: Boolean) = Unit

    override suspend fun setCover(uri: Uri, jpeg: ByteArray?) = Unit

    override suspend fun delete(uri: Uri) {
        val id = uri.substringAfterLast(':')
        client.call("deletePlaylist", mapOf("id" to id))
        invalidatePlaylist(id)
    }

    override suspend fun follow(uri: Uri) = Unit

    override suspend fun unfollow(uri: Uri) = delete(uri)

    override suspend fun join(uri: Uri, invitationToken: String): PlaylistAccess = PlaylistAccess.PUBLIC

    override suspend fun collaboratorInvite(uri: Uri): String = ""

    override suspend fun members(uri: Uri): List<PlaylistMember> = emptyList()

    override suspend fun leave(uri: Uri) = Unit

    override suspend fun setMemberCollaborator(uri: Uri, username: String, collaborator: Boolean) = Unit

    override suspend fun removeMember(uri: Uri, username: String) = Unit

    override suspend fun stopCollaboration(uri: Uri) = Unit

    override suspend fun addedBy(uri: Uri): List<RowAdder> = emptyList()

    override suspend fun addTracks(uri: Uri, trackUris: List<Uri>) {
        val id = uri.substringAfterLast(':')
        val pairs = buildList {
            add("playlistId" to id)
            trackUris.forEach { trackUri ->
                val songId = trackUri.substringAfterLast(':').substringBefore('|')
                if (songId.isNotBlank()) add("songIdToAdd" to songId)
            }
        }
        if (pairs.size > 1) {
            client.callPairs("updatePlaylist", pairs)
            invalidatePlaylist(id)
        }
    }

    override suspend fun removeTracks(uri: Uri, uids: List<String>) {
        val id = uri.substringAfterLast(':')
        val playlist = playlistJson(id)
        val entries = playlist.array("entry")
        val indices = entries.mapIndexedNotNull { idx, entry ->
            val entryId = entry.optString("id")
            if (entryId in uids || idx.toString() in uids) idx else null
        }.sortedDescending()
        if (indices.isEmpty()) return
        val pairs = buildList {
            add("playlistId" to id)
            indices.forEach { idx -> add("songIndexToRemove" to idx.toString()) }
        }
        client.callPairs("updatePlaylist", pairs)
        invalidatePlaylist(id)
    }

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

    override suspend fun top(range: com.adlen.sonora.api.TimeRange, limit: Int): TopContent =
        TopContent(artists = emptyList(), tracks = emptyList())

    override suspend fun follow(userUris: List<Uri>) = Unit

    override suspend fun unfollow(userUris: List<Uri>) = Unit

    override suspend fun isFollowing(userUris: List<Uri>): List<Boolean> = userUris.map { false }

    override suspend fun isBlocked(userUri: Uri): Boolean = false

    override suspend fun blockUser(userUri: Uri) = Unit

    override suspend fun unblockUser(userUri: Uri) = Unit

    override suspend fun friendActivity(): List<com.adlen.sonora.api.FriendActivity> = emptyList()

    override suspend fun history(): List<com.adlen.sonora.api.HistoryEntry> = emptyList()

    override suspend fun setPrivateSession(on: Boolean) = Unit
}

// --- Extras, Recents and unsupported Spotify-only surfaces -------------------

internal class SubsonicPodcasts : Podcasts {
    override suspend fun show(uri: Uri): com.adlen.sonora.api.Show = throw SubsonicException.Unsupported("podcasts")
    override suspend fun audioUrl(uri: Uri): com.adlen.sonora.api.EpisodeAudio = throw SubsonicException.Unsupported("podcasts")
    override suspend fun episodes(uri: Uri, offset: Int, limit: Int): Page<com.adlen.sonora.api.Episode> = emptyPage()
    override suspend fun episode(uri: Uri): com.adlen.sonora.api.Episode = throw SubsonicException.Unsupported("podcasts")
    override suspend fun audiobook(uri: Uri): com.adlen.sonora.api.Audiobook = throw SubsonicException.Unsupported("audiobooks")
    override suspend fun chapters(uri: Uri, offset: Int, limit: Int): Page<com.adlen.sonora.api.Episode> = emptyPage()
    override suspend fun savedEpisodes(offset: Int, limit: Int): Page<com.adlen.sonora.api.Episode> = emptyPage()
    override suspend fun setEpisodeSaved(uri: Uri, saved: Boolean) = Unit
}

internal class SubsonicExtras(
    private val client: SubsonicClient,
    private val capabilities: () -> ServerCapabilities? = { client.capabilities },
) : TrackExtras {
    override suspend fun lyrics(trackUri: Uri): Lyrics? {
        val id = trackUri.substringAfterLast(':').substringBefore('|')
        if (id.isBlank()) return null

        // 1. OpenSubsonic getLyricsBySongId (supports synced lines), when the server advertises it
        val structured = if (capabilities()?.supports(ServerCapabilities.Feature.SONG_LYRICS) != false) {
            client.callOptional("getLyricsBySongId", mapOf("id" to id))
                ?.optJSONObject("lyricsList")?.array("structuredLyrics").orEmpty()
                .firstOrNull { it.array("line").isNotEmpty() }
        } else {
            null
        }
        if (structured != null) {
            val synced = structured.optBoolean("synced", false)
            val lines = structured.array("line").map { line ->
                LyricsLine(
                    startMs = line.optLong("start", 0L),
                    endMs = null,
                    text = line.optString("value"),
                )
            }
            if (lines.isNotEmpty()) {
                return Lyrics(
                    lines = lines,
                    synced = synced,
                    language = structured.optString("lang").takeIf { it.isNotBlank() },
                    provider = "OpenSubsonic",
                    background = null,
                    text = null,
                    highlight = null,
                )
            }
        }

        // 2. Classic Subsonic getLyrics (plain text by artist + title)
        val song = client.callOptional("getSong", mapOf("id" to id))?.optJSONObject("song")
        val artist = song?.optString("artist")?.takeIf { it.isNotBlank() }
        val title = song?.optString("title")?.takeIf { it.isNotBlank() }
        if (artist != null || title != null) {
            val params = buildMap {
                if (artist != null) put("artist", artist)
                if (title != null) put("title", title)
            }
            val plain = client.callOptional("getLyrics", params)
                ?.optJSONObject("lyrics")?.optString("value")?.takeIf { it.isNotBlank() }
            if (plain != null) {
                val lines = plain.lines().map { LyricsLine(startMs = 0L, endMs = null, text = it) }
                return Lyrics(
                    lines = lines,
                    synced = false,
                    language = null,
                    provider = "Subsonic",
                    background = null,
                    text = null,
                    highlight = null,
                )
            }
        }
        return null
    }

    override suspend fun setRating(trackUri: Uri, rating: Int) {
        if (capabilities()?.supports(ServerCapabilities.Feature.RATINGS) == false) return
        val id = trackUri.substringAfterLast(':').substringBefore('|')
        if (id.isBlank()) return
        client.call("setRating", mapOf("id" to id, "rating" to rating.coerceIn(0, 5).toString()))
    }

    override suspend fun similarTracks(seedUri: Uri, limit: Int): List<Track> {
        if (capabilities()?.supports(ServerCapabilities.Feature.SIMILAR_SONGS) == false) return emptyList()
        val seedId = seedUri.substringAfterLast(':').substringBefore('|')
        if (seedId.isBlank()) return emptyList()
        // getSimilarSongs2 wants a song id; an album or artist seed uses its first song.
        val songId = when {
            seedUri.startsWith("spotify:album:") -> firstTrackIdOfAlbum(seedId)
            seedUri.startsWith("spotify:artist:") -> firstTopSongIdOfArtist(seedId)
            else -> seedId
        } ?: return emptyList()
        val root = client.callOptional(
            "getSimilarSongs2",
            mapOf("id" to songId, "count" to limit.coerceIn(1, 100).toString()),
        ) ?: return emptyList()
        val tracks = root.optJSONObject("similarSongs2")?.array("song").orEmpty().map { client.trackOf(it) }
        // A track radio never starts with the song it came from.
        return if (seedUri.startsWith("spotify:track:")) tracks.filter { it.uri != seedUri } else tracks
    }

    private suspend fun firstTrackIdOfAlbum(albumId: String): String? =
        client.callOptional("getAlbum", mapOf("id" to albumId))
            ?.optJSONObject("album")?.array("song")?.firstOrNull()
            ?.optString("id")?.takeIf { it.isNotBlank() }

    private suspend fun firstTopSongIdOfArtist(artistId: String): String? {
        val artist = client.callOptional("getArtist", mapOf("id" to artistId))
            ?.optJSONObject("artist") ?: return null
        val name = artist.optString("name").takeIf { it.isNotBlank() } ?: return null
        val top = client.callOptional("getTopSongs", mapOf("artist" to name, "count" to "1"))
            ?.optJSONObject("topSongs")?.array("song")?.firstOrNull()?.optString("id")
        if (!top.isNullOrBlank()) return top
        return artist.array("album").firstOrNull()?.optString("id")
            ?.let { firstTrackIdOfAlbum(it) }
    }

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
    override suspend fun start(): JamSession = throw SubsonicException.Unsupported("Jam")
    override suspend fun join(joinToken: String): JamSession = throw SubsonicException.Unsupported("Jam")
    override suspend fun leave() = Unit
    override suspend fun end() = Unit
    override suspend fun inviteLink(): String? = null
    override suspend fun removeMember(userUri: Uri) = Unit
    override suspend fun setQueueOnly(on: Boolean) = Unit
    override suspend fun setParticipantVolumeControl(on: Boolean) = Unit
}

internal class SubsonicBlends : com.adlen.sonora.api.Blends {
    override suspend fun invite(): BlendInvite = throw SubsonicException.Unsupported("Blends")
    override suspend fun invitation(linkOrId: String): BlendInvitation = throw SubsonicException.Unsupported("Blends")
    override suspend fun join(linkOrId: String): Uri = throw SubsonicException.Unsupported("Blends")
    override suspend fun group(playlist: Uri): BlendGroup = throw SubsonicException.Unsupported("Blends")
    override suspend fun story(playlist: Uri): BlendStory? = null
    override suspend fun rename(playlist: Uri, name: String) = Unit
    override suspend fun leave(playlist: Uri) = Unit
}

internal class SubsonicRecents(private val client: SubsonicClient) : Recents {
    override suspend fun recentEntries(offset: Int, limit: Int): Page<RecentEntry> {
        val recentAlbums = runCatching { client.albums("recent", 50) }.getOrDefault(emptyList())
        val entries = recentAlbums.map { album ->
            RecentEntry(
                item = RecentItem(
                    uri = album.uri,
                    kind = MediaKind.ALBUM,
                    title = album.name,
                    subtitle = album.artists.joinToString(", ") { it.name },
                    images = album.images,
                    typeLabel = "Album",
                ),
                childrenGroup = album.uri,
                groupCount = album.trackCount.takeIf { it > 0 } ?: 1,
                saved = false,
                music = true,
            )
        }
        return pageOf(entries, offset, limit)
    }
}

internal suspend fun SubsonicClient.genreAlbums(genre: String, size: Int): List<Album> {
    val root = callOptional(
        "getAlbumList2",
        mapOf("type" to "byGenre", "genre" to genre, "size" to size.toString()),
    ) ?: return emptyList()
    return root.optJSONObject("albumList2")?.array("album").orEmpty().map { albumOf(it) }
}

internal suspend fun SubsonicClient.genreSongs(genre: String, size: Int): List<Track> {
    val root = callOptional("getSongsByGenre", mapOf("genre" to genre, "count" to size.toString()))
        ?: return emptyList()
    return root.optJSONObject("songsByGenre")?.array("song").orEmpty().map { trackOf(it) }
}

internal suspend fun SubsonicClient.randomSongs(size: Int): List<Track> {
    val root = callOptional("getRandomSongs", mapOf("size" to size.toString())) ?: return emptyList()
    return root.optJSONObject("randomSongs")?.array("song").orEmpty().map { trackOf(it) }
}

private suspend fun SubsonicClient.albums(type: String, size: Int, offset: Int = 0): List<Album> {
    val root = call(
        "getAlbumList2",
        mapOf("type" to type, "size" to size.toString(), "offset" to offset.toString()),
    )
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
