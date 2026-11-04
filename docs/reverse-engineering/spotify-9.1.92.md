# Spotify Android 9.1.92.3058 — reverse-engineering dossier

Status: static analysis complete for the shipped APK. No runtime instrumentation was
performed. This document is a behavioral/visual reference for an original Subsonic
client; it is not a source-code port and must not be used to redistribute Spotify
code, fonts, artwork, Lottie files, or branding.

## 1. Artifact identity

| Field | Value |
| --- | --- |
| File | `spotify.apk` |
| Size | 143,009,050 bytes |
| SHA-256 | `7f2a4b58eb273207be432b457dc365d53525dd2ec9c0b378fa6502af24b4251f` |
| Package | `com.spotify.music` |
| Version name | `9.1.92.3058` |
| Version code | `147346334` |
| minSdk / targetSdk | 24 / 37 |
| DEX files | 18 (`classes.dex` … `classes17.dex`) |
| Classes | 99,502 (JADX top-level inventory) |
| Resources | 4,830 ZIP entries; 1,941 base layouts |
| Native libraries | 40 (10 per ABI: arm64-v8a, armeabi-v7a, x86, x86_64) |
| App framework | Native Android Views + selective Jetpack Compose; no React Native, Flutter, or Unity |

Evidence IDs from the REA session (APK digest above):

- Android package/manifest: `ev_91ea1e62764969773b55e58dba989ddcc5df4fe544398694d8d89c92212ad79e`
- Decoded resources/manifest/strings: `ev_3d8c4d52d38e6847821ee6c704d410b99e9ce91cf7ba1a3a5516c6df9bcc8743`
- Artifact inventory: `ev_8bed7d70e7d3a3aee3480ef37cb65e687de3ddeb1f4bf07cce86b2642dbf95b4`
- Artifact summary: `ev_85beef58bd3c9e0ebf0dc9c44871842b15c05ed02a0f090dbe1077a1d4ae5659`

Limits: JADX/static output does not prove runtime behavior. 88,753 of 99,502
classes are in the obfuscated `p.*` namespace; the readable `com.spotify.*`
surface is mostly feature APIs and protobuf models. UI reconstruction therefore
relies mainly on the manifest, layouts, strings, style resources, and the public
feature/class surface, not on decompiled UI code.

## 2. Executive summary

Spotify 9.1.92 is a very large, server-driven music client.

- **UI:** classic Android Views (ConstraintLayout, CoordinatorLayout, RecyclerView,
  MotionLayout, ViewStub) with an in-house design system called **Glue / Paste /
  Cat / Solar / Encore**. Compose is used selectively: search filter chips, queue
  placeholder, playlist/entity headers, filter rows. There are 1,941 base layouts
  and 4,706 `res/` entries.
- **State:** **Mobius** (99 classes) for unidirectional state loops in feature
  screens.
- **Networking:** **Cosmos/Cosmonaut** RPC over Retrofit/OkHttp, protobuf models,
  Google Play Services/Cronet, Firebase. The home feed is server-driven
  (`casita` / `funkis`) with typed modules (carousels, banners, inline cards,
  shelves).
- **Playback:** a proprietary native audio pipeline (`playbacknative.AudioDriver`,
  `OboeRenderer`, `MediaCodecFileDecompressor`, `liborbit-jni-spotify.so`) plus
  **Media3** for media-session/library plumbing, ExoPlayer, caching, DRM, HLS and
  audio processors. For an independent client, Media3 + ExoPlayer is the correct
  replacement; the proprietary engine is not needed or reusable.
- **Offline:** segment-oriented download/cache plugin (`EsDownload`,
  `EsOfflinePlugin`), `OfflineSyncWorker`, `OffliningService`, Room databases
  (including lyrics), WorkManager, foreground services.
- **Platform surfaces:** MediaBrowserService + Media3 MediaLibraryService,
  MediaButtonReceiver, MediaRoute2 provider, Android Auto/Car App Library, Cast,
  Glance widgets, picture-in-picture, Wear OS listener service, alarm/Tap
  integrations.
- **Scope reality:** a literal 1:1 reproduction of every Spotify feature is not
  feasible or useful. Spotify Connect, Jam, Canvas, AI DJ, social/chat, concerts,
  merch, checkout, ads, kids/plan management and Wrapped are backend-bound and
  have no Subsonic equivalent. The achievable target is a **1:1 core music
  experience** (Home, Search, Library, Artist/Album/Playlist, Now Playing, Queue,
  Lyrics, Downloads, Settings, Auto, widgets).

