# Subsonic/Jellyfin music client — implementation blueprint

Goal: an original Android music client that reproduces the **core Spotify UX**
(not its backend) on top of the Subsonic / OpenSubsonic API, primarily for
Jellyfin via the Subfin plugin.

Companion document:
[`docs/reverse-engineering/spotify-9.1.92.md`](../reverse-engineering/spotify-9.1.92.md).

## 1. Scope decision

### 1.1 Core (v1)

- Server setup and token auth.
- Home feed built from Subsonic data.
- Search (songs/albums/artists/playlists).
- Library: artists, albums, playlists, downloaded, sort and grid/list.
- Artist, album, playlist detail.
- Playback: stream, queue, shuffle, repeat, seek, next/previous.
- Mini player + full Now Playing.
- Favorites, ratings, playlists CRUD.
- Lyrics (plain and synced when available).
- Offline downloads + offline mode.
- Settings: audio quality, download quality, cache/storage, equalizer,
  sleep timer, autoplay, notifications, theme, about.
- Media notification, media buttons, audio focus, Android Auto browse tree.
- Responsive phone/tablet layout.

### 1.2 Later

- Widgets (Glance), Wear OS, Android Auto voice search, share links, bookmarks,
  podcasts/audiobooks, crossfade, Chromecast, local files.

### 1.3 Explicit non-goals

Spotify Connect, Jam/social, Canvas/video, AI DJ/recommendations, concerts/merch,
checkout/ads, kids/plan management. Do not copy Spotify code, assets, fonts,
icons, Lottie files, or branding.

## 2. Backend reality

Jellyfin does not natively serve the Subsonic API. Choose one:

1. **Subfin plugin** — OpenSubsonic-compatible API for Jellyfin 10.11.x,
   served under `http://<jellyfin>/subfin/`.
2. **Navidrome/Airsonic/Gonic** — native Subsonic servers over the same library.
3. **Jellyfin native API adapter** — future work; requires a second backend
   implementation.

Target **Subsonic 1.16.1 + OpenSubsonic extensions**, with capability probing.
Server differences are expected; never hard-code one server's behavior.

## 3. Technology choices

| Concern | Choice | Reason |
| --- | --- | --- |
| Language/UI | Kotlin + Jetpack Compose + Material 3 | Fast iteration, responsive layouts, matches modern Spotify surfaces |
| Navigation | Navigation Compose (or Navigation 3 when stable in the project) | Nested tab stacks + deep links |
| DI | Hilt | Standard Android DI |
| Async | Coroutines + Flow | Structured concurrency |
| Networking | Retrofit + OkHttp + kotlinx.serialization | Subsonic is simple HTTP/XML/JSON; keep it explicit |
| Images | Coil 3 | Compose-friendly, disk/memory cache |
| Playback | Media3 ExoPlayer + MediaSessionService/MediaLibraryService | Auto, notifications, media buttons, audio focus |
| Offline DB | Room | Songs/albums/playlists/download state/history |
| Preferences | DataStore | Settings, server, auth token material |
| Background | WorkManager | Downloads, sync, auto-download |
| Paging | Paging 3 | Large artist/album/song lists |
| Widgets | Glance | Later milestone |
| Tests | JUnit + Turbine + MockWebServer + Compose UI tests | API contract and UI behavior |

Use a single `app` module initially; split into `core:*` and `feature:*` modules
only when build times or ownership demand it. Avoid premature multi-module
complexity.

## 4. Package structure

```
com.example.music
├── app/                     # Application, MainActivity, navigation host
├── core/
│   ├── auth/                # server config, token auth, credential storage
│   ├── network/             # Retrofit, interceptors, error mapping
│   ├── database/            # Room entities/DAOs
│   ├── datastore/           # settings, server profile, capabilities
│   ├── model/               # domain models (Artist, Album, Song, ...)
│   ├── playback/            # Media3 service, queue, player controller
│   ├── download/            # download manager, workers, storage
│   ├── designsystem/        # colors, type, spacing, components
│   └── util/
├── feature/
│   ├── onboarding/          # server URL + credentials + ping
│   ├── home/
│   ├── search/
│   ├── library/
│   ├── artist/
│   ├── album/
│   ├── playlist/
│   ├── nowplaying/
│   ├── queue/
│   ├── lyrics/
│   ├── downloads/
│   ├── settings/
│   └── profile/
└── platform/
    ├── auto/
    ├── widget/
    └── wear/
```

