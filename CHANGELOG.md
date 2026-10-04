# Changelog

All notable changes to Hazel are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Failed downloads can be picked and removed together: long-press a failed card, or choose Select failed in the queue menu, then remove the picked ones or select all. Picked cards light up with a wash of the accent, and removing them asks first.
- The error log has a Copy URL button, and a download that failed part way says where it stopped and how much had arrived.
- Synthesizing has a "Download sheet opens on" setting, Video or Audio, for the tab a link's sheet and a set of links open on. A link with no audio still opens on Video.

### Changed
- Errors show in a true red across the app, in place of a faded pink. The "Could not read this link" dialog shows the engine's report wrapped in full, in a box seven lines high that scrolls past that.
- Failed downloads are drawn like the cards on the home screen: the artwork with the title, source, time and reason over it, and Error Log and Retry on the card. Tapping the card opens the link's download sheet over the queue, loading as it reads, to download it another way; a read that fails there updates the card's log. A card being retried says Retrying until it settles. The error log opens on a sheet with wrapped lines, sized to the log.
- The failed list keeps the last 100 failures.
- Choice dialogs in the settings are more compact, and a choice is applied with OK instead of the moment it is tapped.

### Fixed
- Retrying a failure, or reading its link again, no longer drops it from the list before the new attempt settles. A retry of a link Instant could not read runs through Instant again, and a link that finishes downloading clears its failure even when it was shared as a short link.
- In incognito, failed downloads are listed for the session but no longer written to storage, as incognito promises.
- A download whose title is a single word, such as "Flickermood", is no longer saved as "NA". A one-word author no longer has its artist tag written as "NA" either.
- Hazel Instant can save audio again. Synthesizing has an "Instant saves as" choice of Video or Audio only. Audio only takes the source's best audio stream, in the preferred language and codec where it has them, and saves it in the chosen audio format with its cover. Since 1.1.10, Instant always saved video.
- Picking NewPipe as the formats source for a site with a saved sign-in now says why it cannot be used, instead of doing nothing. NewPipe sends no cookies, so such a site is always read by yt-dlp; the notice shows only when the link's own site has a saved sign-in and Use cookies is on.
- Switching the formats source while the list is still loading is no longer dropped. The switch runs as soon as the current read ends, and the list shows its answer.
- Once a link is read, its format list always starts with Best quality and ends with Worst quality, for video and audio, whichever source read it. A yt-dlp read showed neither, and a NewPipe read only Best.
- The second share target is labelled Instant, so share sheets that put the app name above it no longer show "Hazel Hazel Instant".
- A link shared or downloaded just as the previous download finished no longer has its download service stopped and its card shown as finished under it. The run now closes in one step, and a link that arrives while it closes starts the next run. Its last write of the queue no longer writes over a link added at that moment, which a restart would then have lost.
- A download shared to Instant now has its card on the home screen too, filling as it downloads, as one started in the app does. It takes the screen as a link read in the app does, replacing the last results or search. Before, opening the app showed nothing until the queue screen was opened.
- The empty home screen no longer shows a faint band under the search bar. It is the separator for a scrolled list, and the empty screen was counted as scrolled.
- The sign-in page opens at once with the shape loader, which stays until the site shows something, instead of a black screen. The browser is started after the screen is up rather than before it.

## [1.1.12] - 2026-10-03
### Fixed
- yt-dlp updates install again. Every downloaded build was wrongly rejected as damaged and each launch went back to the bundled yt-dlp, which YouTube now refuses with HTTP 403. A new build now goes live only after it starts on the device, and the previous one is kept if it does not.
- The yt-dlp update notifications (new version, installed, failed) now post; the unused verify switch is gone.
- The Hazel update screen no longer shows automatic update, notification and verify switches that did nothing there and quietly changed the yt-dlp update settings.
- Nightly and Master users no longer see a false engine update badge.
- A failed download shows the error that stopped it rather than the first warning.
- With several downloads in the queue, the filling download glyph and the stage track show on the card that is actually downloading, not on the one before or after it.
- Waiting for Wi-Fi shows only on queued cards, not on every card on the home screen.
- The address field for a new sign-in no longer starts with the cursor before `https://`, which turned typing into `vimeo.comhttps://`.
- Flinging a list in a sheet to its end no longer stretches and drags the sheet, which then sprang back. This covers the format, cut, audio language, link list, batch audio quality, language, download details and cookie sheets. Dragging down from the top of a list still closes the sheet.

### Changed
- The update screens use a calm wavy progress bar.
- Queued cards on the home screen show the download glyph, empty and still, until their turn.
- Download all stays on a playlist or a set of pasted links while it downloads and after it is saved. It offers what is not queued yet, the whole set again once it is saved, and opens the queue when every link is already in it. Search results still never show it.
- Paste sits above Download all instead of hiding behind it.
- The Cookies screen is laid out like the other settings screens: Use cookies, ways to add a sign-in, and the saved sign-ins on cards. Each saved site shows how many cookies it holds and when they expire, with expired sets marked in red, instead of raw cookie text.
- Signing in to a new site takes just its address in a sheet, and a bare address such as `vimeo.com` works. A saved site opens a sheet to rename it, change its address, show or copy its cookies, sign in again or delete it.

