# Documentation

- [Spotify 9.1.92 reverse-engineering dossier](reverse-engineering/spotify-9.1.92.md) —
  what the shipped APK contains, how it is built, its screens/design tokens, and
  what to reproduce versus what to replace.
- [Subsonic/Jellyfin client blueprint](architecture/subsonic-client-blueprint.md) —
  architecture, API mapping, UI plan, offline/playback design, milestones, and
  acceptance criteria for the original client.
- [UI fidelity and content mapping](architecture/ui-content-mapping.md) — free
  font/icon/asset substitutions, Subsonic replacements for Spotify's dynamic
  modules, and the explicit drop list.
- `.rea/spotify-evidence.json` (generated, not committed) — exported REA evidence
  bundle (19.6 MB, 130 records) for the analyzed APK. Import it with
  `rea import-evidence-bundle` to re-open the evidence without re-running JADX.
- `spotify-decoded/` (generated, not committed) — apktool output for the analyzed
  APK. Keep it out of version control; regenerate with:

  ```bash
  apktool d --no-src -f -o .rea/spotify-decoded spotify.apk
  ```
