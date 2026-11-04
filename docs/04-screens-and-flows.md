# 04 — Screens and flows

The clone has **five root destinations** and **three overlay surfaces**. Keep this
structure; it is what makes the app feel like Spotify more than any individual screen.

```
Roots (bottom bar)                 Overlays
──────────────────                 ────────
1. Home                            A. Now Playing (full-screen, slides up)
2. Search                          B. Queue / context menus / Create / Sort (sheets)
3. Library                         C. Lyrics (full-screen scroll inside player)
4. Create (+)   (sheet)
5. Premium (free tier only) / Settings
```

For a Subsonic client, replace **Premium** with **Settings** (server, playback, audio
quality, downloads, about). Keep the five-slot bar so the silhouette matches.
The library tab label is `Library` in current builds (`navigationbar_musicappitems_library_title`);
older builds use `Your Library`.

---

## 0. Shell

* Status bar transparent; content draws behind it; top bar scrims on scroll.
* `fragment_container` scrolls under a **48 dp bottom gradient** into the mini player.
* Mini player sits directly above the nav bar, with 8 dp side margins on tablets.
* Snackbars appear above the mini player; sheets cover it.
* Tablets: content becomes a floating rounded card (16 dp radius, 8 dp margin) on
  `#000`; nav bar floats inside, max width 450 dp; Now Playing is a permanent right
  panel (min 360, max 450, 33 % of width) instead of a modal.

---

## 1. Home

**Job:** “what should I play now”, not a library dump.

```
[ Good evening ]                        (optional greeting, BodySmall subdued)
[ All ] [ Music ] [ Podcasts ]          chips, 32 dp, horizontally scrollable
┌──────────────────────────────────────┐
│ Recently played  →                   │  shelf: 2 columns of 64 dp tiles
│ [art] Title            [art] Title   │
├──────────────────────────────────────┤
│ Made for you                         │  shelf: 2-column large cards (art 160)
│ [big art]  Daily Mix 1               │
├──────────────────────────────────────┤
│ Your top mixes / New releases        │  horizontal carousel, 160 dp cards
└──────────────────────────────────────┘
```

* Section headers `TitleMedium` (24/700) with optional “Show all”.
* Card: artwork square 8 dp radius, title 16 dp, subtitle 13 dp `textSubdued`,
  press scale 0.96, long-press context menu.
* Home is **server-shaped**: sections come from a repository, rendered by generic
  section composables (`ShelfSection`, `HeroSection`, `ChipRow`). Do not hard-code
  layouts per section.
* Pull-to-refresh; skeleton shimmer on first load; cached last payload for offline.
* Evidence for this shape: `home_title`, `client_native_home_all`, `home_filter_row_add_feed`,
  `home_hidefromhome_*`, `home_section_header_image_size` (48 dp), `home_feedback_*`.

**Subsonic sources:** recent albums, random picks, genre shelves, “Instant mix”,
starred, newest. See mapping §4.

---

## 2. Search

Two states in one screen.

**Idle:**

```
Search                                  (TitleLarge)
[ 🔍 Search songs, albums, artists ]     rounded 8 dp field, #1F1F1F
Recent searches                         (only locally)
Browse all ───────────────────────────
[colour tile] [colour tile]            2-col grid, 16:9, title 16/700,
[colour tile] [colour tile]            coloured backgrounds, rotated art thumb
```

* Category tiles: 8 dp radius, saturated colours (each genre gets a stable colour
  derived from its name), art rotated 25° bottom-right.
* “Browse all” categories for Subsonic: **Genres**, **Years**, **Recently added**,
  **Random**, **Favorites**, **Most played**, **Folders** (if using `getIndexes`).

**Typing:**

* Debounce 250–300 ms, cancel previous request (`search3`).
* Results: “Top result” card (large art + name + type), then Songs (top 4 with
  “See all”), Artists, Albums, Playlists.
