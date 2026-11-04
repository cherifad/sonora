# 05 — Subsonic / OpenSubsonic mapping

Goal: every Spotify surface gets a real data source. Jellyfin is the primary target;
Navidrome/Airsonic/Gonic must work too. Anything Jellyfin-only is behind capability
detection, never assumed.

---

## 1. Connection, auth, envelope

### Base URL

Accept any of:

```
http://host:8096
http://host:8096/subsonic
https://music.example.com/subsonic/rest
```

Normalise to `…/rest`. If the user pastes `/rest`, keep it. Jellyfin serves the same
API at both `/rest` and `/subsonic/rest`; prefer `/rest` when present.

### Authentication

**Token auth (universal, default):**

```
GET {base}/ping?u={user}&t={md5(password + salt)}&s={salt}&v=1.16.1&c=Echo&f=json
```

* `salt`: random 8–16 alphanumeric chars per request (or per session).
* Store the credential in `EncryptedSharedPreferences`/DataStore + Android Keystore.
* MD5 is required by the protocol; use it only for the token.

**API key (OpenSubsonic `apiKeyAuthentication`, Jellyfin 10.9+):**

```
GET {base}/ping?apiKey={key}&v=1.16.1&c=Echo&f=json
```

Provide a toggle “Use API key”; auto-suggest it when the server advertises
`apiKeyAuthentication`.

### Response envelope

```json
{ "subsonic-response": {
    "status": "ok",
    "version": "1.16.1",
    "type": "jellyfin",
    "serverVersion": "10.10.7",
    "openSubsonic": true,
    "error": { "code": 40, "message": "Wrong username or password", "helpUrl": "..." }
}}
```

Handle HTTP errors, non-JSON (reverse-proxy HTML), `status:"failed"`, timeouts and
self-signed TLS (offer an explicit “trust this certificate” flow).

### Error codes to surface

| Code | Meaning | UX |
|---|---|---|
| 0 | generic | toast with server message |
| 10 | missing parameter | bug |
| 20 | client too old | “Server requires a newer client” |
| 30 | server too old | “Server API too old” |
| 40 | wrong credentials | inline on login |
| 41 | token auth unsupported for LDAP | switch to API key |
| 42 | auth mechanism unsupported | switch mechanism |
| 43 | conflicting auth | drop `u` when `apiKey` |
| 44 | invalid API key | re-enter key |
| 50 | not authorized | “You don't have access to this” |
| 70 | not found | remove stale item from cache |

---

## 2. Capability detection (once per server, cached)

```
ping                          → reachable, auth ok
getLicense                    → valid/license, email
getOpenSubsonicExtensions     → extension list (may 404 on older servers)
getMusicFolders               → folders / library selection
getScanStatus                 → library state (optional)
```

```kotlin
data class ServerCapabilities(
    val type: String,                 // "jellyfin", "navidrome", "subsonic", ...
    val serverVersion: String?,
    val apiVersion: String,
    val openSubsonic: Boolean,
    val extensions: Set<String>,      // apiKeyAuthentication, songLyrics, formPost,
                                      // transcodeOffset, topsongsbyartistid,
                                      // playbackReport, indexBasedQueue, sonicSimilarity
    val folders: List<MusicFolder>,
)
```

| Feature | Gate |
|---|---|
| API-key login | `apiKeyAuthentication` |
| Synced lyrics | `songLyrics` → `getLyricsBySongId` |
| Top songs by artist | `topsongsbyartistid` → `getTopSongs` |
| Byte-offset resume for transcodes | `transcodeOffset` |
| Playback reporting | `playbackReport` |
| Similar tracks | `sonicSimilarity` or `getSimilarSongs2` |
| Save/restore queue | `savePlayQueue` (1.16+) / Jellyfin REST fallback |

---

## 3. Endpoint map

