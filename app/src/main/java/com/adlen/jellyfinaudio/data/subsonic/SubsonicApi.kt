package com.adlen.jellyfinaudio.data.subsonic

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The subset of the Subsonic/OpenSubsonic API used by the first vertical slice.
 *
 * The auth parameters (u/t/s/v/c/f) are appended by
 * [SubsonicAuthInterceptor], so callers only pass endpoint-specific values.
 */
interface SubsonicApi {

    @GET("rest/ping.view")
    suspend fun ping(): SubsonicEnvelope<PingResponse>

    @GET("rest/getAlbumList2.view")
    suspend fun getAlbumList2(
        @Query("type") type: String = "newest",
        @Query("size") size: Int = 50,
        @Query("offset") offset: Int = 0,
    ): SubsonicEnvelope<AlbumListResponse>

    @GET("rest/getArtists.view")
    suspend fun getArtists(): SubsonicEnvelope<ArtistsResponse>

    @GET("rest/getAlbum.view")
    suspend fun getAlbum(
        @Query("id") id: String,
    ): SubsonicEnvelope<AlbumDetailResponse>

    @GET("rest/getMusicDirectory.view")
    suspend fun getMusicDirectory(
        @Query("id") id: String,
    ): SubsonicEnvelope<MusicDirectoryResponse>

    @GET("rest/getPlaylists.view")
    suspend fun getPlaylists(): SubsonicEnvelope<PlaylistsResponse>

    @GET("rest/getPlaylist.view")
    suspend fun getPlaylist(
        @Query("id") id: String,
    ): SubsonicEnvelope<PlaylistDetailResponse>

    @GET("rest/getStarred2.view")
    suspend fun getStarred2(): SubsonicEnvelope<Starred2Response>

    @GET("rest/getGenres.view")
    suspend fun getGenres(): SubsonicEnvelope<GenresResponse>

    @GET("rest/star.view")
    suspend fun star(
        @Query("id") id: String,
    ): SubsonicEnvelope<PingResponse>

    @GET("rest/unstar.view")
    suspend fun unstar(
        @Query("id") id: String,
    ): SubsonicEnvelope<PingResponse>

    @GET("rest/scrobble.view")
    suspend fun scrobble(
        @Query("id") id: String,
        @Query("submission") submission: Boolean,
        @Query("time") time: Long,
    ): SubsonicEnvelope<PingResponse>

    @GET("rest/savePlayQueue.view")
    suspend fun savePlayQueue(
        @Query("id") id: List<String>,
        @Query("current") current: String,
        @Query("position") position: Long,
    ): SubsonicEnvelope<PingResponse>

    @GET("rest/getPlayQueue.view")
    suspend fun getPlayQueue(): SubsonicEnvelope<PlayQueueResponse>

    @GET("rest/getLyricsBySongId.view")
    suspend fun getLyricsBySongId(
        @Query("id") id: String,
    ): SubsonicEnvelope<LyricsResponse>

    @GET("rest/getLyrics.view")
    suspend fun getLyrics(
        @Query("artist") artist: String?,
        @Query("title") title: String?,
    ): SubsonicEnvelope<LyricsResponse>

    @GET("rest/createPlaylist.view")
    suspend fun createPlaylist(
        @Query("name") name: String,
        @Query("songId") songId: List<String>? = null,
    ): SubsonicEnvelope<PlaylistDetailResponse>

    @GET("rest/updatePlaylist.view")
    suspend fun updatePlaylist(
        @Query("playlistId") playlistId: String,
        @Query("name") name: String? = null,
        @Query("songIdToAdd") songIdToAdd: List<String>? = null,
        @Query("songIndexToRemove") songIndexToRemove: List<Int>? = null,
    ): SubsonicEnvelope<PingResponse>

    @GET("rest/deletePlaylist.view")
    suspend fun deletePlaylist(
        @Query("id") id: String,
    ): SubsonicEnvelope<PingResponse>

    @GET("rest/search3.view")
    suspend fun search3(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int = 10,
        @Query("artistOffset") artistOffset: Int = 0,
        @Query("albumCount") albumCount: Int = 10,
        @Query("albumOffset") albumOffset: Int = 0,
        @Query("songCount") songCount: Int = 20,
        @Query("songOffset") songOffset: Int = 0,
    ): SubsonicEnvelope<SearchResponse>
}