## 5. Subsonic API layer

### 5.1 Authentication

All requests use:

```
u=<username>
t=md5(password + salt)
s=<random salt>
v=1.16.1
c=<client name>
f=json
```

Generate a new salt per request. Store the password only if "remember" is
enabled; prefer an app password (Subfin issues per-device credentials). Never log
the password, salt, or token.

### 5.2 Response envelope

Subsonic JSON is `{"subsonic-response": {status, version, ...}}`; errors use
`code`/`message`. Model this explicitly and map errors to typed failures
(`AuthError`, `NotFound`, `PermissionDenied`, `ProtocolMismatch`, `NetworkError`,
`ServerError`). Do not parse XML unless a server ignores `f=json`.

### 5.3 Capability probing

After `ping`, call `getOpenSubsonicExtensions` (if present) and cache:

- `getLyricsBySongId` / synced lyrics
- `getPlayQueue` + `savePlayQueue`
- `getArtistInfo2`, `getTopSongs`, `getSimilarSongs2`
- `setRating`
- `getShares` / `createShare`
- `getPodcasts` / `getNewestPodcasts`
- `getBookmarks`
- `getScanStatus` / `startScan`

Feature flags drive UI visibility, not runtime crashes.

### 5.4 Endpoint map

| Use case | Endpoint |
| --- | --- |
| Connectivity | `ping` |
| Folders | `getMusicFolders` |
| Artists | `getArtists`, `getArtist` |
| Albums | `getAlbumList2`, `getAlbum`, `getSong` |
| Search | `search3` |
| Favorites | `getStarred2`, `star`, `unstar` |
| Ratings | `setRating` |
| Playlists | `getPlaylists`, `getPlaylist`, `createPlaylist`, `updatePlaylist`, `deletePlaylist` |
| Streaming | `stream` (`maxBitRate`, `format`) |
| Download | `download` |
| Artwork | `getCoverArt` (`size`) |
| Lyrics | `getLyricsBySongId` (fallback `getLyrics`) |
| Scrobble | `scrobble` |
| Queue | `savePlayQueue`, `getPlayQueue` |
| Radio | `getInternetRadioStations` |
| Bookmarks | `getBookmarks`, `createBookmark`, `deleteBookmark` |
| User roles | `getUser` |
| Scan | `getScanStatus`, `startScan` |

## 6. Domain model

```
ServerProfile(id, baseUrl, username, displayName, capabilities)
Artist(id, name, imageUrl, albumCount, starred)
Album(id, name, artistId, artistName, coverArtId, songCount, year, genre, starred, downloaded)
Song(id, title, artistId, artistName, albumId, albumName, duration, track, disc,
     coverArtId, bitRate, size, suffix, contentType, starred, rating, downloaded)
Playlist(id, name, comment, owner, public, songCount, duration, coverArtId)
Genre(name, songCount, albumCount)
RadioStation(id, name, streamUrl, homepageUrl)
Lyrics(songId, plain, synced, language)
QueueState(items, currentIndex, positionMs, shuffle, repeat)
```

Keep IDs as opaque strings. Do not assume numeric or stable across servers.

## 7. Playback design

### 7.1 Service

`PlaybackService : MediaLibraryService` (or `MediaSessionService` for v1):

- ExoPlayer with a `DefaultMediaSourceFactory` and a custom `DataSource` that maps
  Subsonic song IDs to `stream` URLs.
- `MediaSession` with a `MediaSession.Callback` for play/pause/seek/next/previous/
  shuffle/repeat.
