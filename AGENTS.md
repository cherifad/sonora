# jellyfin-audio

The purpose of this project is to clone spotify app and to make it complient with subsonic api (mainly for Jellyfin)

## Reverse-engineering docs

Start at [`docs/README.md`](docs/README.md). The APK has been decoded into `re/`
(`apktool` resources/manifest, `jadx` sources, extraction scripts). Design tokens are
in [`docs/design-tokens.json`](docs/design-tokens.json).

Do not ship Spotify fonts, icons, logo, package name or `spotify:` deep links — see the
legal note in `docs/README.md`.
