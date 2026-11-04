# Third-party notices

## Myspoti — GPL-3.0

This repository contains a fork of **Myspoti** in [`myspoti/`](myspoti/), originally
published at <https://github.com/kota-zo/myspoti> under the **GNU General Public License
version 3**. The original license and the AI-assistant notice are kept in
[`myspoti/LICENSE`](myspoti/LICENSE) and [`myspoti/AGENTS.md`](myspoti/AGENTS.md).

Copyright (C) Kota-zo and the Myspoti contributors.

The fork replaces Myspoti's Spotify Web Player backend with a Subsonic/OpenSubsonic
backend (`myspoti/base/api-subsonic/`), removes the Spotify sign-in gate, and routes
playback through Media3. The exact scope, the list of stubbed features and the build
instructions are in [`myspoti/PORTING.md`](myspoti/PORTING.md).

Because the combined work contains GPL-3.0 code, **the whole repository is distributed
under GPL-3.0**; see [`LICENSE`](LICENSE). If you distribute a binary built from it, you
must provide the complete corresponding source under GPL-3.0, keep the copyright and
license notices, and credit Myspoti.

## Spotify

No Spotify proprietary code, fonts, icons, logos, Lottie files, or other assets are
shipped. The Spotify APK (if present under `spotify.apk` / `re/`) is reverse-engineering
evidence only and is not part of any build. Myspoti's original repository is likewise a
behavioural/visual reference; only its GPL-3.0 code is used, with the notices above.

## Fonts and icons

- **Figtree** — `app/src/main/res/font/figtree_variable.ttf`, SIL Open Font License 1.1
  (`docs/licenses/figtree-OFL.txt`).
- **Material Symbols / Material Icons** — Apache License 2.0, via
  `androidx.compose.material:material-icons-extended`.
- **Inter / Manrope** — used by the Myspoti fork's own assets, SIL Open Font License
  (see `myspoti/base/licenses/`).

## Test-only

- **Navidrome** (GPL-3.0) was used as a local Subsonic test server. It is not shipped.
- **Jellyfin + Subfin** are the intended production backends; they are not shipped.
