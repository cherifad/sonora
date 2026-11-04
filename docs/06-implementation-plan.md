# 06 — Implementation plan

## 1. Stack

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin 2.x, coroutines + Flow | same as Spotify's direction |
| UI | Jetpack Compose + Material3 (tokens overridden) | fastest path to a custom, animated UI |
| Navigation | Navigation Compose (or Navigation 3) with a custom shell | 5 roots + overlays |
| Player | **Media3 ExoPlayer + MediaSessionService + MediaLibraryService** | replaces Orbit; gives notification, Bluetooth, Auto |
| DI | Hilt | |
| Network | OkHttp + Retrofit + kotlinx.serialization | simple REST + JSON; no protobuf needed |
| Images | Coil 3 | |
| DB | Room + DataStore (proto or preferences) | cache, downloads, queue, settings |
| Paging | Paging 3 (Jellyfin/all-songs), manual paging for Subsonic limits | |
| Downloads | WorkManager + foreground service (`dataSync`) | |
| Background | WorkManager (scrobbles, queue sync, library refresh) | |
| Widgets/Auto/Wear | Glance + Media3 MediaLibraryService | phase 2 |
| Min SDK | 26 (or 24 to match Spotify) | Media3 + Compose are fine at 24; 26 simplifies |

Use a version catalog (`libs.versions.toml`). Pin versions at scaffold time; don't copy
Spotify's (its Compose usage is minimal and several years behind current).

## 2. Module layout

```
:app
:core:model            Track, Album, Artist, Playlist, Lyrics, Queue, ServerConfig
:core:network          SubsonicApi, JellyfinApi, SubsonicClient, auth, DTOs, mapping
:core:database         Room entities/DAOs, migrations
:core:datastore        settings, server config, capability cache
:core:data             repositories (Library, Home, Search, Playlist, Lyrics, Downloads)
:core:player           PlaybackService, QueueManager, PlayerState, scrobble, DSP
:core:designsystem     EchoTheme, tokens, Typography, icons, shared components
:core:ui               shared screens (context menu, chips, top bar, gradient)
:feature:home
:feature:search
:feature:library
:feature:album
:feature:artist
:feature:playlist
:feature:nowplaying    full-screen + mini player + queue + lyrics
:feature:settings      server setup, playback, downloads, about
:feature:downloads
```

Rule: features depend on `core:*`, never on each other. The mini player lives in
`:app` (shell), not in a feature.

## 3. Core abstractions

```kotlin
interface ServerApi {
    suspend fun ping(): ServerInfo
    suspend fun capabilities(): ServerCapabilities
    suspend fun albums(type: AlbumListType, size: Int, offset: Int): List<Album>
    suspend fun album(id: String): AlbumWithSongs
    suspend fun artist(id: String): ArtistWithAlbums
    suspend fun artists(): List<Artist>
    suspend fun playlists(): List<Playlist>
    suspend fun playlist(id: String): PlaylistWithSongs
    suspend fun search(query: String, limits: SearchLimits): SearchResults
    suspend fun streamUrl(song: Track, quality: AudioQuality): String
    suspend fun coverUrl(id: String, sizePx: Int): String
    suspend fun star(id: String, type: EntityType, star: Boolean)
    suspend fun scrobble(id: String, at: Long, submission: Boolean)
    suspend fun similar(songId: String, count: Int): List<Track>
    suspend fun lyrics(song: Track): Lyrics?
    suspend fun saveQueue(ids: List<String>, current: String?, positionMs: Long)
    suspend fun restoreQueue(): RestoredQueue?
}
```

`SubsonicApi` implements it with `@GET("rest/{method}")` + a query map produced by an
OkHttp interceptor (auth, `v`, `c`, `f`). `JellyfinApi` implements the same interface
plus extras, selected by factory after `ping`.

Key invariants:

* one OkHttp client, one dispatcher, 4 max requests,
* auth signer adds `u/t/s` or `apiKey` per request (salt can be per-session),
* every DTO is nullable-tolerant (`ignoreUnknownKeys`, `explicitNulls = false`),
* all list endpoints return `Result<T>` with typed errors (`Auth`, `Network`,
  `Server`, `NotFound`),
* mapping DTO → domain lives in one file per entity, unit-tested with fixtures.

## 4. Player

```
PlaybackService : MediaSessionService
  ├─ ExoPlayer (direct play, gapless)
  ├─ MediaSession (notification, media buttons, Bluetooth)
  ├─ QueueManager (Room-backed, shuffle/repeat, savePlayQueue debounce)
  ├─ ScrobbleTracker (start + threshold, offline queue)
  ├─ DspController (Equalizer, crossfade, mono, normalisation)
  └─ DownloadResolver (local file vs stream URL)
```

* The queue is the single source of truth: `List<QueueItem>` + `currentIndex` +
  `shuffleOrder`; UI observes `StateFlow<PlayerUiState>`.
* Play a list: `setQueue(tracks, startIndex, contextLabel)`.
* Previous: <3 s → previous track, else seek 0 (Spotify behaviour).
* Repeat off/all/one; shuffle keeps “next from context” semantics.
* `MediaItem` metadata: title, artist, album, artwork URI — Android Auto needs it.
* Persist queue + position every 2 s while playing; restore on cold start.

## 5. Milestones

### M0 — Skeleton (2–3 days)
- Gradle project, Hilt, theme from [design-tokens.json](design-tokens.json).
- Compose shell: nav bar + gradient + mini player placeholder + 5 roots.
- **Acceptance:** app runs, identical silhouette to Spotify at 360×800 and 800×1280.