| Spotify surface | Subsonic call | Notes |
|---|---|---|
| Server check / login | `ping` | plus `getLicense` |
| Home “Recently played” | `getAlbumList2?type=recent&size=20` | albums, not tracks |
| Home “New releases” | `getAlbumList2?type=newest&size=20` | |
| Home “Made for you” | `getStarred2`, `getRandomSongs`, `getSimilarSongs2` | compose locally (§6) |
| Home “Most played” | `getAlbumList2?type=frequent` | |
| Home “Random” | `getRandomSongs?size=50` | |
| Home genre shelves | `getGenres` + `getSongsByGenre` | |
| Search suggestions | `search3?query=&artistCount=5&albumCount=5&songCount=10` | debounce 250 ms |
| Search full | `search3` with larger counts | paging via counts |
| Library → Playlists | `getPlaylists` | |
| Library → Artists | `getArtists` (ID3) or `getIndexes` (folders) | ID3 preferred |
| Library → Albums | `getAlbumList2?type=alphabeticalByName&size=500&offset=` | paging |
| Library → Songs | no universal endpoint | see §7 |
| Library → Favorites | `getStarred2` | songs/albums/artists |
| Library → Downloaded | local Room | |
| Album page | `getAlbum?id=` | songs + metadata |
| Artist page | `getArtist?id=` | albums; bio via `getArtistInfo2` |
| Artist top songs | `getTopSongs?artist=` | Jellyfin fallback `/Items?sortBy=PlayCount` |
| Artist similar | `getSimilarSongs2?id=` | needs a song seed |
| Playlist page | `getPlaylist?id=` | songs + owner |
| Create playlist | `createPlaylist?name=` | then `updatePlaylist` |
| Rename/edit | `updatePlaylist?playlistId=&name=&comment=` | |
| Add songs | `updatePlaylist?playlistId=&songIdToAdd=` (repeat) | |
| Remove songs | `updatePlaylist?playlistId=&songIndexToRemove=` (repeat) | remove descending |
| Delete playlist | `deletePlaylist?id=` | |
| Like song/album/artist | `star?id=` / `star?albumId=` / `star?artistId=` | |
| Unlike | `unstar?…` | |
| 5-star rating | `setRating?id=&rating=1..5` | optional |
| Play track | `stream?id=&maxBitRate=&format=` | §5 |
| Download | `download?id=` | original file |
| Cover art | `getCoverArt?id=&size=` | `size` optional |
| Now playing (server view) | `getNowPlaying` | optional |
| Scrobble | `scrobble?id=&time=&submission=` | §5 |
| Save queue | `savePlayQueue?id=…&current=&position=` | OpenSubsonic/Jellyfin |
| Restore queue | `getPlayQueue` | on cold start |
| Lyrics | `getLyricsBySongId?id=` → `getLyrics?artist=&title=` | §8 |
| Audiobook resume | `getBookmarks` / `createBookmark` / `deleteBookmark` | |
| Genres | `getGenres` | |
| Songs in genre | `getSongsByGenre?genre=&count=&offset=` | |
| Folder browsing | `getIndexes` / `getMusicDirectory` | optional view mode |
| Scan status | `getScanStatus` / `startScan` | settings |
| Server info | `getLicense`, `getUser` | settings → about |

---

## 4. Data model

### `Child` (song) → `Track`

| Subsonic | Model | Notes |
|---|---|---|
| `id` | `id` | stable server id |
| `title` | `title` | |
| `artist` | `artistName` | display string |
| `artistId` | `artistId` | nullable on old servers |
| `album` | `albumName` | |
| `albumId` | `albumId` | |
| `albumArtist` | `albumArtist` | fallback to artist |
| `track` | `trackNumber` | |
| `discNumber` | `discNumber` | |
| `year` | `year` | |
| `genre` | `genre` | |
| `coverArt` | `coverArtId` | may equal album id |
| `duration` | `durationMs` (×1000) | |
| `bitRate` | `bitrateKbps` | |
| `size` | `sizeBytes` | |
| `suffix` / `contentType` | `codec` / `mime` | direct-play decisions |
| `path` | `path` | debug / folder view |
| `starred` | `isFavorite` | |
| `playCount` | `playCount` | |
| `created` | `addedAt` | ISO-8601 |
| `bookmarkPosition` | `resumePositionMs` | audiobooks |
| `type` | `kind` | `music` / `podcast` / `audiobook` |

### `AlbumID3` → `Album`

`id, name, artist, artistId, coverArt, songCount, duration, playCount, created, starred,
year, genre` → `Album(id, title, artistName, artistId, coverArtId, songCount,
durationMs, year, genre, isFavorite, addedAt)`.

`getAlbum` returns an `AlbumID3WithSongs`; map `song[]` to `Track[]` sorted by
`discNumber, trackNumber`.

