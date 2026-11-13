# Roadmap — Subsonic client

This repository contains a single Android client: the Myspoti-fork UI in `app/` with the
Subsonic backend in `api-subsonic/`.

The milestones below track that client. `M0`–`M4` are done; `M5` (downloads and offline) is
in its device-check phase.

## M0 — Consolidation and licensing ✅

- [x] Fork moved to the repository root as the single app; the original lightweight
      client was removed from the tree (it remains in git history).
- [x] Root `LICENSE` is GPL-3.0; `THIRD_PARTY_NOTICES.md` credits Myspoti and the
      upstream projects (Spotui, BitChord, Backdrop, Haze, Inter...).
- [x] The app builds with `./gradlew :app:assembleDebug`.
- [x] Subsonic spike verified on an emulator against Navidrome: Home, Search, Library,
      Album, Player, playback.

## M1 — Server setup and authentication (mostly done)

- [x] `SubsonicSettings` store (SharedPreferences): URL, username, password.
- [x] Native setup screen shown on first run (and reachable from Settings → Server, the cloud icon, or the profile menu):
      URL, username/password, **Test connection** (`ping`), save, error states.
- [x] Token auth (`u`, `t = md5(password + salt)`, `s`) instead of the plain `p=`.
- [x] API-key auth when the server advertises `apiKeyAuthentication` (OpenSubsonic).
- [x] Capability probe (`getOpenSubsonicExtensions`, server type/version) and a typed
      error model (`Auth`, `NotFound`, `Server`, `Network`).
- [x] Remove the hard-coded `10.0.2.2:4533` / `admin` / `admin` defaults (the runtime
      starts with a placeholder only until setup is saved).
- [x] Subsonic onboarding, **one sequence**: server step (integrated, step 1 of the dots) →
      look choice (Immersive/Classic) → permissions (notifications/Bluetooth/battery); the
      Spotify intro, streaming-quality and extensions steps are skipped.
- [x] Trust-on-first-use for self-signed TLS, with an explicit warning for HTTP.

**Done when:** a fresh install asks for a server, connects to Jellyfin + Subfin and
Navidrome, shows a useful error on bad credentials, and no credential is hard-coded.

## M2 — Remove the Spotify-only surface (current)

- [x] Profile sheet: Spotify account/recap/friends/extensions/logout replaced by
      Settings / Server / About.
- [x] Settings: Downloads/Extensions tabs hidden; Playback reduced to Server + data/battery;
      the WebView "Restart player" row removed (it crashed in Subsonic mode).
- [x] Home: only the All/Music chips (Podcasts/Audiobooks are not supported).
- [x] Immersive profile sheet: Spotify entries replaced by Settings / Server / About.
- [x] Player output sheet: local "This phone" only (no Connect list, no Spotify quality probe).
- [x] Own profile: listening-stats/extensions buttons hidden.
- [x] Podcasts/audiobooks disabled (`Features.PODCASTS = false`): chips, sections and
      search filters gone.
- [x] URL without a scheme is normalized to `http://` (autofill-friendly).
- [x] Guarded the remaining Spotify host accesses in `SongPlayer` (clips, Connect mirror,
      repeat restore, page reload); the output sheet is local-only.
- [x] Runtime-verified on a OnePlus CPH2207 (Android 13) over wireless ADB + `adb reverse`.
- [x] Application id renamed to `com.adlen.sonora` (debug `…dev`); label
      "Sonora". The internal Kotlin namespace (`com.adlen.sonora.*`) is a later mechanical
      rename.
- [x] Default look switched to **Immersive** (the look of the Myspoti README); Classic is
      still selectable in Settings → Display → Look.
- [x] Immersive Settings cleaned: Server / Playback (Server) / Data / Display / App;
      Downloads and Extensions tabs removed.
- [x] Home quick-tile grid (the 2-column "shortcuts" of the Myspoti look): an untitled
      lead shelf of playlists/albums, with Liked Songs prepended by the UI.
- [x] Share replaced in Subsonic mode: native Android share sheet (`title — artist`) instead
      of `scannables.scdn.co` Spotify Codes and `open.spotify.com` links.
- [x] Display / App settings cleaned in both looks: Canvas, Music video, Hide DJ, Player
      source line and the WebView "Restart player" row removed; `GeniusMeaning` prefetch
      disabled in Subsonic mode.
- [ ] Hide/remove the rest: Connect, Jam, Blends, podcasts/audiobooks, Canvas, Genius,
      Spotify Codes, Wrapped, extensions, ad-blocking and DRM code paths.
