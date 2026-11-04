# 03 — Design system (“Encore”)

Everything below was read from `res/values/{colors,dimens,styles,attrs}.xml` and the
component drawables. The names are Spotify's; the values are exact. Machine-readable
copy: [design-tokens.json](design-tokens.json).

Spotify is **dark-first**. The app theme (`Theme.Glue`) resolves `?textBase`,
`?backgroundBase`, … from `dark_base_*` colours; a light variant exists but the product
ships dark by default.

---

## 1. Colour

### 1.1 Brand

| Token | Value | Note |
|---|---|---|
| `spotify_green_157` / `green_light` | `#1ED760` | current brand green |
| `green` (legacy) | `#1DB954` | old green, still in resources |

### 1.2 Dark semantic palette (the ones to implement)

| Token | Value | Use |
|---|---|---|
| `backgroundBase` | `#121212` | app & page background |
| `backgroundElevatedBase` | `#1F1F1F` | cards, sheets, nav bar on large screens |
| `backgroundElevatedHighlight` | `#2A2A2A` | pressed/hover on elevated |
| `backgroundElevatedPress` | `#191919` | pressed elevated |
| `backgroundHighlight` | `#1F1F1F` | selected/hover |
| `backgroundPress` | `#000000` | pressed on base |
| `backgroundTintedBase` | `#1AFFFFFF` (10 %) | tinted cards/buttons |
| `backgroundTintedHighlight` | `#24FFFFFF` (14 %) | |
| `backgroundTintedPress` | `#36FFFFFF` (21 %) | |
| `textBase` | `#FFFFFF` | primary text/icons |
| `textSubdued` | `#B3B3B3` | secondary text |
| `textBrightAccent` | `#1ED760` | active/success text |
| `textNegative` | `#F3727F` | error text |
| `textWarning` | `#FFA42B` | warning text |
| `textPositive` | `#1ED760` | positive text |
| `decorativeBase` | `#FFFFFF` | dividers, high-contrast decoration |
| `decorativeSubdued` | `#292929` | hairlines, disabled fill |
| `essentialBase` | `#FFFFFF` | essential icons |
| `essentialSubdued` | `#7C7C7C` | secondary icons, disabled |
| `essentialNegative` | `#ED2C3F` | destructive |
| `essentialWarning` | `#FFA42B` | warning |
| `essentialPositive` | `#1ED760` | success |
| `essentialAnnouncement` | `#1278F2` | info |

### 1.3 Accent (“bright accent”) states — play buttons

| Token | Value |
|---|---|
| `brightAccentBackgroundBase` | `#1ED760` |
| `brightAccentBackgroundHighlight` | `#3BE477` |
| `brightAccentBackgroundPress` | `#1ABC54` |
| `brightAccentTextBase` | `#000000` |

Play button: 1ed760 circle, black glyph; disabled = 30 % alpha; press = `#1ABC54`.
Primary CTA: same green background + black label.

### 1.4 Over-media / app frame

| Token | Value |
|---|---|
| `overMediaBackgroundBase` | `#8A000000` (54 % black) |
| `overMediaBackgroundHighlight` | `#94000000` |
| `overMediaBackgroundPress` | `#AD000000` |
| `overMediaTextBase` | `#FFFFFF` |
| `appFrameBackgroundBase` | `#000000` (behind rounded tablet content) |
| `appFrameBackgroundElevatedBase` | `#141414` |
| `appFrameTextSubdued` | `#A9A9A9` |

### 1.5 Grays and opacities

`gray_7 #121212`, `gray_10 #181818`, `gray_15 #282828`, `gray_20 #333333`,
`gray_30 #535353`, `gray_50 #7F7F7F`, `gray_70 #B3B3B3`, `gray_80 #CCCCCC`,
`gray_85 #D9D9D9`, `gray_90 #EEEEEE`, `gray_95 #F8F8F8`.

White/black overlays: 10 %, 30 %, 50 %, 60 %, 70 %, 90 % (white);
10 %, 20 %, 30 %, 50 %, 60 %, 70 %, 80 %, 90 % (black).