## 3. Manifest and platform components

Application: `com.spotify.music.SpotifyApplication`; theme `Theme.Glue`;
`largeHeap=true`; `allowBackup=false`; `networkSecurityConfig` permits cleartext
by default (so local HTTP Jellyfin/Subsonic servers are viable).

### 3.1 Entry point and deep links

- `com.spotify.music.SpotifyMainActivity` — `singleTask`, launcher, `MUSIC_PLAYER`
  and `APP_MUSIC` categories, NFC, `ACTION_SEARCH`, `MEDIA_PLAY_FROM_SEARCH`.
- `spotify:` scheme plus verified `open.spotify.com` / `spotify.com` links with
  path prefixes for home, search, album, artist, playlist, show, episode, track,
  genre, collection, queue, lyrics, settings, checkout, wrapped, etc.
- A full clone only needs its own deep-link scheme plus optional `http(s)` links
  for a future web/share surface.

### 3.2 Media and playback services

| Component | Role |
| --- | --- |
| `SpotifyMediaBrowserService` | Legacy `android.media.browse.MediaBrowserService` |
| `SpotifyMediaLibraryService` | Media3 `MediaLibraryService` + legacy browse action |
| `MediaButtonReceiver` | Headset/Bluetooth media buttons |
| `SpotifyMediaRouteProviderService` | MediaRoute2 / output switching |
| `AndroidAutoService` | Car App Library media service |
| `PlaybackCommandHandlerService` | Widget/PIP playback commands |
| `ForegroundKeeperService` | Keeps playback/state alive in foreground |
| `SpotifyAlarmLauncherService` | Alarm integration (`ACTION_PREPARE`) |
| `GoBluetoothService` / `SpotOnService` | Spotify Tap / Bluetooth quick action |
| `SpotifyWearableListenerService` | Wear OS data layer |

### 3.3 Notifications, widgets and overlays

- Notification actions: pause/resume, skip prev/next, seek ±15 s, shuffle on/off,
  add/remove from collection, ban/unban, shutdown, change segment.
- `PipActions` for picture-in-picture.
- Glance/AppWidget providers: `SpotifyWidget` (legacy), `NpvWidgetProvider`,
  `CoverScreenWidgetProvider` (Samsung cover screen), `MiuiDynamicNpvWidgetProvider`,
  `TranssionCardWidgetProvider`, plus `WidgetUpdateCoordinator`.
- Widget metadata: horizontal-resizable, min 250×40 dp, update driven by playback
  events (not periodic polling).
- Android Auto declares both `media` and `template` capabilities.

### 3.4 Offline/data services

- `OffliningService`, `OfflineSyncWorker`, `BulkRedownloadCancelReceiver`.
- `PlayedStateService`, `RecentlyPlayedService`.
- Room DB for lyrics (`LyricsDatabase`) and other local stores.
- `FileProvider`s for profile pictures, push files, playlist artwork, VTEC uploads,
  shareables, car images.

### 3.5 Permissions worth mirroring (core subset)

`INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
`FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS`, `READ_MEDIA_AUDIO`,
`BLUETOOTH_CONNECT`, `MODIFY_AUDIO_SETTINGS`, `VIBRATE`, `NFC`.
The clone does not need advertising, camera, location, phone-state, contacts,
billing, or install-referrer permissions.

## 4. Architecture and libraries

### 4.1 Code shape

- 18 DEX files, ~120 MB of DEX.
- 88,753 classes in `p.*`; 8,551 in `com.*`; 651 `androidx.*`; 644 `io.*`.
- Readable feature packages include: `campfire` (chat/contributions), `cosmos`
  (RPC), `extendedmetadata`, `player` (Esperanto protobuf), `playlist`,
  `connectivity`, `mobius`, `payment`, `casita` (home modules), `kidsview`,
  `encoreconsumermobile`, `searchview`, `automix`, `voiceassistants`, `allboarding`,
  `share`, `localfiles`, `profile`, `widgets`, `connect`, `offline_esperanto`,
  `home`, `playlistmixing`, `legacyglue`, `lyrics`, `download`, `playbacknative`,
  `collection`, `notifications`, `podcast`, `audio_sink_chain_wrapper_esperanto`.
- UI implementation is largely in `p.*`; feature boundaries are still visible
  through the public classes and resources.

### 4.2 Libraries and frameworks observed

| Area | Evidence |
| --- | --- |
| UI | `androidx.constraintlayout`, `CoordinatorLayout`, `RecyclerView`, `MotionLayout`, `ViewStub`, `ComposeView`, `com.spotify.encoremobile.*`, `com.spotify.legacyglue.*`, `com.spotify.encoreconsumermobile.*` |
| Design system | `Theme.Glue`, `Encore.*`, `Paste.*`, `Cat.*`, `Solar.*` styles; `TextAppearance.Encore.*` |
| State | `com.spotify.mobius.*` |
| RPC/networking | `com.spotify.cosmos.*` (Cosmonaut), `retrofit2`, `okhttp3`, protobuf, Cronet |
| Media | `androidx.media3.*` (98 classes: session, exoplayer, datasource, cache, drm, hls, audio), custom `com.spotify.playbacknative.*` |
| Offline | `EsDownload`, `EsOfflinePlugin`, `OfflineSyncWorker`, `Room`, `WorkManager` |
| Images | Coil 3 / custom image pipeline; `EncoreImageView`, `ArtworkView` |
| Analytics/fCM | Firebase, Crashlytics, Google Play Services, comScore |
| Platform | Car App Library, Cast, MediaRouter, Glance, Wearable, Billing |

### 4.3 Visual/design system

- Core theme is dark: `Theme.Glue` with `Theme.Glue.NoActionBar` screens.
- Component families: **Encore** (cards, buttons, list rows, icons, badges),
  **Glue/Paste/Cat** (legacy views, dialogs, toolbars, headers), **Solar**
  (buttons).
- Typography is **Spotify Mix UI** (Regular/Bold/Title Bold/ExtraBold) — proprietary.
  Use an open substitute (Inter, Figtree, Manrope) and keep the same scale.
- Motion uses MotionLayout (e.g. `now_playing_bar_scene`), Lottie (95 `res/raw`
  animations), and custom interpolators.
- No `res/values-night` split of consequence: the app is dark-first.

## 5. Screen and feature inventory

Derived from the manifest, 1,941 layouts, 11,176 strings, and readable feature
classes. The clone should implement the Core column; Optional items depend on the
Subsonic server/plugin.

| Surface | Core | Optional / Spotify-only |
| --- | --- | --- |
| Main shell | Vertical phone layout + horizontal/tablet side-panel layout; bottom nav; mini player; snackbar; banner; tooltip | Voice/agent bars, ads |
| Home | Greeting, filter chips, server-driven sections/carousels, recently played, new releases, favorites, playlists, genres | Made-for-you/AI DJ, concerts, merch, Wrapped, video |
| Search | Query field, recent searches, filters (All/Songs/Artists/Albums/Playlists/Genres), top result, sectioned results | Podcasts/audiobooks/profiles/concerts |
| Your Library | Chips (Playlists/Artists/Albums/Downloaded), sort, grid/list, drag-reorder pins, search | Podcasts/audiobooks |
| Artist | Header, popular tracks, discography, appears-on, related artists, bio | Concerts, merch, artist pick, clips, events |
| Album | Header, track list, disc headings, release metadata, more-by-artist, related | Pre-save/countdown, merch, music videos |
| Playlist | Header, creator, track list, edit/reorder, add-to-playlist, download | Mix/AI prompt, blend, jam |
| Now Playing | Cover carousel, title/artist, seek bar + timestamps, play/pause, prev/next, shuffle, repeat, like, add-to-playlist, queue, lyrics, more | Connect, Canvas, video, ads, sleep timer? (timer is core) |
| Queue | Current track, next-in-queue, next-from-context, reorder, clear | Jam/social session |
| Lyrics | Full-screen synced lyrics, share | Lyric stickers |
| Downloads | Badges, progress banner, album/playlist download, auto-download, storage/cache limits, offline mode | Podcast auto-download |
| Settings | Account, playback quality, download quality, storage/cache, equalizer, crossfade/gapless, normalize volume, autoplay, notifications, language, about | Plan, social, ads, kids, devices |
| Profile | Avatar, name, recently played artists, public playlists, followers/following | Social/chat, blends |
| Android Auto | Browse tree, now playing, voice search | — |
| Widgets | Now-playing widget, small/medium/large, cover-screen widget | Vendor-specific widgets |
| Wear | Browse/playback companion | — |

### 5.1 Shell structure

`res/layout/vertical_main.xml` and `horizontal_main.xml` use
`com.spotify.mainlayout.ui.view.MainLayout`:

- `fragment_container` for the active tab/screen.
- `now_playing_view_container` / `now_playing_mini_container` above the nav bar.
- `navigation_bar` (56 dp Material navigation height).
- bottom gradient fade to `#D9000000`; black fade 48 dp.
- banner, snackbar, tooltip, and overlay containers.
- Horizontal mode uses a side panel at 25% width (min 360 dp, max 450 dp) and a
  floating content surface with 16 dp radius.