## [1.1.11] - 2026-10-02
### Added
- Hazel Instant is back as a second share target. Sharing a link to it starts the download with no sheet: the link is read in the background and downloaded at the preferred quality and folders from settings (a playlist or channel downloads every entry). A small dialog confirms it with a shimmering Hazel mark, OK and a tune button that opens Synthesizing, and closes itself after 3 seconds. The download is on the queue screen from the moment it is shared, waiting while the link is read; a link that cannot be read goes under Failed with its log and a Retry, and a notification says why, with Sign in when the site wants one. Its read is separate from the home screen, so a link being read there or a second Instant share does not drop it, sharing the same link twice gives one download, and the download keeps running after the dialog is closed.
- Getting started in More, above Documentation, shows the first-launch walkthrough again. The walkthrough has a new Share to Hazel step (download right away with your preferred settings, or wait for the details and choose every option), and the Hazel Instant step says what Instant does now.
- An empty home screen offers three starters as chips: Paste link, Search and Downloads.
- Documentation in More, in place of the License row: User guide, Features, FAQ, What's new and License, read in the app. What's new and License are read straight from the repository's own files and drawn in the app's style, so they open quickly; the rest come from the Hazel website. Pages follow the app's light or dark tone, links to other sites open in the browser, a shape loader shows while a page loads, each page has an Open in browser button, and a page that cannot load offers Try again.
- Save downloads to an SD card. The save location in the download sheet, the batch sheet, the share overlay and More › Downloads lists Internal storage, each SD card that is in (with its free space) and Other folder, set apart for audio and video. Picking a card opens the system picker on that card (Android 10+ through the card itself, 8–9 at its root, 7 among the picker's places), and a card's root gets `Hazel/Audio` or `Hazel/Video` made inside it.
- A download going to an SD card keeps its working files on the card too, so a large video does not have to fit in phone storage while it runs; names are kept to what the card's file system accepts.
- Format list in two panes: a rail of All, one stop per quality the source offers (worked out from the picture size, so cropped and vertical video land on the step they belong to) and Audio, which is always there, beside a list that scrolls on its own. The rail only shows when there is something to split.
- Search inside the set-of-links sheet: a search button beside the count opens a field in that row that narrows the list by title, channel or address, with a close button; tapping elsewhere closes it when nothing is typed.
- Cookies in the Adjust section of both download sheets: the app's own Use cookies switch, whether a sign-in is saved for the link's site, and a Sign in button that opens the sign-in page.
- The link button in the download sheets shows the address with Copy and Open (Copy takes every address of a set).

### Changed
- The format list opens on the quality step set as preferred in settings (or the nearest one below it), and on All when no preference is set, instead of always on the highest step.
- Under All, the kind listed first no longer has a Video (or Audio) header above it, so the first row lines up with the rail.
- Download properties drop the Properties title and the Status and Kind rows; the thumbnail and title lead the sheet.
- Messages in the download sheets, such as turning incognito on or off, show as a small toast sized to the text in the middle of the sheet instead of a full width bar at the top.
- Every link button opens the same Copy and Open dialog: the single-link sheet, the set-of-links sheet (Open goes to the playlist or channel the set was read from) and the link in download properties, which used to copy and open in one tap.
- Store screenshots 4, 5 and 10 are replaced with current ones.
- Storage cleanup's Unfinished downloads, cancelling a queue and removing a paused download now also clear working files left on SD cards.
- If the card is taken out, full or no longer allows access, downloads are saved to internal storage instead of failing, and a dialog says which ones and where they went (once per batch, wherever you are in the app). Problems while choosing a folder (access refused, no folder picker, the Hazel folder could not be made on the card) are shown in a dialog too.
- The single download sheet groups its controls into Details, Quality, Save and Adjust sections that open and close, each saying what it is set to while closed. Quality opens by default and at most two stay open, so the sheet fits the screen. Download is the one filled button, in the accent colour.
- The set-of-links sheet uses more of the width. Its options and container moved into a collapsible Adjust section as compact chips; type, quality and folder stay in the bottom row with the link and incognito buttons at its end; the keyboard opens over the sheet instead of lifting it.
- Adjust options take the accent colour while on instead of showing a count; chips wrap onto new lines and a long label is cut short instead of breaking the chip. Live is always shown, quiet where it does not apply, and its options only reach downloads of live or upcoming streams.
- Container opens a sheet of choices instead of a menu.
- Messages from the link and incognito buttons show over the top of the sheet, where they are always in view.
- The size badge is a fixed steel blue in every theme rather than violet.
- While a link's formats are read, the quality row keeps the best as a stand-in and fills two waiting pills where the codec and size will go; the real badges spring in when they land. The rail glints, the skeleton rows take the colours of a real row, and the Fetching heading sweeps faster.
- The quality steps a link offers before it is read (best, 2160p down to 144p, worst) are one list, used by the single sheet, the share sheet and the set of links; the set's video quality is picked in the same format list as everything else, and these steps carry an Auto tag. 144p is now offered before a link is read too.
- The format list's options button uses a tune icon.
- While a link is read, the format list shows its quality steps with a moving "Reading the rest of the formats" line instead of placeholder rows under them; placeholders only fill a list that has nothing to show yet.
- Switching the format list between yt-dlp and NewPipe keeps each one's last read of the link, so switching back is instant instead of reading the link again. The format list shows the reader its rows actually came from, and changing the reader in settings no longer throws away saved reads.
- Saved reads are kept for the last 50 links (was 40) and still survive the app being closed. A read is only reused while the settings it was made with still apply: changing PO tokens, player clients, YouTube extractor arguments, metadata language or browser imitation makes the next read a fresh one (YouTube settings only affect YouTube links). Reads made in incognito are kept in memory only and never written to disk.
- Temporary Files lists Saved link reads on its own row, with its size, and clearing it also forgets the reads held in memory. Clear everything keeps them unless its new Also clear saved link reads box is ticked (off by default).
- Removing a link from a set by swiping now asks first, needs half the row's width and only goes towards the Remove label.
- Download all appears only for several pasted links or a playlist or channel, not for keyword search results.
- The paste button shows whenever the clipboard holds something to paste, without Android announcing a clipboard read. It opens with its label for a new clip and folds to an icon, is not offered again for a clip already pasted, and stands aside while a read runs or Download all is shown.

### Fixed
- Sharing a link while an earlier share sheet was still open, or left in the background, brought back the earlier link; the sheet now switches to the new one.
- In the share sheet, placeholder rows stayed at the bottom of the format list under the quality steps for as long as the link was being read.
- A quality picked from a shared link's sheet before the link was read was replaced by the default when the formats arrived; it is now carried onto the formats the link offers.
- A long format id squeezed the quality name in the format list; the id is now cut short in its own space.
- Option chips could be pushed off the edge of the download sheet on narrow screens or with long translations.

## [1.1.10] - 2026-10-01
### Store
- Search by words and play videos right on the home cards
- Cut with a live preview, record live streams from the start
- New Synthesizing, Advanced and Backup screens
- Steadier YouTube reads with on-device PO tokens
- Queue card now shows the real download state
- Fixed long titles, chapter splits and subtitles failing downloads
- Fixed engine updates breaking downloads in progress
- Playlists no longer open as a single song

### Added
- More › Synthesizing sets the saved download options the sheets start from: SponsorBlock (on or off, categories and server), bitrate, cover art and crop, preferred audio language and codec, audio format, subtitles (embed, keep the files, languages), video format, chapters, preferred video codec and quality, with a reset.
- More › Advanced sets YouTube player clients, PO tokens and visitor data, YouTube metadata in the app's language (only languages YouTube accepts), other YouTube arguments, a pause between requests, skipping certificate checks and extra download arguments, applied to every read and download.
- Make PO tokens on this device (More › Advanced, experimental, off by default): YouTube's own check runs in a hidden WebView and a token is minted for each video read or downloaded, when none are pasted in, with a Test row that makes one on the spot. Later tokens reuse the check's session for its lifetime (twelve hours), so they take milliseconds. A live harness (`tools/live/test_po_token_live.mjs`) runs the same page in Chromium and confirms the tokens get a video's formats past YouTube's bot check.
- Imitate a browser (More › Advanced) makes every request look like a real browser's, for sites that turn away anything else. The engine is asked which browsers it can imitate on the device and only those are offered, since yt-dlp stops a download asked for one it cannot do.
- Cut in the download sheet downloads only part of a video or track: a range slider with editable start and end times, and an optional precise cut that re-encodes the ends instead of moving to the nearest keyframe.
- Live in the download sheet, for live streams and upcoming premieres: record from the start of the stream, or wait for it to begin and then download it.
- More › Backup saves settings, the downloads list, the queue, failed downloads, cookies and search history to a file (all, or the categories picked), in `Download/Hazel/Backups` or a chosen folder, and restores any of them from a backup file. Restoring merges lists into what the phone already has, leaves out anything tied to the old device (picked folders, update flags), and brings queued downloads back paused. Automatic backup (on by default) backs everything up once when an update to Hazel is found.
- Links read but not downloaded (one, several or a playlist) stay on the home screen when the app is closed and opened again, until a new read replaces them or they are cleared. Nothing is kept in incognito.
- More › Downloads has Parallel fragments (1, 2, 4 or 8 pieces of a stream fetched at once, 8 by default) and Refresh slow links (fetch fresh links when a download drops under a chosen speed, off by default), beside the speed limit (no limit by default).
- Search by words: anything typed that is not a link is searched for, on the site picked in the chips under the search field (YouTube by default, YouTube Music, SoundCloud, Bandcamp, Bilibili, Niconico, PRX, Rokfin). Results arrive as home cards that open the same download sheet as a pasted link, and download on their own or all together. NewPipe answers first on the sites it knows and yt-dlp answers everywhere else and whenever NewPipe cannot. Repeat searches within half an hour are instant, and searches go into the search history. The words searched stay in the home search bar. A result's sheet opens at once and says it is fetching while the details are read, with a thin bar under the quality until the full list of formats arrives; the sheet fills in as they land, and a download chosen meanwhile starts as soon as they do, at the best quality the preferences allow.
- Play from the home screen: the play button on a card plays it in place, with play and pause, a thin seek bar that shows what has loaded and can be tapped or dragged, the time, full screen and a Download button. A double tap on the left or right half skips back or ahead 5 seconds, and a hairline of progress stays along the bottom while the controls are hidden. Full screen shows a wide video sideways across the whole screen, turned to whichever way the phone is tilted even with auto-rotate off, while the app behind stays upright, so playback never stops for a rotation. A quality button beside full screen lists the qualities that video offers (up to 1080p), from whichever engine found it, and shows only when there is a choice; the pick is kept for later videos, and a video without it plays the nearest quality below, or its smallest. Tall videos such as shorts and reels count their shorter side, so a 1080 by 1920 short is 1080p. One card plays at a time and it stops when scrolled away. Works on any site either engine reads, including Instagram reels and SoundCloud: NewPipe finds the stream first where it can, and yt-dlp takes over when it cannot or the stream fails, reading the link again as a last try.
- Cut is now a sheet with a video preview that loops the chosen range. Moving the start plays from it, moving the end plays the moments before it, and Start here and End here set either end at the playing position.
- Search suggestions from Google while typing (More › Link Reading, off by default).
- More › Link Reading is redesigned into Reading links, Search and Playback sections: which engine reads links, runs searches and finds streams, the connection patience, results per search, the default search site, and the tallest video the player asks for (720p by default).

### Changed (search and playback)
- The search field reads "Search or insert URL".
- Everything the app asks of NewPipe goes through one place (`download/extractor/newpipe`), and Media3 players are built in one place (`download/playback`), so a library update is fixed in one file.
- More reads are kept for reuse: up to 200 parsed reads in memory and 40 on disk, up from 10, and reads NewPipe answered are cached too, so the cards of a search or playlist reopen at once.
- The main screen no longer restarts when the phone rotates, so playback carries on into full screen.
- More lists Appearance first, and Downloads after Backup.
- The home screen's Update button follows Hazel's own light or dark theme rather than the phone's, reads in the app's language, and goes away on launch once the offered version is installed, even offline; before, it stayed until the next successful check.

### Fixed (file names)
- A post whose caption is its title (TikTok, X, Facebook and similar) failed to download with "File name too long", since yt-dlp does not shorten titles. Names are now kept inside the 255 bytes Android allows, measured in bytes so Japanese, Chinese and emoji titles fit too, and the sheet's title field starts from the caption on one line without its trailing hashtags, cut at a word.

### Fixed (subtitles and chapters)
- Split by chapters wrote the chapter files to the process's working directory, which on Android cannot be written, so splitting failed; they are now saved beside the video, named after it.
- The default subtitle languages (`en.*`) also requested every machine translation into or out of English, and one refused request (YouTube rate limiting) failed the whole download; the default is now English and the original-language track.

### Fixed
- Downloads failing or looping after a link was shared in (#45): yt-dlp updates were written over the live engine while a download was using it, breaking it with `bad local file header`. A new build is now downloaded beside the live one, verified, and swapped in atomically only while no download or read is running; a damaged engine is restored from the bundled copy.
- Playlists opening as a single song (#44): a video opened from a playlist and the video alone shared one cache entry, so a playlist pasted again could come back as one card. Reads are now keyed by media and collection, and every YouTube playlist no longer shares one key.
- YouTube offering only a single 360p format: Hazel no longer forces a fixed list of YouTube player clients and lets yt-dlp choose the ones that still serve the full ladder. Reads cached by older builds are dropped once.
- Audio downloads saved as `.webm`: audio is now extracted to the file it really is (for example `.opus` or `.m4a`), or converted to the chosen format, with cover art embedded where the format allows it.
- Every song showing the same cover in music players: audio files now carry an album tag, the source's own album where it has one and the track title otherwise.
- Format reads for several links at once failing after the first, and concurrent results overwriting each other.
- Skeleton placeholders drawing dark blocks on the light theme.

### Changed
- Downloads without a chosen folder are saved to `Download/Hazel/Audio` or `Download/Hazel/Video`.
- yt-dlp updates itself in the background on launch, following the automatic download and Wi-Fi only settings of the update screen.
- The batch sheet's quality button opens the format list for audio as well, showing the formats the links share with the size of the whole set; a codec such as Opus or AAC can be chosen for links from mixed sources.
- Bitrate is its own setting for audio conversions, in the single and batch sheets, and the batch sheet's bottom buttons are drawn on a surface.
- The format list has a filter sheet (All, Suggested, Smallest per resolution, Generic, and the sort order), an update button that reads the formats again, and a choice of reader (NewPipe or yt-dlp) for links both can read.
- Loading skeletons rest at low opacity with a soft band sweeping across them, the same in both themes, and show only while a read is actually running.
- A new Queue tab holds everything downloading, waiting or failed, with pause, resume, cancel and retry per item and for all; a red dot on the tab shows while anything is in hand. The home screen no longer carries these controls, and a running card opens the queue.
- The Downloads tab lists finished files only, with a layout toggle, sort order and direction, Audio and Video filters, a deleted/not deleted filter, and removing all, deleted or duplicate entries.
- The bottom bar is a compact row of icons.
- Sharing a link opens its sheet at once with a ready quality ladder (best, ~2160p to ~240p and worst for video; best, ~192 to ~64 kbps and worst for audio) and a shimmering header while the link is read; the real formats replace it when the read lands, and a download chosen before then starts as soon as it does. Ladder choices use yt-dlp format sorting, so a source without that exact quality gives its nearest. The separate Hazel Instant share target and its settings are removed.
- Reading a new link replaces the previous results, and the loading skeleton fills the screen from the top.
- The batch sheet offers an instant quality ladder for audio (best, ~192 to ~64 kbps, worst) and more video heights (down to 144p, and worst), without reading every link first.
- Media cards shrink slightly while their list scrolls (not the format list), in step with how fast it moves: a slow drag barely touches them, a flick pulls them in, and they spring back as soon as the list slows or stops. Chips and small buttons are drawn as flat surfaces.
- The Thumbnail option opens a dialog with Cover art (on by default) and Crop to square (off by default), in the single and batch sheets. A live harness (`tools/live/test_thumbnail_embed_live.py`) checks both against real links for every audio format and video container the sheet offers.
- The share sheet shows a shimmering "Fetching…" heading while a link is read, in place of blank placeholder bars.
- Audio and video each have their own save folder, shown and changed in More › Downloads and in the download sheet, kept until changed or reset. A folder chosen in an earlier version stays in use for both.
- While a download is processing, its artwork shows the stages yt-dlp goes through for it (Fetch, Merge, Extract, Remux, Subtitles, Cut, Tags, Cover, Split, Save, as the download asks for them), three at a time, in the theme's colours, with a shimmer running along the line to the next stage, and sliding along to the next three until the last are in view. The line along the card's bottom edge is no longer drawn while processing, since the track shows the progress, and the stage reached is shown with the track drawn under the title rather than over it.
- The queue card shows the stage track for the whole download: while the streams are fetched, the line from Fetch fills with the transfer, the percentage sits beside Fetch, and the size done of total and time left sit in one compact line at the bottom left; pause, resume and cancel are in the card's menu. The progress ring, cancel button and bottom progress line are gone.
- The home screen card shows a larger download icon (an arrow dropping into a tray) that fills as the download does, straight on the artwork, with a cyan-to-blue liquid that keeps its own colours on any artwork and two rolling waves, in place of a progress ring around a fixed icon; the percentage, sizes and line along the bottom edge are left to the queue, and the duration stays with the state tag (Saved, Queued, Paused and so on) beside it, apart from the play button. Neither card shows a Processing tag any more, since the stage track says it.
- The download sheet's Audio and Video tabs sit at the start of the sheet, with a short bar under the chosen one.
- A read failure offers adding cookies, and the share sheet's failure dialog copies its log.

### Fixed (continued)
- The counts on the queue's Running, In queue and Failed tabs overlapped the end of the tab name; they now sit beside it.
- When a download finished in a list of several, the home screen jumped to the end of the list, following the finished card back to its place; it now stays where it was.
- The queue card could show the processing stages while a download was still transferring (the notification still counting up), which also greyed out Pause. The processing state is now worked out afresh for each run from yt-dlp's own lines.
- Two downloads could start at once when a link was shared while a saved queue was resuming, failing with "Process ID already exists".
- Reading a new link during a download no longer changes that download's title, file name or history record.
- An engine too old for the options the app passes is replaced with the bundled copy instead of failing every read.
- Starting a download while another was paused silently resumed the paused one and dropped it from the list; a paused download now stays paused until resumed, and Resume works while something else downloads.
- Every download shared one temporary folder: cancelling or failing one deleted a paused download's progress, and a finished download could publish another's half-made file into Downloads. Each download now works in a folder of its own, and a cancelled download is discarded instead of published.
- Clear queue removed paused downloads and the running download's record along with the waiting links; it now clears only the waiting links.
- Resume from the notification after the app was closed did nothing (and on Android 12+ could not open the app); it now resumes in the background. Cancel from it now really drops the paused download.
- Cookie files exported from a browser lost their HttpOnly (sign-in) cookies, and sites under two-part domains such as .co.uk or .co.in shared one another's cookies.
- A cover that could not be embedded in the file's container failed the whole download (archive.org, single-file FLV, AVI, TS and similar); such files are now remuxed to MKV, and WAV, AIFF and WMA sources extracted to FLAC or M4A.
- Links whose source reports no length (Instagram reels, some DASH streams) showed no duration; the length is now added up from the stream's segments or read from the media file itself, and a length from a playlist listing is no longer lost when the full read has none.
- A downloaded Hazel update APK stayed in the cache when the app was next opened offline.
- Opening the app after sharing a link from another app (downloaded, cancelled or dismissed) opened that link's sheet again; results read by the share overlay now stay on the list without reopening it.
- A playlist or several links opening on the last card instead of the first: the list kept the scroll position of the previous results.
- Cropping a cover to a square failed the whole download, and a cover that was already a JPEG was never cropped.
- The Downloads tab's row layout stretching each row to many times its height when a long author left no room for the date; the tags now wrap to a second line and are cut short with an ellipsis rather than wrapped letter by letter. The card layout's date no longer overlaps its tags on narrow screens.

## [1.0.10] - 2026-09-27
### Changed
- Reverted default link reading extractor to yt-dlp (`ListingSource.YT_DLP`) to guarantee complete extraction of all available audio languages, subtitle tracks, and video qualities across all media sources.
- Streamlined Link Reading settings screen to display minimal "yt-dlp" and "NewPipe" options without verbose descriptions or badges.
- Accelerated CI and local test execution by tuning Gradle and Kotlin compiler daemons with Parallel GC and build caching.

## [1.0.9] - 2026-09-26

### Store
* In-app Software Update hub for Hazel and yt-dlp extractor engine updates.
* Transparent share overlay with instant downloading and format selection over host apps.
* Built-in NewPipe reader for ultra-fast link extraction with yt-dlp fallback.
* Modern Getting Started onboarding walkthrough and improved permission flows.
* Streamlined download queue, artist metadata tagging, and UI polish across all screens.

### Added
- Software Update hub (`SoftwareUpdateScreen`) presenting two distinct component updaters: Hazel application updates and yt-dlp extractor engine updates.
- Dedicated Hazel in-app updater (`HazelUpdateScreen` & `HazelUpdater`) with GitHub release parsing, semver comparison (`isNewer`), architecture-aware APK matching (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `universal`), release channel selection (Stable, Beta, Nightly), and download speed/ETA reporting.
- Rebuilt yt-dlp extractor updater (`YtDlpUpdateScreen`) adhering to the dark design tokens (Emerald `#8FD6B8`, Warm Amber `#FFCB80`, Soft Blue `#A8CDFF`, deep background `#000000`).
- Product flavors `github` (default, in-app self-updater with `REQUEST_INSTALL_PACKAGES`) and `fdroid` (strict F-Droid policy compliance with no self-updating binaries or package install permissions).
- Dedicated CPU vector icon (`ic_software_update.xml`) for the Software update row in More settings, with a dynamic red notification dot badge indicating available updates.
- Home screen top bar theme-adaptive, accent-independent "Update" pill button next to the incognito icon for GitHub builds when an app update is available.
- F-Droid flavor release check integration querying the official F-Droid package repository metadata, with an "Open in F-Droid" action button.
- Automated test harness `tools/test_software_update_and_flavors.py` integrated into the master test runner (`tools/test_all.py`).
- 10-locale translation parity for `more_software_update` and `more_software_update_subtitle`.
- Transparent share overlay activity (`ShareOverlayActivity`) with quick one-tap confirmation for Hazel Instant and in-place `FormatSheet` selection over host apps without app switching.
- Pixel-perfect `InstantShareSheet` and `OverlayLoadingSheet` matching modern dark specifications (`sheet-instant.html` & `sheet-fetching.html`) with Hazel SVG logo, dynamic progress indicators, and independent emerald `#8FD6B8` & `#0A0A0A` theme tokens.
- Built-in Java reader (NewPipe extractor) as the default listing source (`ListingSource.NEWPIPE`) with fast-path in-process stream and collection recognition (~200ms latency) and transparent silent fallback to the yt-dlp binary engine.
- Configurable listing source preference in More > Fetch settings with live status badge indicators.
- Media search provider interface (`MediaSearchProvider` & `UnifiedSearchCoordinator`) designed for future multi-engine direct search expansion.
- Automated test harnesses for share overlay isolation, history recording, and NewPipe latency/fallback validation (`tools/test_share_overlay_isolation.py` and `tools/test_newpipe_latency_and_fallback.py`).
- Material 3 Getting Started stepper carousel dialog (`GettingStartedDialog.kt`) featuring a 5-step animated walkthrough (Paste & Download, Format Selection, Hazel Instant, Battery Optimization, and Notifications), progress dots indicator, skip/back/next controls, equal-sized Allow and Deny action buttons for battery and notification permissions, direct system battery optimization overlay prompt without dialog unmounting, flat step badge, Hazel SVG bolt logo, and deep black aesthetics (`#000000`/`#0A0A0A`) with subtle ambient tint.
- Complete 10-locale translation parity for all Getting Started dialog strings across German, Spanish, French, Hindi, Indonesian, Japanese, Brazilian Portuguese, Russian, and Simplified Chinese.

### Changed
- Deferred runtime notification permission request on first launch until the Getting Started onboarding flow is fully closed or completed, preventing premature system permission popups on app launch while preserving re-prompting when downloads begin if permissions remain ungranted.
- Cleaned up legacy `UserGuideDialog.kt` and purged stale guide string resources across all 10 localization files.
- Replaced duplicate distribution channel flavor row with a comprehensive "Device & architecture" card in `SoftwareUpdateScreen` displaying device hardware model, Android OS version and API level, primary architecture, and supported ABIs.
- Refined Software Update hub and component update screens: removed redundant "Verified binaries" row from distribution overview, removed "Checked recently · Signature/Binary verified" subtitles from hero cards, reduced outer horizontal margins from 20dp to 12dp to utilize available screen width, and made hero cards more compact.
- Compacted the in-app update downloading card to match the exact size and proportions of the update available card, combining download speed, transfer count, and percentage into a single streamlined row.
- Retained downloaded APKs in cache across screen visits, allowing users who defer installation to return and install immediately without re-downloading.
- Fail-safe APK auto-installation: verified package install permissions on API 26+ (`canRequestPackageInstalls()`) and prompted the system unknown sources toggle rather than failing silently, granting explicit URI permissions to the resolved package installer.
- Removed description subtitles from "Software update" and "Link reading" rows in More settings for consistent visual density across all setting items.
- Fixed unit test execution on CI by registering a forward-compatible `testDebugUnitTest` task alias in `app/build.gradle.kts` mapping to flavor-specific test tasks (`testGithubDebugUnitTest` and `testFdroidDebugUnitTest`).
- Replaced the Ko-fi sponsor option (which was marked "Opening soon") with a live
  Buy Me a Coffee link (`buymeacoffee.com/sibtainocean`).
- Refined `OverlayLoadingSheet` circular loader spinner with a concentric circular container and clean vector Hazel bolt (`ic_hazel_bolt.xml`), eliminating the clipped rounded square artifact.
- Replaced `OverlayLoadingSheet` infinite phase animation loop with a single-pass progression that smoothly advances through status stages to "Almost ready..." and holds at 94% progress rail fill until fetching completes.
- Overhauled `SponsorScreen` with an independent, theme-adaptive dark luxury aesthetic (`#121418` obsidian surfaces with fine `#F9FAFB` whitish text and `#1A1D24` icon containers) matching `GettingStartedDialog`, completely decoupled from user accent colors while maintaining clean adaptive light theme styling.
- Streamlined the Home screen by removing the redundant batch action row below the search bar while fully preserving per-media card controls (center play/pause/cancel and 3-dot menu).
- Merged separate Downloading and Queued screens into a single unified "Downloading queue" view with dedicated component architecture (`DownloadingQueueView.kt`), presenting active downloads and waiting queue items in one cohesive interface with real-time badges.
- Added batch controls ("Pause all" / "Resume all", "Cancel all" with confirmation dialog, and "Clear queue") into the Downloads screen 3-dot overflow menu.
- Replaced icon buttons with clean clickable text buttons ("Pause" / "Resume", "Cancel", and "Clear") in the header bar below the search field for improved clarity and consistency.
- Kept header Cancel button styled with standard primary tint rather than warning/error red to align with adjacent actions.
- Added confirmation dialogs before cancelling active downloads and before clearing results to safeguard against accidental wipes.
- Replaced the search bar search icon.
- Updated batch download action bar quality button to display real-time chosen quality labels (e.g. HQ: AUTO, HQ: BEST, HQ: 1080p) instead of a static generic icon.
- Aligned pause, resume, and cancel actions across the header controls, thumbnail center button, and 3-dots menu with full support for cancelling waiting queue items without clearing the batch.
- Modernized home and download media cards with an edge-to-edge 16:9 full-artwork thumbnail design; title and author are now overlaid directly atop the artwork with a dual gradient scrim for high legibility, duration and active download progress chips are anchored to the bottom-left corner, and status tags remain on the bottom-right.
- Updated app launcher icon to a sleek black background with a white bolt foreground.
- CI test harnesses and Android backup rules: aligned release regression and share overlay test suites with the black icon and dynamic accent theme, and removed invalid backup domain to resolve fatal lintVitalRelease errors.
- Modernized the Downloads screen title with an unread activity indicator mark beside the dropdown chevron and on the "Downloading queue" filter when active downloads or queued items exist.

### Fixed
- Resolved GitHub API unauthenticated 403 rate limiting (`API rate limit exceeded`) on in-app update checks by implementing dual-layer resilient fetching with automatic fallback to public, CDN-cached GitHub Releases Atom feeds (`releases.atom`) and release redirects (`releases/latest`), making update checks for Hazel and yt-dlp completely immune to 403 errors across all network environments.
- Fixed F-Droid flavor release check by correctly reading `packages[0].versionName` from the official F-Droid package repository metadata, ensuring F-Droid builds accurately discover updates without falling back to GitHub API rate limits.
- Fixed release channel error reporting: when switching to channels without published releases (e.g. Beta or Nightly), the updater now gracefully displays "Up to date: No updates available on this channel" instead of incorrectly showing network connection errors or 403 rate limit banners.
- Resolved top inset spacing gap across all 3 update screens (`SoftwareUpdateScreen`, `HazelUpdateScreen`, `YtDlpUpdateScreen`) by preventing redundant status bar window inset accumulation.
- Fixed Light/Dark theme adaptiveness for the Software Update hub and component update screens: implemented dynamic high-contrast light theme surface tokens (`UpdateTokens`) while strictly preserving dark theme aesthetics and independent emerald green toggle switch accents.
- Defaulted "Install on Wi-Fi only" to off (`false`) across settings repository and update view models.
- Added comprehensive unit and integration test suite (`UpdaterTest.kt`) covering semver comparison, architecture APK resolution (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`, `universal`), Atom feed parsing, channel filtering, F-Droid metadata parsing, and live rate-limit resilience.
- Fixed in-app updater download cancellation: aborts active network calls immediately, purges partial files, and gracefully resets to the available state without showing coroutine cancellation error banners.
- Added permission confirmation dialog prior to opening unknown app sources settings, with real-time polling and lifecycle resume detection to automatically launch the installer once permission is granted.
- Added automatic installation prompt upon download completion so users are not required to manually tap install.
- Background download execution: Fixed a critical issue where initiating downloads from `ShareOverlayActivity` (both Normal Share and Instant Share) caused downloads to remain suspended in pending state until `MainActivity` was manually launched; resolved by passing target `MediaInfo` explicitly to `startDownload` and starting `DownloadService` synchronously before closing the activity, preventing Android and OEM process freezers (e.g. `OplusHansManager`) from suspending background execution.
- Foreground service start lifecycle: Started `DownloadService` synchronously and immediately on the UI thread when initiating downloads from both `ShareOverlayActivity` and `InstantShareSheet`, ensuring the process enters the foreground before `finishAndRemoveTask()` destroys the calling activity and preventing `ForegroundServiceStartNotAllowedException` or process freezing on Android 12+.
- Notification permission & shade delivery: Registered `PermissionHelper` in `ShareOverlayActivity.onCreate()` and prompted for `POST_NOTIFICATIONS` permission on Android 13+, ensuring download progress notifications appear in the status bar and notification drawer immediately upon queuing.
- Format badge alignment: Fixed a layout issue in `FormatSelectionSheet` where container badges for 4-letter streams (`WEBM`, `OPUS`) overflowed available width at 15sp bold and aligned to the far-left border; dynamically scaled font sizes based on character length (`>5` chars: 10sp, `≥4` chars: 11.5sp, `<4` chars: 13sp) and added `TextAlign.Center` with horizontal padding, ensuring all container tags (`WEBM`, `M4A`, `MPEG-4`, `DEFAULT`) align in the exact center of the badge.
- Clean overlay dismissal: Replaced `finish()` with `finishAndRemoveTask()` in `ShareOverlayActivity.closeOverlay()`, preventing the Android window manager on certain OEM launchers from erroneously bringing `MainActivity` to the foreground when dismissing the share overlay.
- Stream and format latency optimization: NewPipe in-process extractor now parses video and audio streams directly into concrete format choices in ~200ms rather than triggering a 20-30 second yt-dlp Python dump, enabling instant format sheet population with zero waiting.
- Robust YouTube thumbnail resolution via `UrlExtractor.extractYouTubeId` ensuring instant, crisp artwork loading across single shares and collection listings.
- Optimized yt-dlp metadata probe with `--compat-options manifest-filesize-approx` and `--skip-download` to eliminate redundant fragment probing latency on mobile connections during fallback.
- Fixed button text truncation on the instant share sheet where "Download Now" clipped to "Download No" on standard screen widths by tightening padding and tuning typography bounds.
- Fixed shared link isolation where sharing a single video unintentionally opened `BatchDownloadSheet` with previously searched items; shared URLs now resolve in clean isolation with dedicated routing (`fetchShare`).
- Ensured both normal share and Hazel instant share immediately record captured URLs into history.
- Removed artificial delay timers in share loading sheet, driving progress dynamically via smooth continuous transitions.

### Added
- Synchronized 16:9 skeleton shimmer loading animation (`ShimmerHost` & `shimmerCard`) with rounded card placeholders and dark gradient scrim during metadata fetching.
- One-shot search bar refresh shine highlight sweep animation (`refreshShine`) that illuminates the search bar upon completing metadata extraction.
- Paste button on the bottom-right corner of the home screen when empty, styled with a rounded pill and "Paste" label matching the batch download action, allowing one-tap pasting and fetching of links directly from the clipboard.
- 3-dot overflow menu on the home screen search bar with "Clear search results" and "Clear search history", providing seamless visual and functional parity with the search screen.
- Confirmation dialog before clearing search history from both home and search screen 3-dot menus to prevent accidental data loss.
- Smart auto-expanding search bar on the Downloads screen with smooth expansion animation and one-tap dismissal when clicking anywhere on empty screen space.
- Explicit backup and data extraction rules (`backup_rules.xml` and `data_extraction_rules.xml`) ensuring user cookies, search history, download history, and app preferences are strictly preserved across app updates, cloud restores, and device transfers.
- Complete 10-locale translation parity for all newly introduced search actions and confirmation dialogs.
- Direct cookie file import option ("Import file" button and overflow menu item) allowing users to import Netscape cookie files directly with automatic domain recognition, multi-site splitting, and full 10-locale translation parity.
- Dedicated "Pause All / Resume All" and "Cancel All" batch control buttons in the Downloads
  screen header row, active whenever a batch or download is running.
- Granular per-item cancellation (`cancelItem`), allowing items waiting in the queue to be
  removed from memory and persistent queue storage without disturbing currently downloading items.
- Downloads screen workflow dropdown on the title with chevron selector, replacing horizontal button bar.
- Reusable progressive thumbnail media components with live progress, pause/resume/cancel actions, and visual parity with the Home screen.
- Active downloads now appear pinned at the top of the All Downloads list.
- Diagnostic log modal and one-tap retry for failed downloads.
- Asynchronous and distinct DataStore flow deserialization on background dispatchers to optimize CPU, I/O, and recomposition.

### Removed
- Removed single-row compact layout ("Show as list") across all screens (Home, All Downloads, Queue, and Failed), standardizing strictly on the modern 16:9 full-artwork card layout.
- Purged all obsolete compact row implementations (`MediaRow`, `QueuedRow`, `FailedRow`, `HistoryRow`) and cleaned up related dead code and toggle actions from the 3-dot overflow menus.
- Removed obsolete layout preferences (`RESULTS_COMPACT_KEY` and `HISTORY_COMPACT_KEY`) from `SettingsRepository`.
- Removed top-right 'X' (dismiss/remove) button from multi-video and playlist cards on the Home screen to streamline card presentation.

### Fixed
- Fixed CI release build failure caused by AAPT2 non-positional format string validation on `options_filename_hint` across all 10 locales by specifying `formatted="false"`.
- Resolved release `lintVitalRelease` fatal validation errors by removing invalid and redundant `cache` and `no-backup` domain exclusions from `backup_rules.xml` and `data_extraction_rules.xml` (which are automatically excluded by Android's backup framework).
- Fixed search bar "Clear search results" action to cleanly clear both resolved media cards and the active search URL.
- Resolved bottom navigation bar clipping on devices with 3-button navigation by restoring dynamic window insets handling in Material 3 NavigationBar.
- Made unread activity indicator dot theme-adaptive using `MaterialTheme.colorScheme.error` for proper contrast across dark and light themes, and aligned it directly on the horizontal centerline with the screen title and dropdown chevron.
- Fixed Cookies screen master switch event collision where tapping the switch failed to turn back ON; converted row to single-source toggleable semantics and remembered Flow collections across recompositions.
- Ensured individual cookie switches operate independently without closing off the global master switch when disabled, allowing flexible per-cookie control while preserving global cookie status.
- Eliminated latency when holding to delete cookie sets; deletion immediately dismisses confirmation dialogs, cleans up site-specific cookie cache files, and updates repository state without blocking Compose recompositions.
- Fixed multi/playlist download sheet quality ceiling selection where picking a quality from the batch action bar left unresolved items displaying best quality; pending cards now receive bounded generic format selectors with automatic fallback to the closest available lower resolution.
- Fixed cookie screen master toggle synchronization where disabling all cookies left individual cookie switches displaying as active; the top master toggle and individual cookie switches are now bidirectionally synchronized across storage and UI states.
- Fixed YouTube playlist and multi-link extraction when cookies are active by avoiding overriding client User-Agent headers on YouTube endpoints, resolving tab page extraction failures.
- Implemented automatic metadata and listing cache invalidation (`InfoCache.clear()`) on cookie updates, toggles, additions, and deletions, as well as extractor setting changes, preventing stale cached results or expired session states.
- Prioritized cookie authentication during metadata probes and playlist reads while maintaining automatic fallback to anonymous extraction if signed-in requests encounter failures.
- Fixed search history persistence where searches and fetched URLs failed to store and display due to coroutine cancellation upon screen dismissal. All queried and fetched links are now recorded on `applicationScope` / `viewModelScope` (when incognito is disabled) even when searching without downloading, via Hazel Instant, or using custom cookies.
- Audio downloaded from JioSaavn, SoundCloud, Bandcamp and other music platforms now shows
  the artist name instead of the record label. The probe reads the `artist` and `artists`
  fields the extractors actually populate, and the download writes a proper `artist` tag into
  the file's metadata. (Issue #34)
- Titles and authors that contain a colon (e.g. "Episode 1: Pilot", "Artist A : Feat B") are
  no longer silently excluded from metadata tagging. Colons are escaped before they reach
  `--parse-metadata`, which splits on the first unescaped colon.
- Multi-artist tracks whose extractor returns an `artists` JSON array (e.g.
  `["Artist A", "Artist B"]`) are joined into a clean comma-separated string instead of
  being ignored or shown as raw JSON.
- Audio formats lacking an explicit `acodec` property in yt-dlp's extractor output (such as
  JioSaavn's 128 kbps and 320 kbps streams) are recognized as valid audio streams, resolving
  format lists and preventing the format sheet from staying on a loading skeleton.
- Cancelling an individual download item in a multi-link or playlist queue no longer aborts
  the entire batch. Clicking the thumbnail cancel button ("X") or item cancel now stops only
  that specific item, purges its temporary fragments, and allows subsequent downloads in the
  queue to proceed seamlessly.
- Batch completion reporting (`finishBatch`) no longer counts user-cancelled downloads as
  failures, eliminating misleading error banners (such as "1 of 49 failed") when items are
  intentionally cancelled or skipped.
- URL validation now accepts uppercase and mixed-case URI schemes (e.g. `HTTPS://`, `Http://`) and trims leading/trailing whitespace.
- Fixed home screen search bar click target where tapping outer segments failed to open search; expanded clickable surface across the entire bar container while isolating the 3-dot overflow menu.
- Fixed compact single-row visual misalignment across Downloads and History screens where active downloading items displayed a smaller thumbnail (`104×60dp`) and mismatched corner radius (`12dp`) compared to completed and queued rows; aligned `MediaRow` with `HistoryRow` and `QueuedRow` (`128×78dp` thumbnail, `20dp` surface shape, `14dp` corner clip, and `14dp` spacer).
- Fixed home screen batch and playlist downloads where advancing from one completed item to the next active download left the viewport anchored on the previous completed item; added smooth automatic scroll to the top active item whenever the running download advances.
- Clearing search results from the 3-dot menu now also clears the URL from the search bar, so no stale link lingers after results are wiped.

## [1.0.8] - 2026-09-03

### Fixed
- The published APKs no longer carry Gradle's dependency-metadata block inside the APK
  signing block. AGP adds it by default, F-Droid's scanner rejects any APK that has it, and
  it describes the machine that built the APK rather than the app.

## [1.0.7] - 2026-09-03

### Fixed
- Builds reproduce on another machine. `libdatastore_shared_counter.so` is now packaged as
  the dependency provides it, because the debug symbols AGP strips out of it come away
  differently under each NDK version, leaving a rebuild unable to match the release.

## [1.0.6] - 2026-09-03

### Fixed
- The version name and code are literals in `app/build.gradle.kts` again, the only form
  F-Droid can read: it greps the file rather than running Gradle, so `gradle.properties`
  left its update check finding no version at all.
- The release workflow reads those same literals, and stops if the tag or a store changelog
  disagrees with them. It no longer passes `-PVERSION_NAME`, so a tag builds to the same
  version here and on the F-Droid server.

## [1.0.5] - 2026-09-03

Nothing in the app itself changes here. This is the release that makes Hazel something
F-Droid can build, which it could not before, and every item below came out of running
their own tooling against it rather than from reading their documentation.

### Removed
- **The foojay toolchain resolver.** It arrived with the Android Studio template and
  resolved a Java toolchain by fetching a JDK from the Foojay API partway through the build.
  Nothing here ever asked for a toolchain, so it did nothing, but F-Droid's build scanner
  refuses an entire source tree that contains it: a build that downloads its own compiler
  from a third party is not one anybody else can reproduce.

### Added
- **The version name lives in `gradle.properties`.** A tag build still overrides it, so a
  release is named by the tag that produced it. The fallback exists because F-Droid's
  builder is handed a checkout and a tag and never a build argument, so the version has to
  be somewhere in the repository for the APK to come out named correctly.
- **`tools/release.ps1`**, which cuts a release in one step: it bumps both versions, moves
  the Unreleased section of this file under the new number, generates a store changelog for
  every architecture, commits, tags and pushes. It refuses to start on a dirty tree or a tag
  that already exists, and `-DryRun` prints the whole plan without touching anything.

### Fixed
- **CI reported failure on builds that had succeeded.** The account's artifact storage had
  filled with a fortnight of APKs, around 470 MB per run, and once full every
  upload failed, including a 12 KB test report that had nothing to do with it. CI now keeps
  one APK per run rather than the whole set, and a failed upload no longer fails the job:
  whether the build worked is decided by the build, not by whether there was room to store a
  copy of it.

## [1.0.4] - 2026-09-03

### Added
- **Hazel speaks ten languages.** Spanish, Hindi, Simplified Chinese, Brazilian Portuguese,
  French, German, Russian, Japanese and Indonesian, alongside English. Nothing has to be
  chosen: with no answer stored Android already resolves every string against the device
  language, so a phone set to Spanish opens a Spanish app on first launch. Every one of the
  nine carries the full set of 461 translatable strings and all 9 plural groups, with the
  format arguments left in the order the code passes them.
- **A language picker, under More.** For the person whose phone is in one language and who
  wants the app in another, which is common on a shared or a work device. It opens as a
  sheet of cards, two to a row, each naming the language in itself with the English
  underneath: somebody hunting for their own language is looking for the word they would
  write. Choosing a card does not change anything on its own. The heading says what the app
  is in now and switches to what has been picked, and only Confirm commits it, because a
  language is the one setting where a mis-tap leaves you unable to read the screen you would
  use to undo it. A short note follows in the language just chosen.
- **The app appears in the system language settings.** From Android 13 the platform owns the
  per-app language, so the choice is stored by the system, sits beside every other app's,
  and survives clearing the app's data. Below that the choice is kept by the app itself and
  applied as each screen is built.
- **The README is readable in eighteen more languages.** Arabic, Azerbaijani, German,
  Spanish, Persian, French, Hindi, Indonesian, Italian, Japanese, Brazilian Portuguese,
  Russian, Serbian, Thai, Ukrainian, Urdu, Simplified Chinese and Traditional Chinese, kept
  under `assets/TRANSLATIONS/`. Each one carries the same headings, badges and screenshots
  as the English, and a switcher at the top of every file reaches all the others.

### Changed
- **The link field no longer offers to search.** It reads "Enter URL", in every language,
  because searching is not something it has ever done and a field that says it does is a
  field people try it in.
- Project images moved from `BRAND/` to `assets/`, and the screenshots now live under
  `fastlane/metadata/android/en-US/` where the store listing reads them, with the READMEs
  pointing at that one copy rather than a second one.
- The version code is a plain counter rather than the date, with two digits appended for the
  architecture, so one bump per release numbers every APK it publishes.

## [1.0.3] - 2026-09-02

### Added
- **A soundtrack can be chosen where a source publishes several.** A row under the quality
  card names the one the download will take, and tapping it opens the list of what the
  source actually offers. The choice reaches the download as the track's own id with the
  engine's language filter behind it, so a track that has since gone is replaced by another
  in the same language rather than by the original. It is on the single sheet, on the set
  sheet for a whole run, and in the instant settings as a standing preference; anything that
  does not carry the chosen language is downloaded with what it has.
- **A Support screen, reached from More.** It says what the app takes from anyone, which is
  nothing, and offers the two places money can go alongside the ways of helping that cost
  none. Everything opens in the in-app browser, so nothing here leaves the app.
- **The download sheet and the set sheet close with the link and an incognito switch.**
  Tapping the address copies it and opens it, in the site's own app when that is installed
  and in the browser otherwise. The switch is the same setting the rest of the app reads,
  and it says which way it went.
- **Hazel Instant has its own settings.** Container, filename template, cover art, chapters,
  subtitles and SponsorBlock are set for the share target itself and kept apart from the
  sheet's, so a choice made for a download being watched cannot change what an unattended
  share does with the next link. The screen also says whether saved sign-ins are in use.
- **Properties, for a finished download.** Held down on a row, or picked from its menu, it
  says everything the app knows: whether the file is still there, what quality was asked
  for, how long it runs, its bitrate, resolution, size, container, type, what was written
  into it besides the media, the name it was saved under, where it landed and when. Two
  sources feed it and neither is expensive: what was asked for comes from the record, and
  what arrived is read once from the file's own header, on a background thread, only for a
  file that is still there. A fact neither source has is left out rather than shown as
  blank, so an entry made before any of this was recorded is short rather than empty. The
  sheet closes with the address, which copies and opens the same way the download sheet's
  does, and for a download whose file has gone that is the way back to the thing itself.

### Changed
- **The format list was rebuilt.** It opens half way up the screen, each row leads with its
  container as a block, the quality is the headline with the format id beside it, and what
  was measured sits under it as pills that scroll rather than wrapping the row into
  different heights. Rows are laid out once per ordering, which is what makes a fast fling
  smooth, and the list opens on the row that is currently chosen.
- **The best row is shown from outside the list.** A source that reports formats no longer
  gets a synthesised "best" entry mixed in with the real ones: the generic row now only
  appears where a source listed nothing of that kind, and the sheet shows the best concrete
  format from the moment it opens, including while the rest are still being read.
- **The audio tab keeps its own choice.** Each tab holds what it is set to, so switching to
  audio no longer shows a video resolution, and opening the format list from the audio tab
  leads with the audio section.
- **The last ten links are remembered across restarts.** What a read produced is rebuilt
  from what it wrote, so a link pasted again fills its sheet at once, and a collection opens
  as the set of cards it opened as last time instead of being walked again.
- **The dark theme's own container tones.** The search field took Material's dark default,
  which is derived from a violet neutral and read as a lilac bar across a black screen.
- **The app opens sooner.** The wait was the launch window and then the splash screen, one
  after the other, with the splash always holding for a fixed 1.4 seconds on top of however
  long the start itself took. The hold is now what is left of a 900 ms budget counted from
  the moment the process starts, so a slow start spends that budget instead of adding to it,
  and a fast one still shows the splash long enough to be seen rather than flashed. The
  highlight sweeping through the wordmark was retimed to finish inside the shorter stay, and
  the mark in the launch window was moved to sit where the splash screen draws it, so it no
  longer jumps as one replaces the other.
- **Unpacking the download engine no longer competes with the launch.** yt-dlp and FFmpeg
  unpack and check for updates on a background thread, but the work was starting while the
  app was still drawing its first screen and took CPU and disk from it. It now starts once
  that screen is up, and still starts on its own for a launch with no UI, such as a
  notification action resuming a download after the process was killed.
- **The link entry screen has a paste control.** A link is nearly always copied somewhere
  else first, so the opening move on that screen was a paste and then a confirm. The control
  in the corner is the two of them at once, and it rides above the keyboard rather than
  under it. It goes through the same submit the field does, so a paste holding several links
  is split the way a typed one is.
- **The source is offered in one place.** It was on the More list and on the Support screen,
  the same address by two routes, and the one that stayed is the one that says why anyone
  would follow it.
- **The results layout switch is offered from the first link.** It was held back until there
  were two, so it was a control that came and went and nobody learned it was there. The
  count beside it says "1 link" rather than "1 links".
- **The downloads list dropped a glyph from its rows.** A play or note mark sat in front of
  the size and the date, saying what the artwork beside it already said and spending the
  width the date needed to finish.

### Fixed
- **Signing in cut the format list down to 360p.** The largest site now answers a signed-in
  request with a single stream unless it carries a token the app cannot produce. A link is
  read anonymously first and the sign-in is used only when the media will not open without
  it, so a public link keeps its full ladder and a private, members-only or age-restricted
  one still opens. The download asks the same way the read did.
- **Sign-ins are scoped to the site they were collected on.** One file held every cookie and
  was sent with every request, so an account on one site was handed to another site's
  servers. Each site now gets its own, and a site with no saved sign-in is fetched without
  one.
- **A finished download could not be opened on older Android versions.** The saved file was
  recorded under a `file://` address, which the system refuses to pass to another app from
  Android 7 onwards, so the file was there and the history said it was missing. Files are
  now handed over as content addresses, a refused direct write falls back to the media
  index, and anything that cannot be published is kept and offered from where it is rather
  than recorded as saved somewhere it never reached.
- **A chosen soundtrack was lost on the way to the download.** A queued item was rebuilt
  from what was written down for it, and the audio track written down was the source's
  default, so the file arrived in the original language whatever the sheet had said.
- **The set card showed a resolution twice.** The measured size was pushed off the end of
  the row by a headline that repeated what the quality already said.
- **A download deleted from the phone still counted as downloaded.** The record of a
  download was taken as proof the file was there, and where it was checked at all, opening
  the address was the whole test. From Android 11 a gallery delete moves the row to the
  system bin, which the app that owns it can still open, so a file the user had deleted read
  as present until they emptied the bin as well. The index is now asked whether the row is
  binned or half written before the file behind it is opened, the answer is thrown away and
  taken again every time a screen returns to the foreground, and the downloads list asks
  once more at the moment of the tap rather than handing a missing file to a player that
  opens on nothing and comes straight back. A link whose file has gone is offered as
  something to fetch again: no repeat warning, and no marker on the card.
- **Download all appeared for a single link, and offered to fetch what was already saved.**
  Reading a new link added it to what was already on screen, so one new video beside one
  already downloaded read as a set of two, and the sheet behind the action held both. What
  has finished downloading is now taken off the list when the next link is read, with a line
  at the foot of the results saying where it went, and the action itself is offered on what
  is actually left to fetch rather than on how long the list is. Neither is offered while a
  run is in hand, including one sitting paused.
- **A row in the downloads list broke apart once its file was deleted.** The size, the date
  and the deleted marker shared a row in which neither was allowed to give way, so the text
  took the full width and the marker beside it was measured into nothing: it collapsed to a
  red thread down the side and its label wrapped one letter at a time, dragging the row to
  several times its height. The line is what shortens now. The row was rebuilt around it
  with larger artwork, room between the title, the author and what the file cost, and the
  same word for a missing file that the card form uses.
- **The single-line layout lost every download control.** Pause, resume and the options
  behind them were on the card form alone, so switching layout mid-download left the row
  with nothing but a cancel button, and a paused item showed no sign of being paused. The
  row now carries the same menu in the same order, the artwork holds resume while it is
  paused, and the progress line stays put with what is already down beside it.
- **The run summary sat above the links it was about.** A line reporting how a set of
  downloads went was the first thing on the screen, before any of the cards it counted.

## [1.0.2] - 2026-08-31

### Fixed
- **A set action offered to download what was already saved.** Reading a new link adds it to
  what is already on screen, which is what makes a growing list of results useful. The set
  action counted the whole list, so one new video alongside one already downloaded read as
  two waiting and Download all fetched the saved one a second time. The action now covers
  only what is still owed, and the button disappears once nothing is. A link already
  downloaded keeps its place in the list, marked, because seeing what arrived is the point
  of the list. Both records are consulted: what finished in this run, and what finished in
  any run, which is what a link read after a restart turns on.
- **Pasting several links at once said the paste was invalid.** The field took everything
  typed into it as a single address, and an address cannot contain a space, so a set of links
  copied together was rejected as one malformed link. Anything separated by whitespace is now
  read as the several links it is, which is how links arrive when they are copied from
  somewhere else.
- **The card and the list disagreed on what counted as already downloaded.** One compared
  addresses as they were written and the other reduced them to the media first, so a share
  link and an address bar link for the same video were the same media in one layout and two
  in the other.

### Changed
- **A history row whose file has gone reads Deleted** rather than File missing. The file is
  not mislaid, and almost always it is gone because the user removed it.

## [1.0.1] - 2026-08-31


### Added
- **The saved file is named before it is made.** Tapping Convert opens a sheet holding the
  name the audio will be saved under, filled in from the video's own filename so the usual
  answer is to leave it and press Start. What is typed there names the file and is written
  into its title tag, because a file called one thing and announcing itself as another is a
  distinction nobody asked for.
- **A License entry under More**, opening the licence in the user's own browser rather than
  the in-app one: a licence is a thing people save, share and read alongside something else,
  and none of that works in a window that closes with the screen behind it. The project is
  licensed GPL-3.0, and the text now ships in the repository.
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
- **The converter sits directly under More.** It used to be behind a Tools screen whose
  entire content was that one row, which is a tap and a screen spent saying one word. Tools
  remains, empty, for whatever the next tool turns out to be.
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

[Unreleased]: https://github.com/SibtainOcn/Hazel/compare/v1.0.2...main
[1.0.2]: https://github.com/SibtainOcn/Hazel/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/SibtainOcn/Hazel/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/SibtainOcn/Hazel/releases/tag/v1.0.0