### 1.6 Light palette (secondary)

`backgroundBase #FFFFFF`, `backgroundHighlight #F5F5F5`, `backgroundPress #E2E2E2`,
`textBase #000000`, `textSubdued #656565`, `textBrightAccent #107434`,
`essentialSubdued #818181`, `decorativeSubdued #DEDEDE`.

### 1.7 Artwork-derived gradients

Spotify tints entity headers from the cover art. Resources contain
`gradient_start #391326`, `gradient_end #191847`, and overlay gradient stops
(`50 % → 70 % → 100 %` black). Implementation: Palette/`androidx.palette` extract a
dominant colour from the cover, then build a vertical gradient:

```
header top    = artworkDominant (darkened / 0.6–0.8 luminance)
header bottom = backgroundBase #121212
```

The main shell fades content into `#D9000000` above the mini player and a 48 dp
accelerating fade to `#FF000000` beneath it (`MainLayoutGradientView`).

---

## 2. Typography

Spotify ships three proprietary families:

| File family | Weights | Use |
|---|---|---|
| `spotify_mix_ui_*` | Regular, Bold (+ Cyrillic/Greek/Hebrew/Arabic/Thai/Devanagari) | UI/body |
| `spotify_mix_ui_title_*` | Bold, ExtraBold | headings, titles |
| `spotify_mix_mono_regular` | Regular | timecodes, mono |
| `circular_sp_*` | Book, Bold, Black | legacy |
| `spoticon.ttf` | icon font | legacy icons |

**Do not redistribute these.** Substitute Figtree (closest), Manrope or Inter. Keep the
sizes/weights and the tight tracking; that is what makes it read as “Spotify”.

### Type scale (exact)

| Style | Size | Weight | Family | Typical use |
|---|---|---|---|---|
| `HeadlineLarge` | 48 sp | 800 | title | huge marketing/detail titles |
| `HeadlineMedium` | 40 sp | 700 | title | page hero |
| `TitleLarge` | 32 sp | 700 | title | large entity title |
| `TitleMedium` | 24 sp | 700 | title | section/page title (album, playlist) |
| `TitleSmall` | 20 sp | 700 | title | rows, card title, button large |
| `BodyMedium` | 16 sp | 400 | UI | body, list title |
| `BodyMediumBold` | 16 sp | 700 | UI | list title emphasis |
| `BodySmall` | 13 sp | 400 | UI | subtitles, metadata |
| `BodySmallBold` | 13 sp | 700 | UI | |
| `Marginal` | 11 sp | 400 | UI | captions |
| `MarginalBold` | 11 sp | 700 | UI | badges, counters |
| `MarginalSmallBold` | 9 sp | 700 | UI | micro labels |
| `Badge` | 9 sp | 700 | UI, UPPERCASE | “NEW”, “E”, explicit |
| `TagLabel` | 10 sp | 700 | UI, UPPERCASE | tags |

Inheritance: `TextAppearance.Encore` sets `textColor=?textBase`;
`Subtitle`/`Pretitle`/`SubtitleBold` set `?textSubdued`.
`fontFeatureSettings=liga 1` everywhere (keeps the geometric ligatures).

Recommended tracking approximation: `-0.02 em` at 40–48 sp, `-0.01 em` at 24–32 sp,
`0` at ≤20 sp. Line heights: 1.15× for titles, 1.45× for body.

---

## 3. Spacing, radii, sizes

### Spacing scale (dp)

`2, 4, 6, 8, 12, 16, 20, 24, 32, 40, 48, 64`

Named as `encore_spacer_tighter5 … looser6`. The working constants:

* screen horizontal margin **16**
* list row horizontal padding **16**, media-to-text gap **12**
* card gap in shelves **12**
* max content width **640** (`encore_layout_max_width_narrow`)

### Radii (dp)

| Token | Value |
|---|---|
| smaller | 2 |
| base | 4 |
| larger | 6 |
| larger2 | 8 |
| larger3 | 16 |
| floating (tablet shell) | 32 |
| pill / button | 500 |
| chip | 1500 |
| tooltip | 10 |