* Chips above results to filter: All / Songs / Albums / Artists / Playlists.
* Empty: “No results found for ‘x’”.
* Evidence: `search_default_title`, `search_hint`, `search_tab_title`,
  `filter_chip_title_track/album/artist/playlist`, `search_main_entity_subtitle_profile`.

---

## 3. Your Library

```
Your Library              [+] [⌕] [↕]     header: title + create + search + sort
[ Playlists ][ Artists ][ Albums ][ Podcasts ][ Audiobooks ][ Downloaded ]
┌──────────────────────────────────────┐
│ [art] Liked Songs                    │  pinned smart rows (if enabled)
│ ♥     Playlist · 128 songs           │
│ [art] Album name                     │
│       Artist · Album                 │
└──────────────────────────────────────┘
```

* Header icons from strings: `library_all_header_create_playlist_icon_content_description`,
  `…search_icon…`, `…download_center…`, `…profile_button…`, `…recents_icon…`.
* Views: **list** (rows 64 dp) and **grid** (2 columns, art square + 2 lines), toggled
  from sort sheet; persisted.
* Sort options: Recents, Recently added, Alphabetical, Creator, Custom order (local
  only). Sort sheet also carries the grid/list toggle (`library_sort_row_edit_button`).
* Filter chips are multi-select and combinable; “Downloaded” chip when offline support
  is on.
* Long-press row → context menu (pin, download, edit, delete); swipe-left on a
  playlist → delete (with undo snackbar).
* Empty: “Add music and podcasts” / “Collect your favorites…” + green CTA.
* Evidence: `library_*` strings (72), `library_grid_view_mode_accessibility_label`,
  `library_list_view_mode_accessibility_label`, `library_chip_min_height=32`.

**Subsonic sources:** `getPlaylists`, `getStarred2`, `getAlbumList2` by type,
`getArtists`, `getGenres`. “Liked Songs” = `getStarred2` (songs).

---

## 4. Create (＋)

Modal bottom sheet, drag handle, three large rows:

```
Create
[ ▣ ] Playlist        Create a playlist with songs or episodes
[ ⧉ ] Folder          Organise your playlists            (local only)
[ ⚡ ] Jam / Blend     optional, hide for self-hosted
```

For the clone: **Playlist** and **Folder** only. Playlist → name/description sheet →
`createPlaylist`; Folder → local Room grouping (Subsonic has no folders).

Evidence: `create_playlist`, `create_playlist_create_button`,
`createmenu_navigationbar_item_open_action_label`, `library_all_header_create_playlist_in_folder_icon_content_description`.

---

## 5. Settings (replaces Premium tab)

Grouped list, section headers 13/700 uppercase `textSubdued`, rows 56–72 dp with
leading icon, trailing value/chevron.

```
Account
  Server URL, username, connection status, sign out
Playback
  Streaming quality (Wi-Fi / cellular), download quality
  Gapless playback, crossfade (0–12 s), mono audio, equalizer
  Autoplay / “keep playing similar”, normalize volume
Audio & Downloads
  Download over cellular, storage used, clear cache, download center
Notifications
  Playback notification, new releases
Appearance / Library
  Language, grid/list default, show “Liked Songs”
About
  Version, open-source licences, server capabilities
```

Evidence: 543 `settings_*` strings, including every quality/crossfade/gapless/equalizer
label quoted in `02-architecture.md`.

---

## 6. Album

```
[←]                                     top bar over gradient
        [ 232 dp artwork ]
        Album title (TitleLarge, 2 lines)
        [avatar] Artist · 2021
        12 songs · 47 min
        [▶ 48 green] [shuffle] [download] [⋯]
──────────────────────────────────────
 1  Song one                    3:42  ⋯
 2  Song two                    4:01  ⋯
 ...
──────────────────────────────────────
 © 2021 Label · ℗ 2021 Label
```

* Header gradient from artwork; artwork shrinks into a 48 dp thumbnail in the top bar
  on scroll (collapsing toolbar), title moves into the bar.
