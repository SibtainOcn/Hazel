# Changelog

All notable changes to Hazel are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Everything merged since 1.0.0, plus what is open in review.

### Added
- **Playlists and channels resolve to every item they hold.** A playlist link produced one
  card, because the metadata read was told to ignore playlists outright. It now asks the
  engine what the link actually holds and reads the answer off its own `_type`, so a
  playlist, a channel, an album or anything else that turns out to hold several items
  becomes one card per item. Nothing is matched against a list of known address shapes, so
  a source nobody anticipated behaves correctly too.
- **Hazel Direct**, a second share target. Sharing a link to it downloads immediately at the
  saved quality, with no sheet and no questions. Its own settings screen under More chooses
  video or audio and a quality ceiling; a repeat warning never interrupts it, since being
  uninterrupted is the point.
- **A getting-started guide on first launch**, covering the four things the screen does not
  show on its own: that the field takes several links at once, that a share target skips the
  sheet entirely, where finished files are listed, and that Android suspends a download the
  moment the app is left. It appears once.
- **Background downloads card**, heading More while the exemption is missing and disappearing
  once it is granted. Android stops a long network job shortly after the app loses the
  foreground, which reads as downloads that never finish and cannot be fixed from inside the
  app. The card opens the system's own screen, trying three intents in turn so it works
  across builders and versions.
- **Incognito**, from a ghost in the top right of the home screen. While it is on a download
  leaves no record: nothing is written to the downloads list and no link is remembered for
  the search suggestions. The file itself still arrives in the same place it always would,
  so this is about what the app keeps and not about hiding anything from the device or the
  network. The control is lit while active, because a mode that silently changes what is
  recorded has to be visible from the screen it affects.
- **Compact list layout**, on both the results list and Downloads. A button appears once
  there are more than three items and swaps the large artwork for single-line rows, which
  fits several times as many on screen. Below three the two layouts read much the same, so
  the control stays out of the way.
- **Failures are reported outside the app.** A download or a link that fails now raises a
  notification, audible when the app is in the background, on the same terms as a success.
  Tapping it reopens the app on the failure, carrying the reason in the intent so it
  survives the process being gone, and offers the log to copy. Where the reason is a missing
  sign-in the notification carries a Sign in action straight to the cookie collector.
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
- **Processing is shown as its own stage.** Once every byte is in and yt-dlp moves on to
  merging, converting or tagging, a raked sweep crosses the artwork, the corner reads
  Processing, and the line along the bottom edge runs on its own. The band is a bright core
  inside a broader halo, with a dark shoulder either side, so it reads as light moving over
  a surface and stands out over pale and dark artwork alike; it crosses, then rests, because
  a band that loops without a gap stops being noticed.

### Fixed
- **Every download extracted the same link twice.** Resolving a link and downloading it are
  separate runs of the engine, and the second repeated all the work of the first. The read's
  own payload is now replayed into the download, which skips it. Measured on a mid-range
  device, the wait before the first byte moved fell from 5.8 seconds to 3.2, the remainder
  being the engine starting up. The payload holds addresses with a limited life, so it is
  only reused while recent, and a download that refuses it reads the link again rather than
  failing.
- **A link read a moment ago was read again from scratch.** Resolved metadata is now kept
  briefly, so pasting the same link twice, or sharing what was just looked at, costs
  nothing. This is the single largest saving available, since a read is around six seconds
  and roughly none of it is the app's own work.
- **The same video written two ways counted as two videos.** `youtu.be/ID` and
  `youtube.com/watch?v=ID` are the same video, and share links carry tracking parameters
  besides, so the repeat-download warning missed most repeats and the cache above never hit.
  Links are now compared by what they point at rather than by how they are spelled.
- **The size recorded against a download was the transfer figure, not the file.** yt-dlp
  reports one stream at a time, so a video muxed from separate video and audio streams was
  filed under whichever of them finished last, a fraction of the real size. It is now
  measured off the finished file.
- **The launch window was always dark**, so opening the app on a light device flashed black
  before the warm ground appeared, and the mark on the first screen was stroked white on
  white. Both follow the theme now. The launch window can only follow the device's own
  setting, since it is drawn before the app has read its own preference.
- **Progress notifications showed a bar and nothing else** on some builders, which collapse
  a notification carrying one down to its title. The figures are stated as an expanded style
  as well, and composed from the app's own counts rather than parsed back out of engine
  output that may not match. It reads percentage, transferred of total, speed and ETA.
- **File sizes quoted well above the finished file**, 30.1 MB shown against a 12.6 MB
  result. The probe passed `--compat-options manifest-filesize-approx`, which makes yt-dlp
  drop the exact size it would otherwise report and substitute bitrate times duration. That
  product is an upper bound, so anything that compressed well came out far under the quote.
  An exact size is now used whenever the source reports one; where none is reported the
  estimate is computed knowingly and shown as `~ 30.1 MB` rather than passed off as
  measured.