- `now_playing_bar.xml` is a MotionLayout: cover art, track carousel, connect,
  add-to, play/pause, 2 dp progress bar; height 56 dp (96 dp large).
- `nowplayingmini_default.xml`: fading seek bar, 54 dp track carousel, track info,
  heart, 40 dp play/pause, 24 dp next.
- Full Now Playing is a bottom sheet/activity with `NowPlayingActivity` and
  dynamic content, not a single static layout.

### 5.2 Search

`search_fragment_layout.xml`: Compose filter row + content; `searchview.proto`
models cover Album, Artist, Track, Playlist, Show, Episode, Audiobook, Author,
Genre, Profile, Concert, and AI personas. Core clone needs songs/albums/artists/
playlists/genres.

### 5.3 Library

Filter strings: All, Playlists, Artists, Albums, Songs, Downloaded, Podcasts,
Audiobooks, Profiles, Genres & Moods, New, Released, Videos. Sort: Recently added,
Title, Artist, Album, Release date, Custom. View: grid/list.

### 5.4 Home feed

`funkis_home.xml`: AppBar + filter chips + subfeed. `casita` modules include
Heading, InlineCard, ActionCardCarousel, Banner, FullBleedBanner, AudioSpotlight,
AudioPromo, ImmersivePreview, ConcertCampaign, ContentEvaluationList. The server
decides the layout. A Subsonic client should generate equivalent sections locally
from album lists, favorites, playlists, genres, and local history.

### 5.5 Queue and lyrics

`layout_queue_sheet_content.xml`: `QueueInteractionLayout`, Jam header, queue
header, RecyclerView + Compose placeholder. Lyrics has a dedicated full-screen
activity, Room database, and a V3 lyrics response (`LyricsV3Response`).

## 6. Playback architecture

Spotify does **not** use ExoPlayer for its primary audio path. It ships:

- `com.spotify.playbacknative.AudioDriver`, `AudioRenderer`, `OboeRenderer`,
  `AudioTrackAdapter`, `AudioEffectsListener`, `MediaCodecFileDecompressor`.
- `liborbit-jni-spotify.so` (25 MB arm64) for native playback/DRM/audio.
- Esperanto protobuf RPC for player state/commands (`EsContextPlayerState`,
  `EsGetQueueRequest`, `EsAddToQueueRequest`, etc.).
- Media3 for session/library integration, ExoPlayer sources, caching, DRM, HLS,
  audio processors, and notification/media-button plumbing.

Implications for the clone:

- Use **Media3 ExoPlayer + MediaSessionService / MediaLibraryService**. Do not
  attempt to reuse the native engine.
- Model the queue explicitly (current item, context, next/previous, shuffle,
  repeat) and persist it with `savePlayQueue`.
- Use `scrobble` for play counts/now-playing; `star`/`unstar` for likes;
  `setRating` for ratings.
- Stream through `stream` with `maxBitRate`/`format`; use `download` for offline.
- For gapless, prefer server formats that decode gaplessly and ExoPlayer's
  `setWakeMode`/audio sink; crossfade needs a custom `AudioProcessor` and is
  optional.
- Attach Android `Equalizer`/`BassBoost`/`Virtualizer` to the ExoPlayer audio
  session for the equalizer screen.
- Sleep timer = coroutine/handler that pauses at deadline or end of track.

## 7. Offline and caching architecture

