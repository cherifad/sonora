# Roadmap — Subsonic client

The repository now contains two clients:

- **`app/`** — the original lightweight Compose client. It stays as a clean, small
  fallback and as the reference implementation of the Subsonic mapping.
- **`myspoti/`** — a GPL-3.0 fork of [Myspoti](https://github.com/kota-zo/myspoti) whose
  Spotify backend has been replaced with Subsonic/OpenSubsonic. It is the feature-rich
  UI path.

The milestones below track the **`myspoti/` fork**. `M0` is done and the core of `M1` is
done; `M2` is the current focus.

## M0 — Consolidation and licensing ✅

- [x] Fork copied into `myspoti/` (source only, no nested `.git`, no build outputs).
- [x] Root `LICENSE` is GPL-3.0; `THIRD_PARTY_NOTICES.md` credits Myspoti and keeps its
      notices.
- [x] Fork builds in this environment (`cd myspoti/base && ./gradlew :app:assembleDebug`).
- [x] Subsonic spike verified on an emulator against Navidrome: Home, Search, Library,
      Album, Player, playback.

## M1 — Server setup and authentication (mostly done)

- [x] `SubsonicSettings` store (SharedPreferences): URL, username, password.
- [x] Native setup screen shown on first run (and reachable from Settings → cloud icon):
      URL, username/password, **Test connection** (`ping`), save, error states.
- [x] Token auth (`u`, `t = md5(password + salt)`, `s`) instead of the plain `p=`.
- [ ] API-key auth when the server advertises `apiKeyAuthentication` (OpenSubsonic).
- [ ] Capability probe (`getOpenSubsonicExtensions`, server type/version) and a typed
      error model (`Auth`, `NotFound`, `Server`, `Network`).
- [x] Remove the hard-coded `10.0.2.2:4533` / `admin` / `admin` defaults (the runtime
      starts with a placeholder only until setup is saved).
- [ ] Trust-on-first-use for self-signed TLS, with an explicit warning for HTTP.

**Done when:** a fresh install asks for a server, connects to Jellyfin + Subfin and
Navidrome, shows a useful error on bad credentials, and no credential is hard-coded.

## M2 — Remove the Spotify-only surface (current)

- [x] Profile sheet: Spotify account/recap/friends/extensions/logout replaced by
      Settings / Server / About.
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
