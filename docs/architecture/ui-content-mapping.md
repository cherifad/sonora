# UI fidelity and content mapping

This document fixes the rules for the clone:

1. Match Spotify's **layout, spacing, colors, hierarchy and interactions**.
2. Replace proprietary fonts/icons/artwork/Lottie with **free, licensed**
   alternatives.
3. Replace Spotify's dynamic modules with **Subsonic/OpenSubsonic data**.
4. Replace what has an equivalent; **drop** what cannot exist on Subsonic.

The result is a *pixel-close* UI, not a literal asset copy.

## 1. Free asset substitutions

### 1.1 Fonts

| Spotify | Clone | License | Why |
| --- | --- | --- | --- |
| Spotify Mix UI / Mix Title | **Figtree** (variable) | SIL OFL 1.1 | Geometric sans with rounded terminals and a wide weight range; closest free match to Mix/Circular |
| Circular (legacy) | Figtree | SIL OFL 1.1 | Same rationale |
| Spoticon icon font | Material Symbols Rounded | Apache 2.0 | Rounded, simple icon language; no Spotify glyphs |

- Font resource: `app/src/main/res/font/figtree_variable.ttf`
- License: `docs/licenses/figtree-OFL.txt`
- Weights used: 400 (body), 500 (medium), 600 (semibold), 700 (title),
  800 (headline). Variable-font axes are applied in
  `ui/theme/Type.kt`.
- Do **not** ship Spotify Mix, Circular, or `spoticon.ttf`. They are
  proprietary.

### 1.2 Icons

Use `Icons.Rounded.*` from `androidx.compose.material:material-icons-extended`
(Apache 2.0). The current scaffolds already use rounded Home, Search,
LibraryMusic, PlayArrow, MusicNote.

Mapping:

| UI need | Icon |
| --- | --- |
| Home | `Rounded.Home` |
| Search | `Rounded.Search` |
| Library | `Rounded.LibraryMusic` |
| Play / pause | `Rounded.PlayArrow` / `Rounded.Pause` |
| Prev / next | `Rounded.SkipPrevious` / `Rounded.SkipNext` |
| Shuffle / repeat | `Rounded.Shuffle` / `Rounded.Repeat` |
| Like | `Rounded.Favorite` / `Rounded.FavoriteBorder` |
| Queue | `Rounded.QueueMusic` |
| Lyrics | `Rounded.Lyrics` |
| Download | `Rounded.Download` / `Rounded.DownloadDone` |
| More | `Rounded.MoreVert` |
| Back / close | `Rounded.KeyboardArrowDown` / `Rounded.Close` |
| Add | `Rounded.Add` |
| Check | `Rounded.Check` |
| Account | `Rounded.AccountCircle` |
| Settings | `Rounded.Settings` |
| Cast/output | System output switcher; no Spotify Connect icon |

The app icon is an original green play mark; do not use the Spotify logo.

### 1.3 Artwork and motion

- Album/artist/playlist art comes from `getCoverArt` (`size` parameter).
- When cover art is missing, render a deterministic gradient derived from the
  item ID plus a `MusicNote` glyph. No Spotify artwork or placeholder assets.
- Do not copy Spotify's Lottie JSON or illustrations. Recreate simple motion
  with Compose (`animateFloatAsState`, `AnimatedContent`, `Crossfade`,
  `Animatable`) and Material motion.
- No Spotify sounds, fonts, or brand illustrations.

## 2. Dynamic content: Spotify module → Subsonic source

### 2.1 Home

| Spotify module | Clone source | Status |
| --- | --- | --- |
| Greeting | Local time-based text | Keep |
| Recently played | Local Room history + `getAlbumList2?type=recent` | Replace |
| New releases | `getAlbumList2?type=newest` | Replace |
| Most played | `getAlbumList2?type=frequent` | Replace |
| Your playlists | `getPlaylists` | Replace |
| Favorites | `getStarred2` | Replace |
| Favorite artists | `getStarred2.artist`, fallback `getArtists` | Replace |
| Genres & moods | `getGenres` + `getSongsByGenre` | Replace |
| Made for you / Daily Mix | Locally generated mix from favorites/history; label “Made from your library” | Adapt |
| Random discovery | `getAlbumList2?type=random` / `getRandomSongs` | Replace |
| Continue listening | Local history + `getPlayQueue` | Replace |
| Charts / Trending | “Most played” or “Random”; no chart endpoint | Adapt/drop |
| Concerts / Merch / Wrapped / AI DJ / Jam / Canvas / Video | No Subsonic equivalent | Drop |
| Ads / Premium upsell | No ads in the clone | Drop |