Cards use **6–8 dp**; the tablet content surface uses **16 dp** top corners and 32 dp
for the floating “side panel”.

### Control sizes

| Token | Value |
|---|---|
| control small / base / large | 32 / 48 / 56 |
| button small / medium / large | 32 / 48 / 56 height |
| button horizontal padding (S/M/L) | 16 / 32 / 48 |
| button icon | 16 / 24 / 32 |
| play button S / L / XL | 32 / 52 / 54 |
| play glyph S / L / XL | 16 / 24 / 32 |
| back button | 48 hit target, 32 bg circle |
| chip min height | 32, padding 16×8, pill |
| row artwork S / M / L | 48 / 56 / 80 |
| header artwork | 160 |
| profile image | 112 |

### Bottom sheets

Max width **640 dp**; grapple **36 × 4 dp** with 10 dp vertical margin;
min peek for context menus **320 dp**; corner radius 16 dp.

---

## 4. Components (implementable spec)

### Buttons (Encore)

* **Primary**: green pill (`#1ED760`), black text, no elevation, press scale **0.9**
  (`encore_button_animation_pressed_scale`), text = `TitleSmall` (L) /
  `BodyMediumBold` (M) / `BodySmallBold` (S).
* **Secondary**: transparent, 1 dp stroke `essentialSubdued #7C7C7C`, white label,
  press fill `backgroundHighlight`.
* **Tertiary**: text-only, white → subdued on press.
* Disabled: `backgroundBase` + 30 % alpha glyph/label.
* Shape: fully rounded (radius 500).

### Icon buttons

40–48 dp hit area; 24 dp glyph; icon-only uses `essentialBase`; active/selected uses
`textBrightAccent` or filled glyph (heart, shuffle, repeat, download).

### Chips (filter chips in Home / Library / Search)

Pill, 32 dp min height, 16 dp horizontal padding, 8 dp vertical, 1.5 dp border when
selected, label `BodySmall`/`BodySmallBold`; unselected = `backgroundTintedBase` +
`textBase`, selected = `textBase` background + `backgroundBase` text (inverted).

### List rows

* **Track row**: 48–56 dp, optional 1–4 track number (centred, `BodySmall`,
  `textSubdued`; now-playing = green + equaliser), 48 dp artwork for playlists,
  16 dp title (`BodyMedium`, white), 13 dp artist/album (`BodySmall`, `#B3B3B3`),
  trailing “more” (⋮) and duration. Long-press opens context menu.
* **Entity row** (album/artist/playlist): 56–80 dp, 56–80 dp artwork (4 dp radius for
  albums/playlists, circle for artists), title 16 dp, subtitle 13 dp, optional
  download/playing indicator.
* **Section heading**: title `TitleMedium` (24/700), optional “Show all” (`BodySmall`,
  `textSubdued`), optional icon; sticky in library.

### Navigation bar

Custom Encore bar, 56 dp content height (+ system inset), icons 24 dp, label 11 dp.
Items: **Home**, **Search**, **Library** (`navigationbar_musicappitems_library_title`;
the older label is “Your Library”), and **Create** (+ in a rounded square). The free
tier adds a **Premium** slot. Active item = `textBase`; inactive = `textSubdued`;
no pill indicator, no ripple.
On tablets the bar floats inside the content card (`backgroundElevatedBase`, 16 dp
radius, max width 450, min 360).

### Top bar

Transparent over content, 56 dp; title appears on scroll (`BodyMediumBold`), back
chevron 24 dp in 32 dp circle, right actions 24–32 dp. Page headers fade from the
artwork-derived colour to `#121212`.

### Mini player

Persistent above the nav bar, 16 dp side margins on large screens, background
`gray_15 #282828` with 8 dp radius (floating variant) or transparent over the gradient.