### `ArtistID3` → `Artist`

`id, name, coverArt, albumCount, starred, artistImageUrl` →
`Artist(id, name, coverArtId, albumCount, isFavorite, imageUrl)`.

`getArtist` returns `artist` with `album[]` (full `AlbumID3` objects).

### `Playlist` → `Playlist`

`id, name, comment, owner, public, songCount, duration, created, changed, coverArt` →
`Playlist(id, name, description, owner, isPublic, songCount, durationMs, coverArtId,
createdAt, updatedAt, songs: List<Track>)`.

### `structuredLyrics` → `Lyrics`

`lang, synced, line[]{start?, value}, offset` → `Lyrics(synced: Boolean,
lines: List<LyricLine(startMs, text)>)`.

---

## 5. Streaming, quality, scrobbling

### Stream URL

```
{base}/stream?id={songId}&maxBitRate={kbps}&format={mp3|aac|flac|opus|raw}
   &estimateContentLength=true&timeOffset={sec}
```

| UI label | `maxBitRate` | `format` |
|---|---|---|
| Low | 96 | `mp3` |
| Normal | 160 | `mp3` |
| High | 320 | `mp3` |
| Very High | omit (no limit) | omit (direct play) |
| Lossless / Original | omit | `raw` (Jellyfin) or omit |

* Prefer **direct play** when the codec is supported; ExoPlayer handles
  FLAC/ALAC/Opus/MP3/AAC.
* `estimateContentLength=true` gives a Content-Length for transcoded streams.
* Android Auto/Bluetooth need a stable `MediaItem` URI; OkHttp follows redirects.

### Gapless / crossfade / DSP

* **Gapless:** enqueue the whole queue in ExoPlayer, `pauseAtEndOfMediaItems = false`.
  Transcodes break gapless.
* **Crossfade (0–12 s):** two players with volume ramps, or a custom `AudioProcessor`.
* **Normalisation:** apply ReplayGain when `getSong` exposes it (OpenSubsonic payload).
* **Equalizer:** `android.media.audiofx.Equalizer` on ExoPlayer’s audio session;
  21 presets from `equalizer_preset_*` (Flat, Acoustic, Bass booster, …).
* **Mono:** downmix `AudioProcessor`.

### Scrobbling

```
start:      scrobble?id=&submission=false&time={epochMs}
threshold:  scrobble?id=&submission=true&time={epochMs}   // 50% or 4 min
```
Update local play count/recency immediately so Home works offline; queue failures in
Room and retry with WorkManager.

### Queue persistence

`savePlayQueue?id=A&id=B&…&current={songId}&position={ms}` on pause/queue change
(debounced ~2 s). Restore with `getPlayQueue` on cold start; fall back to local Room.

---

## 6. Replacing Spotify-only features

| Spotify feature | Reality on Subsonic | Clone decision |
|---|---|---|
| Daily Mix / Made For You | no recommendation engine | local mixes: favourite genres, top played, “forgotten favourites” (high play count, not played in 30 d), decade mixes; seed `getSimilarSongs2` |
| Spotify Radio | `getSimilarSongs2` / Jellyfin InstantMix | “Song radio” = seed + similar, endless refill |
| Autoplay / “keep playing” | `getSimilarSongs2` on last track | toggle, default on |
| Smart Shuffle | no server recs | shuffle + inject similar every N tracks |
| Spotify Connect | no direct equivalent | “This device” in v1; Jellyfin `/Sessions` remote later |
| Jam / group session | none | remove |
| Podcasts / Shows | Jellyfin doesn't implement `getPodcasts*` | hide Podcasts chip unless capability present |
| Audiobooks | music library + bookmarks | “Audiobooks” chip; resume via `createBookmark` |
| Follow artist | `star?artistId` | “Follow” = star artist; hide social counts |
| Verified badge / listeners | unavailable | hide |
| Lyrics | OpenSubsonic `songLyrics` | show card only when advertised |
| Music videos / clips | Jellyfin video items | out of scope v1 |
| Offline downloads | `download` + local files | implement fully |
| Ads / Premium | n/a | remove |

---

## 7. Songs list without a “get all songs” endpoint