Suggested Home section order (phone):

1. Greeting + filter chips (`All`, `Music`, optional `Podcasts`).
2. Continue listening (if queue/history).
3. Recently played (horizontal cards).
4. New releases (album grid, 2 columns).
5. Your playlists (horizontal cards).
6. Made from your library (generated mixes).
7. Favorites (albums/artists).
8. Genres (chips or cards).
9. Random / discover (horizontal cards).

The section list is a fixed, locally generated `HomeSection` list. It does not
pretend to be Spotify's server-driven feed.

### 2.2 Search

| Spotify filter | Subsonic source | Status |
| --- | --- | --- |
| All | `search3` (artists/albums/songs) | Replace |
| Songs | `search3.song` | Replace |
| Albums | `search3.album` | Replace |
| Artists | `search3.artist` | Replace |
| Playlists | `getPlaylists` + local name filter (`search3` does not return playlists) | Adapt |
| Genres & moods | `getGenres` + `getSongsByGenre` | Replace |
| Podcasts / Audiobooks | `getPodcasts` if the capability exists | Optional |
| Profiles | No social graph | Drop |
| Recent searches | Local Room/DataStore | Keep |

### 2.3 Library

| Spotify chip | Subsonic source | Status |
| --- | --- | --- |
| Playlists | `getPlaylists` | Replace |
| Artists | `getArtists` | Replace |
| Albums | `getAlbumList2?type=alphabeticalByName` (paged) | Replace |
| Downloaded | Local Room download index | Replace |
| Songs | Local scanned-song index; otherwise omit in v1 | Adapt |
| Podcasts / Audiobooks | `getPodcasts` if available | Optional |
| Sort | `created` (recent), title, artist, release date, custom playlist order | Adapt |
| Grid/list | Local preference | Keep |
| Pins / drag reorder | Local Room | Keep (later) |

### 2.4 Artist

| Spotify section | Clone source | Status |
| --- | --- | --- |
| Header image/name | `getCoverArt(artist)`, `getArtistInfo2` image | Replace |
| Popular tracks | `getTopSongs`, fallback first songs from albums | Replace/fallback |
| Discography | `getArtist.album` | Replace |
| Bio | `getArtistInfo2.biography` | Replace |
| Similar artists | `getArtistInfo2.similarArtist` | Replace |
| Appears on | Not exposed consistently | Drop |
| Concerts / Merch / Artist Pick / Clips / Events | No equivalent | Drop |

### 2.5 Album

| Spotify section | Clone source | Status |
| --- | --- | --- |
| Header | `getAlbum` + `getCoverArt` | Replace |
| Track list | `getAlbum.song`, grouped by `discNumber` | Replace |
| More by artist | `getArtist.album` | Replace |
| Related | `getSimilarSongs2` / `getAlbumInfo2` | Replace |
| Pre-save / countdown / merch / videos | No equivalent | Drop |

### 2.6 Playlist

| Spotify section | Clone source | Status |
| --- | --- | --- |
| Header | `getPlaylist` + `getCoverArt` | Replace |
| Track list | `getPlaylist.entry` | Replace |
| Create/rename/delete | `createPlaylist`, `updatePlaylist`, `deletePlaylist` | Replace |
| Reorder | `songIndexToRemove` + `songIdToAdd` | Replace |
| Download | `download` + Room | Replace |
| Mix / AI prompt / Blend / Jam | No equivalent | Drop |
| Shuffle play | Local queue | Keep |

### 2.7 Now Playing

