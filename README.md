# Jellyfin Audio

An Android **Subsonic / OpenSubsonic** music client for **Jellyfin (Subfin)** and
**Navidrome**, built on the GPL-3.0 [Myspoti](https://github.com/kota-zo/myspoti) UI with
a Subsonic backend (Media3) in place of the Spotify Web Player.

> **License: GPL-3.0.** See [`LICENSE`](LICENSE) and
> [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md). Myspoti, Spotui, BitChord and the
> other upstream projects are credited there. The original Myspoti README, AGENTS notice
> and third-party notices are kept in [`docs/upstream/`](docs/upstream/).

## Status

- [x] Subsonic backend (`api-subsonic/`) behind the Myspoti `MyspotiApi` contract.
- [x] Home, Search, Library, Album, Artist, Now Playing, Queue.
- [x] Playback through Media3 `ExoPlayer` (streaming, seek, queue, next/previous).
- [x] First-run server setup and Settings → Server; Subsonic token auth.
- [ ] Remove the remaining Spotify-only surfaces (Connect, Jam, Blends, podcasts, Canvas,
      Genius, extensions, ads/DRM) — see [`ROADMAP.md`](ROADMAP.md), M2.
- [ ] MediaSession/background playback (M3), playlist CRUD (M4), downloads (M5).

See [`ROADMAP.md`](ROADMAP.md) and [`PORTING.md`](PORTING.md). Screenshots are in
[`docs/screenshots/subsonic-spike/`](docs/screenshots/subsonic-spike/).

## Build

JDK 17 and the Android SDK (platform 37) are required.

```bash
printf 'sdk.dir=/path/to/android-sdk\n' > local.properties
./gradlew :app:assembleDebug

# Debug APK: app/build/outputs/apk/debug/app-debug.apk
# Debug package: app.myspoti.dev
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.myspoti.dev/app.myspoti.MainActivity
```

On first launch, enter the server URL and credentials:

- Jellyfin + Subfin: `http://<host>:8096/subfin/`
- Navidrome: `http://<host>:4533/`
- Android emulator to the host: `http://10.0.2.2:4533/`

The setup screen is also reachable later from **Settings → Server** and the profile menu.

## Layout

```
app/            Compose UI (Myspoti fork)
api/            domain contract (MyspotiApi, models)
api-subsonic/   Subsonic/OpenSubsonic backend + Media3 player
api-spotify/    upstream Spotify engine (kept for reference, not started by the app)
host-android/   Android runtime (MyspotiRuntime, playback service)
host-cdp/       upstream CDP tooling (reference)
extensions/     upstream extension sandbox (reference)
docs/           documentation and screenshots; docs/upstream/ has the original Myspoti docs
licenses/       font and Apache-2.0 licences
tools/          development scripts
```

The `api-spotify`, `host-cdp` and `extensions` modules are still compiled because the
upstream UI references them; removing them is part of M2. The app itself only starts the
Subsonic backend.

## Licence

GPL-3.0. If you distribute a binary, publish the complete corresponding source under
GPL-3.0 and keep the notices. No Spotify assets are shipped.