1. **Jellyfin REST** (`type=="jellyfin"`): `GET /Items?IncludeItemTypes=Audio&Recursive=true&SortBy=SortName&Fields=…&Limit=…&StartIndex=…`.
2. **`search3` with empty query** on servers that return the full library (Navidrome) — not portable.
3. **Walk albums**: `getAlbumList2` pages → `getAlbum` each (many requests).
4. **Folder walk**: `getIndexes` → `getMusicDirectory` recursively (slow, universal).

Ship (1) for Jellyfin, (2) when detected, (4) as the universal fallback behind a
“Songs” tab that may build a local index.

---

## 8. Lyrics

```
getLyricsBySongId?id={songId}      → lyricsList.structuredLyrics[]
  { lang, synced, line: [{ start: ms, value: "..." }], offset }
```

* `synced=false` → static text.
* Fallback `getLyrics?artist=&title=` (plain only).
* Jellyfin: `GET /Audio/{id}/Lyrics` when the OpenSubsonic call is empty.
* Cache per song id + `updatedAt`.

---

## 9. Images

```
getCoverArt?id={coverArtId}&size={px}
```

* Coil, request size = 2× view size, disk cache, key = `id-size`.
* Auth params are part of the URL; never log the token.
* Placeholder: gradient from a hash of the id + a music-note glyph.
* Artists: `getArtistInfo2` → `largeImageUrl`; Jellyfin image endpoint as fallback.

---

## 10. Jellyfin specifics (behind `type == "jellyfin"`)

| Need | Jellyfin REST |
|---|---|
| Login → token | `POST /Users/AuthenticateByName` |
| All songs + paging | `GET /Items?IncludeItemTypes=Audio&Recursive=true` |
| Instant mix | `GET /Items/{id}/InstantMix?Limit=50` |
| Direct play decision | `POST /Items/{id}/PlaybackInfo` |
| Playback reporting | `POST /Sessions/Playing`, `…/Progress`, `…/Stopped` |
| Resume | `/Items/{id}/…` user data |
| Lyrics | `GET /Audio/{id}/Lyrics` |
| Favorites | `POST/DELETE /Users/{userId}/FavoriteItems/{itemId}` |
| Remote control (later) | `POST /Sessions/{id}/Playing/{command}` |

Keep Subsonic as the default `ServerApi`; the Jellyfin adapter is optional and selected
after capability detection.

---

## 11. Caching and offline

* Room tables: `tracks`, `albums`, `artists`, `playlists`, `playlist_tracks`,
  `downloads`, `play_queue`, `pending_scrobbles`, `search_history`.
* TTL: library 24 h; playlists 15 min; home 5 min; lyrics 30 d.
* Download: `download` → `filesDir/downloads/{songId}.{suffix}` + Room row
  (size, state). `stream` is replaced by the local URI when a download exists.
* Offline queue actions → `pending_actions`, replayed on reconnect.
* Storage screen: download size + cache size + “clear cache”.

---

## 12. Response shapes

```jsonc
// getAlbum
{ "subsonic-response": { "album": {
      "id": "...", "name": "...", "artist": "...", "artistId": "...",
      "coverArt": "...", "songCount": 12, "duration": 2820,
      "playCount": 41, "created": "2024-01-02T03:04:05Z",
      "starred": "2024-02-01T...", "year": 2021, "genre": "Rock",
      "song": [ { /* Child */ } ] } } }

// getPlaylists
{ "subsonic-response": { "playlists": { "playlist": [
      { "id": "...", "name": "...", "owner": "user", "public": false,
        "songCount": 30, "duration": 7200, "created": "...", "changed": "...",
        "coverArt": "..." } ] } } }

// getLyricsBySongId
{ "subsonic-response": { "lyricsList": { "structuredLyrics": [
      { "lang": "eng", "synced": true, "offset": 0,
        "line": [ { "start": 1200, "value": "..." } ] } ] } } }
```

---

## 13. What cannot be mapped (accept it)

* Spotify catalogue metadata (audio features/BPM/key, credits, “about”, concerts,
  merch, licensed lyrics).
* Recommendations at Spotify quality. Local mixes are good enough; label them
  honestly (“Your mixes”).
* Cross-device handoff (optional later via Jellyfin session control).
* Social graph, Jam, Wrapped, AI DJ.
* Ads/billing — irrelevant.
