# 01 — App profile

## Identity

| Property | Value |
|---|---|
| File | `spotify.apk` (143,009,050 bytes ≈ 136 MiB) |
| Package | `com.spotify.music` |
| Version | `9.1.92.3058` (versionCode `147346334`) |
| minSdk / targetSdk | 24 / 37 (compileSdk 37) |
| Application class | `com.spotify.music.SpotifyApplication` |
| Base theme | `@style/Theme.Glue` (Encore / “Paste” theme family) |
| Build system | Bazel (`assets/empty_asset_generated_by_bazel~`), R8-obfuscated |
| ABIs | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| Dex files | 17 (`classes.dex` … `classes17.dex`) |
| Classes | 69,494 total; 10,089 under `com.spotify.*` |
| Resources | 11,189 strings, 899 colours, 1,433 layouts (+variants), 2,238 drawables |

## Components (decoded manifest)

| Type | Count |
|---|---|
| `<activity>` | 84 |
| `<activity-alias>` | 4 |
| `<service>` | 38 |
| `<receiver>` | 32 |
| `<provider>` | 9 |
| `<uses-permission>` | 56 |

### Launcher / entry points

* `com.spotify.music.SpotifyMainActivity` — `MAIN` + `LAUNCHER`, also handles
  `MUSIC_PLAYER`, `MEDIA_PLAY_FROM_SEARCH`, NFC `spotify:` tags, `content://audio/*`.
* `com.spotify.music.MainActivity` (alias) — legacy launcher alias.
* `com.spotify.music.main.AppIconEmeraldGreen` (alias) — alternate icon pack.
* `com.spotify.tome.pageactivity.PageActivity` — hosts server-driven “Tome” pages
  (browse, album, artist, playlist pages are page payloads, not native screens).
* `com.spotify.nowplaying.musicinstallation.NowPlayingActivity` — full-screen player.

### Media / playback components

* `com.spotify.mediabrowserservice.medialibraryservice.SpotifyMediaLibraryService`
  — `MediaLibraryService` + `MediaBrowserService` (Android Auto, assistant, browse tree).
* `com.spotify.mediabrowserservice.mediabrowserservice.SpotifyMediaBrowserService`
  — legacy `MediaBrowserService`.
* `com.spotify.mediasession.mediasession.receiver.MediaButtonReceiver`
  — wired `MEDIA_BUTTON`.
* `com.spotify.connect.mediarouteproviderapi.SpotifyMediaRouteProviderService`
  — `MediaRoute2ProviderService` (Spotify Connect / output switcher).
* `com.spotify.carapplibrary.androidauto.AndroidAutoService` — Car App Library media.
* `com.spotify.wear.wearabledatalayer.SpotifyWearableListenerService` — Wear OS.
* `com.spotify.widget.widget.widgetimpl.SpotifyWidget`,
  `…proactiveplatforms.npvwidget.NpvWidgetProvider`,
  `…miuidynamicnpvwidget.MiuiDynamicNpvWidgetProvider` — home-screen widgets (Glance).
* `com.spotify.music.alarmlauncher.SpotifyAlarmLauncherService` — alarm integration.
* `com.spotify.playbacknotifications.*` actions — notification transport controls
  (`SKIP_PREV`, `PAUSE`, `RESUME`, `SKIP_NEXT`, shuffle, 15 s seek).

### Other notable components

* `com.spotify.xrprojected.ui.XrProjectedActivity` — Android XR projected mode.
* `com.spotify.share.resultactivity.api.ShareResultActivity` — share-sheet target.
* `com.spotify.appauthorization.sso.AuthorizationActivity` — OAuth/SSO.
* Firebase Messaging, Crashlytics, Play Billing, WorkManager, Room.

## Permissions actually relevant to a music client

`INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `BLUETOOTH_CONNECT`,
`BLUETOOTH_SCAN`, `MODIFY_AUDIO_SETTINGS`, `READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE`,
`WRITE_EXTERNAL_STORAGE`, `RECEIVE_BOOT_COMPLETED`, `ACCESS_LOCAL_NETWORK`,
`FOREGROUND_SERVICE_DATA_SYNC` (downloads), `VIBRATE`, `NFC`, `CAMERA` (scan codes).

Everything else is Spotify business: ads/attribution, Play Billing, Samsung/Sony
account linking, Google Meet live sharing, Waze return, Facebook SDK, `AD_ID`, etc.

## Native libraries

| Library | Size | Purpose |
|---|---|---|
| `liborbit-jni-spotify.so` | 25 MB | **Spotify playback engine** (JNI). Strings inside prove gapless, crossfade, fade-out, pitch factor, `audio.normalize_v2`, audio-driver error reporting. |
| `libcomScore.so` | 1.8 MB | Audience measurement SDK |
| `libcrashlytics*.so` | ~1.1 MB | Crash reporting |
| `librootChecker.so` | 7 KB | Root/tamper detection |
| `libandroidx.graphics.path.so` | 10 KB | AndroidX path |
| `libimage_processing_util_jni.so` | 29 KB | ML Kit image processing |

**Implication:** audio is decoded/rendered by a proprietary native engine. A clone must
use ExoPlayer/Media3 instead, which changes only the internals — the UX contract
(gapless, crossfade, normalisation, speed, EQ) is reproducible with Media3 + audio effects.

## Deep links present (patterns worth mirroring)

* `https://open.spotify.com/{track,album,artist,playlist,show,episode,user,…}/{id}`
* `spotify:{track,album,artist,playlist,…}:{id}` (+ `spotify:` NFC tags)
* `spotify://…` for internal navigation, `spotify-auth-music://callback/...`
* App Links for `spotify.link`, Branch links, `accounts.spotify.com/...`

For the clone: define `echo://track/{id}` / `echo://album/{id}` and optionally map
`https://host/...` to in-app routes. Also accept `content://` audio intents and
`MEDIA_PLAY_FROM_SEARCH`.

## What is NOT in the APK

* No user credentials, no API keys, no server config — all server-driven at runtime.
* No Subsonic support at all; every network call is Spotify protobuf (“esperanto”)
  over their own backend (`com.spotify.cosmos`, `com.spotify.messaging`, …).
* No local-library scanner worth reusing (there is `com.spotify.localfiles`, but it
  feeds Spotify's own “local files” feature).
