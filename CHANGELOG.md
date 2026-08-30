# Changelog

All notable changes to Hazel are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Everything merged since 1.0.0, plus what is open in review.

### Added
- **Every audio format the engine can produce**, chosen from a sheet rather than from three
  fixed rows. Opus, AAC and MP3 head the list because they are the three anybody actually
  wants, and each format carries a word or two saying what picking it costs: Best, Most
  compatible, Lossless, Big file. MP3 stays the default rather than the one tagged Best,
  because Opus in a `.opus` file only plays out of the box from Android 10 and converting
  perfectly into something that will not play is not an improvement.
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

- **Continuous integration.** Every pull request and every push to `main` runs the unit
  tests and a build, as two jobs that start together rather than one after the other, so the
  check finishes in about the time the slower half takes. The run summary reports the total,
  passed, failed and skipped counts with a per-suite breakdown, all read from the reports the
  run just produced, so a new test file changes the numbers with no workflow edit. A pull
  request builds one unsigned debug APK, which is all that is needed to prove the code
  compiles; only a push to `main` builds release.
- **Tagged releases build and publish themselves.** Pushing a `v*` tag runs the tests, builds
  an APK for every architecture plus a universal one, signs them from the repository secrets
  and publishes a GitHub release titled after the tag, carrying the requirements and a link
  to the full changelog. A tag with a suffix, `v1.1.0-beta.1`, publishes as a pre-release.
  Nothing about the release is typed by hand: the tag is the only input.
- **Testing notes**, in `docs/TESTING.md`, covering how to run the suite, what is covered and
  why only that, and the two rules that keep the check fast as the project grows: unit tests
  stay pure JVM, and tests are named for the behaviour a user would notice breaking.

### Fixed
- **A finished file never reached the user's folder on Android 7 through 10.** Publishing a
  download or a conversion is a MediaStore insert from Android 11 and a direct file write
  below it, and the direct write needs a storage permission nothing had ever asked for. The
  write threw, the failure was caught and logged, and the file stayed in the app's own
  storage where nothing else on the phone can see it. It is asked for now, as a download or
  a conversion starts, and where it is refused the screen says the file stayed inside the
  app instead of naming a folder it never reached.
- **A file written that way was invisible until something else happened to scan it.** The
  pre-MediaStore path put the file in the folder and told nothing about it, so no music
  player or gallery listed it. It is handed to the media scanner as part of the move now.
- **The converted file reported its size as nothing.** The size was read after the file had
  been moved out of the folder it was read from, so every conversion finished by announcing
  zero bytes.
- **Opening a folder could not work on any version this app supports.** The last of the three
  attempts built a `file://` intent, which has thrown `FileUriExposedException` since
  Android 7. The documents URI it tries first was also assembled by pasting a path into a
  string, leaving the separators unencoded, and it fell back to the whole path when the file
  was not on the primary volume. It is built through `DocumentsContract` now, and the last
  attempt opens the system's own picker at the folder rather than an intent that cannot run.
- **Videos the system could not identify were unpickable.** The converter's picker asked for
  video types alone, and a document provider that does not recognise a container reports it
  as a generic stream of bytes, so those files were greyed out. The file the user came to
  convert was the one they could not choose.
- **A file whose provider withheld its name became a file called Unknown.** The name is now
  looked for in the provider, then in the address, and the extension falls back to the
  declared type, so the cached copy still carries something the engine can recognise. The
  name is also made safe to use as a filename before it becomes one.
- **The app crashed when a download was refused for being on mobile data.** With downloads
  set to Wi-Fi only, starting one on mobile data killed the app a few seconds later with
  `ForegroundServiceDidNotStartInTimeException`, leaving the notification behind to say the
  download was waiting for Wi-Fi when nothing was waiting for anything. The service that
  keeps a download alive was being started before the connection was checked and stopped
  again a few milliseconds later, and stopping a service the system has been told to start
  but has not yet created leaves the start unsatisfied, which the system kills the process
  over. The service is now started after the run is known to be going ahead, and stopping it
  goes through the service itself so it always reaches the foreground first, however short
  its life.