- Segment/stream-based download plugin (`EsDownload.*`: media manifest, segment
  file streamer, progressive file streamer, cache index checks).
- `EsOfflinePlugin` commands: Identify, Download, StopDownload, Remove.
- `OfflineSyncWorker` / `OffliningService` for sync and bulk re-download.
- Download states are animated with Lottie
  (`download_status_*.json`), and badges exist in Encore.
- Cache limit and storage guard settings are first-class; `DiskAlmostFullActivity`.
- Lyrics are cached in Room (`LyricsDatabase`).

For Subsonic, whole-file `download` is simpler and sufficient. Store metadata in
Room, files in app-private storage, and reconcile by song ID + size/etag. Add an
explicit offline mode and storage/cache limits.

## 8. Design tokens

Extracted from `res/values/colors.xml`, `dimens.xml`, `styles.xml`.

### 8.1 Color

| Token | Value |
| --- | --- |
| Base background | `#121212` |
| App frame background | `#000000` |
| Elevated background | `#1F1F1F` |
| Highlight / press | `#2A2A2A` / `#191919` |
| Card background | `#242424` |
| Primary text | `#FFFFFF` |
| Subdued text | `#B3B3B3` |
| Disabled/subdued essential | `#7C7C7C` |
| Brand green | `#1DB954` |
| Bright accent green | `#1ED760` |
| Accent press | `#1ABC54` |
| Negative | `#ED2C3F` (text `#F3727F`) |
| Warning | `#FFA42B` |
| Positive | `#1ED760` |
| Over-media scrim | `#8A000000` |
| Artwork shadow | `#75000000` |
| Tinted white layer | `#1AFFFFFF` / `#21FFFFFF` |

### 8.2 Type scale

| Role | Size | Family |
| --- | --- | --- |
| Marginal small | 9 sp | Mix UI Bold |
| Marginal | 11 sp | Mix UI Regular/Bold |
| Body small | 13 sp | Mix UI Regular/Bold |
| Body / title | 16 sp | Mix UI Regular/Bold |
| Title small | 20 sp | Mix UI Title Bold |
| Title medium | 24 sp | Mix UI Title Bold |
| Headline | 32 / 40 / 48 sp | Mix UI Title Bold/ExtraBold |

Fonts in the APK are **Spotify Mix** and **Circular** (proprietary). Use an open
font with a similar geometric/grotesk feel and keep the sizes/weights.

### 8.3 Spacing and shape

Spacing scale: 2, 4, 6, 8, 12, 16, 20, 24, 32, 40, 48, 64 dp.
Radii: 2, 4, 6, 8, 16, 24, 48, 999 dp. Cards/rows commonly 8 dp; bottom sheets
16 dp; chips/buttons pill-shaped; mini player 56 dp tall with 8 dp margins and
4 dp cover-art radius.

### 8.4 Motion

- `now_playing_bar_scene` (MotionLayout) for bar expansion/collapse.
- Lottie for play/pause morph, hearts, download states, onboarding, and ambient
  animations.
- Duration/easing tokens live in `res/values/styles.xml` (`EasingCurve.*`).

## 9. Mapping to Subsonic / OpenSubsonic

Jellyfin does **not** implement the Subsonic API natively. As of 2026 the practical
options are:

1. **Subfin plugin** (`williamkray/subfin-plugin`) for Jellyfin 10.11.x, which
   exposes an OpenSubsonic-compatible REST API under `/subfin/`.
2. A separate Subsonic server (Navidrome, Airsonic, Gonic…) pointed at the same
   library.
3. A second backend adapter using Jellyfin's native API (out of scope for the
   first milestone).

The client should target **Subsonic 1.16.1 + OpenSubsonic extensions**, detect
capabilities, and degrade gracefully.