| Element | Size |
|---|---|
| seek bar | 2 dp, full width, green progress, `#4D4D4D` track |
| artwork | 54 dp, 4 dp radius |
| title | 16 sp (marquee), subtitle 13 sp |
| heart | 56 dp hit area, 24 dp glyph |
| play/pause | 40 dp button, 24 dp glyph |
| next | 24 dp glyph, 24 dp hit |
| padding | 16 dp horizontal, 8–12 dp vertical |

Tap → full-screen Now Playing; swipe up → expand; swipe down → collapse; horizontal
swipe → next/previous. `now_playing_bar_large_height` = 96 dp on tablets.

### Now Playing (full screen)

```
top bar: ⌄ (collapse)   context label   ⋮
artwork: square, width − 32 dp, 8 dp radius, shadow
title row: title (TitleMedium/24) + artist (BodySmall) + heart (52)
seek bar: 4 dp, thumb appears on drag, elapsed / remaining 11 sp
controls: shuffle 24 · prev 32 · play 64 · next 32 · repeat 24   (spaced evenly)
bottom row: device / share / queue (24 dp icons, labels 11 sp)
scroll cards: Lyrics, “About the artist”, “Related”, Credits
background: gradient from artwork colour → #121212, blurred/soft
```

Play button in the player is 64 dp (white on dark, or green with black glyph when the
context is “playing”), with press scale 0.9. Controls are disabled (30 % alpha) when
unavailable.

### Snackbars / toasts

Dark `#282828` (or `backgroundElevatedBase`), white `BodySmall`, 8–12 dp radius,
action label green (`textBrightAccent`), anchored above the mini player.

### Context menus

Bottom sheet, drag handle, grouped rows 48–56 dp, leading 24 dp icon, trailing
check/expand; destructive row red; “Don't play this” / “Go to artist” / “Add to
playlist” / “Add to queue” / “Share” / “Download”.

### Empty / loading states

Centred icon (48–64 dp, `essentialSubdued`), title `TitleSmall`, body `BodySmall`
`textSubdued`, optional green CTA. Skeletons: `#1F1F1F` blocks with a shimmer, rather
than spinners, on home/library.

---

## 5. Motion

No duration tokens are exposed in resources; the observed Encore conventions are:

* button press: scale 0.9, 100–120 ms;
* page push/pop: 250–300 ms, shared-element artwork when opening an album/playlist;
* now playing open: 300 ms slide-up + dim of the shell, artwork shared element;
* bottom sheets: 250–350 ms spring, scrim 32 % black;
* content fade under the mini player: gradient driven by scroll offset (`AdjustBottomGradientBasedOnScrollBehavior`);
* marquee text in the mini player and long track titles (`marquee_forever`).

Use `MotionLayout`-style equivalents in Compose (`AnimatedContent`, `SharedTransitionLayout`,
`ModalBottomSheet`, `animateContentSize`).

---

## 6. Compose implementation sketch

```kotlin
@Immutable
data class EncoreColors(
    val backgroundBase: Color,
    val backgroundElevated: Color,
    val backgroundHighlight: Color,
    val textBase: Color,
    val textSubdued: Color,
    val accent: Color,          // #1ED760
    val onAccent: Color,        // #000000
    val essentialSubdued: Color,
    val negative: Color,
    val warning: Color,
    val overMediaScrim: Color,
)

val LocalEncoreColors = staticCompositionLocalOf { DarkEncoreColors }

@Composable
fun EchoTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalEncoreColors provides DarkEncoreColors) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                background = DarkEncoreColors.backgroundBase,
                surface = DarkEncoreColors.backgroundElevated,
                primary = DarkEncoreColors.accent,
                onPrimary = DarkEncoreColors.onAccent,
                onBackground = DarkEncoreColors.textBase,
                onSurface = DarkEncoreColors.textBase,
            ),
            typography = EchoTypography,   // table above, Figtree font
            content = content,
        )
    }
}
```

Rules: never hard-code `#1ED760` outside the token file; never use Material elevation
shadows (Spotify is flat, separation is by `#1F1F1F` and gradients); use 16 dp margins
and 12 dp gaps; all tappable icons ≥ 48 dp.