- **Waiting for Wi-Fi reads on the card rather than as a line of red text.** A run held back
  for want of Wi-Fi darkened nothing and said so above the list, in the colour used for
  failures, while the card underneath sat untouched and bright. The card now carries it: the
  artwork is dimmed exactly as a running download dims it, and the middle says Waiting for
  Wi-Fi. Nothing failed and nothing was lost, so it is no longer reported as though something
  had: the queue is intact and the run picks up where it left off. The notification is
  posted only when the app is in the background, since a notification repeating what is on
  screen is one the user has to sweep away for having been told what they were looking at.
- **Wi-Fi only allowed anything that was not the mobile network.** The check asked whether
  the connection was cellular, so a phone tethered over USB or Bluetooth, or any connection
  the system describes some other way, downloaded freely under a setting whose whole purpose
  is to stop that. It now asks for Wi-Fi or Ethernet, which is what the setting offers. A VPN
  reports the transport carrying it, so a VPN over Wi-Fi still counts as Wi-Fi.
- **The build assumed a `local.properties` was present.** It read the file unconditionally at
  configuration time, so a checkout without one, which is every build server, failed before
  it reached any task. The file is now read when it is there, and the signing folder can
  arrive from the environment instead.

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
- **The converter screen is three decisions in a column**: what to convert, what to turn it
  into, where it lands. Each is one row that says what it is currently set to, and nothing
  else appears until it has something to say.
- **One line of engine output instead of a growing list.** The converter used to stack every
  line the engine printed into a panel that pushed the rest of the screen off the bottom.
  It now shows the line the engine is on, replaced in place as the next one arrives, with
  the percentage on the same row. What the engine said four seconds ago is of no use to
  anybody watching it work.
- **The Convert button stays visible while it works.** Material drains a disabled button, and
  the button spends the whole conversion disabled, so the spinner and the word were barely
  there. Nothing chosen yet and a conversion already running are now told apart: the first
  recedes, the second stays lit.
- **Secondary text across the converter is legible in both themes.** It was drawn by fading
  the primary text colour, which lands somewhere unreadable on one theme or the other. It
  uses the theme's own secondary colour now, and the dark theme gained a neutral one rather
  than inheriting Material's violet-tinted default.
- **APKs are named after what they are.** Every output was `app-<abi>-<buildType>.apk`, which
  is indistinguishable from every other build once a few of them share a downloads folder.
  They are now `Hazel-v1.0.0-arm64-v8a-stable.apk`: the app, the version, the architecture
  and the channel. The channel is `debug` for a debug build, `beta` for a version carrying a
  pre-release suffix, and `stable` otherwise. The version can be supplied by the build, which
  is what lets a tagged release be versioned by its tag, and a build number that would have
  overflowed its two digits in the version code is clamped rather than rolling over into the
  date.
- **Ignored signing material by shape rather than by name.** The keystore was matched by the
  one filename it happens to have, so a renamed copy, an exported `.p12` or the base64 form a
  build server is handed would all have been committable. Extensions, environment files and
  Terraform state are now covered as well. Nothing sensitive was ever committed: the history
  is clean.
- **Per-ABI splitting can be turned off** with `-PSPLIT_ABI=false`, producing the universal
  APK alone. Five APKs take five times as long to package, which is worth it for a release
  and wasted on a check nobody installs.

- **A set of links is adjusted one link at a time.** Tapping a card in the set-of-links
  sheet used to turn on the tick boxes, which is not what tapping a thing you want to
  change should do. It now opens that link's own download sheet, the same one a single
  download uses, so one link of a playlist can be 720p, another 1080p and a third audio,
  and they still go out together. Ticking is its own mode now, reached by holding a card or
  from the list menu, and it narrows what a change applies to rather than deciding what
  gets downloaded. Opening a link's sheet reads its formats once and keeps them, so nothing
  is fetched twice.
