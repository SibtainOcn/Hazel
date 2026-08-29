<div align="center">

<img src="BRAND/Hazel.png" alt="Hazel" width="96" />

# Hazel

**A media downloader for Android.** Paste one link or several, see exactly what each source
offers, choose, and download.

</div>

---

## What it does

Hazel shows you what a link actually has, instead of guessing at a quality preset. Every
format the site offers is listed with its size and quality, and the best one is picked for
you.

**One link, many, or a whole playlist.** Queue several links at once and download the set
with one action, or open any of them and change just that one. A playlist or channel becomes
one card per video.

**Real formats.** No presets. You see the exact qualities the source has and choose.

**Works nearly everywhere.** YouTube, Instagram, TikTok, X, Reddit, SoundCloud and hundreds
of other sites.

## Features

### Downloading
- Paste or share one link, several at once, or a whole playlist
- **Hazel Direct** — share a link to it and the download starts immediately at a quality you
  set once
- Full quality list, sortable by quality, size or format
- Edit the title, author and file type before saving
- Save to `Download/Hazel` or any folder you choose, including an SD card
- Warns before downloading something you already have, and offers to play it instead
- Progress in the notification shade, with a sound when a download finishes

### Processing
- **SponsorBlock** — cut out sponsors, intros and other segments
- **Chapters** — keep them in the file, or split into one file per chapter
- **Subtitles** — burn in, save alongside, or both, in the languages you pick
- **Formats**: mp4, webm, mkv, mov, avi, flv for video; mp3, m4a, aac, alac, flac, opus, wav
  for audio
- Cover art embedding and custom file naming

### Sign-ins
- Sign in to reach age-restricted, private and members-only media
- Offered automatically when a link needs an account
- Stays on your device

### Library
- Everything you have downloaded, with search, sorting and audio/video filters
- Switch between large artwork and a compact list
- Tap to play in your usual app

### Privacy
- **Incognito** — downloads are not added to your library and links are not remembered
- No accounts, no analytics, nothing sent anywhere else

### Settings
- Link reading speed, for slower or unreliable connections
- Default quality for Hazel Direct
- Clear temporary files, with a running total
- Keep the download engine up to date, separately from app updates
- Dark and light themes, with an accent colour

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
| Metadata | [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) |
| Storage | DataStore, MediaStore, Storage Access Framework |
| Images | Coil |

## Licence

See [LICENSE](LICENSE). Hazel is a client for yt-dlp; respect the terms of the sites you
download from and the rights of the people whose work you are saving.
