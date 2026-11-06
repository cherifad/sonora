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
- [x] Add a `getMusicFolders` selector when the server exposes more than one folder: the
      choice is stored, `musicFolderId` is added to later calls, and the row is hidden with
      zero or one folder.
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

- [x] Bookmarks (`getBookmarks`, `createBookmark`, `deleteBookmark`) for long tracks:
      pausing a track of at least ten minutes (away from its edges) writes one, and a new play
      resumes it unless the caller asked for an explicit position.
- [x] Shares (`getShares`, `createShare`, `deleteShare`): the song sheets copy the server's
      share URL, with a toast on success/failure.
- [x] Scan status (`getScanStatus`, `startScan`) in Settings, both looks (the Immersive row
      starts a scan; the Classic row shows the status and starts one).
- [x] Now playing (`getNowPlaying`) and avatar (`getAvatar`): the account avatar is wired,
      and Settings opens a sheet with what other clients are playing.
- [x] Tests for bookmarks, shares, scan, now playing, and a Jellyfin bridge that is not
      asked for bookmarks.

**Done when:** every extra is invisible on a server that does not support it.

---

## Phase 9 — Platform

- [ ] Cast a Subsonic `stream` URL: needs the Google Cast SDK and a receiver; the existing
      `cast/` module speaks Spotify's protocol, not Cast. Deferred to its own change.
- [ ] Wear OS: media controls + a downloaded subset. Needs a new module and dependencies.
- [x] `NowPlayingWidget` drives `SongPlayer`, so it covers Subsonic streams; it now has a
      shuffle button that reflects the queue's mode.
- [x] Quick Settings tile: play/pause, state follows the player.
- [~] Automotive/two-pane polish: the existing layouts are unchanged; the TalkBack and
      font-scale pass is still to do.

**Done when:** the phone, tablet, car, watch and Cast all play the same queue.

---

## Phase 10 — Security and privacy

- [x] Move the server password and API key to `EncryptedSharedPreferences` / Keystore, with a
      one-time migration from the old plain file (which is emptied).
- [~] Replace global `usesCleartextTraffic` with a scoped `networkSecurityConfig`.
      Android's config cannot whitelist arbitrary private IP ranges, which a self-hosted LAN
      client needs; the manifest keeps cleartext and the setup screen warns on `http://`.
- [x] Clear the cached server credentials on logout. Downloads stay parked, encrypted, under
      the account (deleting them on sign-out would silently lose offline content).
- [ ] Optional biometric lock.
- [~] Tests: the HTTP warning is visible; there is no Context-level test for the encrypted
      file yet, and TOFU TLS is still Phase 10's open item.

**Done when:** `adb shell run-as … cat shared_prefs/*` shows no plaintext password.

---

## Phase 11 — Bug control (the system)

- [x] Contract tests in `FakeSubsonic`: paging, auth failure, malformed/empty JSON,
      timeouts, star/unstar ids, playlist create/update/delete, scrobble, play queue.
      A few read endpoints (`getGenres`, the player's internal calls) are still only
      exercised through their screens.
- [~] Capability-matrix CI: `tools/server_contract.py` checks the required endpoints and
      reports OpenSubsonic extensions against any live server; the Docker Navidrome/Jellyfin
      job and the emulator run are still manual.
- [x] Typed-error tests: auth, not-found, HTTP 500, protocol, empty answer, network refusal,
      timeout, and `Unsupported`.
- [~] Performance budget from `docs/audit-2026-10-18.md`: `tools/tap_latency.py` and
      `tools/playback_test.py` measure it; no nightly threshold gate yet.
- [~] Fault injection: HTTP 500, malformed/empty bodies and a timeout are unit-tested;
      airplane mode, clock skew and self-signed TLS stay manual.
- [ ] Migration tests: upgrade from the previous release APK; downloads/queue/settings
      survive.
- [x] Opt-in diagnostics: `Logbook.export` builds the redacted report the Settings row shares;
      it now includes the server type/version and OpenSubsonic extensions. No remote reporter
      is sent automatically; the report is only exported when the user asks.
- [x] Release gates: the R8 release build (with lint-vital) now runs in CI; the signed APK
      stays in the release workflow.
- [x] Issue template asking for server type/version + `ping` + debug log.
- [x] Rule enforced in review: no stub returns empty data (see `AGENTS.md`).

**Done when:** CI fails on a contract, error, paging or performance regression, and a bug
report arrives with the server facts needed to reproduce it.

---

## Phase 12 — Docs and release

- [x] Update `ROADMAP.md` (M7) to point here; mark M6 leftovers.
- [x] Update `PORTING.md`, `docs/NAVIDROME-COMPAT.md`, `docs/TESTING.md`,
      `docs/RELEASING.md` and the README feature list.
- [ ] Publish the source (GPL-3.0) and the Navidrome app-catalogue entry. Needs the public
      repository and store screenshots; not done here.
- [ ] Tag `vX.Y.Z` on `main`; publish signed phone + automotive APKs. Belongs to the release
      flow (`bumpv`/`Release`), not to this work.
- [~] Verify the release checklist end to end: the list is in `docs/RELEASING.md`; the device
      pass (clean install, upgrade, offline, tile/widget) is still to do.

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
| 2026-11-06 | 10 | Encrypted server credentials + migration, logout clears them, HTTP warning | `feat(security)` |
| 2026-11-06 | 11 | Contract/fault tests, R8 release gate in CI, bug template, review quality gate | `chore(ci)` |
| 2026-11-06 | 9 | Quick Settings play/pause tile and a widget shuffle action | `feat(platform)` |
| 2026-11-06 | 12 | ROADMAP M7, PORTING/compat/testing/releasing docs, README features, release checklist | `docs(release)` |
| 2026-11-06 | 11 | `tools/server_contract.py`: live-server endpoint/capability probe | `test(tools)` |
| 2026-11-06 | 8 | Bookmark long tracks on pause, with a threshold test | `feat(subsonic)` |
| 2026-11-06 | 8 | Resume a long track from its bookmark on a new play | `feat(subsonic)` |
| 2026-11-06 | 8 | Now-playing sheet in Settings, both looks | `feat(ui)` |
| 2026-11-06 | 3 | Multi-library selector (`getMusicFolders` + `musicFolderId`), both looks | `feat(subsonic)` |
| 2026-11-06 | 11 | Debug report carries server type/version and OpenSubsonic extensions | `feat(diagnostics)` |

## Decisions and risks

- **Rename first.** It touches every file; doing it before feature work avoids a second
  sweep and keeps later diffs readable.
- **Extension wire names stay.** `myspoti.spotify.*` / `myspoti.attest` are protocol
  identifiers; renaming them is a compatibility break, not a cosmetic change.
- **CI secrets stay compatible.** The build reads `SONORA_*` first and falls back to
  `MYSPOTI_*` so existing repository secrets keep working.
- **Capability probe is the keystone.** It turns silent `runCatching` fallbacks into
  explicit, testable behaviour.