### M1 — Server setup + API client (3–5 days)
- Onboarding: URL, username/password or API key, `ping`, capabilities, error states.
- `SubsonicApi` for ping/license/folders/album list/album/artist/playlists/search.
- Token auth + apiKey, capability cache, encrypted credential storage.
- **Acceptance:** connect to Jellyfin and Navidrome; browse albums/artists/playlists.

### M2 — Library + Search (4–6 days)
- Library header, chips, list/grid, sort sheet, context menus.
- Search idle (Browse categories) + results with debounce and filter chips.
- **Acceptance:** 10k-track library scrolls at 60 fps; search < 300 ms after debounce.

### M3 — Playback + Now Playing + Queue (5–8 days)
- Media3 service, queue, mini player, full player, seek, shuffle/repeat, notification.
- Cover art gradients, shared-element artwork, swipe gestures.
- **Acceptance:** background playback, Bluetooth controls, gapless within an album,
  queue reorder/remove/undo, state survives process death.

### M4 — Entities (3–5 days)
- Album, Artist, Playlist pages; favorites; create/edit/delete playlist; add/remove.
- **Acceptance:** all CRUD round-trips against Jellyfin and Navidrome.

### M5 — Home (3–4 days)
- Repository-composed sections, local mixes, pull-to-refresh, cached offline payload.
- **Acceptance:** home loads < 1 s cached, < 2 s cold on LAN.

### M6 — Downloads + offline (4–6 days)
- Download manager, states, storage screen, offline banner, local playback, action queue.
- **Acceptance:** airplane-mode playback of downloads; storage accounting correct.

### M7 — Lyrics, settings, polish (3–5 days)
- Synced lyrics, settings IA, equalizer, crossfade, sleep timer, speed (audiobooks).
- **Acceptance:** every setting persists; lyrics seek works when synced.

### M8 — Platform (optional, 1–2 weeks)
- Android Auto (`MediaLibraryService`), Glance widget, Wear, tablet two-pane layout.

## 6. Testing

* **Unit:** DTO mapping, auth signing (known MD5 vectors), queue shuffle/repeat algebra,
  scrobble thresholds, quality selection, capability gating.
* **API contract:** MockWebServer fixtures for each endpoint; golden JSON for Jellyfin
  and Navidrome quirks (nulls, missing `artistId`, empty `search3`).
* **UI:** Compose UI tests for library sort/filter, queue edit mode, player state.
* **Screenshot:** Roborazzi/Paparazzi against the design tokens at 360 dp; compare
  against reference screenshots (manual set from the real app).
* **E2E:** a fake Subsonic server (small Ktor app) for instrumentation tests.
* **Perf:** Macrobenchmark for home/library scroll, player open, cold start.
* **Network matrix:** LAN HTTP, HTTPS with self-signed cert, reverse proxy subpath,
  IPv6, offline.

## 7. Risks and mitigations

| Risk | Mitigation |
|---|---|
| “1:1” drifts | freeze a reference screenshot set from the real APK; pixel-diff in CI for skeleton screens |
| Subsonic endpoint variance | capability detection + per-endpoint fallbacks; test Jellyfin, Navidrome, Gonic |
| Large libraries | Paging 3, `LazyColumn` keys, `contentType`, image downsampling |
| Transcode gapless/crossfade | direct play by default; crossfade optional and disabled for transcodes |
| Auth edge cases (LDAP/MFA, API keys) | apiKey path + clear error surface; never silently retry with weaker auth |
| Downloads storage | WorkManager constraints, resumable, quota, user-visible storage screen |
| Legal/brand | no Spotify assets, fonts, icons or name; own logo, own deep-link scheme |

## 8. “1:1” parity checklist

- [ ] shell: gradient, mini player, 56 dp nav bar, 16 dp margins, 5 slots
- [ ] dark palette exactly (`#121212`, `#1F1F1F`, `#1ED760`, `#B3B3B3`)
- [ ] type scale (48/40/32/24/20/16/13/11/9) with a close open font
- [ ] 32 dp pills, 8 dp cards, 16 dp sheets, 4 dp artwork radii
- [ ] mini player 54 dp art, 2 dp seek, 40 dp play
- [ ] full player artwork + gradient + 64 dp play + scroll cards
- [ ] queue sheet with sticky current track, edit mode, undo
- [ ] library chips + grid/list + sort sheet
- [ ] search: idle browse tiles, debounce, top result, filter chips
- [ ] album/artist/playlist headers with collapsing artwork
- [ ] context menus, snackbars with undo
- [ ] transitions: shared artwork, 300 ms player slide, press scale 0.9
- [ ] edge-to-edge, gesture nav insets, no Material elevation shadows

## 9. Decisions needed before scaffolding

1. **App name + package** (cannot be Spotify-like). Placeholder: Echo / `dev.echo.music`.
2. **Brand accent**: keep green `#1ED760` (strong “Spotify” signal) or shift to a
   different accent while keeping the layout? Layout parity works with any accent.
3. **Min SDK**: 24 (match Spotify) vs 26 (simpler).
4. **Offline downloads in v1** or after parity?
5. **Tablet / Android Auto / Wear** in scope?
6. **Jellyfin REST enhancements** allowed (better “all songs”, instant mix, lyrics),
   or strictly Subsonic-only for portability?

## 10. Suggested first commit

```
chore: scaffold Echo — Compose shell + Encore design tokens

- Gradle version catalog, Hilt, Compose
- :core:designsystem with exact Encore colours/type/spacing from docs/design-tokens.json
- :app shell (MainLayout): content + gradient + mini player + 5-slot nav bar
- placeholder screens Home / Search / Library / Create / Settings
```
