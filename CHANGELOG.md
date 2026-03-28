# Changelog

All notable changes to Hazel (formerly FetchKit) will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- **Update Dialog Flickering Fix**: Root cause was `AnimatedContent` re-animating every progress tick because each `Downloading` state was a new data class instance. Fixed by adding a `contentKey` that returns the state TYPE name only — progress updates within `Downloading` no longer trigger re-animation.
- **Centralized Update State**: Eliminated duplicated `mutableStateOf` update logic from both `MainActivity` and `SettingsScreen`. All update state now lives in a single `UpdateViewModel` (single source of truth), preventing the state synchronization bugs that caused flickering.
- **Settings Update Row**: "Check for Updates" row in Settings now navigates directly to the dedicated Software Update screen instead of duplicating the check/download/install logic inline.
- **Consolidated Update Package**: All update-related files (`UpdateViewModel`, `UpdateScreen`, `UpdateIndicator`) moved into `com.hazel.android.update` package.
- **M3 Fluid Transitions**: All update UI animations now use M3 emphasized decelerate/accelerate easing curves for fluid, professional transitions.
- **Changelog Button**: Instead of showing raw release notes inline, the update dialog now has a "Changelog" button that opens the curated GitHub Pages changelog in a dark Chrome Custom Tab.
- **Background Download**: "Background" button in the downloading state dismisses the dialog while keeping the download running. Auto-prompts install when the download completes.
- **Browser Fallback Safety**: Double-wrapped browser fallback — Custom Tabs → any browser → silent no-op. Prevents crash on devices with no browser installed.

### Added
- **Dedicated Software Update Screen**: Full-page screen (`UpdateScreen.kt`) accessible from Settings and top-bar indicator. Shows version comparison (current → new), large animated install icon with accent-color fill, download progress card with percentage/size/remaining, action buttons (Download/Cancel/Install/Retry/Check), and info section (repository, APK size, distribution, changelog link, refresh).
- **Animated Update Indicator**: `install_ic.xml` icon button placed next to the Hazel logo in the top bar. Features accent-color "liquid fill" animation that sweeps downward — tracks actual download progress during active downloads, continuous sweep when update is available, fully filled when ready to install. Tap opens the UpdateScreen.
- **Smart APK Caching**: If APK was previously downloaded but never installed, `startDownload()` detects the cached file and skips straight to "Ready to Install" instead of re-downloading. `autoCheckOnLaunch()` also detects cached APKs.
- **Intelligent Cleanup**: Old APKs cleaned on cancel. On install, APK is kept (user might cancel system installer). On next app launch, if current version is up-to-date, stale cache is cleaned automatically.
- **GitHub Pages Changelog**: New `docs/` directory with `index.html` and `changelog.json` for a curated, auto-rendering changelog page at `https://sibtainocn.github.io/Hazel/`.

### Infrastructure
- **GitHub Actions CI/CD**: Added `ci.yml` (lint + debug build on every PR), `release.yml` (auto-build signed APK on GitHub Release), `security.yml` (weekly dependency + secret scan).
- **Contributing Guide**: Added `CONTRIBUTING.md` with dev setup, signing, PR workflow, commit format, and code style guidelines.
- **Code of Conduct**: Added `CODE_OF_CONDUCT.md` (Contributor Covenant v2.1).
- **Issue Templates**: Added structured YAML forms for bug reports (device info, screenshots, logs) and feature requests.
- **PR Template**: Added checklist template for pull requests (testing, lint, changelog, screenshots).
- **CODEOWNERS**: All PRs require maintainer review.
- **EditorConfig**: Standardized formatting across editors (4-space Kotlin, 2-space YAML).
- **README**: Removed Windows references, added Buy Me a Coffee badge, Tech Stack table, Build from Source section.

### Fixed
- **GitHub Pages Changelog Not Loading**: `changelog.json` had trailing commas (lines 12, 15) making it invalid JSON. `JSON.parse()` fails silently on strict-mode trailing commas, causing the page to show "Unable to load changelog."
- **Download Log Timer**: Fixed "Downloading file..." timer not stopping when the download progress stats start streaming.
- **Release Build OOM Crash**: `packageRelease` was failing with `OutOfMemoryError: Java heap space` during APK packaging. Increased Gradle JVM heap from 2048m → 4096m in `gradle.properties`.
- **Release Build Signing**: Release build variant could not be run from Android Studio — no signing configuration existed. Added auto-detect signing config that reads keystore credentials from an external `signing.properties` companion file.