| Client feature | Subsonic endpoint(s) | Notes |
| --- | --- | --- |
| Server login/test | `ping`, `getLicense` | Token auth: `md5(password + salt)` |
| Library folders | `getMusicFolders` | Optional folder selector |
| Artists | `getArtists`, `getArtist` | Indexed by ID3 tags |
| Albums | `getAlbumList2`, `getAlbum`, `getSong` | Types: newest, recent, frequent, random, starred, alphabeticalByName/Artist, byYear, byGenre |
| Artist detail | `getArtist`, `getTopSongs`, `getSimilarSongs2`, `getArtistInfo2` | Similar/top data depends on server/last.fm |
| Album detail | `getAlbum`, `getAlbumInfo2`, `getSimilarSongs2` | — |
| Search | `search3` | Query + per-type counts/offsets |
| Favorites | `getStarred2`, `star`, `unstar` | Albums/artists/songs |
| Ratings | `setRating` | 0–5 |
| Playlists | `getPlaylists`, `getPlaylist`, `createPlaylist`, `updatePlaylist`, `deletePlaylist` | Reorder via `songIndexToRemove` + `songIdToAdd` |
| Playback | `stream` | `maxBitRate`, `format=raw|mp3|…` |
| Offline download | `download` | Original file, no transcode |
| Cover art | `getCoverArt` | `size` parameter |
| Lyrics | `getLyricsBySongId` (OpenSubsonic), `getLyrics` | Synced lyrics only if provided |
| Scrobbling | `scrobble` | `submission=false` on start, `true` on threshold/end |
| Queue persistence | `savePlayQueue`, `getPlayQueue` | Cross-device resume |
| Radio | `getInternetRadioStations` | Optional |
| Podcasts | `getPodcasts`, `getNewestPodcasts` | Only if server supports |
| Bookmarks | `getBookmarks`, `createBookmark`, `deleteBookmark` | Audiobooks/podcasts |
| Shares | `getShares`, `createShare`, `updateShare`, `deleteShare` | Optional; Subfin supports |
| User roles | `getUser` | Gate stream/download/share/jukebox |
| Scan status | `getScanStatus`, `startScan` | Admin-only, optional |
| Capabilities | `getOpenSubsonicExtensions` | Feature detection |

### 9.1 Home feed from Subsonic data

There are no recommendations. Build a Spotify-shaped home from:

- Continue / Recently played (local history; optionally `getAlbumList2?type=recent`)
- New releases → `getAlbumList2?type=newest`
- Most played → `getAlbumList2?type=frequent`
- Favorites → `getStarred2`
- Playlists → `getPlaylists`
- Genres & moods → `getGenres` + `getSongsByGenre`
- Random / discovery → `getRandomSongs` or `getAlbumList2?type=random`
- "Made for you" → locally generated mixes from favorites/history (label clearly)

### 9.2 Feature flags for server differences

Treat these as capabilities, not assumptions: `getLyricsBySongId`, synced lyrics,
`getPlayQueue`/`savePlayQueue`, `getOpenSubsonicExtensions`, share, podcasts,
bookmarks, `setRating`, `getArtistInfo2`, `getTopSongs`, `getSimilarSongs2`.
Probe once after login and cache the result per server.

## 10. What not to clone

- **Code, assets, fonts, icons, Lottie JSON, sounds, artwork, and branding.**
  They are proprietary. Recreate the layout/behavior with original assets.
- **Spotify Connect / Jam / social sessions** — backend and device ecosystem.
- **Canvas / video / podcasts/audiobooks UI** unless the server exposes them.
- **AI DJ / prompted playlists / recommendations / Wrapped / concerts / merch /
  checkout / ads / kids / plan management.**
- **The native playback/DRM engine** — use Media3.
- **Spotify trademarks and the Spotify name/icon** in the clone.

## 11. Open questions and risks

1. Which Jellyfin version and Subsonic plugin will be the reference server?
   Endpoint coverage (especially lyrics, play queue, shares, podcasts) varies.
2. Is HTTP (cleartext) required for LAN servers? The clone must allow it.
3. Should the first release be phone-only or include tablet, Auto, Wear, widgets?
4. Offline storage format: original files (`download`) vs transcoded streams
   (`stream` + `format`). Original files are simpler and preserve quality.
5. Crossfade/gapless expectations: server-side transcoding can break gapless.
6. Localization/i18n: Spotify has 11,176 strings; the clone starts with English.
7. "1:1" acceptance criteria must be visual/interaction-based, not pixel-identical
   (fonts and assets differ by design).

## 12. Recommended next step

Implement the core vertical slice described in
[`docs/architecture/subsonic-client-blueprint.md`](../architecture/subsonic-client-blueprint.md):
server setup → library browse → album/artist/playlist → Media3 playback → mini/full
player → queue → favorites/playlists → search → downloads → settings. Defer Auto,
widgets, Wear and advanced audio until the core loop is stable.