- [ ] Remove Spotify strings/assets/branding; rename the package and app label.
- [ ] Clean navigation: Home, Search, Library, Settings, Now Playing, Queue, Lyrics
      (when the server provides them).
- [ ] Replace the internal `spotify:*` URI scheme with a neutral `subsonic:*` scheme (or
      opaque IDs) so no code assumes Spotify semantics.
- [ ] Settings screen that only exposes features the backend supports.

**Done when:** no Spotify-only button dead-ends, and the app contains no Spotify
branding or assets.

## M3 — Playback platform (mostly done)

- [x] `PlaybackService` (`MediaBrowserServiceCompat` + `MediaSessionCompat`) + `ExoPlayer`
      with audio focus (`USAGE_MEDIA`), becoming-noisy handling, network wake lock, media
      notification, lock screen and headset/Bluetooth controls.
- [x] Server queue persistence (`savePlayQueue` debounced + `getPlayQueue` restore on
      startup) and local last-track restore.
- [x] Subsonic scrobbling (`submission=false` on start, `submission=true` at 50% or 4 min).
- [x] Gapless playback, shuffle/repeat semantics, previous (<3 s previous track, ≥3 s restart)
      and next behaviour.
- [x] Media notification with artwork and transport actions (Like, Shuffle, Prev, Play/Pause, Next).
- [x] Android Auto browse tree (`AutoBrowser` over `Graph.api` → Subsonic), verified on the
      emulator by an instrumented test (service, root tabs, library, album songs, search).

**Done when:** playback continues in the background, the notification and headset
buttons work, and the queue survives process death.

## M4 — Library, playlists and lyrics (mostly done)

- [x] Favorites (`star`/`unstar`) for tracks, albums and artists (`SubsonicLibrary`).
- [x] Playlist CRUD (`SubsonicPlaylists`): `createPlaylist`, `updatePlaylist` (rename,
      comment, public, `songIdToAdd`, `songIndexToRemove`), `deletePlaylist`.
- [x] Artist page (`SubsonicCatalog`): `getTopSongs` (fallback to album tracks),
      `getArtistInfo2` (biography, images, similar/related artists), discography.
- [x] Lyrics (`SubsonicExtras.lyrics`): OpenSubsonic `getLyricsBySongId` (synced + plain)
      with classic `getLyrics` fallback (plus LRCLIB fallback in `LyricsApi`).
- [x] Recents (`SubsonicRecents`): `getAlbumList2?type=recent`.
- [x] Ratings (`setRating`) behind capability detection.

**Done when:** all mutations round-trip against Jellyfin and Navidrome.

## M5 — Downloads and offline ✅

- [x] Download manager: the existing `OfflineDownloader` queue, fed in Subsonic mode by the
      server's own `stream` endpoint (no download-source extension), with WorkManager
      (`DownloadWorker`) holding the process and showing progress in a notification.
- [x] Quality mapping: `HI_RES` → original file, `CD` → `maxBitRate=320`, `SMALL` →
      `format=mp3&maxBitRate=128`; a server that cannot transcode falls back to the original.
- [x] Downloads are validated (MediaExtractor), encrypted (`OfflineCrypto`) and stored with
      their cover, like the Spotify pipeline did.
- [x] Per-track/album/playlist download state and progress (existing badges, queue screen,
      "stop all", retry/cancel); the "install a download extension" cards and prompts are
      hidden in Subsonic mode.
- [x] Offline mode (`OfflineMode`): one state from the network callback, the server's own answers
      (a LAN server is out of reach away from home even with mobile data on) and the data saver;
      a downloaded song plays from its file, anything else says "download this song to play it";
      an offline banner heads Home in both looks; a ping brings it back when the server returns.
- [x] Storage screen: downloads count/size, cache size + clear, free space, remove all
      (Immersive Settings → Downloads, Classic Settings → Data).
- [x] Device pass: downloads land and resume after a restart, offline playback (server out of
      reach) with the banner and the remote-only message, storage numbers and cache clearing.
- [x] Device pass on the release build (R8): a whole album downloaded (27 → 53 songs,
      219 → 490 MB), local playback, storage numbers; "remove all downloads" returns to 0.

**Done when:** airplane-mode playback of downloaded content works and storage accounting
is correct.

## M6 — Polish, tests, release

- [x] Onboarding (Subsonic): server → look → **downloads and storage** (quality, songs/size,
      free space) → **companion ingest** (optional, prefilled `navidrome-ingest.<domain>`) →
      permissions (notifications, Bluetooth, battery); the local-files permission is asked when
      Local Files is opened. The steps are remembered, so updates skip them.