* Disc separators (“Disc 2”) with a 16 dp album icon (`album_track_row_layout.xml`).
* Multi-select on long-press → selection toolbar (add to queue/playlist, download,
  delete from playlist, share).
* Evidence: `album_track_row_layout.xml`, `TextAppearance.Encore.TitleSmall` for disc
  separators, `artist_releases_albums`, `album_options_menu_add_to_playlist`.

**Subsonic:** `getAlbum` (AlbumID3WithSongs) → header + disc/track list.

---

## 7. Artist

```
[hero image, 320–384 dp, gradient bottom]
Artist name (HeadlineMedium/40)
[✔ verified]  (hide — no Subsonic equivalent)
1,234,567 monthly listeners  (hide or “N albums · M songs”)
[Follow] [Shuffle] [⋯]
──────────────────────────────────────
Popular
 1  Track                       3:42  ⋯
 2  Track                       4:10  ⋯
 3  Track                       2:58  ⋯
 4  Track                       3:20  ⋯
 5  Track                       4:02  ⋯
──────────────────────────────────────
Discography  →                    carousel of albums
About                             bio, genres, links
```

* Tabs on large screens: Music / Events / Merch / Clips (keep **Music** + **About**
  only; Events/Merch have no Subsonic source).
* “Popular” uses `getTopSongs` (OpenSubsonic) or Jellyfin `/Items?sortBy=PlayCount`.
  If unsupported, use the artist's most recent album tracks.
* Evidence: `artist_music_tab_title`, `artist_releases_albums`,
  `artist_carousel.xml`, `artist_details.xml`, `follow_button_*`.

---

## 8. Playlist

* Header: cover collage (4-up) or single cover, title (auto-shrinking 12–24 sp),
  description, owner (“By you” / username), `N songs, 1 hr 12 min`, play / shuffle /
  download / ⋯.
* Track list with drag handles in edit mode; swipe to remove; “Add songs” row at the
  end (only for owned playlists).
* Editable title/description for owned playlists (`updatePlaylist`).
* Evidence: `playlist_all_songs_header_content.xml` (title 24 sp, 64 dp horizontal
  padding, auto-size down to 12 sp), `playlist_all_songs_page.xml`,
  `playlist_header_layout.xml`, 225 `playlist_*` strings.

---

## 9. Now Playing (full screen)

```
        ⌄                 context              ⋮
              [ artwork ]
        Song title                       ♥ (52)
        Artist · Album

        0:42 ───────●────────────── −3:01
        ⤨     ⏮      (▶ 64)      ⏭     ↻

        [device]      [share]      [queue]

  ── scroll cards ──────────────────────────
  Lyrics →                 (Card 8 dp, #1F1F1F)
  About the artist →
  Related songs →
  Credits
```

* Open: tap mini player; 300 ms slide-up; background gradient from artwork colour to
  `#121212`; status bar icons white.
* Close: chevron down, back gesture, or drag down > 25 % → mini player.
* Horizontal swipe on artwork: previous/next.
* Seek bar: 4 dp, grows to 6 dp + thumb on drag; elapsed left, remaining right
  (`np_seekbar_state_description`).
* Long-press artwork on video tracks → fullscreen (skip for audio-only).
* Controls: shuffle / previous / play-pause / next / repeat. Repeat cycles
  off → all → one (`np_state_desc_repeat_*`). Disabled = 30 % alpha.
* Bottom row: Connect/device (optional, hide or “Play on this device”), Share,
  Queue (`npv_screen_queue_button`).
* Extra buttons that appear for podcasts/audiobooks: sleep timer
  (`np_content_desc_sleep_timer`), speed (`np_content_desc_speed_control`),
  15 s skip. Keep these for audiobooks.
* Scroll cards are the differentiator: for Subsonic implement **Lyrics**, **Credits**,
  **Related** (`getSimilarSongs2`), **Album**. Hide “About the artist” if no bio.

---

## 10. Queue

