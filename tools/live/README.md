# Live verification scripts

Scripts that run the real yt-dlp and ffmpeg against real links and local stand-in files,
to prove that the download flags the app builds actually work. They are **not** part of
`tools/test_all.py`: they need the network and take minutes. Run them by hand whenever
the flags in `DownloadViewModel.buildRequest` change.

Setup (Python 3):

    pip install -U yt-dlp mutagen
    # plus ffmpeg and ffprobe on the PATH

| Script | What it proves |
|---|---|
| `test_thumbnail_embed_live.py` | Cover art is embedded, and cropped square when asked, for every audio format and video container the sheet offers, on real sites and on awkward local formats (OGV, FLV, AVI, TS, WebM, WAV, AIFF, WMA, AC3, MKA). A cover never fails a download. |
| `test_po_token_live.mjs` | The in-app PO token page (`app/src/main/assets/po_token.html`) mints real tokens when run in Chromium from the youtube.com address the app gives it, reuses its session for later mints, and with `--verify` yt-dlp lists a video's formats with those tokens. Needs Node and Playwright: `NODE_PATH=$(npm root -g) node tools/live/test_po_token_live.mjs --verify`. Set `JS_RUNTIME` to point yt-dlp at a Node it supports, and `HARNESS_ROUTE_REQUESTS=1` behind a proxy whose certificate Chromium does not trust. |
| `test_playback_live.py` | The in-app player's stream choice (mirrored from `StreamResolver.pick`) plays real links: the chosen file, HLS playlist or DASH manifest is fetched with the player's headers and checked to be media. 15 sites passed (Reddit, JioSaavn, SoundCloud, Bandcamp, Bilibili, Twitch, archive.org, PeerTube, Imgur, 9GAG, BitChute, Bluesky, Medal, Streamable); sites that refuse the test network are SKIP. |
| `test_sponsorblock_live.py` | SponsorBlock removal really cuts the segments from audio and video, marking writes them as chapters, other sites skip it cleanly, and a cookie file changes nothing. |

Each script also has a `--static` mode, which needs no network: it checks that the flags
it tests are the ones the Kotlin passes, so the scripts and the app cannot drift apart.

A site that refuses the network the run is on (for example one asking a server address to
sign in) is reported as SKIP, not FAIL. The SponsorBlock script then falls back to a local
stand-in carrying the real video id, so the real segments are still fetched and cut.
