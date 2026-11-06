# PLAN — Sonora: finish the Subsonic client, retire the fork's names

This file is the single source of truth for the work. Every task is a checkbox; every
completed task gets a line in the **Progress log** at the bottom with its commit. Work
happens on `develop` (see `AGENTS.md`); `main` only receives release-ready merges.

Legend: `[ ]` todo · `[~]` in progress · `[x]` done · `[!]` blocked (note why)

---

## Goal

1. Finish the Subsonic/OpenSubsonic client so no visible path dead-ends and large libraries
   behave.
2. Add the features that make it a real daily client (discovery, ReplayGain, offline sync,
   server extras, platform surfaces).
3. Replace the fork's internal names (`app.myspoti`, `com.music.spotui`, `MyspotiApi`,
   `MyspotiApplication`, …) with Sonora names. Keep the Myspoti/Spotui **attribution** in
   `README.md`, `THIRD_PARTY_NOTICES.md`, `LICENSE` and `docs/upstream/`.
4. Put a bug-control system in place so the next features cannot silently regress.

## Non-goals

- Reintroducing Spotify as a backend, Spotify branding/assets, ads or DRM.
- Podcasts/audiobooks unless a server exposes them (the `Features.PODCASTS = false` rule).
- Rewriting the UI framework or the extension sandbox.

## Conventions

- One phase per commit series; a commit must build and pass the JVM tests.
- A stub must either be **hidden** or raise a typed `Unsupported`, never return an empty
  list that looks like real data.
- Every new endpoint gets a fixture in `api-subsonic/src/test/.../FakeSubsonic.kt` and a
  contract test.
- Update this file in the same commit as the code it describes.

---

## Phase 0 — Baseline and guardrails

- [x] Confirm a green baseline before touching anything.
  - `./gradlew :api-subsonic:testDebugUnitTest :app:testMobileDebugUnitTest`
  - `./gradlew :app:assembleMobileDebug :app:assembleAutomotiveDebug`
- [x] Record the baseline APK sizes and test counts in the Progress log.
- [x] Add a `PLAN.md` reference to `AGENTS.md` so contributors know it is the tracker.

**Done when:** the current tree builds and tests pass, and the numbers are written down.

---

## Phase 1 — Rename: remove the fork's internal names

The attribution stays in docs. Everything the compiler, the manifest, the resources and the
tools see becomes Sonora.

### 1.1 Package and directory mapping

| Old | New |
|---|---|
| `app.myspoti` (engine, api, subsonic, host, spotify, extensions) | `com.adlen.sonora` |
| `com.music.spotui` (app UI, data, di, cast, connect, video, widget, cli) | `com.adlen.sonora.app` |
| `com.myspoti` (androidTest) | `com.adlen.sonora.test` |
| `app/myspoti/extensions` resources | `com/adlen/sonora/extensions` |

- [x] `git mv` every source root (main/test/debug/androidTest, all modules).
- [x] Replace package declarations, imports and fully-qualified names.
- [x] Update `namespace` in `app`, `api-subsonic`, `host-android` build files.
- [x] Update `AndroidManifest.xml` files (`application`, activities, services, receivers,
      providers, custom actions).
- [x] Update `app/proguard-rules.pro` keep rules.
- [x] Update `settings.gradle.kts` `rootProject.name` to `Sonora`.

### 1.2 Class and file names