- [ ] Tablet/two-pane layout; responsive player; large-screen polish.
      Android Automotive OS: the `automotive` flavor (no launcher, media metadata, `appCategory`
      audio) runs on the car emulator — the car's media app browses Home / Library / Downloads
      and plays from the Subsonic server. The phone build is unchanged (`assembleDebug` still
      builds both variants).
      Audited on the 2560×1600 tablet AVD: Home, the quick tiles and the shelves reflow
      (4 columns); the album page keeps a 300 dp sleeve on wide windows, so the songs show on
      the first screen. Next: the full two-pane page (cover-left / list-right).
- [x] Unit tests: the Subsonic contract — URL shape + salted token (never the password), error
      messages, HTTP failures, reachability reporting, JSON → model mapping, album track paging,
      download quality parameters (23 tests in `api-subsonic`).
- [ ] Unit tests: player queue and the remaining pure logic in `app`.
- [x] UI tests: setup, home, search, album, playback.
      `MainFlowTest`: Home, Library, Search and an album played end to end.
      `SetupFlowTest`: the server form connects, then the permissions step and Home.
      (The look step's preview animates forever, which a Compose test cannot idle on: the test
      marks it seen and covers the rest.) Android Auto stays covered by `AutoBrowserTest`.
- [ ] Package rename, icon, store metadata; remove all Spotify branding.
      Icon done: a pink-to-amber adaptive icon with five white level bars (plus the themed
      monochrome layer and `app_mark` for the About sheet), replacing Myspoti's green wave.
      Done for every reachable screen: app name, About, update/language/battery texts, Local
      Files, library order, speed/silences, lyrics summary, shuffle and the Settings rows say
      "Sonora" / "your server", with no Spotify Connect or Myspoti left. Remaining:
      the dead Spotify-only screens' strings and the upstream Discord/Support links.
- [ ] GPL-3.0 compliance: source publication, notices, Myspoti credit.
      In the app: About credits "a fork of Myspoti, under GPL-3.0" and its Source code row opens
      this fork's repository. Left: publish the source, keep `LICENSE`/`THIRD_PARTY_NOTICES.md`
      with every release.
- [x] CI: `.github/workflows/ci.yml` — JDK 17 + Android SDK, the JVM tests of `api-subsonic`
      and `app`, then the debug APK, uploaded as an artifact.
- [x] Test harness: the official emulator replaces the phone (phone + tablet AVDs, window on
      the Windows desktop, Navidrome at `10.0.2.2:4533`, airplane mode for the offline checks);
      scripts in `tools/`, notes in `docs/TESTING.md`.

- [x] Release 1.2.0 (R8, signed with `keystore/release.jks`) built for both flavors and checked
      on a **clean** phone emulator: onboarding against Navidrome, Home, album playback, an album
      downloaded (6 songs · 13 MB, WorkManager notification) and, in airplane mode, the offline
      banner and playback from the file. `jellyfin-audio-1.2.0-release.apk` (17.8 MB) and
      `…-automotive.apk` (10.8 MB).
- [x] "Nerd stats" no longer reads the Spotify page in Subsonic mode (it opened a session that
      was never started); the account sheet names the user without Spotify's plan wording.

**Done when:** a signed release builds from CI, installs on a clean device, and passes
the manual matrix (phone/tablet, HTTP/HTTPS, Jellyfin/Navidrome, offline).

## M7 — Subsonic feature completion (current)

The delivery tracker is [`PLAN.md`](PLAN.md); this is the summary.

- [x] Capability probe, typed errors, API-key auth, precise setup errors.
- [x] Server-side paging for albums and search; 30 s caches for artists, playlists and
      starred songs.
- [x] Genre albums/songs, playlist search, ratings.
- [x] Similar-track radio, “For you” and “Recently played” shelves, “More like this”.
- [x] ReplayGain, equalizer and skip-silence on streamed Subsonic tracks.
- [x] Offline 2.0: resumable downloads, retry/backoff, storage quota, Wi-Fi-only.
- [x] Bookmarks, shares, scan status, now playing, avatar.
- [x] Quick Settings tile and widget shuffle.
- [x] Encrypted server credentials, logout cleanup, HTTP warning.
- [x] Contract/fault tests, R8 release gate in CI, bug-report template.
- [ ] Cast a Subsonic stream, Wear OS.
- [ ] Capability-matrix CI, upgrade/migration test.
- [ ] Store metadata, source publication, signed release tag.

## Non-goals

Spotify Connect, Jam, Blends, Spotify Codes, Wrapped, ads/DRM, podcasts/audiobooks
(until a Subsonic server exposes them), and any Spotify asset or trademark.
