# jellyfin-audio

A single Android **Subsonic / OpenSubsonic** client, forked from
[Myspoti](https://github.com/kota-zo/myspoti) (GPL-3.0).

## Licence and attribution

The whole repository is **GPL-3.0**. Keep `LICENSE`, `THIRD_PARTY_NOTICES.md` and the
upstream notices in `docs/upstream/` (including `docs/upstream/AGENTS.md`). If you
distribute the app, publish the complete corresponding source under GPL-3.0 and credit
Myspoti, Spotui, BitChord and the other upstream projects.

## Scope

- One client only: the Myspoti-fork UI in `app/` with the Subsonic backend in
  `api-subsonic/`.
- Do not reintroduce a Spotify backend, Spotify assets/trademarks, ads/DRM code, or the
  Spotify login gate.
- The Subsonic/OpenSubsonic API is the source of truth; Jellyfin (Subfin) and Navidrome
  are the targets.

## Build

```bash
./gradlew :app:assembleDebug
```

JDK 17 and Android SDK 37. The debug package is `app.myspoti.dev`.

## Docs

- [`ROADMAP.md`](ROADMAP.md) — milestones.
- [`PORTING.md`](PORTING.md) — what the Subsonic port does and what is still stubbed.
- [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) — attribution.
