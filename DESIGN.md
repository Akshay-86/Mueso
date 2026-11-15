---
name: Mueso
description: Modern native Android music player with Reels-style vertical playback, Material 3 theming, and zero-backend streaming
colors:
  primary: "#FF512F"
  secondary: "#DD2476"
  surface-dark: "#1A1A2E"
  surface-amoled: "#000000"
  surface-light: "#FFFFFF"
  background-dark: "#0F0F0F"
  background-amoled: "#000000"
  background-light: "#F2F2F7"
  text-primary-dark: "#FFFFFF"
  text-primary-light: "#1C1C1E"
  text-secondary-dark: "#A0A0A0"
  text-secondary-light: "#8E8E93"
  accent-sunset-orange: "#FF512F"
  accent-spotify-green: "#1DB954"
  accent-electric-violet: "#8A2387"
  accent-neon-cyan: "#00C9FF"
  accent-crimson-red: "#FF416C"
  accent-ocean-blue: "#2193B0"
  accent-amber-gold: "#F7971E"
  accent-rose-pink: "#FF758C"
  accent-emerald-mint: "#11998E"
  badge-hi-res: "#FFB300"
  badge-lossless: "#00E5FF"
typography:
  display:
    fontFamily: "system-ui, Roboto, sans-serif"
    fontSize: "32sp"
    fontWeight: 700
    lineHeight: 1.25
    letterSpacing: "0sp"
  headline:
    fontFamily: "system-ui, Roboto, sans-serif"
    fontSize: "20sp"
    fontWeight: 700
    lineHeight: 1.4
    letterSpacing: "0sp"
  title:
    fontFamily: "system-ui, Roboto, sans-serif"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: 1.35
    letterSpacing: "0.15sp"
  body:
    fontFamily: "system-ui, Roboto, sans-serif"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: 1.42
    letterSpacing: "0.25sp"
  label:
    fontFamily: "system-ui, Roboto, sans-serif"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: 1.33
    letterSpacing: "0.5sp"
rounded:
  sharp: "4dp"
  squircle: "10dp"
  rounded: "16dp"
  pill: "50%"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
components:
  card:
    backgroundColor: "{colors.surface-dark}"
    rounded: "{rounded.rounded}"
    padding: "16dp"
  quality-badge:
    backgroundColor: "{colors.surface-dark}"
    rounded: "{rounded.pill}"
    padding: "5dp 10dp"
  docked-miniplayer:
    backgroundColor: "{colors.surface-dark}"
    rounded: "22dp"
    padding: "8dp 12dp"
---

# Design System

<!-- impeccable:design-schema 1 -->

## Overview

Mueso's design language combines the disciplined foundations of **Material Design 3** with the energetic, media-forward aesthetic of contemporary music culture. Inspired by the visual flow of Resso, Mueso foregrounds large album artwork, fluid background color sampling, dynamic blur effects, and real-time karaoke lyrics.

### Design Principles
- **Artwork-First Canvas:** The visual hierarchy is anchored by high-resolution album artwork. Backgrounds organically reflect and sample the current track's color palette with customizable vertical framing.
- **Dark-First & AMOLED Optimized:** Built for long listening sessions with deep blacks, high contrast typography, and a dedicated 100% Pure Black AMOLED mode that maximizes battery life on OLED displays.
- **Expressive Motion & Tactile Feedback:** Gestural transitions (vertical swipe-up track skipping, horizontal deck paging, and smooth seekbar release locks) feel physical, responsive, and immediate.
- **Zero Interface Clutter:** Secondary controls, technical bitrates, and administrative menus are tucked into bottom sheets and contextual capsules, leaving the primary playback screen uncluttered.

---

## Colors

Mueso features a comprehensive color system supporting dynamic Material You wallpaper matching on Android 12+, nine crafted accent presets, dark/light themes, and an OLED-saving Pure Black mode.

### Theme Modes & Background Roles
| Role | Dark Theme | Pure Black AMOLED | Light Theme |
| :--- | :--- | :--- | :--- |
| **Background** | `#0F0F0F` | `#000000` | `#F2F2F7` |
| **Surface** | `#1A1A2E` | `#0D0D0D` | `#FFFFFF` |
| **Surface Variant / Tertiary** | `#1E1E2E` | `#141414` | `#E5E5EA` |
| **Text Primary** | `#FFFFFF` | `#FFFFFF` | `#1C1C1E` |
| **Text Secondary** | `rgba(255, 255, 255, 0.65)` | `rgba(255, 255, 255, 0.65)` | `rgba(28, 28, 30, 0.65)` |
| **Border / Divider** | `rgba(255, 255, 255, 0.12)` | `rgba(255, 255, 255, 0.15)` | `rgba(0, 0, 0, 0.08)` |

