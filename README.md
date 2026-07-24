# 🎵 Mueso — Next-Gen Online & Offline Android Music Player

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![ExoPlayer](https://img.shields.io/badge/Media-Media3%20ExoPlayer-FF6F00?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)

**Mueso** is a state-of-the-art, high-performance Android music player built with **Jetpack Compose**, **Material 3**, and **Media3 ExoPlayer**. It seamlessly bridges local offline audio libraries with online streaming, ready-made curated top charts, smart SponsorBlock audio filtering, embedded metadata tagging, and batch playlist downloads.

---

## ✨ Key Feature Highlights

### 🎨 1. Dynamic Glassmorphism UI & High Refresh Rate (120Hz)
- **Fluid Micro-Animations**: Built with pure Jetpack Compose, featuring real-time album artwork color extraction and glassmorphic overlays.
- **Peak Refresh Rate Support**: Optional toggle to unlock **120Hz / 90Hz peak display refresh rates** supported by modern Android screens for buttery-smooth scrolling.
- **Adaptive Light & Dark Themes**: Fully synchronized dark mode and light mode across all screens, player controls, top bars, sleep timers, and bottom sheets.

### 🌐 2. Online Streaming & Curated Playlists
- **Ready-Made Global Charts**: Instant access to Daily Top 50, Mood & Focus, Energy, and Trending Playlists with automatic 24-hour client-side caching.
- **Dynamic 2x2 Collage Cover Art**: Custom and curated playlists dynamically synthesize 2x2 image collages from contained song thumbnails.
- **Hero Playlist Elevation**: Long-press any curated or user-created online playlist to set it as the main featured Hero Banner.

### 🛡️ 3. Smart Skip (SponsorBlock Integration)
Automatically skip non-music segments during online playback:
- **Sponsor & Paid Ads**: Skip sponsored brand messages.
- **Self-Promotion & Plugs**: Skip channel merch plugs and promos.
- **Interaction Reminders**: Skip "Like & Subscribe" audio prompts.
- **Intros & Outros**: Skip non-music intro/outro sound clips.
- **Non-Music Filler**: Skip spoken interludes and off-topic dialogue.

### 📥 4. Batch Playlist Downloading & Embedded Tagging
- **Interactive Batch Download Dialog**: One-tap download of entire playlists or custom-selected tracks with individual checkboxes and select/deselect options.
- **Custom Storage Target**: Choose saved target directory between `Music/Mueso`, `Downloads`, `Internal App Storage`, or enter a **custom directory path** (e.g. `/sdcard/MyMusic`).
- **Python ID3 Tag & Artwork Embedding**: Powered by Chaquopy and Mutagen to embed high-res cover art, song title, artist, and album tags directly into downloaded MP3 files.

### 🔒 5. Lockscreen Controls & Sleep Timers
- **Show Over Lockscreen**: Toggle to display player controls directly on the lockscreen when the phone is locked.
- **Smart Sleep Timers**: Countdown Timer (minutes), **Stop After Current Song**, and **Stop at End of Playlist** modes.

---

## 🛠️ Architecture & Technology Stack

Mueso follows **MVVM Clean Architecture** principles:

```
app/src/main/java/com/akshay/musicplayer/
├── data/
│   ├── db/              # Room Database (Online & Local Playlists DAOs)
│   ├── repository/      # Media & Online Repositories with caching
│   └── sources/         # MediaStore & Piped/Invidious API Data Sources
├── domain/
│   ├── models/          # TrackEntity, PlaylistEntity, SleepTimerMode
│   └── usecase/         # GetLocalTracksUseCase, ResolveOnlineStreamUseCase
├── media/
│   └── player/          # ExoPlayerController (Media3, Session & Service)
└── ui/
    ├── components/      # Glassmorphism Cards, Bottom Sheets, Collages
    ├── screens/         # OfflineLibrary, PlayerScreen, OnlinePlaylistsScreen
    ├── theme/           # Color tokens, Typography, Material3 Theme
    └── viewmodel/       # PlayerViewModel (Unified StateFlow Management)
```

### Libraries Used
- **UI Framework**: Jetpack Compose, Material3, Accompanist Permissions.
- **Audio Engine**: AndroidX Media3 ExoPlayer (`1.3.1`).
- **Image Loading**: Coil Compose (`2.6.0`).
- **Database & Storage**: Room Persistence Library (`2.6.1`).
- **Networking**: OkHttp 3 & Gson.
- **Python Runtime**: Chaquopy Python SDK with Mutagen metadata tagger.

---

## 🚀 Building & Setup

### Prerequisites
- **Android Studio**: Koala / Jellyfish (2024.1+)
- **JDK**: Version 17+
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

### Clone & Build
```bash
# Clone repository
git clone https://github.com/Akshay-86/Mueso.git
cd Mueso

# Build Debug APK
./gradlew assembleDebug

# Run Unit Tests
./gradlew test
```

---

## ⚙️ Configuration & App Settings

Access the **App Settings** sheet directly from the top navigation bar:

| Category | Setting | Description |
| :--- | :--- | :--- |
| **Display & Behavior** | **Dark Mode** | Toggle between Dark Glassmorphism and Light Theme |
| | **Show Over Lockscreen** | Display player controls on lockscreen when phone is locked |
| | **High Refresh Rate** | Unlock peak 120Hz/90Hz refresh rate supported by hardware |
| **Quality & Downloads** | **Streaming Bitrate** | High (320kbps), Standard (256kbps), Medium (128kbps) |
| | **Thumbnail Quality** | 1080p Maxres -> 720p High -> 480p Medium -> Default fallback |
| | **Download Quality** | Audio quality for offline MP3 files |
| | **Target Location** | `Music/Mueso`, `Downloads`, `Internal App Storage`, or `Custom Path` |
| **Smart Skip** | **SponsorBlock Categories** | Individually toggle Sponsors, Self-Promo, Interaction, Filler |

---

## 🙏 Open Source Credits & Acknowledgments

Mueso is built on top of amazing open source technologies, tools, and community APIs:

- **[Jetpack Compose](https://developer.android.com/jetpack/compose)**: Android's modern toolkit for building native UI with Material 3 design tokens.
- **[AndroidX Media3 ExoPlayer](https://developer.android.com/media/media3)**: Advanced media playback engine for streaming and local audio rendering.
- **[SponsorBlock API](https://sponsor.ajay.app/)**: Crowdsourced database for skipping non-music segments, sponsors, and interludes.
- **[Chaquopy](https://chaquo.com/chaquopy/)**: Python SDK for embedding Python scripts into Android apps.
- **[Mutagen](https://mutagen.readthedocs.io/)**: Python audio metadata tagging module used for embedding high-res album artwork and ID3 tags.
- **[Piped & Invidious APIs](https://piped.video)**: Open-source privacy-focused YouTube stream resolution endpoints.
- **[Coil Compose](https://coil-kt.github.io/coil/)**: Image loading library for Android backed by Kotlin Coroutines.
- **[Room Persistence Library](https://developer.android.com/training/data-storage/room)**: SQLite object mapping library for local database storage.

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for details.