### Added
- **Global Typography Upgrade**: Switched the entire application's default system font to the premium `Inter` font family (Regular, Medium, SemiBold, Bold) for a more polished and modern UI.
- **License Settings Row**: Added a dedicated row for License in settings that opens the GitHub GPL-3.0 license page in-app.
- **In-App Auto-Update System**: Industry-grade update mechanism — checks GitHub Releases API, downloads APK in-app with progress bar, triggers Android system installer. Replaces the old browser-redirect approach. Matches how Signal, Telegram, and other real open-source apps handle updates.
- **AppUpdater.kt**: New utility handling the full APK download-to-install lifecycle — streaming download with OkHttp, progress callbacks, FileProvider-based install trigger, cache management.
- **3-State Update Dialog**: Polished Material 3 update dialog with animated state transitions — Update Found (version + notes + size + download button), Downloading (progress bar with percentage + cancel), Ready to Install (confirmation + install button).
- **Date-Based Version Code**: `versionCode` now auto-generated from build date (`YYYYMMDDNN` format) — never exhausts, always increases, self-documenting. Used by Signal, Brave, Firefox.

### Changed
- **License Upgrade**: Upgraded repository license from MIT to GPL-3.0 across README and LICENSE files.
- **Completion Dialog UI**: Updated success messages to use dynamic accent color instead of hardcoded green, and removed unnecessary folder icon from Open button.
- **UpdateChecker.kt**: Now parses GitHub release `assets` array to find the correct APK download URL. Prefers device-specific ABI (arm64) → universal → any `.apk`. Returns direct download URL + file size.
- **UpdateDialog.kt**: Complete rewrite from simple info dialog to 3-state flow with `AnimatedContent` transitions. Non-dismissable during download.
- **Storage Permission Dialog Removed**: Eliminated the custom "Storage Access" AlertDialog rationale popup. Permission is now requested through the standard Android system prompt directly — cleaner UX.