- **The set-of-links sheet keeps its settings in a bar rather than laid out below the
  list.** Download type, quality, save location and container are buttons along the bottom
  that each open a sheet of their own, and the adjust-download options sit above them named
  the way the single download sheet names them, wrapping onto as many lines as they need.
  Nothing is behind an overflow menu, because an overflow hides exactly the settings whose
  current value is worth seeing. Each of them applies to the ticked links, or to all of them
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
- **An instant share says what it is doing while it reads.** The instant target asks nothing
  and opens nothing, so the seconds between the share and the first byte were spent on an
  empty screen that looked like a share which had gone nowhere. A stand-in card now stands
  there, named after the app the link was shared from, or after the site when Android does
  not say which app that was.
- **Screens fade through each other instead of sliding.** Home, Downloads and More are
  peers, and the vertical slide between them read as a short panel moving about in the
  middle of the screen rather than one screen replacing another. The outgoing screen fades,
  then the incoming one fades up into place; nothing translates.
- **The battery card no longer appears a moment after the settings do.** It was drawn on an
  assumed answer and corrected once the screen resumed, so it arrived late and pushed
  everything under it down. The answer is read before the first frame.
- **Source, at the end of More**, opening the project on GitHub.
- **Pause and resume, from a menu in the corner of a downloading card.** The engine has no
  pause of its own, so the process is stopped the way a cancel stops it; what makes it a
  pause is what does not happen afterwards. The part file is left exactly where it is,
  nothing is published, and the link goes back to the head of the queue. Starting again
  hands yt-dlp the same part file, which it carries on from rather than fetching a second
  time. While a download is paused the artwork keeps the treatment that says it is in hand,
  and the control in the middle becomes the one that starts it again.
- **A resumed download no longer reads as though it had started over.** The engine counts
  what the current run has fetched, not what is already on disk, so a download picked up at
  190 MB reported nought per cent while its file sat untouched. The bytes already written
  are measured before the engine starts, and the readout never falls below them.
- **A cancelled download stays cancelled.** Cancelling clears what was still owed from the
  record, so nothing comes back on the next launch; a paused one is kept, but shown as
  paused rather than started again, since a launch undoing a pause would make the button
  mean nothing.
- **The speed limit is chosen from a list rather than typed.** The value has a shape the
  engine expects, a typo in it only shows up as a download running at modem speed, and
  nobody has a particular number in mind. The choices cover the reasons for setting one at
  all: sparing a metered connection, and leaving room for everything else on the network.
- **The download queue outlives the app.** It was held in memory only, so a swipe off the
  recents list, a crash or a low-memory kill threw it away without a word: the user asked for
  ten downloads, got three, and nothing anywhere said what happened to the other seven. Each
  link is written down as it is queued, with the settings it was asked for under, taken off
  again once it is done either way, and picked up on the next launch. A download interrupted
  part way through starts itself again where it left off.
- **Wi-Fi only**, under Downloads in More. Checked as a download starts rather than
  throughout, so a transfer already going when the phone leaves Wi-Fi is left alone: cutting
  it off partway wastes the data it has already spent.
- **A speed limit**, under Downloads in More. Blank for none, otherwise a number with an
  optional K or M, passed through as yt-dlp's own rate limit.
- **Source code** at the end of More, opening the project on GitHub, and the storage screen
  it sits above is now called Downloads, since it covers more than where files land.
- **A download keeps going once the app is left.** It ran inside the screen that started
  it, so the system suspended it shortly after the app stopped being visible and killed it
  outright when the task was swiped away. It runs behind a foreground service now, on a
  scope tied to the process rather than to the screen, so a set of ten links finishes on its
  own with the phone in a pocket. The service holds the progress notification the download
  already posts, rather than adding a second one, and goes away when the queue is empty.
- **The home screen keeps every link of a run, with the one downloading now at the top.**
  It showed whichever link was being worked on and nothing else, so a set shared in over a
  few seconds looked like a single download. Everything asked for stays listed for as long
  as the app is running, each saying whether it is downloading, queued or saved, and the
  list is reordered rather than animated so a card does not slide about under a moving
  progress bar.