### Accent Palettes (`ThemePresets`)
Every palette defines a primary accent and a complementary gradient partner:
- **Sunset Orange (Default):** `#FF512F` → `#DD2476`
- **Spotify Green:** `#1DB954` → `#108639`
- **Electric Violet:** `#8A2387` → `#E94057`
- **Neon Cyan:** `#00C9FF` → `#92FE9D`
- **Crimson Red:** `#FF416C` → `#FF4B2B`
- **Ocean Blue:** `#2193B0` → `#6DD5ED`
- **Amber Gold:** `#F7971E` → `#FFD200`
- **Rose Pink:** `#FF758C` → `#FF7EB3`
- **Emerald Mint:** `#11998E` → `#38EF7D`

### Audio Quality Signal Colors
- **Hi-Res Audio (24-bit / 96kHz+):** Amber Gold `#FFB300` on dark amber container `#261D00` (85% opacity).
- **CD Lossless (16-bit / 44.1kHz FLAC):** Electric Cyan `#00E5FF` on dark cyan container `#00222B` (85% opacity).
- **Standard Lossy (MP3 / AAC / Opus):** On-Surface Variant `rgba(255, 255, 255, 0.85)` on subtle container `rgba(255, 255, 255, 0.08)`.

---

## Typography

Typography is built on the standard **Material 3 Typography Scale** using the system font (`FontFamily.Default`), scaled exclusively with `sp` units to honor user accessibility preferences.

### Type Roles
| Role | Size | Line Height | Weight | Usage |
| :--- | :--- | :--- | :--- | :--- |
| **Display Large** | `32sp` | `40sp` | Bold (700) | Fullscreen track titles, hero banners |
| **Display Medium** | `28sp` | `36sp` | Bold (700) | Onboarding headings, major section titles |
| **Headline Large** | `20sp` | `28sp` | Bold (700) | Screen titles, playlist detail names |
| **Headline Small** | `16sp` | `24sp` | Bold (700) | Track titles in lists, card headers |
| **Body Large** | `16sp` | `24sp` | Normal (400) | Dialog descriptions, lyrics line items |
| **Body Medium** | `14sp` | `20sp` | Normal (400) | Artist names, secondary subtitles |
| **Label Large** | `14sp` | `20sp` | Medium (500) | Button labels, bottom sheet actions |
| **Label Small** | `11sp` | `16sp` | Medium (500) | Bitrate badges, timestamps, audio format pills |

### User-Configurable Type Scaling
- **App Font Scale (`FontScaleOption`):** Compact (`0.88x`), Standard (`1.0x`), Comfortable (`1.12x`), Large (`1.25x`).
- **Lyrics Font Size (`LyricsFontSizeOption`):**
  - *Compact:* Active line `22sp`, Inactive line `15sp`
  - *Standard:* Active line `26sp`, Inactive line `17sp`
  - *Large:* Active line `32sp`, Inactive line `21sp`

---

## Layout

Mueso provides three distinct layout paradigms, allowing users to choose how they navigate their music library:

### 1. Reels-Style Vertical Swipe Player (`VerticalPagerScreen`)
- **Interaction:** Fullscreen vertical pager allowing users to swipe up/down to seamlessly transition between tracks like short-form video reels.
- **Composition:** Edge-to-edge blurred album artwork background, top navigation overlay with quality capsule and overflow menu, center artwork canvas with interactive horizontal framing drag, and bottom-anchored metadata, synchronized lyrics snippet, and playback controls.

### 2. Classic Player (`ClassicPlayerScreen`)
- **Interaction:** Traditional player with persistent bottom navigation bar (`ClassicBottomBar`) and floating docked mini-player (`DockedMiniPlayer`).
- **Composition:** Tap-to-expand modal bottom sheet player with collapsible header, full-width seekbar, and quick access to Queue, Sleep Timer, and Equalizer.

### 3. Dual Realm Mode (`PlayerRealm`)
- **Interaction:** 4-page horizontal pager that establishes independent offline and online music environments:
  - Page 0: Offline Library (Local tracks, albums, folders)
  - Page 1: Offline Player Deck (Dedicated queue for local audio)
  - Page 2: Online Player Deck (Dedicated queue for YouTube Music streaming)
  - Page 3: Online Playlists & Explore (Curated shelves, mood categories, Spotify imports)
- **Benefit:** Prevents accidental queue mixing between local high-res downloads and online streams.

### Touch Targets & Safe Areas
- All interactive buttons and icon clicks enforce a minimum of **48×48 dp**.
- Window insets (`WindowInsets.statusBars`, `WindowInsets.navigationBars`, and `WindowInsets.ime`) are respected across all screens so interactive controls are never obscured by system bars.

---

## Elevation & Depth