- [x] `MyspotiApplication` → `SonoraApplication`
- [x] `MyspotiRuntime` → `SonoraRuntime`
- [x] `MyspotiApi` → `SonoraApi` (and every receiver/import in `extensions`)
- [x] `myspoti_shuffle_*` string keys → `sonora_shuffle_*` in every `values-*`
- [x] `res/values/myspoti_strings.xml` → `res/values/sonora_strings.xml`
- [x] `tools/myspoti-cli` → `tools/sonora-cli` (and its own help text)
- [x] `extensions-cli` `applicationName` `myspoti-ext` → `sonora-ext`
- [x] `host-cdp` property `myspoti.cdp` → `sonora.cdp`
- [x] Build-time env vars: prefer `SONORA_*`, keep reading `MYSPOTI_*` as a fallback so CI
      secrets keep working (`UPDATE_REPO`, `KEYSTORE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
      `KEY_PASSWORD`).
- [x] One-time prefs migration: copy `myspoti_prefs` to `sonora_prefs` on first start.

### 1.3 Deliberate exceptions (do not rename)

- `README.md`, `THIRD_PARTY_NOTICES.md`, `LICENSE`, `docs/upstream/**`: attribution.
- Source license headers: they keep the upstream `Myspoti` notice and the fork's URL, as
  GPL-3.0 requires. This is the only `Myspoti` left in the code besides the next two items.
- The `myspoti_prefs` constant in `PrefsMigration`: it reads the old file once, then never
  again. Removing it would drop every setting of an existing install.
- The extension catalogue URLs (`kota-zo/myspoti-extensions`): that repository still exists
  upstream; pointing at a non-existent `sonora-extensions` would break the catalogue.
- `api-spotify` module name: it is the upstream backend, not the fork's name.
- `docs/brand/myspoti-logo*` moved to `docs/upstream/brand/`: attribution assets, not app
  assets.

### 1.4 Verify

- [x] `grep -ri myspoti` returns only the deliberate exceptions above.
- [x] `grep -ri "app.myspoti\|com.music.spotui\|com.myspoti"` returns nothing.
- [x] Unit tests and both debug APKs build.
- [ ] Install the phone APK on the emulator; Home, an album, playback, Settings open.
- [ ] Check the signed release path (R8) still builds.

**Done when:** the code, manifest, resources and tools contain no `myspoti`/`spotui`
identifiers, the app builds, and the attribution files are untouched.

---

## Phase 2 — Capability probe, typed errors, auth

- [x] Add `ServerCapabilities` (server type, version, OpenSubsonic extensions, features:
      `apiKeyAuthentication`, `formPost`, `songLyrics`, `transcodeOffset`, plus the core
      features ratings/shares/bookmarks/scan/artist-info/top-songs/podcasts).
- [x] Probe once at startup and cache it (`ping` + `getOpenSubsonicExtensions`; `getLicense`
      is not needed for feature gating).
- [x] Add a typed error model: `Auth`, `NotFound`, `Network`, `Server`, `Protocol`,
      `Unsupported`; `RateLimited` is `Server(429)`. HTTP/Subsonic codes are mapped once in
      `SubsonicClient`.
- [x] Show precise setup errors ("wrong password", "server unreachable", "not a Subsonic
      server") instead of one generic message.
- [~] Gate optional calls on capabilities — lyrics, artist info and top songs use
      `callOptional` + `supports()` today; ratings/shares/bookmarks/scan are gated when their
      phases land.
- [x] Implement API-key auth when `apiKeyAuthentication` is advertised (optional field in
      setup; `apiKey` replaces `u`/`t`/`s`).
- [ ] Trust-on-first-use for self-signed TLS, with an explicit HTTP warning in setup.
      Deferred to Phase 10: it needs a trust manager plus a stored fingerprint per host.
- [x] Contract tests: each error class, capability JSON, missing extension, API-key URL.

**Done when:** a wrong password, a dead server, and a non-Subsonic server each produce a
distinct message, and no optional endpoint is called on a server that does not advertise it.
TOFU TLS is the one remaining item.

---

## Phase 3 — Real server-side paging

- [x] Pass `offset`/`size` to `getAlbumList2` and `search3` (`songOffset`, `artistOffset`,
      `albumOffset`). `getArtists`, `getPlaylist` and `getStarred2` have no offset/size in the
      API: they are fetched once, cached for 30 s and sliced locally.
- [x] Fix `SubsonicLibrary.items` (albums/artists/playlists) and `SubsonicSearch.*`.
- [x] Stop re-sorting truncated sets; `getAlbumList2?type=alphabeticalByName` sorts on the
      server, artists and playlists keep the server order.
- [x] Cache the starred set briefly and invalidate on `star`/`unstar` instead of fetching it
      in every `contains()` call.
- [ ] Add a `getMusicFolders` selector when the server exposes more than one folder.
      Deferred: it needs a Settings row and a folder-scoped `musicFolderId` on every call.
- [x] Tests: page 2 of a 250-album library, page 2 of a 250-result search, >100-song
      playlist, one `getStarred2` for contains + liked songs, cache dropped on a mutation.

**Done when:** scrolling past the first page returns the next items on a 250-item fixture.

---

## Phase 4 — Close the dead ends

- [x] Genre page: `getAlbumList2?type=byGenre` and `getSongsByGenre` behind
      `SubsonicHome.category`; the existing category screens now get Albums and Songs shelves.
- [x] Playlist search: `SubsonicSearch.playlists` filters the server's list; the Playlists
      tab and `all()` are no longer empty.
- [x] Ratings: `setRating` is wired behind `ServerCapabilities.RATINGS`, `Track.rating` reads
      `userRating`, the client clamps 0..5, and both song sheets show a 1–5 star row (tap the
      current value to clear it).
- [x] Make every remaining stub return `Unsupported` and hide its entry point (podcasts,
      Jam, Blends; Connect, friends and history are already hidden in Subsonic mode).
- [x] Tests: genre albums/songs, playlist search, rating round-trip, Jellyfin rating skip.
      Hidden-entry-point tests come with the stub cleanup.

**Done when:** tapping every visible row on Home/Search/Library produces content or a
clear "not supported by this server", never an empty screen.

---

## Phase 5 — Discovery

- [x] Instant mix from a track, album and artist: `TrackExtras.similarTracks` uses
      `getSimilarSongs2`; an album seed uses its first song, an artist seed its top song.
      The server has no radio playlist id, so "Go to song radio" stays a Spotify-only route.
- [x] "For you" shelf on Home from `getRandomSongs`; "Recently played" from
      `getAlbumList2?type=recent`; "Frequently played" already covered most played.
- [x] "More like this" on the album page: `similarAlbums` maps `getSimilarSongs2` rows to
      their distinct albums.
- [x] Tests: similar tracks (track/album/artist seeds), seed dropped from its own radio,
      Jellyfin skips the call, random songs, distinct similar albums, empty answer.

**Done when:** a user can start radio from any song and get a sensible queue.

---

## Phase 6 — Playback quality

- [x] ReplayGain: `Track.replayGain` maps the OpenSubsonic fields, `ReplayGainProcessor`
      applies track/album gain capped by the peak in `SubsonicPlayer`, and the Equalizer
      screen has "Normalize volume" and "Use album gain".
- [x] `EqualizerProcessor` moved to `api-subsonic` so the Subsonic player uses the same
      chain as the local player: equalizer, ReplayGain, skip-silence.
- [ ] Crossfade in `SubsonicPlayer` (the local player's two-player ramp is not shared).
- [ ] Buffer tuning + pre-cache the next track on Wi-Fi.
- [ ] Audio offload where the device supports it.
- [x] Tests: ReplayGain math (track/album/peak cap), `replayGain` mapping, the moved
      equalizer tests. A crossfade-ramp test waits for crossfade.

**Done when:** the Equalizer screen changes the sound of a streamed Subsonic track, and a
mixed-loudness album plays at a steady level.

---

## Phase 7 — Offline 2.0

- [x] "Keep this playlist/album offline" with automatic sync when the server changes
      (`OfflineCollections.sync`/`refreshAll`, already present and kept).
- [x] Wi-Fi-only downloads, storage quota (default 8 GB, 500 MB reserve), metered warning.
- [x] Resumable Subsonic downloads (`.part` + HTTP `Range`) and two retries with backoff.
- [x] Re-validation after a server re-scan follows the list sync: changed ids leave as
      `gone`, new ones are downloaded; singles are re-downloaded on demand.
- [x] Auto-cleanup of files removed from the server (the sync's `gone` set).
- [x] Tests: download budget (limit, reserve, unlimited). A resumable-transfer test needs a
      local HTTP fixture; it is the one gap in this phase.

**Done when:** a playlist kept offline stays in sync across a server re-scan without user
action.

---

## Phase 8 — Server-native extras

- [~] Bookmarks (`getBookmarks`, `createBookmark`, `deleteBookmark`) for long tracks:
      backend done and gated; the player does not create them on pause yet.
- [x] Shares (`getShares`, `createShare`, `deleteShare`): the song sheets copy the server's
      share URL, with a toast on success/failure.
- [x] Scan status (`getScanStatus`, `startScan`) in Settings, both looks (the Immersive row
      starts a scan; the Classic row shows the status and starts one).
- [~] Now playing (`getNowPlaying`) and avatar (`getAvatar`): the account avatar is wired;
      the now-playing list has no screen yet.
- [x] Tests for bookmarks, shares, scan, now playing, and a Jellyfin bridge that is not
      asked for bookmarks.

**Done when:** every extra is invisible on a server that does not support it.

---

## Phase 9 — Platform

- [ ] Cast a Subsonic `stream` URL (the `cast/` module is Spotify-only today).
- [ ] Wear OS: media controls + a downloaded subset.
- [ ] Verify `NowPlayingWidget` in Subsonic mode; add a shuffle-play action.
- [ ] Quick Settings tile.
- [ ] Automotive/two-pane polish; TalkBack and font-scale pass.

**Done when:** the phone, tablet, car, watch and Cast all play the same queue.

---

## Phase 10 — Security and privacy

- [ ] Move the server password to `EncryptedSharedPreferences` / Keystore.
- [ ] Replace global `usesCleartextTraffic` with a scoped `networkSecurityConfig`.
- [ ] Clear downloads and cached credentials on logout.
- [ ] Optional biometric lock.
- [ ] Tests: no password in prefs/logs, HTTP warning shown, TLS pinning/TOFU.

**Done when:** `adb shell run-as … cat shared_prefs/*` shows no plaintext password.

---

## Phase 11 — Bug control (the system)

- [ ] Contract tests in `FakeSubsonic`: paging, auth failure, malformed JSON, missing
      fields, transcode fallback, timeouts, every endpoint the app calls.
- [ ] Capability-matrix CI: run the smoke suite against Navidrome and Jellyfin+Subfin
      fixtures (version-pinned), plus Gonic/Airsonic when available.
- [ ] Typed-error tests: one test per error class.
- [ ] Performance budget from `docs/audit-2026-10-18.md`: cold start, tap→sound, idle
      calls/min; nightly script with thresholds.
- [ ] Fault injection: airplane mode, 500s, slow 3G, expired token, server restart
      mid-stream, clock skew, self-signed TLS.
- [ ] Migration tests: upgrade from the previous release APK; downloads/queue/settings
      survive.
- [ ] Opt-in crash/diagnostics with redacted logs + "copy debug report".
- [ ] Release gates: lint, detekt/ktlint, R8 build, signed APK, screenshot tests.
- [ ] Issue template asking for server type/version + `ping` + debug log; severity labels.
- [ ] Rule enforced in review: no stub returns empty data.

**Done when:** CI fails on a contract, error, paging or performance regression, and a bug
report arrives with the server facts needed to reproduce it.

---

## Phase 12 — Docs and release

- [ ] Update `ROADMAP.md` (M7) to point here; mark M6 leftovers.
- [ ] Update `PORTING.md`, `docs/NAVIDROME-COMPAT.md`, `docs/TESTING.md`.
- [ ] Publish the source (GPL-3.0) and the Navidrome app-catalogue entry.
- [ ] Tag `vX.Y.Z` on `main`; publish signed phone + automotive APKs.
- [ ] Verify the release checklist end to end.

---

## Progress log

| Date | Phase | What landed | Commit |
|---|---|---|---|
| 2026-11-06 | 0 | `PLAN.md` created; JVM tests green; debug APKs build (115 MB each, unminified) | `docs(plan)` |
| 2026-11-06 | 1 | Packages, classes, resources, manifests, tools and tests renamed to Sonora; prefs migration added; attribution kept in docs and license headers | `refactor(rename)!` |
| 2026-11-06 | 2 | Typed Subsonic failures, cached capability probe, precise setup errors, optional-call cache, API-key auth; 8 new contract tests | `feat(subsonic)` |
| 2026-11-06 | 3 | Server-side album/search paging, 30 s caches for artists/playlists/starred/playlist tracks, 5 paging tests | `fix(subsonic)` |
| 2026-11-06 | 4 | Genre albums/songs, playlist search, rating backend + `Track.rating`, 5 browse tests | `feat(subsonic)` |
| 2026-11-06 | 4 | 1–5 star row in the Classic and Immersive song sheets, capability-gated | `feat(ui)` |
| 2026-11-06 | 4 | Podcast/Jam/Blends stubs raise typed `Unsupported`; entry points stay hidden | `refactor(subsonic)` |
| 2026-11-06 | 5 | `getSimilarSongs2` radio/autoplay, "For you" and "Recently played" Home shelves, 3 tests | `feat(subsonic)` |
| 2026-11-06 | 5 | Album/artist seeds for the mix, `similarAlbums` for "More like this", 5 more tests | `feat(subsonic)` |
| 2026-11-06 | 6 | ReplayGain + equalizer + skip-silence on Subsonic streams, settings rows, gain tests | `feat(subsonic)` |
| 2026-11-06 | 7 | Resumable downloads, retry/backoff, download quota, Wi-Fi-only setting, budget test | `feat(offline)` |
| 2026-11-06 | 8 | Bookmarks/shares/scan/now-playing extras, share-link and scan UI, avatar, 6 tests | `feat(subsonic)` |

## Decisions and risks

- **Rename first.** It touches every file; doing it before feature work avoids a second
  sweep and keeps later diffs readable.
- **Extension wire names stay.** `myspoti.spotify.*` / `myspoti.attest` are protocol
  identifiers; renaming them is a compatibility break, not a cosmetic change.
- **CI secrets stay compatible.** The build reads `SONORA_*` first and falls back to
  `MYSPOTI_*` so existing repository secrets keep working.
- **Capability probe is the keystone.** It turns silent `runCatching` fallbacks into
  explicit, testable behaviour.