- **A search adds to the list rather than replacing it, and Clear empties it.** Everything a
  run has collected, downloaded or queued stays in view for as long as the app is running,
  whether it arrived by share or by search, so a link read a minute ago is still there to
  open. A Clear action sits beside the layout switch for putting the list down, with the
  layout switch itself kept in the corner where it has always been.
- **Cards arrive rather than appear.** Each one fades up from slightly below where it
  belongs the first time it is drawn, and the pinned header takes on a separation once the
  list passes under it. The header itself stays put: what a scrolling app bar does for the
  space is not worth losing the field and the layout switch to.
- **Links asked for while a download is running join the queue instead of being dropped.**
  Starting a download was the only way in, so a second set asked for mid-run was silently
  turned away. Sharing three links to Hazel Instant in a row now downloads all three: the
  shares are read one after another, each handing its download to the queue behind it, and
  each keeps the settings it was asked for under rather than picking up whatever changed
  later. Shares themselves are held as a list too: they arrive as separate intents on the
  same screen, and the single slot they used to land in meant each one overwrote the last
  before it had been read.
- **The download sheet stops reopening every time you come back to the home screen.** Which
  link had already had its sheet opened was remembered by the screen, and the screen is
  rebuilt on every return from the downloads list or the settings, so the memory was blank
  and the sheet opened again. It is remembered by the view model now, and a fresh read is
  what lets the next one open.
- **A link already downloaded and still on the device offers to play it.** The download
  sheet puts a Play action next to the one that would fetch it a second time, so coming back
  to a link to watch it does not mean downloading it again first.
- **Links shared into the app are remembered like typed ones.** The search screen offered
  back only what had been typed there, which made the history look like it had forgotten
  half of what the app had downloaded. Incognito still records nothing, which is its point.
- **The compact rows show how much of how much, not just a percentage.** A percentage on its
  own says nothing about whether the wait is thirty seconds or ten minutes.
- **The app name sits over the home screen only.** The other two carry their own headings,
  and the name above those stacked two titles on top of each other and pushed the screen's
  own one down a bar's height for nothing. More names itself now, the way the downloads list
  does.
- **The link count appears from two links up.** A single card is not a list, and "1 links"
  read as a bug.
- **The share sheet stops saying the name twice.** Android puts the app name in front of a
  share target's own label, and the label named the app again, so the entry read "Hazel
  Hazel direct". It is "Hazel Instant" now, and the feature is called that everywhere else
  in the app too.
- **The downloads search field is shaped like the one on the home screen.** It was a boxed
  outlined input with square corners sitting a screen away from a pill, which read as a
  control borrowed from somewhere else. Same pill, same tone, with a clear button once
  something is typed.
- **The downloads tab mark closes its bowl.** It was left open on one side to echo the home
  mark, but at the size the navigation bar draws it the gap read as a rendering fault.
- **The results list builds only what is on screen.** It was a scrolling column, which
  composes everything it holds whether or not any of it is visible, so a playlist of a
  hundred built a hundred full-width images at once and the app ran out of memory on the way
  back from the compact layout. It is a lazy list now and holds any length without that.
- **The search field, the link count and the layout switch stay put while the results scroll
  under them.** A set of a hundred links used to carry all three off the top of the screen on
  the first flick, which left the controls the screen is for a long scroll away.
- **The layout switch is always offered, and each list remembers its own answer.** It
  appeared only past three items, so the control came and went with the item count and
  nobody learned it was there. Both lists show it whenever they hold anything, and the
  results list and the downloads list are remembered separately between launches: the first
  is read while deciding what to download, where artwork identifies a link, and the second
  while looking for a file that is already there, where a name finds it faster.
- **A download's size stops moving while it runs.** The figure beside the progress was read
  off yt-dlp's own output, where on a fragmented transfer it is an estimate refined upward
  as the download goes, so it climbed for the whole download and ended nowhere near where it
  started. It is now the size the sheet advertised, plus the audio track where one is being
  muxed in, and it does not move. A source that reported no size at all still falls back to
  the engine's figure, which is the best there is in that case.
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