- `MediaLibrarySession` for Android Auto browse tree later.
- Audio focus, noisy receiver, becoming-noisy handling, wake lock.
- Media notification via `DefaultMediaNotificationProvider` with the standard
  actions (prev, play/pause, next) plus seek ±15 s if desired.
- Persist state with `savePlayQueue` and restore with `getPlayQueue`.
- Scrobble: `submission=false` on start; `submission=true` after 50% or 4 minutes.

### 7.2 Queue

- Single source of truth in a `PlayerRepository`/`QueueRepository`.
- Context = album, playlist, artist top songs, search results, or home section.
- Support "play next", "add to queue", reorder, remove, clear.
- Shuffle uses a stable permutation, not random per skip.
- Repeat off/all/one.
- On queue end, optionally autoplay similar songs (`getSimilarSongs2`) if the
  server supports it; otherwise stop.

### 7.3 Audio quality

- Settings: Low/Normal/High/Very High/Lossless/Automatic.
- Map to `maxBitRate` (e.g. 64/96/160/320/0) and `format` (`raw` for lossless).
- "Auto" can switch based on network type (Wi-Fi vs cellular) and measured
  bandwidth.
- Cache stream URLs and invalidate on server change.

### 7.4 Equalizer and sleep timer

- Attach `android.media.audiofx.Equalizer` (5 bands), `BassBoost`, `Virtualizer`
  to the ExoPlayer audio session ID.
- Presets from Spotify-style names (Flat, Acoustic, Bass booster, …).
- Sleep timer: off / 5–60 min / end of track / end of episode; pause and clear.

## 8. Offline/download design

- Download original files with `download` (no transcode) by default; optionally
  download `stream` output for a chosen quality.
- WorkManager chain: enqueue → download to temp → verify size/hash if available →
  move to app-private storage → update Room.
- Track download state per song and aggregate per album/playlist:
  `NOT_DOWNLOADED`, `QUEUED`, `DOWNLOADING(progress)`, `DOWNLOADED`, `FAILED`.
- Offline mode: player resolves local files first; remote-only items are disabled
  or shown with an offline indicator.
- Storage screen: downloaded size, cache size, free space, cache limit, clear
  cache, remove all downloads.
- Reconcile by song ID + expected size; Subsonic has no delta sync/etag, so verify
  at startup and on manual refresh.

## 9. UI composition

### 9.1 App shell

`MainActivity` → `MainScaffold`:

- `NavHost` with bottom tabs: Home, Search, Library.
- Each tab keeps its own back stack.
- Mini player anchored above the bottom bar (56 dp, 8 dp horizontal margins,
  4 dp artwork radius).
- Tap mini player → full Now Playing (bottom sheet or full-screen destination).
- Snackbar host, banner slot, tooltip slot.
- Responsive: `BoxWithConstraints` switches between bottom-nav (compact) and
  NavigationRail/side panel (medium/expanded), matching Spotify's 25% side panel
  at ≥600 dp.

### 9.2 Design tokens

Reuse the values documented in the RE dossier:

- Background `#121212`, elevated `#1F1F1F`, card `#242424`, text `#FFFFFF`,
  subdued `#B3B3B3`, accent `#1ED760`/`#1DB954`.
- Type scale 9/11/13/16/20/24/32/40/48 sp; open font substitute.
- Spacing 2/4/6/8/12/16/20/24/32/40/48/64 dp; radii 2/4/6/8/16/24/48/999 dp.
- Mini player 56 dp; large player 96 dp; bottom nav 56 dp.

### 9.3 Screens

- **Home:** greeting, filter chips (All/Music/Podcasts if supported), section
  carousels (recently played, new releases, most played, favorites, playlists,
  genres, random).
- **Search:** search field, recent searches, filter chips, top result, sectioned
  results, empty/error/offline states.
- **Library:** chips (Playlists/Artists/Albums/Downloaded), sort menu, grid/list
  toggle, drag-reorder pins, search.
- **Artist:** header image/name, play/shuffle, popular tracks, discography grid,
  appears-on, related artists, bio.