Depth is achieved through **tonal surface elevation** and **frosted glass overlays** rather than arbitrary heavy drop shadows.

- **Tonal Elevation:** Surfaces use Material 3 tonal elevation layers (Level 0: Base background `#0F0F0F`, Level 1: Cards `#1A1A2E`, Level 2: Dialogs & Floating MiniPlayer `#242438`).
- **Frosted Glass Overlays (`GlassDark` & `GlassLight`):**
  - Dark Glass: `#1A1A1A` with 70% opacity and `backdrop-blur` for bottom navigation bars and floating capsules.
  - Light Glass: `#2A2A2A` with 60% opacity for floating chips and pill badges.
- **Ambient Glow:** Quality pills and active playback icons use a subtle color-tinted ambient glow rather than harsh black shadows.

---

## Shapes

Mueso implements a dynamic shape system governed by `LocalCornerRadius`, allowing users to customize the corner styling across cards, dialogs, and containers.

### Corner Radius Options
- **Rounded (Default):** `16.dp` — Friendly, fluid, modern Material 3 aesthetic.
- **Squircle:** `10.dp` — Tight, technical, balanced curve.
- **Sharp:** `4.dp` — Minimal, architectural, high-density styling.

### Fixed Semantic Shapes
- **Full Pill (`RoundedCornerShape(50)` / `CircleShape`):** Play/pause toggle buttons, search filter chips, audio quality capsules, and floating mini-player progress handles.
- **Docked Mini-Player Shape:** `RoundedCornerShape(22.dp)` with hairline 1px border.
- **Album Artwork Thumbnails:** `RoundedCornerShape(12.dp)` for list items; `RoundedCornerShape(16.dp)` for grid cards.

---

## Components

### 1. `PlayerControls`
- **Visual Design:** Floating row featuring Previous, Play/Pause, Next, and Repeat/Shuffle toggles. Play button position is user-configurable (`Left`, `Center`, `Right`).
- **Scrubbing Decoupling:** Uses `pendingSeekPosition` and deferred state updates to ensure seek slider thumbs lock smoothly at the release point without jittering against async player position updates.

### 2. `AudioQualityCapsule`
- **Visual Design:** Compact 24dp height pill with an animated glowing indicator dot (`6.dp CircleShape`) and format badge text (`FLAC • 16-bit`, `HI-RES • 24-bit/96kHz`, `128 kbps`).
- **Interactivity:** Tapping the capsule displays audio stream source telemetry without interrupting playback.

### 3. `DockedMiniPlayer`
- **Visual Design:** Persistent floating capsule positioned above bottom navigation bars (`height = 64.dp`). Features album art thumbnail, marquee title, artist subtitle, play/pause toggle, and a micro linear progress indicator bar running along the bottom edge.

### 4. `LyricsView` & `ClassicFullScreenLyricsView`
- **Visual Design:** Full-screen karaoke typography with smooth line-by-line active color highlight and inactive line dimming.
- **Interaction:** Tap-to-seek directly to lyric timestamp; manual timing offset adjuster dialog (`+/-500ms`); auto-scroll pauses seamlessly when user touch scrolling is detected.

### 5. `BottomSheets` (Queue, Sleep Timer, Track Menu, Add to Playlist)
- **Visual Design:** Drag-handle header (`width = 36.dp, height = 4.dp`), dark tonal surface, 16dp horizontal padding, and smooth spring physics slide-in/slide-out animations.

---

## Do's and Don'ts

### Do:
- **Do** wrap edge-to-edge screens with window inset padding (`statusBarsPadding()`, `navigationBarsPadding()`) so controls never collide with system navigation bars or camera cutouts.
- **Do** enforce a minimum touch target size of `48.dp` on all clickable icons, close buttons, and seekbar slider handles.
- **Do** preserve system Back navigation across all nested overlays (search filters, lyrics expansion, bottom sheets) using Jetpack Compose `BackHandler`.
- **Do** declare `basicMarquee()` with `min-w-0` on track titles and artist names to prevent horizontal text clipping.
- **Do** respect user-selected `FontScaleOption` and `LyricsFontSizeOption` without breaking layout heights.

### Don't:
- **Don't** use hardcoded pixel (`px`) sizes for typography or margins; always use `sp` for text and `dp` for dimensions.
- **Don't** port iOS-style segmented controls or Cupertino alert dialogs; adhere strictly to Material 3 dialogs, bottom sheets, and chips.
- **Don't** show empty placeholder boxes when tracks lack artwork or lyrics; collapse the UI gracefully or render a clean fallback collage.
- **Don't** allow seeking sliders to jump or jitter while user fingers are actively dragging.
- **Don't** hardcode raw hex colors in screen composables; route colors through `MaterialTheme.colorScheme`, `LocalAccentColor`, or `DarkColors`.