### Removed
- **REPORT.md**: Removed internal technical decisions report (competitive analysis, not for public).
- **FETCHKIT-APP/**: Removed empty stale folder from pre-rename era.
- **SECURITY.md**: Updated all FetchKit references to Hazel, fixed GitHub advisory link.
- **Storage Architecture Overhaul (Critical)**: Downloads and conversions were failing on Realme/Redmi/Xiaomi devices with "unable to create directory" because the old code required `MANAGE_EXTERNAL_STORAGE` which OEM devices don't reliably support. Complete rewrite to use app-specific temp storage + MediaStore move — **no write permission needed on any Android version**.
- **Removed `MANAGE_EXTERNAL_STORAGE`**: Eliminated from `AndroidManifest.xml` entirely. Downloads/conversions now work without ANY storage permission.
- **Permission System Simplification**: `PermissionHelper.kt` drastically simplified — only requests READ permissions for music library scanner. All MANAGE_EXTERNAL_STORAGE logic, Settings redirects, and API 30-32 race condition handling removed.
- **Share Intent Quality Bug (Critical)**: Share-to-app was downloading at low/default quality instead of best. Share intent now always downloads at **maximum available quality** (`bestvideo+bestaudio`). Paste method continues to use the persisted quality selection from settings.
- **Storage Locations Screen**: Removed Logs row (no longer needed — CrashLogger is now in-memory). Removed ugly chevron arrows. Removed subtitle descriptions. Clean title + path only.
- **History Screen**: "Open Folder" was using broken `resource/folder` MIME type. Fixed to use Documents UI strategy (`vnd.android.document/directory`) matching the download completion flow. Corrected paths to `Download/Hazel` and `Music/Hazel`.
- **Download Log Labels**: Changed "Downloading audio/video..." to generic "Downloading file..." — avoids confusion when yt-dlp shows both stages during audio extraction.
- **Completion Message**: Replaced "Checkout in your gallery" / "Listen in your music player" with single clean line: "Available in your file manager".
- **Error Buttons Redesign**: Replaced 3 bulky error buttons with 2 clean accent chips (Retry + Logs).
- **Open Button (Download/Converter)**: Updated to point to public `Download/Hazel/` and `Music/Hazel/` final locations.

### Added
- **MediaStoreHelper.kt**: New utility that moves files from app-specific temp dir to public shared storage via MediaStore API (API 30+) or direct file move (API ≤ 29). Zero-copy `FileChannel.transferTo()` for fast transfers.
- **Storage Locations Screen**: Settings sub-screen showing Hazel storage paths with file manager integration.
- **FILE_SYSTEM_REPORT.md**: Comprehensive documentation of the storage problem, all approaches evaluated, and the chosen architecture.

### Changed
- **CrashLogger.kt**: Rewritten to use in-memory `StringBuilder` buffer instead of disk files. No more log directory, no auto-clean, no file I/O. Session-scoped only — cleared on app restart.
- **LogViewerScreen.kt**: Reads from `CrashLogger.getLog()` in-memory buffer instead of disk files. Title changed to "Session Logs".
- **StoragePaths.kt**: Removed `logs` property and `LOGS_DISPLAY` constant. Rewritten as context-aware singleton with two-tier paths — temp (app-specific, always writable) and final (public shared, via MediaStore).
- **file_paths.xml**: Removed internal logs `<files-path>` entry.
- **HazelApp.kt**: Updated comments to reflect in-memory CrashLogger.
- **Download Flow**: yt-dlp downloads to temp dir (no permission) → after completion, files moved to `Download/Hazel/` via MediaStore → files survive uninstall.
- **Conversion Flow**: FFmpeg writes to temp dir → after completion, files moved to `Music/Hazel/` via MediaStore.
- **FileProvider Paths**: Updated to cover app-specific external, public Download, and public Music.
- **Guide Screen**: Updated all path references.


### Fixed
- **Now Playing Ghost Touches**: Fixed taps on empty areas of the Now Playing screen leaking through to the track list underneath — causing unintended song changes and seekbar jumps. Root Box now consumes all touch events. Also reduced M3 Slider minimum interactive touch target from 48dp to match the visible 24dp height.
- **Seek Bar Smoothness**: Replaced custom Canvas+pointerInput seek bar with Material3 `Slider` widget for industry-standard drag handling. Added `isSeeking` guard so position updates don't fight user drag (eliminates snap-back). Added `animateFloatAsState` interpolation for buttery auto-progress. Reduced position poll from 200ms → 100ms. Time label now updates live during drag.
- **Slider Thumb Black Square**: Removed M3 Slider's default `thumbTrackGapSize` (6dp) and `drawStopIndicator` from the Track composable — eliminates the black square artifact around the white circle thumb on both light and dark themes. Thumb increased to 16dp for better visual fit.
- **Per-Track Album Art**: Replaced `albumId`-based artwork (which grouped all yt-dlp downloads under one thumbnail) with per-track embedded art extraction via `MediaMetadataRetriever.getEmbeddedPicture()`. Each song now displays its own unique artwork. Falls back to `albumArtUri` → placeholder if no embedded art.
- **Library Auto-Refresh**: Added `ContentObserver` on `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` for real-time change detection (new songs auto-appear). Added lifecycle `ON_RESUME` re-scan so returning to app picks up changes. All re-scans debounced (500ms) and run on IO thread. ContentObserver properly unregistered on ViewModel cleanup.
- **Now Playing Auto-Open**: Removed `showNowPlaying` from `syncStateFromController()` — Now Playing screen no longer auto-opens when returning to the Player tab. Only opens on explicit user action (track tap or mini player tap).
- **Notification Artwork**: Per-track embedded artwork now displayed in system notification via `MediaMetadata.setArtworkData()`. Each song shows its own unique art instead of sharing one album thumbnail.
- **Play Button Not Working**: Fixed `togglePlayPause()` silently failing when controller is in `STATE_IDLE` or `STATE_ENDED`. Now calls `prepare()` before `play()` for those states.
- **First Track Tap Not Playing**: Rewrote `playTrack()` to build MediaItems synchronously (using `albumArtUri` instead of heavy IO artwork extraction for all tracks). First tap now plays instantly. Notification artwork updated lazily in background.
- **Track Switching Broken**: Added `loadedTrackIds` cache so tapping a different track in the same playlist uses `seekToDefaultPosition()` instead of rebuilding all media items. Cancelled stale coroutines via `playJob`.
- **Swipe Gesture Accidental Skips**: Removed `detectHorizontalDragGestures` from album art box in Now Playing — was causing accidental track changes and position resets on touch.
- **Skip Next/Previous Buttons**: Replaced manual `hasNextMediaItem()`/`hasPreviousMediaItem()` checks with standard Media3 `seekToNext()`/`seekToPrevious()` which handle repeat mode, shuffle, and all edge cases.
- **Notification Dismiss App-Close**: Notification X button now explicitly cancels all active notifications and stops the entire app process (clean quit).
- **Library UI Optimization**: Removed per-track thumbnail loading in the music library to improve scan performance and memory usage. All library tracks now consistently show the Hazel app icon (art still shown on Player screen).
- **HazelLoader Visibility**: Added a high-contrast circular backplate to the M3 loading indicator and restored the accent color to ensure it's visible on all themes.
- **Notification Dismiss Reconnect**: After tapping X on notification (which releases player+service), tapping a track now auto-reconnects to a fresh PlaybackService. Previously required app restart.
- **URL Format Pre-Check**: Invalid input (not starting with `http://` or `https://`) is rejected immediately with a clear error, before any network or yt-dlp calls.

### Added
- **Notification Dismiss Button**: X (close) button in media notification via custom `SessionCommand`. Tapping it stops playback, clears media items, and removes the notification.
- **Music Library Search**: Expandable search bar in library header (magnifying glass icon). Filters tracks by title and artist in real-time. Shows result count, context-aware empty state. Correctly maps filtered indices to original track positions.
- **Loading Indicator**: Reusable `HazelLoader` component using M3 `LoadingIndicator` (morphing polygon shapes, theme-adaptive). Shows centered fade-in in Player library during scan. Uses M3 `1.5.0-alpha12` for `ExperimentalMaterial3ExpressiveApi`.
- **Accent Chip**: "Accent" chip button in top bar (all main screens) opens shared `AccentPickerDialog` — same picker as Settings.
- **Bulk File Import**: Bulk mode now uses SAF file picker to import any file containing URLs (one per line). Validates each line, skips comments (`#`, `//`), rejects invalid chars, deduplicates, shows error/skip counts. Auto-starts batch download on successful parse with all user settings (quality, video/audio, folder preference).
- **Guide Chip**: Small rounded "Guide" chip at bottom-left of Download screen (replaces full-width button).

### Changed
- **Borderless Buttons**: Guide chip and Import File (Bulk mode) button now use borderless design matching the Accent chip — subtle `primary` background at 12% opacity with no border stroke.
- **Settings Social Row**: Buy Me a Coffee button, Instagram, and GitHub icons merged into a single row (BMC left, icons right). Social icons now borderless with 12% alpha background.
- **App Renamed**: FetchKit → **Hazel**. All user-visible strings, component names, theme names, notification titles, folder paths (`Download/Hazel/`), log tags, and doc comments updated across 30+ files. Package name changed to `com.hazel.android`.
- **Internal IDs Renamed**: DataStore (`hazel_settings`), SharedPrefs (`hazel_history`), notification channels (`hazel_download`, `hazel_dl_progress`, `hazel_dl_complete`), process ID, and action strings all renamed from `fetchkit` to `hazel`.
- **AccentPickerDialog**: Extracted inline accent dialog from `SettingsScreen` into shared `AccentPickerDialog` component.
- **Bulk Mode**: Removed BulkEditorScreen (paste/write URLs). Replaced with SAF file import. Download button now always shows "Download" (no more "Open" text).

### Removed
- **Universal Nav Loader**: Removed unnecessary screen-transition loader from `AppNavigation`. Loader now only shows during actual loading operations (Player library scan).
- **BulkEditorScreen**: Removed in favor of SAF file import.
- **Discord Social Link**: Removed Discord icon button from Settings and deleted `ic_discord.xml` vector.

---

## [0.4.0] - 2026-03-25

### Added
- **URL Validation**: Real-time format validation on Multi Links — rejects invalid URLs (non http/https) and duplicates with inline error text.
- **Duplicate File Handling**: `--force-overwrites` flag ensures re-downloads even if file exists (user may change quality). Logs WARN: "Item already exists, re-downloading".
- **7 New Log Mappings**: `detectPhase()` now maps: thumbnail fetching, subtitles, metadata embedding, post-processing, format fixup, extracting URL, thumbnail embedding — all clean 1-liners.
- **Animated Download Button**: During download, button shows "Please wait..." with cycling dots animation (`.  ..  ...`) in fixed-width space.
- **Per-URL Validation in Review**: Each URL in Review screen shows red border + error text for invalid format. Confirm auto-strips invalid entries.
- **Separate Folder Toggle**: Per-mode toggle in Download Mode dialog — saves downloads to auto-created subfolders inside default download dir. Playlist uses first 10 chars of playlist name; Multi Links/Bulk use `Batch_01`, `Batch_02` (auto-increment). Collision-safe: auto-appends `_1`, `_2` suffix if folder exists. Persisted via DataStore. Playlist ON by default, others OFF.
- **In-App Browser**: Social links and Buy Me A Coffee now open in Chrome Custom Tabs (dark toolbar) instead of leaving the app. Falls back to external browser if unavailable.
- **Smart Speedometer**: First 3s shows "Connecting...", then displays actual speed only if >500 KB/s, otherwise shows "Working..." (avoids confusing "0 B/s"). Uses `TrafficStats` kernel counter — zero overhead.
- **Batch Progress Counter**: Shows `1/4`, `2/4` etc. on the right side of the speed row for Playlist, Multi Links, and Bulk downloads.
- **Cancel Download Button**: Accent-colored chip button below log console during downloads. Gracefully kills yt-dlp process, cancels coroutine, cleans `.part/.ytdl/.temp` temp files, and logs cancellation.
- **Raw yt-dlp Logs**: Download progress lines (%, size, speed, ETA) and destination/format info now forwarded to log console with `↓` prefix. Progress lines update in-place instead of spamming.
- **Audio Format Picker**: Replaced bitrate-only audio options with 5 format choices: MP3 · 320kbps, AAC · 256kbps, FLAC, WAV, Opus — each with 1-liner description. MP3/AAC auto-retry at lower bitrates on failure; FLAC/WAV/Opus fail-fast with clear notification.
- **Quality Persistence**: Video quality and audio format selection persisted via DataStore. Survives app restarts.
- **Download Complete Hints**: Completed download card shows context-aware hint: "Open your gallery or video player to view" (video) / "Open your music player to listen" (audio).
- **Download Notifications**: Real-time progress bar in notification bar (silent, no popup). On completion: sound + popup only when app is minimized. Cancel/error shown briefly then auto-dismiss. Uses FetchKit lightning bolt as status bar icon.
- **App Logo in Top Bar**: Lightning bolt icon (`update.xml`) shown next to "FetchKit" title in main top bar.
- **About Screen Logo**: Replaced generic Download icon with FetchKit lightning bolt in About screen.
- **About Screen Redesign**: Rich feature cards — Save Any Video, Works Offline, Batch Downloads, Audio Extraction, Privacy First, Open Source — all non-technical language. Removed top empty space.
- **Guide Screen Redesign**: Added app screenshot vectors (p1, p2) between sections. Removed emojis for clean professional look.
- **Auto-Update System**: On app launch, checks GitHub Releases API for newer versions. Shows Material 3 dialog with version + release notes + Update/Dismiss buttons.
- **Manual Update Check**: "Check for Updates" in Settings is now clickable — triggers GitHub API check with loading state and result feedback.
- **Music Player Tab**: New "Player" tab (3rd in bottom nav) with Spotify-identical design. MediaStore scans all device audio with album art thumbnails. VLC-style track list with "Change Directory" chip (rounded dropdown + "Custom" SAF folder picker). Mini player bar with progress line. Full-screen Now Playing: dark gradient background, large album art, animated title, seek bar, shuffle/prev/play/next/repeat controls. Directory choice persisted via DataStore across sessions. LazyColumn optimized with stable keys for 1000+ tracks smooth scrolling. Background playback via Media3 ExoPlayer foreground service.
- **Error Sanitization**: Raw yt-dlp errors (like `ERROR: [generic] 'hi' is not a valid URL`) now show clean user-friendly messages. 12+ error patterns mapped (invalid URL, geo-restricted, private, network, format, copyright, etc.).

### Changed
- **Batch Logs Cleaned**: Removed square brackets from all batch/playlist log prefixes — now `2/4 · Downloading from YouTube...` format.
- **Multi Links Persistence**: `selectedMode` moved to ViewModel — mode persists when navigating to Review screen and back.
- **Next URL / View All**: Replaced full-width buttons with compact `AssistChip` pill-style chips.
- **Review Screen**: Custom `ArrowBackIosNew` back arrow, removed top padding via `WindowInsets(0)`, per-URL inline validation.
- **Chevron Accent**: Quality and Mode card chevrons changed from grey to accent color.
- **View All Auto-Capture**: Clicking View All auto-adds current URL from input field (if valid) before navigating.
- **Playlist Item Tracking**: Broadened regex to match `Downloading item/video/playlist item X of Y` + fallback `X of Y` pattern.
- **Quality Auto-Reset**: Switching Video↔Audio auto-resets quality to appropriate default (Video→"1080p", Audio→"MP3 · 320kbps").
- **Mode Dialog Highlight**: Selected mode item gets subtle primary-color background fill (like Gemini's model picker). Toggle row refined with folder icon, pill container, and 70%-scaled switch.
- **Default Theme**: First install now defaults to dark mode + White accent (was system theme + Teal).
- **Dynamic History Path**: `savedPath` in download history now reflects actual output dir (including subfolders) instead of hardcoded path.
- **Download Speed Boost**: Concurrent fragments 4→8, buffer size 16K, skipped certificate checks. Removed all artificial `delay()` calls (300ms total removed).
- **Graceful Cancel Flow**: `CancellationException` rethrown properly, `isCancelled` flag prevents post-cancel success logs, error UI (Try Again/Full Logs/Copy Error) hidden on user cancel.

### Removed
- **FetchKitDotLoader from Download Button**: Replaced with text-based "Please wait..." animation for better visibility.
- **Pencil Icon**: Removed edit icon from View All button.
- **Dead Imports**: Removed unused `FetchKitDotLoader` and `Icons.Filled.Edit` imports.
- **Artificial Delays**: Removed cosmetic `delay(200)` and `delay(100)` calls from single/playlist/batch download flows.
- **Tick Icon on Completed Card**: Removed green tick from filename in download complete card.
- **Cancel X Icon**: Cancel button now shows text-only "Cancel" without the ✕ icon.

### Fixed
- **Vector Rendering Crash**: Fixed `Aapt2Exception` during build caused by invalid `fillColor="None"` and `strokeColor="None"` in `p1.xml` and `p2.xml` exported vectors (replaced with `#00000000` transparent color).
- **Splash White Flash**: Fixed white flash between splash animation and main screen. Changed `Theme.FetchKit` from `Material.Light` to dark base with black `windowBackground` — seamless dark-to-dark transition.
- **Download Location Display**: Fixed Settings showing raw document ID (`msd:1000111272`) for non-primary storage. Now correctly shows "External Storage", "Internal Storage", or folder name.
- **Unnecessary Permissions**: Removed `READ_MEDIA_VIDEO` from manifest and runtime permission requests — app only needs audio access for music player. No more "allow access to videos?" prompt.

---

## [0.3.0] - 2026-03-24

### Added
- **Download Modes**: 4 modes — Single, Playlist, Multi Links (up to 10 URLs), Bulk (import TXT/CSV or in-app editor).
- **Multi Links UI**: Same URL field reused with dynamic "URL 1/2/3..." label. "Next URL" saves silently, "View All" opens review screen for edit/remove.
- **Multi Links Review Screen**: Full-screen editable URL list with per-item remove buttons and confirm/cancel actions.
- **Bulk Editor Screen**: Full-screen editor with SAF file picker (TXT/CSV) and manual paste mode. Shows parsed URL count, ignores comments (#).
- **Playlist Pre-Fetch**: `yt-dlp --flat-playlist --dump-json` fetches playlist name and item count before downloading.
- **Batch Download Engine**: Sequential URL processing with shared log stream. Individual failures skip to next URL without aborting batch.
- **Green Tick Dialogs**: Replaced RadioButton circles with `Icons.Filled.Check` green tick icons in Quality and Mode dialogs.

### Changed
- **Bottom Nav (Light Theme)**: Container changed from hardcoded `#000000` → `surfaceContainerHigh` for frosted glass look. Unselected items → `onSurface.copy(0.4)`. Dark theme unchanged.
- **Toggle Pill (Light Theme)**: Container changed to `surfaceContainerHigh` for visible differentiation on light backgrounds.
- **Dialog Typography**: Upgraded to `bodyLarge` text with 14dp vertical padding, 4dp spacer between items. Mode dialog descriptions use `bodySmall` with proper breathing room.
- **Theme Flash Fix**: Accent preference now loads with `null` initial → `return@setContent` prevents rendering until loaded.

### Optimized
- **DownloadViewModel Refactor**: Extracted shared helpers (`buildRequest`, `executeYtDlp`, `validateUrl`, `handleHttpStatus`, `fetchPlaylistInfo`, `finishDownload`).
- **Backward Compatibility**: Single-URL `startDownload(context, url, isVideo)` convenience overload kept.
- **Resource Handling**: `BulkEditorScreen` uses Kotlin `.use{}` pattern — streams and cursors auto-close on all paths.
- **Navigation Safety**: `multiLinkUrls` SnapshotStateList owned by ViewModel — survives navigation without state loss.
- **State Reset**: `resetState()` clears both DownloadState and multiLinkUrls list.

---

## [0.2.0] - 2026-03-24

### Added
- **URL Validation**: HTTP HEAD request validates URL before download. Logs HTTP status code (200/403/404/500) with per-step timing.
- **Network Check**: `ConnectivityManager` check before URL validation — instant "No internet" error if offline.
- **Gradient Sweep Shimmer**: Active log entries display a horizontal light-gleam shimmer effect (industry-standard loading indicator).
- **Full-Screen Log Viewer**: "Full Logs" button opens dedicated screen with raw logs, back navigation, and "Copy All" button.
- **Error Action Buttons**: On download failure, 3 grouped buttons — Try Again (primary), Full Logs (navigate), Copy Error (clipboard last 100 lines).
- **Crash Logger**: Global `UncaughtExceptionHandler` saves crash stack traces to `Download/FetchKit/logs/crash_*.log`.
- **Download Error Logging**: Failed downloads generate detailed `.log` files with URL, platform, error, and full log trail.
- **Log Auto-Cleanup**: 7-day auto-cleaner deletes old log files on every app launch.
- **FileProvider**: Open button now opens actual downloaded file in system video/audio player via `ACTION_VIEW` intent.
- **Floating Bottom Nav**: Pill-shaped bottom navigation bar with deep black (#000000) background, 28dp rounded corners, and `navigationBarsPadding()`.

### Changed
- **Unselected Button Opacity**: Video/Audio toggle unselected state bumped from 0.6f → 0.85f for better visibility.
- **Per-Step Timers**: Each log entry tracks its own duration. Active steps show live counter (100ms refresh), completed steps show final duration.
- **Real-Time Network Speed**: `TrafficStats` monitors actual device throughput (KB/s, MB/s) instead of yt-dlp internal speed.
- **Input Focus Color**: Reduced focus ring alpha to 0.5f for cleaner, non-neon appearance.
- **Filename Display**: Completion card filename limited to single line with ellipsis overflow.

### Fixed
- **App Name**: Fixed `strings.xml` key from `FetchKit` to `app_name` — resolves `@string/app_name` reference in AndroidManifest.
- **Open Button**: Replaced broken DocumentsUI folder intent with FileProvider-based file open intent.
- **System Nav Overlap**: Bottom nav bar no longer goes behind system navigation bar.

### Optimized
- **OkHttp Client**: Singleton instance (class-level) instead of per-download creation — avoids thread pool exhaustion.
- **HTTP Response**: `response.close()` added to prevent connection/file-descriptor leaks.
- **Timer Interval**: Live counter reduced from 50ms → 100ms — halves recompositions, visually identical.
- **Shimmer Rendering**: Uses `drawWithContent` for GPU-accelerated rendering without layout recomposition.

---

## [0.1.0] - 2026-03-23

### Added
- Initial FetchKit Android app with Material 3 dark/light theme.
- yt-dlp integration via `youtubedl-android` (0.18.1) with FFmpeg support.
- Video/Audio format toggle.
- Progressive log console with custom vector icons (tick, error, warn, chevron).
- Auto-paste FAB for clipboard URL detection.
- Download notification via foreground service.
- Settings screen with accent color picker and download path configuration.
- yt-dlp auto-updater on cold start (bypasses 90-day binary expiry).
- Share-to-download intent filter for URLs from other apps.