- **Cancel offered after there was anything left to cancel.** The cancel control stayed on
  the card through merging and tagging, where the transfer is already over and stopping it
  does nothing. It now goes as soon as that stage begins.
- **The download sheet opened half height** for a single link, so reaching the format rows
  and the fields under them took a drag before anything could be done. It opens at full
  height, as the sheet for a set of links already did.
- **The progress notification lagged behind the download.** It was redrawn on every second
  percentage point, and a large transfer holds one percentage for seconds at a time, so the
  speed and ETA on it were routinely stale. It is redrawn on a timer instead.

### Changed
- **A set of links is adjusted one link at a time.** Tapping a card in the set-of-links
  sheet used to turn on the tick boxes, which is not what tapping a thing you want to
  change should do. It now opens that link's own download sheet, the same one a single
  download uses, so one link of a playlist can be 720p, another 1080p and a third audio,
  and they still go out together. Ticking is its own mode now, reached by holding a card or
  from the list menu, and it narrows what a change applies to rather than deciding what
  gets downloaded. Opening a link's sheet reads its formats once and keeps them, so nothing
  is fetched twice.
- **The set-of-links sheet keeps its settings behind a bar rather than laid out below the
  list.** Download type, quality, save location, container and the rest each open a sheet of
  their own from a row of buttons along the bottom, which gives the list back the height the
  settings had taken from it. Each of them applies to the ticked links, or to all of them
  when nothing is ticked. The count beside the list is the whole set added up under whatever
  each link is currently set to, marked as a floor while some link's formats are unread.
- **The set-of-links sheet is drawn in near-black.** It covers most of the screen and is
  mostly artwork, so the grey surfaces underneath it were showing through as a cast behind
  every thumbnail. It sits on flat black now, with one step up for the rows and the bar, and
  its text is a size larger throughout. A light theme is untouched.
- **The repeat-download warning moved to where the link is entered.** It appeared on the
  home screen after the link had already been read, having spent the seconds that reading
  costs. It is now raised in the search screen at the moment of submitting, where going back
  means editing a field that is still open. A link shared in from another app never passes
  through that screen, so it keeps its warning on the home screen, and a link shared to
  Hazel Direct is never interrupted at all.
- **Playlist entries resolve their formats when opened, not before.** A listing reports
  title, author, duration and artwork for every entry cheaply; reading formats costs a
  separate request each. Doing that up front would mean minutes of waiting before a
  three-hundred-entry playlist showed anything, for cards that will mostly never be opened.
  A card that has not been opened yet shows the sheet's existing loading state instead.
- **An optional reader for listings**, chosen under More. yt-dlp remains the default and
  does all format resolution and every download either way; the alternative only answers
  what a link holds, on the sites it recognises, and falls back silently whenever it cannot.
  yt-dlp is the default because it updates itself in the field, where the alternative is
  fixed at the version the app shipped with.
- **The repeat warning and the failure dialog were rebuilt.** Both sit on the darkest
  surface and take the screen's width less a margin. The repeat warning shows the copy that
  already exists, with artwork, running time, whether it was saved as video or audio, its
  size and its age, because those are what decide whether it is the copy wanted. Its
  location is a link rather than a caption, opening the folder in the device's file browser,
  and a Play action sits beside Download again, since playing what already exists is usually
  the answer to the question the dialog is asking. The failure
  dialog leads with a sentence saying what happened and keeps the engine's own output below
  it, monospaced and scrollable in both directions.
- **The overflow menu moved into the search screen** and appears only while the field is
  empty. Its actions clear the results and the search history, both of which belong to that
  screen, and neither is what someone half way through typing a link wants.
- **The light theme is warm rather than white.** Background, surfaces and every container
  tone are mixed towards paper, replacing a near-white ground that read as glare under a
  full-bleed thumbnail. The container tones are now named outright, which also takes the
  violet tint out of the search field and the navigation bar, where Material's own light
  defaults had put it. The dark theme is untouched.
- **The notification says less.** The progress line is the source's own output with its
  stage prefix stripped, giving percentage, size, speed and ETA, and it no longer carries
  destination paths. Titles are shortened to one line, and a finished download is headed by
  the media's title rather than the generated file name. Tapping it still opens the file in
  the device's default player.
- **The log line under the cards is gone.** The card's own header already shows the
  percentage and the transferred size, the notification carries the full status line, and a
  failure offers its log to copy, so a truncated third copy on the screen said nothing new.
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

[Unreleased]: https://github.com/SibtainOcn/Hazel/compare/v1.0.0...main
[1.0.0]: https://github.com/SibtainOcn/Hazel/releases/tag/v1.0.0