- **Album:** artwork/header, title/artist/year/track count, play/shuffle/download,
  track list with disc headings, more by artist, related.
- **Playlist:** artwork/header, owner/description, play/shuffle/download, track
  list, edit/reorder, add-to-playlist, delete.
- **Now Playing:** cover carousel, title/artist, seek bar + timestamps, transport,
  shuffle/repeat, like, add-to-playlist, queue, lyrics, sleep timer, more.
- **Queue:** now playing, next in queue, next from context, edit/reorder/clear.
- **Lyrics:** full-screen, synced highlight if timestamps exist, share.
- **Downloads:** list by album/playlist, progress, storage, auto-download.
- **Settings:** account, playback, audio quality, downloads, storage, equalizer,
  notifications, appearance, about.
- **Server setup:** URL, username, password/app password, test connection, TLS
  warning for HTTP, library selection.

## 10. Platform integrations

- **Android Auto:** `MediaLibraryService` browse tree: Home, Artists, Albums,
  Playlists, Genres, Downloads; voice search via `search3`.
- **Widgets (later):** Glance now-playing widget with play/pause/next and artwork.
- **Wear (later):** Media3 session on phone; Wear app browses/controls.
- **Notifications:** media style, artwork, actions; respect POST_NOTIFICATIONS.
- **Deep links:** custom scheme (`music://`) and optional `http(s)` links for
  share URLs.

## 11. Testing strategy

- **API contract:** MockWebServer fixtures for ping/search3/getAlbum/getPlaylist/
  stream/download/error envelopes; test token generation and URL encoding.
- **Repositories:** fake API + in-memory Room; verify caching and offline fallback.
- **Playback:** fake `Player`/`MediaItem` mapping; queue/shuffle/repeat logic;
  scrobble thresholds.
- **UI:** Compose tests for navigation, mini player, search, library filters;
  screenshot tests for the main screens on phone/tablet.
- **Manual matrix:** phone (compact), tablet (expanded), Android Auto, offline
  mode, HTTP and HTTPS server, wrong credentials, server 5xx, empty library.

## 12. Milestones

| Milestone | Deliverable | Exit criteria |
| --- | --- | --- |
| M0 | Project scaffold, DI, theme, server setup | `ping` succeeds; credentials stored securely; app launches to Home |
| M1 | Browse: Home, Search, Library, artist/album/playlist | Real library data renders; pagination and empty/error states work |
| M2 | Playback: Media3 service, mini/full player, queue | Streams play; notification/media buttons work; queue survives process death |
| M3 | Favorites, ratings, playlist CRUD, lyrics | Server mutations round-trip; lyrics render (plain at minimum) |
| M4 | Downloads, offline mode, storage settings | Download/play offline; storage accounting correct |
| M5 | Settings polish, equalizer, sleep timer, responsive layout | Phone/tablet parity with the core Spotify flows |
| M6 | Auto, widgets, Wear, share links | Platform surfaces functional on real devices |

## 13. Acceptance criteria for "1:1 core"

- The primary navigation and screen hierarchy match the dossier.
- The mini player, full player, queue, search, library, artist, album, and playlist
  screens are visually and behaviorally equivalent at the interaction level.
- All playback actions work against a real Subsonic/Jellyfin server.
- Offline mode is explicit and reliable; no silent remote dependency.
- No Spotify proprietary assets, fonts, code, or trademarks are shipped.
- Accessibility: content descriptions, 48 dp touch targets, dynamic type, contrast.
- Performance: cold start < 2 s on mid-range, 60 fps scrolling, no ANRs.

## 14. Immediate next actions

1. Confirm the reference server: Jellyfin + Subfin version, or Navidrome.
2. Confirm minSdk (24 vs 26) and target devices (phone/tablet/Auto).
3. Scaffold the Android project (Kotlin, Compose, Hilt, Media3).
4. Implement `core/network` + `core/auth` and the onboarding screen.
5. Implement the first vertical slice: login → `getAlbumList2` → album → `stream`
   → mini player.
