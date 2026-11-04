# Roadmap — Subsonic client

This repository contains a single Android client: the Myspoti-fork UI in `app/` with the
Subsonic backend in `api-subsonic/`.

The milestones below track that client. `M0` is done and the core of `M1` is done; `M2`
is the current focus.

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
- [ ] API-key auth when the server advertises `apiKeyAuthentication` (OpenSubsonic).
- [ ] Capability probe (`getOpenSubsonicExtensions`, server type/version) and a typed
      error model (`Auth`, `NotFound`, `Server`, `Network`).
- [x] Remove the hard-coded `10.0.2.2:4533` / `admin` / `admin` defaults (the runtime
      starts with a placeholder only until setup is saved).
- [x] Subsonic onboarding: server setup → look choice (Immersive/Classic) → permissions
      (notifications/Bluetooth/battery); the Spotify intro, streaming-quality and extensions
      steps are skipped.
- [ ] Trust-on-first-use for self-signed TLS, with an explicit warning for HTTP.

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
- [x] Application id renamed to `com.adlen.jellyfinaudio` (debug `…dev`); label
      "Jellyfin Audio". The internal Kotlin namespace (`app.myspoti.*`) is a later mechanical
      rename.
- [x] Default look switched to **Immersive** (the look of the Myspoti README); Classic is
      still selectable in Settings → Display → Look.
- [x] Immersive Settings cleaned: Server / Playback (Server) / Data / Display / App;
      Downloads and Extensions tabs removed.
- [x] Home quick-tile grid (the 2-column "shortcuts" of the Myspoti look): an untitled
      lead shelf of playlists/albums, with Liked Songs prepended by the UI.
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

## M3 — Playback platform

- [ ] `MediaSessionService` + ExoPlayer with a real `MediaSession` (notification, lock
      screen, headset, Bluetooth, audio focus, noisy handling).
- [ ] Queue persistence (`savePlayQueue` / `getPlayQueue` when supported).
- [ ] Gapless playback, shuffle/repeat semantics, previous/next behaviour.
- [ ] Media notification with artwork and the transport actions.
- [ ] Android Auto browse tree (`MediaLibraryService`) and voice search via `search3`.

**Done when:** playback continues in the background, the notification and headset
buttons work, and the queue survives process death.

## M4 — Library and playlists

- [ ] Favorites (`star`/`unstar`) for tracks, albums and artists.
- [ ] Playlist CRUD: create, rename, delete, add/remove/reorder tracks.
- [ ] Artist page: top tracks, discography, appears-on, related artists where the server
      supports them.
- [ ] Search filters and sorting; library grids/lists; empty and error states.
- [ ] Ratings (`setRating`) behind capability detection.

**Done when:** all mutations round-trip against Jellyfin and Navidrome.

## M5 — Downloads and offline

- [ ] Download manager (WorkManager), original or transcoded quality.
- [ ] Per-track/album/playlist download state and progress.
- [ ] Offline mode: local files first, clear indicators for remote-only items.
- [ ] Storage screen: downloads, cache, free space, clear cache.

**Done when:** airplane-mode playback of downloaded content works and storage accounting
is correct.

## M6 — Polish, tests, release

- [ ] Onboarding (server, quality, storage) and a permissions pass.
- [ ] Tablet/two-pane layout; responsive player; large-screen polish.
- [ ] Unit tests: Subsonic contract, mapping, URL/auth, player queue.
- [ ] UI tests: setup, home, search, album, playback.
- [ ] Package rename, icon, store metadata; remove all Spotify branding.
- [ ] GPL-3.0 compliance: source publication, notices, Myspoti credit.
- [ ] CI: build + tests.

**Done when:** a signed release builds from CI, installs on a clean device, and passes
the manual matrix (phone/tablet, HTTP/HTTPS, Jellyfin/Navidrome, offline).

## Non-goals

Spotify Connect, Jam, Blends, Spotify Codes, Wrapped, ads/DRM, podcasts/audiobooks
(until a Subsonic server exposes them), and any Spotify asset or trademark.
