<div align="center">

<img src="BRAND/Hazel.png" alt="Hazel" width="96" />

# Hazel

**A media downloader for Android.** Paste one link or several, see exactly what each source
offers, choose, and download.

Built on [yt-dlp](https://github.com/yt-dlp/yt-dlp) and FFmpeg, with a Material 3 interface
written entirely in Jetpack Compose.

</div>

---

## What it does

Hazel resolves a link into what the source actually has, rather than guessing at a quality
preset. Every format the site reports is listed with its container, codec, size, bitrate and
format id, and the best concrete one is chosen for you.

**One link or many.** Queue several links in one pass and download the whole set with one
action, or open any of them individually and change just that one.

**Real formats.** No quality presets and no hardcoded ladders. A video-only stream names the
audio track it will be muxed with, and that is the track the download requests.

**Anything yt-dlp supports.** Nothing in the app is written for a particular site. YouTube,
Instagram, TikTok, X, Reddit, SoundCloud and the rest of yt-dlp's extractor list all go
through the same path, and sources that report almost no metadata still work because every
field degrades instead of failing.

## Features

### Downloading
- Multi-link entry with queued links as removable chips and a history of links used before
- Reads grouped by site and run in parallel, so a set resolves in about the time its slowest
  member takes
- Full format list in its own sheet, sortable by quality, size or container
- Editable title, author and output container, for both audio and video
- Save to `Download/Hazel` or to any folder picked through the system document picker,
  including removable storage
- Downloads run one after another with per-link progress, and one failure does not stop the
  rest of the set

### Processing
- **SponsorBlock** segment removal, by category
- **Chapters** embedded into the file, or used to split it into one file per chapter
- **Subtitles** embedded, saved alongside, or both, with a language selector
- **Containers**: mp4, webm, mkv, mov, avi, flv for video; mp3, m4a, aac, alac, flac, opus,
  wav, vorbis for audio
- Thumbnail embedding and a configurable yt-dlp filename template

### Sign-ins
- Sign in through an in-app browser to reach age-restricted, private and members-only media
- Cookies are stored per site and reused by every later read and download
- Offered automatically when a link fails because it needs an account
- Kept in the app's private storage and never sent anywhere but yt-dlp

### Library
- Everything downloaded, with search, sorting and audio/video filtering
- Updates live as downloads finish, and flags entries whose file has since been deleted
- Tap to open in your default player

### Settings
- Link reading speed: Fast, Balanced or Thorough, plus an IPv4 fallback for networks with a
  broken IPv6 route
- Temporary file cleanup, by category, with a running total
- yt-dlp engine updates on a Stable, Nightly or Master channel, independent of app releases
- Dark theme and accent colour

## Install

Grab the APK from [Releases](https://github.com/SibtainOcn/Hazel/releases). Universal and
per-ABI builds are published; the per-ABI build for your device is smaller.

Minimum Android 7.0 (API 24).

## Build

```bash
git clone https://github.com/SibtainOcn/Hazel.git
cd Hazel
./gradlew assembleDebug
```

Release builds are signed from a keystore kept outside the repository. Point
`HAZEL_SIGNING_DIR` in `local.properties` at a folder holding your keystore and a
`signing.properties` with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. Without
it the release build still succeeds and produces an unsigned APK.

## Built with

| | |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Download engine | [yt-dlp](https://github.com/yt-dlp/yt-dlp) via [youtubedl-android](https://github.com/JunkFood02/youtubedl-android) |
| Media processing | FFmpeg |
| Storage | DataStore, MediaStore, Storage Access Framework |
| Images | Coil |

## Licence

See [LICENSE](LICENSE). Hazel is a client for yt-dlp; respect the terms of the sites you
download from and the rights of the people whose work you are saving.
