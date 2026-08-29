# Changelog

All notable changes to Hazel are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Everything merged since 1.0.0, plus what is open in review.

### Added
- **Downloads tab.** Everything that has finished, backed by a flow so an entry appears the
  moment a download completes. Search, sort by date, title or size, and filter by audio or
  video. Tapping a row opens the file in the device's default player; the row menu removes
  the entry or deletes the file.
- **Already downloaded warning.** Resolving a link that has been downloaded before raises
  it before the sheet opens, offering to go ahead or leave it. Only a copy still present on
  the device counts, so an entry whose file was deleted elsewhere does not block anything.
  Links inside a multi-link set carry a Downloaded tag instead.
- **Missing files read as missing.** A history row whose file has gone shows its artwork
  drained of colour and dimmed, alongside the tag saying so.
- One placeholder per link while a set of links is being read, rather than a single card
  standing in for the whole set.

### Fixed
- **File sizes quoted well above the finished file**, 30.1 MB shown against a 12.6 MB
  result. The probe passed `--compat-options manifest-filesize-approx`, which makes yt-dlp
  drop the exact size it would otherwise report and substitute bitrate times duration. That
  product is an upper bound, so anything that compressed well came out far under the quote.
  An exact size is now used whenever the source reports one; where none is reported the
  estimate is computed knowingly and shown as `~ 30.1 MB` rather than passed off as
  measured.

### Changed
- Home, Downloads and More use stroked marks in one style, instead of Home and Downloads
  sharing an icon.
- README rewritten around what the app does.

## [1.0.0] - 2026-08-29

First release. The app was rebuilt around a single idea: paste one link or several, see
exactly what each source offers, choose, and download. Everything below describes that app
as it now stands rather than how it got here.

### Downloading

- **Paste, resolve, choose.** A link resolves into a card with its artwork, title, author
  and duration, and the download sheet opens on it automatically. The card itself is the
  control: tapping anywhere on it reopens the sheet, so nothing competes with it.
- **Several links at once.** Links are queued in the search screen and read together. The
  results become a list with one action that downloads the whole set, and each card can
  still be opened and adjusted on its own.
- **Real formats, not presets.** Every format the source actually reports is listed, with
  its container, codec, size, bitrate and format id. The best concrete format is
  preselected, and a video-only stream names the audio track it will be muxed with, which
  is also the track the download requests.
- **Full format list in its own sheet.** The download sheet shows one quality row; tapping
  it opens the complete list, sortable by quality, size or container, with video and audio
  under their own headings.
- **Editable title, author and container.** All three are editable for both audio and
  video. The edited values name the saved file, and where the value is unambiguous they are
  written into its tags.
- **Chosen save location.** Downloads land in `Download/Hazel`, or in any folder picked
  through the system document picker, including one on removable storage. If a picked
  folder cannot be written the built-in folder takes the files instead, so a download is
  never lost to a revoked grant.
- **Sources that report almost nothing still work.** Only the address is guaranteed to come
  back from an extractor. Title, author, artwork, duration and codecs each fall back
  through several keys, carousels are unwrapped, and a payload describing a single direct
  stream is turned into one entry. A format is discarded only when it is provably not
  downloadable.

### Processing

- **SponsorBlock.** Segment categories are chosen per download and removed by yt-dlp, which
  is also what queries the service, so the feature follows whatever yt-dlp build is
  installed.
- **Chapters.** Embedded into the file, or used to split it into one file per chapter.
- **Subtitles.** Embedded, saved alongside the file, or both, with a language selector.
- **Containers.** Video is muxed into the chosen container; audio is extracted and encoded
  into it. Cover art is skipped for containers that cannot hold it, rather than failing the
  download in post-processing.
- **Filename template.** The yt-dlp output template, editable per download.

### Sign-ins

- **Cookies for gated media.** Signing in through the in-app browser stores that site's
  cookies and hands them to every later read and download, which is what makes
  age-restricted, private and members-only media reachable.
- **Automatic offer.** When a link fails because it needs an account, the failure dialog
  offers to collect cookies for that site and retries the link once they are saved.
- **Managed per site.** Sets can be switched off without deleting them, refreshed in place
  by signing in again, imported from or exported to the clipboard, and removed.

### Speed

- **Bounded network waits.** Reading a link is almost entirely network waiting, so the
  socket timeout and retry count are bounded rather than left at yt-dlp's defaults of
  twenty seconds and ten retries. Fast, Balanced and Thorough are selectable, and Balanced
  is the default.
- **A second attempt before giving up.** A first read has to fetch player data the cache
  does not hold yet, so a failed attempt is retried once with the most patient settings
  before the link is reported as unreadable.
- **Reads grouped by site.** Links from the same site share one yt-dlp run, so its
  extractor is warmed up once; different sites run at the same time, so a slow site cannot
  hold up a fast one. This adapts to whatever was pasted with no per-site configuration.
- **Warm start.** The engine initialises in the background as the app launches, and the
  metadata cache is shared with the download, so player data is resolved once rather than
  twice.

### Interface

- **Full screen link entry** with the links queued so far shown as removable chips, and a
  history of previously used links.
- **Shimmer placeholders** while a link is being read, laid out like the card that replaces
  them so nothing shifts when the metadata arrives.
- **Progress on the card**: a percentage chip with transferred and total size, a ring around
  the cancel control, and a filled line along the artwork's lower edge. A finished download
  is marked with a flat tag in the artwork's corner.
- **Notifications** that name the media and carry the live progress line, and open the file
  in the device's default player when tapped.
- **Appearance**: dark theme and accent colour.
- **Temporary files**: what the app is holding on disk, by category, with the option to
  clear any of it. Downloads and saved sign-ins are never included.
- **Storage locations** and an **offline audio converter**, which owns its own output
  folder.

### Engine

- **Independent yt-dlp updates.** The engine updates separately from the app, on a Stable,
  Nightly or Master channel, so extractor fixes can be picked up the day they ship.

[1.0.0]: https://github.com/SibtainOcn/HAZEL-DLP/releases/tag/v1.0.0
