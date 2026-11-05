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
./gradlew :app:assembleDebug            # both variants
./gradlew :app:assembleMobileDebug      # phone + Android Auto projection
./gradlew :app:assembleAutomotiveDebug -Pabis=x86_64   # Android Automotive OS
```

JDK 17 and Android SDK 37. Two flavors share the code: **mobile** (debug package
`com.adlen.sonora.dev`, APK in `app/build/outputs/apk/mobile/debug/`) and
**automotive** (no launcher activity, `.car` id — `…/apk/automotive/debug/`, used on the car
emulator; see `docs/TESTING.md`).

## Branches and releases

- **`develop`** (default): all work; CI runs the JVM tests and builds the debug APKs.
- **`main`**: release-ready only, protected; updated by a pull request from `develop`.
- A tag **`vX.Y.Z` on `main`** runs the `Release` workflow: it refuses a tag that is not on main,
  builds the signed phone and automotive APKs with the keystore secrets and publishes them.
  See [`docs/RELEASING.md`](docs/RELEASING.md).

```bash
git checkout develop && git pull
git checkout main && git pull && git merge --no-ff develop && git push origin main
git tag v1.2.0 && git push origin v1.2.0
```

## Test device: the emulator

Device checks run on the **official emulator**, not on a physical phone:

```bash
tools/fetch-emulator-libs.sh   # once: host libraries WSL lacks
tools/create-avds.sh           # once: jellyfin (phone) and jellyfin_tablet
tools/emulator.sh              # phone AVD, window on the Windows desktop (WSLg)
tools/emulator.sh jellyfin_tablet
```

The emulator reaches the test Navidrome on the host at `http://10.0.2.2:4533`
(no `adb reverse`). `adb devices` then shows `emulator-5554`; screenshots with
`adb -s emulator-5554 exec-out screencap -p`.

See [`docs/TESTING.md`](docs/TESTING.md) for the full matrix (tablet, Wear, Android Auto
via the Desktop Head Unit).

## Docs

- [`ROADMAP.md`](ROADMAP.md) — milestones.
- [`PORTING.md`](PORTING.md) — what the Subsonic port does and what is still stubbed.
- [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) — attribution.
- [`docs/TESTING.md`](docs/TESTING.md) — emulators, the test matrix, Android Auto.
- [`docs/RELEASING.md`](docs/RELEASING.md) — branches, tags, secrets and the release workflow.
- [`docs/NAVIDROME-COMPAT.md`](docs/NAVIDROME-COMPAT.md) — the client against Navidrome's Subsonic API docs.
