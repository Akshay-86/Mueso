# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

- **Primary:** Music power users and daily mobile listeners who want a free, ad-free player combining their local offline MP3/FLAC/M4A library with online YouTube Music streaming, without subscription fees or external server dependencies.
- **Situation:** Commuting, working out, studying, or relaxing, often moving through varying network conditions (cellular dead zones, Wi-Fi transitions, or airplane mode).
- **Core Job:** Seamlessly play, manage, and discover music with zero friction, combining offline local files, synced karaoke lyrics, Spotify playlist imports, and high-quality streaming without interruptions or telemetry.
- **Secondary:** Android customization enthusiasts and audiophiles who appreciate pure AMOLED dark mode, fine-grained lyric timing adjustments, interactive album art framing, and lightweight standalone APKs.

## Product Purpose

Mueso exists to provide an uncompromised, beautiful, and completely private native Android music experience. Rather than relying on subscription-gated platforms or backend microservices that can be shut down or tracked, Mueso runs 100% client-side on-device. Success means a reliable, responsive, and battery-efficient listening companion that respects user privacy, bridges local and online audio effortlessly, and never skips a beat during network interruptions.

## Positioning

Unlike cloud-dependent streaming apps (Spotify, YouTube Music) that require paid subscriptions and harvest telemetry, or traditional local players that cannot stream (Poweramp, Retro Music), Mueso is a **100% Client-Side & Zero-Backend** native Android hybrid player. It directly resolves InnerTube YouTube streams on-device, synchronizes Spotify playlists via client-side delta sync, filters sponsorships through SponsorBlock, and backs up private library data to the user's personal Google Drive AppData—all with zero external servers.

## Operating Context

- **Environment:** Android mobile devices running Android 7.0 (API 24) through Android 15 (API 36).
- **Physical Realities:** Frequent transitions between strong Wi-Fi, cellular networks, and dead zones. Headphone plug/unplug events, Bluetooth car audio disconnects, incoming phone call interruptions, and screen-off background playback.
- **Hardware Diversity:** High-refresh-rate displays (90Hz/120Hz), diverse aspect ratios (compact phones, tall modern slabs, foldable devices), and OLED/AMOLED screens where true pitch-black surfaces yield tangible battery savings.

## Capabilities and Constraints

- **Audio Engine:** AndroidX Media3 ExoPlayer for local audio and direct online streams, with an embedded IFrame audio fallback layer for restricted streams.
- **Triple UI Layouts:**
  1. *Reels Vertical Pager:* Fullscreen vertical swipe-up playback inspired by Resso with dynamically blurred background artwork and quick karaoke lyrics.
  2. *Classic Player:* Standard player layout with persistent floating mini-player capsule and gesture controls.
  3. *Dual Realm:* 4-page horizontal pager separating Offline Library, Offline Player deck, Online Player deck, and Online Playlists.
- **Zero-Backend Constraint:** Strictly no intermediate proxy servers, Python scrapers, or third-party cloud backends. All API calls (InnerTube, Spotify Web Scrape, LRCLIB, SponsorBlock, Google Drive REST, GitHub Releases) originate directly from the Android client.
- **Smart Spotify Delta Sync:** Permanent Spotify playlist link tracking that deduplicates existing tracks and fetches only newly added songs with one tap.
- **Lyrics Engine:** Real-time synchronized lyrics via LRCLIB with line-by-line karaoke highlighting, +/-500ms manual offset adjusters, and interactive candidate search.
- **Interactive Album Art Framing:** Touch-drag artwork bias positioning persisted per track and backed up to Google Drive.
- **Offline Downloads & Native Tagging:** Downloads audio in user-selectable qualities (Medium 128 kbps default) with native ID3v2.3 (MP3) and MP4 Box (M4A) metadata tags and companion `.lrc` lyrics.
- **Network Resilience:** Real-time `NetworkMonitor` auto-pauses and resumes on connectivity restoration instead of skipping or dropping queued tracks.

## Brand Commitments

- **Identity:** Mueso (Modern Open-Source Music Player by Akshay).
- **Tone:** Fluid, energetic, native, respectful of privacy, and unapologetically engineered for the listener.
- **Visual Integrity:** Material 3 foundation with pitch-black AMOLED dark mode, vibrant multi-shade accent palettes, edge-to-edge window insets, smooth animations, and zero user-facing profanity or artificial marketing claims.
- **Package Hygiene:** Lightweight standalone ABI APK splits (~6 MB) distributed directly via GitHub Releases.

## Evidence on Hand

- **Repository Assets:** High-resolution screenshots in `/previews/` demonstrating the reels-style player, search screens, lyrics picker, and settings.
- **Working Codebase:** Production-grade Kotlin codebase with MediaSession service, Room database migrations, ExoPlayer controllers, and Compose UI screens.
- **Open Standards:** MIT License, public GitHub releases, and standard Material 3 components.

## Product Principles

1. **Client-Side Sovereignty:** Never introduce a server when on-device code can do the job. The user's device communicates directly with public APIs; no user data or listening telemetry is ever routed through intermediary servers.
2. **Uninterrupted Listening:** Playback is sacred. Audio focus losses must duck or pause cleanly; network drops must pause and auto-resume without skipping tracks; background services must hold power locks responsibly and release them immediately on pause.
3. **Respect for Storage & Bandwidth:** Deliver clean, compact APKs (~6 MB), avoid uncompressed audio bloat, and let users pick stream and download quality tiers that match their data constraints.
4. **Authentic Native Android Craft:** Embrace Material 3 gestures, proper system Back navigation, edge-to-edge window insets, predictive back animations, and 48dp touch targets. Never imitate iOS navigation idioms.

## Accessibility & Inclusion

- Dynamic font scaling support respecting the system font size without text clipping or layout overflow.
- 48x48 dp minimum touch target sizing across player controls, seekbar handles, and navigation items.
- Full contrast compliance across both pure black AMOLED dark mode and light themes.
- Clear semantic content descriptions and accessibility labels on all icon-only player controls, theme switchers, and modal dismiss actions.
