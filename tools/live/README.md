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
| `test_sponsorblock_live.py` | SponsorBlock removal really cuts the segments from audio and video, marking writes them as chapters, other sites skip it cleanly, and a cookie file changes nothing. |

Each script also has a `--static` mode, which needs no network: it checks that the flags
it tests are the ones the Kotlin passes, so the scripts and the app cannot drift apart.

A site that refuses the network the run is on (for example one asking a server address to
sign in) is reported as SKIP, not FAIL. The SponsorBlock script then falls back to a local
stand-in carrying the real video id, so the real segments are still fetched and cut.
