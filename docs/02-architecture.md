# 02 — Architecture (as found) and what to reuse

## Runtime architecture

```
SpotifyApplication
└── SpotifyMainActivity                     (single activity)
    └── MainLayout  (vertical_main.xml / large_main.xml)
        ├── fragment_container              ← tab content (Home/Search/Library/Create/Premium)
        │   └── Tome PageActivity pages     ← server-driven page JSON → components
        ├── bottom_gradient                 ← 48dp black fade above mini player
        ├── now_playing_view_container      ← mini player (persistent, viewstub)
        ├── now_playing_view_attachments    ← e.g. “up next” bubbles above mini player
        ├── navigation_bar                  ← custom Encore bottom bar
        ├── snackbarContainer               ← anchored above mini player
        ├── overlay / bottom-sheet host     ← queue, context menus, create, sort
        └── now playing full screen         ← NowPlayingActivity (own window, slides up)
```

* **One activity** for the shell. Full-screen player, lyrics, share, checkout, scanner
  are separate activities; almost everything else is a fragment or bottom sheet.
* **Server-driven UI**: `com.spotify.tome.*` (“Tome”) fetches page payloads and maps
  them to native component types. Browse/album/artist/playlist pages are largely
  generic page renderers. The clone should use the same idea — a small set of
  reusable sections (shelf, track list, header, chips) fed by the Subsonic repo.
* **Design system**: Encore (`EncoreThemeInstaller` sets theme attrs at runtime; ~all
  widgets are `Encoremobile` views). 46 layouts embed `ComposeView`, mostly newer
  features; the core list/player surfaces remain View-based.
* **Responsive**: three shell variants — `vertical_main` (phone), `horizontal_main`
  (landscape/tablet), `large_main` (tablet/desktop-class, now playing as a permanent
  right-hand panel with a floating rounded content card). See `03-design-system.md`.

## Playback architecture

```
MediaSessionService  ── SpotifyMediaLibraryService / SpotifyMediaBrowserService
      │                    └── browse tree for Auto / Assistant / Wear
      ├── MediaSession (Media3)  ←→ notification, media buttons, Bluetooth
      ├── Orbit native engine (liborbit-jni-spotify.so)
      │      gapless · crossfade · fade-in/out · normalisation · pitch/speed
      ├── MediaRoute2 provider (Spotify Connect / output switcher)
      └── Playback notification + widget + Car App service
```

The queue lives in the player service, is exposed to UI through a player state
observer, and is persisted/synced to the backend. Relevant feature strings found:

* shuffle / smart shuffle (with “recommendations in your queue”),
* repeat off/all/one,
* sleep timer (blocks Connect while active),
* playback speed, 15 s skip back/forward,
* crossfade (0–12 s), gapless playback, mono audio,
* equalizer (6 bands: 60 Hz, 150 Hz, 400 Hz, 1 kHz, 2.4 kHz, 15 kHz + presets),
* audio quality: Low / Normal / High / Very High / Lossless, per-network
  (Wi-Fi vs cellular), download quality, auto-adjust, data saver,
* offline mode + “offline access”, download centre, storage/cache limits,
* “ban / don’t play this” per track,
* scrobbling / recently played / play counts.

## Data & networking stack (evidence)

* **OkHttp** + **Retrofit** (RxJava3 adapter) — `okhttp3`, `retrofit2` retained in dex.
* **Protocol Buffers** — generated `com.spotify.*.proto` classes in native lib and dex;
  Spotify’s “esperanto” schemas for metadata/playback/autoplay/download.
* **RxJava3** (`retrofit2.adapter.rxjava3`), coroutines, Flow.
* **Dagger / dagger.android** (Hilt not clearly present; `Ldagger/android`).
* **Room** (`androidx.room`, `room-rxjava3`) for local caches.
* **Compose** (ui 314 classes, foundation/runtime/material3 minimal) embedded in Views.
* **Media3** session/exoplayer (157/79 classes) as the session layer.
* **Firebase** (Crashlytics, Messaging, Sessions), **Play Billing**, **WorkManager**,
  **Glance** widgets, **Car App Library**, **Android XR projected**.

## Module map (top `com.spotify.*` packages by class count)

```
737 campfire         social/chat + listening parties
731 messaging        in-app messaging, slates, upsells
541 cosmos           Spotify's RPC/protobuf transport
375 remoteconfig     server-driven feature flags
277 player           player state, queue, playback controls
277 extendedmetadata metadata enrichment
193 playlist / 184 playlist4
184 mobius           state management framework
177 messages
142 watchfeed        video/tiktok-style feed
130 payment / 188 checkout
126 kidsview / 126 connectivity
115 highlightsstats
110 widgets
100 encoreconsumermobile
 91 voiceassistants
 85 localfiles
 83 contentagnostic
 81 home
 78 casita / 76 rcs / 76 musicquizservice / 76 dj
 75 pam / 72 searchview / 70 authentication / 66 scrollsita
 62 automix / 60 share / 60 ads / 58 playlistmixing / 58 allboarding
 57 encoremobile / 57 connect
 54 betamax (video player)
 51 profile / 51 pendragon (podcasts)
 47 wrapped / 47 offline_esperanto / 47 concertpageview
 45 lyrics / 45 listeningactivity
 39 playbacknative
```

For a Subsonic client the useful partitions are: shell/mainlayout, nowplaying,
nowplayingqueue, nowplayingmini, collection (library), searchview, home, playlist,
lyrics, offline/downloads, settings, connect (optional). Everything social, ads,
checkout, messaging, podcast-exclusive and AI is out of scope.

## Mapping the architecture to the clone

| Spotify piece | Clone replacement |
|---|---|
| Tome server-driven pages | Repository + typed `HomeSection` models; still server-shaped but room-local |
| Cosmos/esperanto protobuf backend | Subsonic REST + kotlinx.serialization |
| Orbit native playback | Media3 ExoPlayer + `MediaSessionService` |
| Spotify Connect / MediaRoute2 | Optional: local `MediaRoute2ProviderService`; Jellyfin remote-control later |
| Spotify auth (OAuth/SSO) | Server URL + username/password (token+salt) or API key |
| Room caches | Room (library, playlists, downloads, play queue) |
| Encore Views | Compose design system reproducing the same tokens |
| Glance widgets / Auto / Wear | Same approach, phase 2 |

The rest of the Spotify stack (billing, ads, social, ML recommendations) has no
Subsonic equivalent and should be deleted rather than stubbed.
