# Hazel

[![License](https://img.shields.io/badge/LICENSE-GPL--3.0-00bcd4?style=for-the-badge&labelColor=0d1117)](https://opensource.org/licenses/GPL-3.0)
[![Kotlin](https://img.shields.io/badge/KOTLIN-2.0-6d6d6d?style=for-the-badge&logo=kotlin&logoColor=white&labelColor=0d1117)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/COMPOSE-MATERIAL3-ffffff?style=for-the-badge&logo=jetpackcompose&logoColor=white&labelColor=0d1117)](https://developer.android.com/jetpack/compose)
[![Platform](https://img.shields.io/badge/PLATFORM-ANDROID-00bcd4?style=for-the-badge&logo=android&logoColor=white&labelColor=0d1117)](#installation)
[![Buy Me a Coffee](https://img.shields.io/badge/BUY%20ME%20A%20COFFEE-ffdd00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black&labelColor=0d1117)](https://buymeacoffee.com/sibtainocn)

Hazel is a media downloader, converter, and music player for Android. Download videos and audio from 3000+ platforms, convert between formats offline, and play your music — all from one app.

## What Makes Hazel Different

- **Share & Download** — See a reel, tweet, or story? Just tap _Share → Hazel_ and it downloads automatically. No copy-pasting URLs.
- **Full Playlist in One Tap** — Paste a playlist link and Hazel downloads every track in your chosen quality — up to 4K video or lossless FLAC audio.
- **Multi Links** — Queue up to 10 different URLs from different platforms and download them all in one batch.
- **Bulk Import** — Have a file full of URLs? Import it and Hazel validates, deduplicates, and downloads everything automatically.
- **Direct Format Choice** — Download directly as MP3, AAC, FLAC, WAV, or Opus — no need to download first and convert later.
- **Offline Converter** — Already have a video file? Convert it to any audio format right on your device, no internet needed.
- **Built-in Music Player** — Play your downloads immediately with background playback, album art, and full controls.

## Features

### Download
- **3000+ Platforms** — YouTube, Instagram, Twitter/X, TikTok, SoundCloud, Vimeo, Dailymotion, and more
- **Video Quality** — 4K, 2K, 1080p, 720p, 480p, 360p
- **Audio Formats** — MP3 320kbps, AAC 256kbps, FLAC (lossless), WAV, Opus
- **4 Download Modes** — Single, Playlist, Multi Links (up to 10), Bulk (import from file)
- **Share to Download** — Share any URL from any app to start downloading instantly
- **Smart Notifications** — Real-time progress bar, speed display, and completion alerts
- **Auto Folder Organization** — Separate folders per playlist or batch download

### Converter
- **Offline Conversion** — Convert any downloaded video to MP3, AAC, or FLAC without internet
- **Batch Support** — Convert multiple files at once
- **Quality Control** — Choose bitrate and format before converting

### Music Player
- **Full Library** — Browse and play your entire music collection
- **Background Playback** — Keep listening while using other apps
- **Per-Track Album Art** — Each song displays its own embedded artwork
- **Now Playing** — Full-screen controls with seek bar, shuffle, repeat
- **Folder Browsing** — Switch between directories or pick custom folders

### Customization
- **12+ Accent Colors** — Quick access from the top bar or Settings
- **Dark & Light Mode** — One-tap theme toggle
- **Download History** — Track all past downloads
- **Auto Updates** — In-app update with download progress and one-tap install

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM (ViewModel + Repository) |
| Download Engine | yt-dlp (via youtubedl-android) + FFmpeg |
| Media Player | Media3 ExoPlayer + MediaSession |
| Storage | DataStore, MediaStore API, SAF |
| Networking | OkHttp |
| Min SDK | 24 (Android 7.0) — 99%+ device coverage |

## Installation

Download the latest APK from [GitHub Releases](https://github.com/SibtainOcn/Hazel/releases).

The app includes a built-in auto-updater — you'll be notified when new versions are available and can download + install directly from the app.

## Building from Source

```bash
git clone https://github.com/SibtainOcn/Hazel.git
cd Hazel
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/`.

For release builds, you'll need to configure your own signing key — see the [Contributing Guide](CONTRIBUTING.md).

## Contributing

We welcome contributions! Please read our [Contributing Guide](CONTRIBUTING.md) before submitting a pull request.

## License

GPL-3.0 License — see [LICENSE](LICENSE) for details.

