<div align="center">
  <img src="logo.svg" width="128" height="128" alt="Mueso Logo" />
  <h1>Mueso</h1>
  <p><strong>A Modern, Open-Source Android Music Player</strong></p>

  <p>
    <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform" />
    <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Language" />
    <img src="https://img.shields.io/github/v/release/Akshay-86/Mueso?style=for-the-badge&color=FF512F" alt="Version" />
  </p>
  <p>
    <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="UI" />
    <img src="https://img.shields.io/badge/Media-Media3%20ExoPlayer-FF6F00?style=for-the-badge" alt="Media" />
    <img src="https://img.shields.io/badge/License-MIT-007EC6?style=for-the-badge" alt="License" />
    <img src="https://img.shields.io/github/last-commit/Akshay-86/Mueso?style=for-the-badge&color=7F52FF" alt="Last Commit" />
  </p>
</div>

<br />

**Mueso** is a next-generation, high-performance Android music player built with **Jetpack Compose**, **Material 3**, and **AndroidX Media3 ExoPlayer**. It seamlessly bridges local offline audio libraries with online streaming, ready-made curated top charts, smart SponsorBlock audio segment filtering, embedded ID3 metadata tagging, and batch playlist downloads.

---

## ✨ Features

- **🟢 Spotify Playlist Import**: Paste any public Spotify playlist URL to instantly fetch track listings and match them against high-quality online audio streams with interactive top-down review and manual alternative selection.
- **🌐 Online Streaming & Curated Playlists**: Stream online tracks, browse curated top charts (YouTube & Spotify), create custom online playlists, and set custom Hero Banner playlists.
- **🛡️ Smart SponsorBlock Audio Filtering**: Master toggle with customizable sub-settings to automatically skip non-music segments, sponsor messages, self-promotions, and intros/outros in real-time during online streaming.
- **📥 Batch Playlist Downloads**: Download full online playlists directly to your local device with embedded high-resolution album artwork and ID3 tags powered by Chaquopy Python & Mutagen.
- **🎵 Synced & Unsynced Lyrics**: Automatically fetches real-time synced lyrics via LRCLIB with line-by-line karaoke highlights and fallback unsynced lyric viewer.
- **🎨 Dynamic Light & Dark Theme Adaptation**: Fully adaptive glassmorphism & Material 3 color system with status bar color syncing across Light and Dark themes.
- **🔄 In-App Updates & Self-Installer**: Automatically check GitHub Releases for app updates, view changelogs, track download progress, and install new APKs directly within the app.
- **⚡ High Refresh Rate Display Mode**: Force 120Hz / 90Hz display refresh rates for butter-smooth scrolling and zero-lag Compose UI animations.
- **🔒 Lockscreen Controls & Sleep Timer**: Full playback controls on Android lockscreen and status bar media notification with configurable sleep timers.
- **☁️ Automated Google Drive Backup**: Backup and restore your custom playlists, hero banners, and app preferences securely to Google Drive AppData.

---

## 🛠️ Architecture & Tech Stack

Mueso follows modern **MVVM Clean Architecture** guidelines:

| Component | Technology |
| :--- | :--- |
| **UI Framework** | Jetpack Compose (1.6+), Material 3 Design System |
| **Audio Engine** | AndroidX Media3 ExoPlayer |
| **State Management** | Kotlin Coroutines, StateFlow, ViewModel |
| **Database** | Room Persistence Library |
| **Networking** | OkHttp 3, Gson, Spotify Web API & Embed Scraper |
| **Python Runtime** | Chaquopy SDK (for Mutagen ID3 Tagging & yt-dlp metadata) |
| **Lyrics API** | LRCLIB Synced Lyrics API |
| **Sponsor Filtering** | SponsorBlock Community API |
| **App Updates** | GitHub Releases API & Android FileProvider Installer |
| **Image Loading** | Coil Compose |

---

## 🚀 Building & Setup

### Prerequisites
- **Android Studio**: Koala / Jellyfish (2024.1+)
- **JDK**: Version 17+
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

### Clone & Compile

```bash
# Clone the repository
git clone https://github.com/Akshay-86/Mueso.git
cd Mueso

# Build Debug APK
./gradlew assembleDebug
```

---

## 🙏 Open Source Credits

Mueso is built on top of amazing open-source technologies, tools, and community APIs:

- [AndroidX Media3 ExoPlayer](https://developer.android.com/media/media3)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [SponsorBlock API](https://sponsor.ajay.app/)
- [yt-dlp](https://github.com/yt-dlp/yt-dlp)
- [LRCLIB](https://lrclib.net/)
- [Chaquopy](https://chaquo.com/chaquopy/) & [Mutagen](https://mutagen.readthedocs.io/)
- [Piped & Invidious APIs](https://piped.video)

---

## 🤝 Contributing, Issues & Feedback

Contributors are very welcome! Feel free to **fork** this repository, make changes, and submit a **Pull Request**.

If any credits were missed, or if you have any issues, complaints, or feedback, feel free to:
- Open a GitHub [Issue](https://github.com/Akshay-86/Mueso/issues) or submit a [Pull Request](https://github.com/Akshay-86/Mueso/pulls).
- Or email directly at **[nalliakshaykumar@gmail.com](mailto:nalliakshaykumar@gmail.com)**.

---

## 📄 License

Distributed under the **MIT License**. See [`LICENSE`](LICENSE) for more information.