| Spotify element | Clone source | Status |
| --- | --- | --- |
| Cover carousel | `getCoverArt`; gradient fallback | Replace |
| Title/artist | Song metadata | Keep |
| Seek bar + timestamps | ExoPlayer position/duration | Keep |
| Play/pause/prev/next | Media3 | Keep |
| Shuffle/repeat | Queue logic | Keep |
| Like | `star` / `unstar` | Replace |
| Add to playlist | `updatePlaylist` | Replace |
| Queue | Local queue + `savePlayQueue` | Replace |
| Lyrics | `getLyricsBySongId` (OpenSubsonic), fallback `getLyrics` | Replace |
| Sleep timer | Local coroutine/handler | Keep |
| More | Go to album/artist, share (if `createShare` supported) | Replace |
| Connect / Canvas / video / ads | No equivalent | Drop |

### 2.8 Queue

| Spotify element | Clone source | Status |
| --- | --- | --- |
| Current track | Local queue | Keep |
| Next in queue | Local queue | Keep |
| Next from context | Context label from album/playlist/artist | Adapt |
| Reorder / remove / clear | Local queue + `savePlayQueue` | Replace |
| Jam header | No equivalent | Drop |

### 2.9 Profile → Account

Replace the social profile with an **Account** screen:

- `getUser` → username, admin/stream/download/share roles.
- `getAvatar` → avatar if available; otherwise initials/gradient.
- Server URL + server type (Subsonic/Jellyfin/Subfin).
- Log out / switch server.
- Recently played artists (local history).
- Public playlists (`getPlaylists?username=...` when permitted).
- Drop followers/following, blends, social/chat, listening activity.

### 2.10 Settings

| Spotify setting | Clone |
| --- | --- |
| Streaming quality | `maxBitRate` + `format` |
| Download quality | Download `stream` quality |
| Cache/storage limit | Local storage manager |
| Equalizer | Android `Equalizer`/`BassBoost`/`Virtualizer` |
| Crossfade / gapless | ExoPlayer; crossfade optional |
| Autoplay | `getSimilarSongs2` at queue end |
| Sleep timer | Local |
| Notifications | Local notification permission/settings |
| Theme | Dark-first; system option later |
| About/licenses | Figtree OFL, Material Icons, open-source notices |
| Account plan / social / ads / kids / devices / privacy | Drop |

### 2.11 Podcasts and audiobooks

- Only show if the server advertises `getPodcasts` / `getNewestPodcasts`.
- Treat episodes as playable media items; use `getBookmarks` for audiobook
  progress if available.
- Do not fake Spotify's podcast/audiobook modules when the server lacks them.

## 3. Drop list

These Spotify-only modules are intentionally omitted:

- Spotify Connect / output devices UI (use Android's system output switcher).
- Jam / social sessions / listening parties.
- Canvas, video, clips, music videos, live events.
- AI DJ, prompted playlists, AI chat, blends.
- Concerts, merch, ticketing, fan support.
- Podcasts/audiobooks when unsupported by the server.
- Ads, premium checkout, plan management, kids/account switching.
- Wrapped, listening stats, social profiles, followers/following.
- Wear OS app (v1), vendor-specific widgets (keep a standard widget later).

## 4. Fidelity rules

- Use the exact tokens from the dossier: colors, 2–64 dp spacing scale,
  2–999 dp radii, 9–48 sp type scale.
- Use 56 dp mini player, 56 dp bottom navigation, 8 dp mini-player margins,
  4 dp artwork radius, 16 dp bottom-sheet radius.
- Use Rounded Material icons at 24 dp; 48 dp touch targets.
- Use a dark-only theme initially; no light theme.
- Animate with Compose; no copied Lottie.
- Keep content-driven layout: the same component adapts to 1 or 20 items.
- Validate on phone and tablet; use screenshot tests against reference
  screenshots for the core screens.
- "Pixel-close" is the goal. Exact text metrics are impossible without Spotify
  Mix; Figtree is the approved substitute.

## 5. Implementation order

1. Design system: tokens + Figtree + rounded icons + core components.
2. App shell: bottom nav, mini player, Now Playing container, queue sheet.
3. Home: section model + Subsonic-backed sections.
4. Library: chips, sort, grid/list.
5. Search: filters + sectioned results.
6. Artist / Album / Playlist pages.
7. Playback: Media3 service, queue, scrobbling, like.
8. Account / Settings.
9. Downloads / offline mode.
10. Android Auto + widget (later).