Full-height bottom sheet over the player/shell.

```
        ▔▔▔ (grapple)
Queue                                Edit
Playing next
  [art] Song                    ⋯
  [art] Song                    ⋯
Next from: Album name
  [art] Song                    ⋯
  ...
[ Add songs ]
```

* Current track pinned at top with green title + animated equaliser.
* Edit mode: multi-select, drag handles, “Move up / Remove” toolbar, undo snackbar
  (`queue_undo_action`, `queue_move_track_up_accessibility`, `queue_remove_track_accessibility`).
* Swipe-left removes; drag reorders; tap plays immediately; long-press context menu.
* Empty: “Add to your queue” / “Tap ‘Add to queue’ from a track’s menu”.
* Evidence: `layout_queue_sheet_content.xml`, 54 `queue_*` strings, `EditModeBarContainerView`,
  `StickyHeaderLinearLayoutManager`.

---

## 11. Lyrics

* Entry: “Lyrics” card in Now Playing.
* Full-screen, artwork blurred behind at low opacity, lines 24–32 sp bold when active,
  20 sp `textSubdued` otherwise, auto-scroll with 3-line lead, tap a line to seek.
* If only plain lyrics: centred scrollable text, no sync.
* If none: “Lyrics aren’t available for this song”.

**Subsonic:** `getLyricsBySongId` (OpenSubsonic, Jellyfin), fallback `getLyrics`
(title/artist), then Jellyfin’s `/Audio/{id}/Lyrics`.

---

## 12. Context menus (long-press / ⋯)

**Track**: Add to playlist · Add to queue · Go to artist · Go to album · Like ·
Download · Share · Don’t play this · Song info.
**Album**: Play · Add to queue · Download · Add to playlist · Go to artist · Share · Copy link.
**Artist**: Follow · Shuffle · Go to radio · Share.
**Playlist**: Play · Download · Edit · Delete · Share.

Bottom sheet, 24 dp leading icons, grouped, destructive in `textNegative`, drag handle.
No checkboxes except “Liked”.

---

## 13. Downloads / offline

* Download indicator states: not downloaded (↓), waiting (clock), downloading
  (progress ring), downloaded (green ↓ in circle), error (red !).
* `download_center_title` screen: storage bar, per-item list, pause/resume, remove all.
* Offline banner at the top of Home/Library when `ACCESS_NETWORK_STATE` says offline.
* Playback falls back to downloaded file; if absent, snackbar “You're offline”.

---

## 14. Global flows

### First run (replaces Spotify login)

```
Welcome to Echo
[ Server URL              ]  http://192.168.1.10:8096
[ Username                ]
[ Password                ]  or [ Use API key ]
[ Connect ]
      ↓ ping + getLicense + getOpenSubsonicExtensions
      ↓ detect Jellyfin / Navidrome / Airsonic
[ Choose music folder (optional) ]
      ↓
Home
```

Errors inline: wrong URL, TLS error, bad credentials, server too old, no music library.

### Play a track

tap row → optimistically update queue/now-playing → `stream` starts → mini player
animates in → `scrobble(submission=false)` at start → `scrobble(submission=true)` at
50 %/4 min → next track prefetch (gapless).

### Add to playlist

row ⋯ → Add to playlist → sheet lists playlists + “New playlist” → `updatePlaylist`
(`songIdToAdd`), optimistic insert, undo snackbar.

### Search → play

type → debounce → `search3` → tap song → queue = result list starting at that song.

### Swipe gestures

* Mini player: swipe up = expand, swipe down (when expanded) = collapse,
  swipe left/right = next/prev.
* Queue rows: swipe left = remove.
* Library playlist rows: swipe left = delete.
* Now Playing: horizontal swipe on artwork = next/prev.

### Deep links

`echo://track/{id}`, `echo://album/{id}`, `echo://artist/{id}`,
`echo://playlist/{id}`, `echo://search?q=`, plus `content://` audio intents.
