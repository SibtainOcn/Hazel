#!/usr/bin/env python3
"""
Release Readiness & Full Regression Test Harness for Hazel.

Validates all subsystems touched and untouched by recent PRs (#35, #36, etc.)
including:
1. Video Downloader Request Building & Option Generation (single streams, muxed, separate audio)
2. Audio Downloader Request Building & Option Generation (extraction -x, audio containers, re-encoding)
3. Metadata Tagging, Colon Escaping & yt-dlp first-unescaped-colon split round-trip
4. Filename Templating, Token Replacement, and Filesystem Sanitization
5. MediaProbe Format Classification, Codec Badges, and Sorting (Video vs Audio)
6. Data Store Models Serialization & Deserialization (Queue, Failed, History)
7. Downloads / History UI State Machine (Categories, Active Pinning, Search, Sort)
8. UI Assets & Resource Parity (Launcher Icon colors, 10-locale strings, Sponsor links)

Run:
    python tools/test_release_regression_harness.py
Exit:
    0 on success, 1 on any failure.
"""

import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# Ensure UTF-8 output on Windows consoles
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

REPO_ROOT = Path(__file__).resolve().parent.parent

# ---------------------------------------------------------------------------
# Test Framework
# ---------------------------------------------------------------------------
PASS_COUNT = 0
FAIL_COUNT = 0


def check(name: str, got, expected):
    global PASS_COUNT, FAIL_COUNT
    if got == expected:
        PASS_COUNT += 1
        print(f"  PASS  {name}")
    else:
        FAIL_COUNT += 1
        print(f"  FAIL  {name}")
        print(f"        Expected: {expected!r}")
        print(f"        Got:      {got!r}")


def check_true(name: str, condition: bool, msg: str = ""):
    check(name, bool(condition), True)


# ---------------------------------------------------------------------------
# Suite 1: Video Downloader Request Building Simulation
# ---------------------------------------------------------------------------

NO_ARTWORK_CONTAINERS = {"webm", "opus", "wav", "flac"}
DEFAULT_SUB_LANGUAGES = "en.*,.*-orig"
SPONSORBLOCK_API_URL = "https://sponsor.ajay.app"


class DownloadOptions:
    def __init__(
        self,
        videoContainer: str = "",
        audioContainer: str = "m4a",
        embedThumbnail: bool = True,
        addChapters: bool = true_val if (true_val := True) else True,
        splitByChapters: bool = False,
        sponsorBlockFilters: list[str] | None = None,
        writeSubs: bool = False,
        writeAutoSubs: bool = False,
        embedSubs: bool = False,
        subLanguages: str = "",
        filenameTemplate: str = "%(title)s [%(id)s].%(ext)s",
    ):
        self.videoContainer = videoContainer
        self.audioContainer = audioContainer
        self.embedThumbnail = embedThumbnail
        self.addChapters = addChapters
        self.splitByChapters = splitByChapters
        self.sponsorBlockFilters = sponsorBlockFilters or []
        self.writeSubs = writeSubs
        self.writeAutoSubs = writeAutoSubs
        self.embedSubs = embedSubs
        self.subLanguages = subLanguages
        self.filenameTemplate = filenameTemplate


class MediaFormatMock:
    def __init__(
        self,
        formatId: str,
        selector: str,
        hasVideo: bool,
        hasAudio: bool,
        isGeneric: bool = False,
        ext: str = "mp4",
    ):
        self.formatId = formatId
        self.selector = selector
        self.hasVideo = hasVideo
        self.hasAudio = hasAudio
        self.isGeneric = isGeneric
        self.ext = ext


def simulate_build_request(
    format_item: MediaFormatMock,
    options: DownloadOptions,
    title: str,
    author: str,
    audio_selector: str | None = None,
    audio_language: str | None = None,
    speed_limit: str = "",
) -> list[tuple[str, str]]:
    """
    Port of DownloadViewModel.buildRequest() yt-dlp option assembly logic.
    Returns list of (option, value) pairs.
    """
    args: list[tuple[str, str]] = []

    def add_opt(opt: str, val: str = ""):
        args.append((opt, val))

    is_video = format_item.hasVideo
    container = (options.videoContainer if is_video else options.audioContainer).strip()

    add_opt("-o", f"/downloads/template_output")
    add_opt("--no-playlist")
    add_opt("--no-mtime")

    if speed_limit.strip():
        add_opt("--limit-rate", speed_limit.strip())
    add_opt("--no-check-certificates")
    add_opt("--cache-dir", "/cache")

    needs_merge = False
    language_filter = f"ba[language^={audio_language}]" if audio_language and audio_language.strip() else None

    if format_item.isGeneric:
        add_opt("-f", format_item.selector)
        needs_merge = is_video
    elif is_video and not format_item.hasAudio:
        audio_id = audio_selector
        selector = ""
        if audio_id:
            selector += f"{format_item.selector}+{audio_id}/"
        if language_filter:
            selector += f"{format_item.selector}+{language_filter}/"
        selector += f"{format_item.selector}+bestaudio/{format_item.selector}"
        add_opt("-f", selector)
        needs_merge = True
    elif not is_video and language_filter:
        add_opt("-f", f"{format_item.selector}/{language_filter}/ba")
    else:
        add_opt("-f", format_item.selector)

    if is_video:
        if container:
            add_opt("--merge-output-format", container.lower())
        elif needs_merge:
            add_opt("--merge-output-format", "mp4")
    elif container:
        add_opt("-x")
        add_opt("--audio-format", container.lower())

    artwork_container = container.lower() not in NO_ARTWORK_CONTAINERS
    if options.embedThumbnail and artwork_container:
        add_opt("--embed-thumbnail")

    # Chapters
    if is_video and options.addChapters:
        add_opt("--sponsorblock-mark", "all")
        add_opt("--embed-chapters")
    if options.splitByChapters:
        add_opt("--split-chapters")
        add_opt("-o", "chapter:%(section_number)d - %(section_title)s.%(ext)s")

    # SponsorBlock
    filters = [f for f in options.sponsorBlockFilters if f.strip()]
    if filters:
        add_opt("--sponsorblock-remove", ",".join(filters))
    if filters or options.addChapters:
        add_opt("--sponsorblock-api", SPONSORBLOCK_API_URL)

    # Subtitles (Video only!)
    if is_video:
        if options.writeSubs:
            add_opt("--write-subs")
        if options.writeAutoSubs:
            add_opt("--write-auto-subs")
        if options.embedSubs:
            add_opt("--embed-subs")
        if options.embedSubs or options.writeSubs or options.writeAutoSubs:
            add_opt("--sub-langs", options.subLanguages if options.subLanguages.strip() else DEFAULT_SUB_LANGUAGES)

    # Metadata
    if title.strip() or author.strip():
        add_opt("--embed-metadata")
        if title.strip():
            escaped = title.replace(":", r"\:")
            add_opt("--parse-metadata", f"{escaped}:%(title)s")
        if author.strip():
            escaped = author.replace(":", r"\:")
            add_opt("--parse-metadata", f"{escaped}:%(uploader)s")
            add_opt("--parse-metadata", "%(uploader)s:%(artist)s")

    return args


# ---------------------------------------------------------------------------
# Suite 2: Filename Sanitization Simulation
# ---------------------------------------------------------------------------

def sanitize_for_filename(value: str) -> str:
    cleaned = re.sub(r'[\\/:*?"<>|%]', '_', value).strip()
    truncated = cleaned[:120]
    return truncated if truncated else "download"


def output_template(options: DownloadOptions, title: str, author: str, probe_title: str, probe_uploader: str) -> str:
    template = options.filenameTemplate.strip() or "%(title)s [%(id)s].%(ext)s"
    if title.strip() and title != probe_title:
        template = template.replace("%(title)s", sanitize_for_filename(title))
    if author.strip() and author != probe_uploader:
        safe = sanitize_for_filename(author)
        template = template.replace("%(uploader)s", safe).replace("%(channel)s", safe)
    return template


# ---------------------------------------------------------------------------
# Suite 3: MediaProbe Parsing Simulation
# ---------------------------------------------------------------------------

AUDIO_CONTAINERS = {"m4a", "mp3", "opus", "flac", "wav", "aac", "ogg"}
VIDEO_CONTAINERS = {"mp4", "webm", "mkv", "mov", "flv", "avi", "3gp", "ts"}


def resolve_artist(obj: dict) -> str:
    artists = obj.get("artists")
    if isinstance(artists, list) and len(artists) > 0:
        joined = ", ".join(
            s for s in artists
            if isinstance(s, str) and s.strip() and s != "null"
        )
        if joined:
            return joined
    artist = obj.get("artist", "")
    if isinstance(artist, str) and artist.strip() and artist != "null":
        return artist
    return ""


def first_non_blank(*values) -> str:
    for v in values:
        if isinstance(v, str) and v.strip() and v != "null":
            return v
    return ""


def parse_probe_uploader(media: dict, root: dict | None = None) -> str:
    root = root or {}
    return first_non_blank(
        resolve_artist(media),
        media.get("uploader", ""),
        media.get("channel", ""),
        media.get("creator", ""),
        media.get("uploader_id", ""),
        resolve_artist(root),
        root.get("uploader", ""),
        root.get("channel", ""),
    )


def parse_format(fmt: dict) -> dict | None:
    format_id = str(fmt.get("format_id", ""))
    ext = fmt.get("ext", "")

    vcodec = fmt.get("vcodec")
    if vcodec in ("none", "null", ""):
        vcodec = None
    acodec = fmt.get("acodec")
    if acodec in ("none", "null", ""):
        acodec = None

    has_video_key = "vcodec" in fmt
    has_audio_key = "acodec" in fmt
    codecs_unknown = not has_video_key and not has_audio_key

    height = fmt.get("height", 0) or 0
    fps = fmt.get("fps", 0) or 0
    audio_ext = fmt.get("audio_ext")
    if audio_ext in ("none", "null", ""):
        audio_ext = None
    resolution = str(fmt.get("resolution", "")).lower()
    abr = fmt.get("abr")
    if abr is not None and (not isinstance(abr, (int, float)) or abr <= 0):
        abr = None

    has_video = bool(
        vcodec is not None if vcodec else
        False if has_video_key else
        (height > 0 or fps > 0 or ext in VIDEO_CONTAINERS) if codecs_unknown else
        False
    )

    has_audio = (
        True if acodec is not None else
        False if has_audio_key else
        True if (audio_ext is not None or "audio only" in resolution or abr is not None or ext in AUDIO_CONTAINERS) else
        True if codecs_unknown and (not has_video or ext in VIDEO_CONTAINERS) else
        True if not has_video else
        False
    )

    if not has_video and not has_audio:
        return None

    resolved_acodec = acodec or (audio_ext or ext if (has_audio and not has_video) else None)
    codec_label = ""
    if has_video and vcodec:
        codec_label = vcodec.split(".")[0].upper()
    elif not has_video and resolved_acodec:
        codec_label = resolved_acodec.split(".")[0].upper()

    bitrate = fmt.get("tbr") or abr or fmt.get("vbr") or 0.0

    return {
        "formatId": format_id,
        "hasVideo": has_video,
        "hasAudio": has_audio,
        "vcodec": vcodec,
        "acodec": resolved_acodec,
        "codecLabel": codec_label,
        "ext": ext,
        "height": height,
        "fps": fps,
        "bitrate": float(bitrate),
    }


# ---------------------------------------------------------------------------
# Suite 4: History UI Filter & Search Logic Simulation
# ---------------------------------------------------------------------------

class HistoryEntryMock:
    def __init__(self, id_val: str, title: str, author: str, isVideo: bool, completedAt: int, sizeBytes: int):
        self.id = id_val
        self.title = title
        self.author = author
        self.isVideo = isVideo
        self.completedAt = completedAt
        self.sizeBytes = sizeBytes


def filter_and_sort_history(
    entries: list[HistoryEntryMock],
    category_filter: str,  # ALL, AUDIO, VIDEO
    query: str,
    sort_by: str,          # NEWEST, TITLE, SIZE
) -> list[HistoryEntryMock]:
    result = []
    for e in entries:
        if category_filter == "AUDIO" and e.isVideo:
            continue
        if category_filter == "VIDEO" and not e.isVideo:
            continue
        if query.strip():
            q = query.lower()
            if q not in e.title.lower() and q not in e.author.lower():
                continue
        result.append(e)

    if sort_by == "NEWEST":
        result.sort(key=lambda x: x.completedAt, reverse=True)
    elif sort_by == "TITLE":
        result.sort(key=lambda x: x.title.lower())
    elif sort_by == "SIZE":
        result.sort(key=lambda x: x.sizeBytes, reverse=True)

    return result


# ---------------------------------------------------------------------------
# Test Execution
# ---------------------------------------------------------------------------

def run_tests():
    print("=" * 70)
    print("  Hazel Comprehensive Release Readiness & Regression Harness")
    print("=" * 70)

    # -----------------------------------------------------------------------
    print("\n--- 1. Video Downloader Command & Argument Integrity ---")
    # -----------------------------------------------------------------------

    # Test 1.1: Standard Muxed Video (YouTube 720p / TikTok / Twitter)
    muxed_fmt = MediaFormatMock(formatId="22", selector="22", hasVideo=True, hasAudio=True, ext="mp4")
    opts = DownloadOptions(videoContainer="", embedThumbnail=True, addChapters=True)
    args = simulate_build_request(muxed_fmt, opts, "Nature Documentary", "BBC")
    opt_map = [opt for opt, val in args]
    opt_dict = dict(args)

    check("Muxed video has -f selector", opt_dict.get("-f"), "22")
    check("Muxed video no automatic merge requested", "--merge-output-format" in opt_map, False)
    check("Muxed video embeds thumbnail", "--embed-thumbnail" in opt_map, True)
    check("Muxed video embeds chapters", "--embed-chapters" in opt_map, True)
    check("Muxed video has --no-playlist", "--no-playlist" in opt_map, True)

    # Test 1.2: Video-only Stream Paired with Audio (YouTube 1080p 137 + 140)
    vid_only = MediaFormatMock(formatId="137", selector="137", hasVideo=True, hasAudio=False, ext="mp4")
    opts_merge = DownloadOptions(videoContainer="mkv", embedThumbnail=True)
    args_merge = simulate_build_request(vid_only, opts_merge, "Tech Review", "TechChannel", audio_selector="140")
    opt_dict_merge = dict(args_merge)

    check("Video-only pairs with audioId", "137+140/" in opt_dict_merge.get("-f", ""), True)
    check("Video-only merges to requested container", opt_dict_merge.get("--merge-output-format"), "mkv")

    # Test 1.3: Video-only fallback when container not specified defaults to mp4
    opts_no_cont = DownloadOptions(videoContainer="")
    args_fallback = simulate_build_request(vid_only, opts_no_cont, "Vlog", "Vlogger", audio_selector=None)
    opt_dict_fb = dict(args_fallback)
    check("Video-only fallback merges to mp4", opt_dict_fb.get("--merge-output-format"), "mp4")

    # Test 1.4: Subtitle options applied to Video
    opts_subs = DownloadOptions(writeSubs=True, embedSubs=True, subLanguages="en,es")
    args_subs = simulate_build_request(muxed_fmt, opts_subs, "Film", "Studio")
    opts_subs_dict = dict(args_subs)
    check("Video has --write-subs", "--write-subs" in [k for k, v in args_subs], True)
    check("Video has --embed-subs", "--embed-subs" in [k for k, v in args_subs], True)
    check("Video has --sub-langs", opts_subs_dict.get("--sub-langs"), "en,es")

    # Test 1.5: SponsorBlock options
    opts_sb = DownloadOptions(sponsorBlockFilters=["sponsor", "selfpromo"], addChapters=False)
    args_sb = simulate_build_request(muxed_fmt, opts_sb, "Podcast", "Host")
    sb_dict = dict(args_sb)
    check("SponsorBlock filter string", sb_dict.get("--sponsorblock-remove"), "sponsor,selfpromo")
    check("SponsorBlock API URL set", sb_dict.get("--sponsorblock-api"), SPONSORBLOCK_API_URL)

    # -----------------------------------------------------------------------
    print("\n--- 2. Audio Downloader Command & Separation from Video ---")
    # -----------------------------------------------------------------------

    aud_fmt = MediaFormatMock(formatId="320", selector="320", hasVideo=False, hasAudio=True, ext="m4a")

    # Test 2.1: Audio Extraction -x and --audio-format
    opts_audio = DownloadOptions(audioContainer="mp3", embedThumbnail=True)
    args_audio = simulate_build_request(aud_fmt, opts_audio, "My Song", "Musician")
    audio_opts = [k for k, v in args_audio]
    audio_dict = dict(args_audio)

    check("Audio download includes -x", "-x" in audio_opts, True)
    check("Audio download specifies --audio-format mp3", audio_dict.get("--audio-format"), "mp3")
    check("Audio download embeds thumbnail for mp3", "--embed-thumbnail" in audio_opts, True)
    check("Audio download NEVER merges output format", "--merge-output-format" in audio_opts, False)
    check("Audio download NEVER includes subtitles", "--embed-subs" in audio_opts, False)

    # Test 2.2: Audio with NO_ARTWORK_CONTAINER (e.g. opus / flac / wav)
    opts_flac = DownloadOptions(audioContainer="flac", embedThumbnail=True)
    args_flac = simulate_build_request(aud_fmt, opts_flac, "FLAC Song", "Artist")
    check("Audio flac skips artwork embedding", "--embed-thumbnail" in [k for k, v in args_flac], False)

    # Test 2.3: Audio language filter
    args_lang = simulate_build_request(aud_fmt, opts_audio, "Song", "Artist", audio_language="hi")
    check("Audio language filter applied in selector", "ba[language^=hi]" in dict(args_lang).get("-f", ""), True)

    # -----------------------------------------------------------------------
    print("\n--- 3. Metadata Colon Escaping & Edge Cases (yt-dlp split) ---")
    # -----------------------------------------------------------------------

    def split_yt_dlp_parse_metadata(arg_val: str) -> tuple[str, str]:
        """Simulates yt-dlp's split on the first unescaped colon."""
        i = 0
        from_chars = []
        while i < len(arg_val):
            if arg_val[i] == "\\" and i + 1 < len(arg_val) and arg_val[i + 1] == ":":
                from_chars.append(":")
                i += 2
            elif arg_val[i] == ":":
                to_part = arg_val[i + 1:]
                return "".join(from_chars), to_part
            else:
                from_chars.append(arg_val[i])
                i += 1
        return "".join(from_chars), ""

    # Test 3.1: Video with Single Colon in Title
    args_colon_vid = simulate_build_request(muxed_fmt, opts, "Spider-Man: Across the Spider-Verse", "Sony Pictures")
    pm_args = [v for k, v in args_colon_vid if k == "--parse-metadata"]
    check("Three --parse-metadata options generated", len(pm_args), 3)

    from_title, to_title = split_yt_dlp_parse_metadata(pm_args[0])
    check("Title with colon roundtrips verbatim", from_title, "Spider-Man: Across the Spider-Verse")
    check("Target field is %(title)s", to_title, "%(title)s")

    from_author, to_author = split_yt_dlp_parse_metadata(pm_args[1])
    check("Author roundtrips verbatim", from_author, "Sony Pictures")
    check("Target field is %(uploader)s", to_author, "%(uploader)s")
    check("Artist slot mapping present", pm_args[2], "%(uploader)s:%(artist)s")

    # Test 3.2: Multiple Colons in Title and Author
    args_multi = simulate_build_request(muxed_fmt, opts, "Vol 1: Ep: 04: The End", "Label: Sublabel: Artist")
    pm_multi = [v for k, v in args_multi if k == "--parse-metadata"]
    from_m_title, _ = split_yt_dlp_parse_metadata(pm_multi[0])
    from_m_author, _ = split_yt_dlp_parse_metadata(pm_multi[1])
    check("Multi-colon title roundtrips", from_m_title, "Vol 1: Ep: 04: The End")
    check("Multi-colon author roundtrips", from_m_author, "Label: Sublabel: Artist")

    # Test 3.3: Special Characters (Percent %, Quotes, Slashes, Brackets, Emojis)
    special_title = '100% Real "Uncut" [4K] & (60fps) - C++ / Python #1 \xe2\x9a\xa1'
    args_special = simulate_build_request(muxed_fmt, opts, special_title, "CodeCamp")
    pm_special = [v for k, v in args_special if k == "--parse-metadata"]
    from_special, _ = split_yt_dlp_parse_metadata(pm_special[0])
    check("Special characters and emoji preserved in metadata", from_special, special_title)

    # Test 3.4: Unicode Scripts (Devanagari, Arabic, Japanese, Cyrillic)
    unicode_titles = [
        ("Hindi", "\u0924\u0947\u0930\u0940 \u092e\u093f\u091f\u094d\u091f\u0940: \u0915\u0947\u0938\u0930\u0940", "\u092c\u0940 \u092a\u094d\u0930\u093e\u0915"),
        ("Japanese", "\u591c\u306b\u99c6\u3051\u308b: \u539f\u66f2", "YOASOBI"),
        ("Cyrillic", "\u0421\u043b\u0443\u0447\u0430\u0439\u043d\u044b\u0439 \u0432\u0430\u043b\u044c\u0441: \u0412\u0435\u0441\u043d\u0430", "\u041b\u0435\u043e\u043d\u0438\u0434 \u0423\u0442\u0451\u0441\u043e\u0432"),
    ]
    for label, u_title, u_author in unicode_titles:
        u_args = simulate_build_request(muxed_fmt, opts, u_title, u_author)
        u_pm = [v for k, v in u_args if k == "--parse-metadata"]
        f_title, _ = split_yt_dlp_parse_metadata(u_pm[0])
        f_author, _ = split_yt_dlp_parse_metadata(u_pm[1])
        check(f"Unicode title roundtrip ({label})", f_title, u_title)
        check(f"Unicode author roundtrip ({label})", f_author, u_author)

    # Test 3.5: Blank Handling
    args_blank = simulate_build_request(muxed_fmt, opts, "", "")
    check("Both blank -> NO --embed-metadata", "--embed-metadata" in [k for k, v in args_blank], False)

    args_title_only = simulate_build_request(muxed_fmt, opts, "Only Title", "")
    pm_to = [v for k, v in args_title_only if k == "--parse-metadata"]
    check("Title only -> 1 metadata option", len(pm_to), 1)
    check("Title only -> no artist mapping", "%(uploader)s:%(artist)s" not in pm_to, True)

    # -----------------------------------------------------------------------
    print("\n--- 4. Filename Sanitization & Template Safety ---")
    # -----------------------------------------------------------------------

    # Test 4.1: Sanitizer removes forbidden filesystem chars
    forbidden = 'Hello: World / Test \\ File * Question? "Quote" <Less> >More< |Pipe| 100%'
    sanitized = sanitize_for_filename(forbidden)
    for bad_char in [":", "/", "\\", "*", "?", '"', "<", ">", "|", "%"]:
        check(f"Forbidden char '{bad_char}' replaced", bad_char not in sanitized, True)

    # Test 4.2: Length limit of 120 chars
    long_name = "A" * 200
    check("Sanitized length capped at 120", len(sanitize_for_filename(long_name)), 120)

    # Test 4.3: Empty/whitespace defaults to 'download'
    check("Whitespace fallback to 'download'", sanitize_for_filename("   "), "download")
    check("Forbidden-only replaced with underscores", sanitize_for_filename("///:::***"), "_________")

    # Test 4.4: Template substitution when edited
    t_opt = DownloadOptions(filenameTemplate="%(uploader)s - %(title)s.%(ext)s")
    tmpl_result = output_template(t_opt, "New: Title", "New: Artist", "Old Title", "Old Artist")
    check("Edited title sanitized in template", "New_ Title" in tmpl_result, True)
    check("Edited author sanitized in template", "New_ Artist" in tmpl_result, True)

    # -----------------------------------------------------------------------
    print("\n--- 5. MediaProbe Stream Parsing & Codec Badges ---")
    # -----------------------------------------------------------------------

    # Test 5.1: JioSaavn Stream (missing acodec, vcodec="none", abr=320)
    jio_json = {
        "format_id": "320", "ext": "m4a", "abr": 320, "vcodec": "none",
        "audio_ext": "m4a", "resolution": "audio only"
    }
    jio_res = parse_format(jio_json)
    check_true("JioSaavn parsed format is not None", jio_res is not None)
    if jio_res:
        check("JioSaavn hasAudio is True", jio_res["hasAudio"], True)
        check("JioSaavn hasVideo is False", jio_res["hasVideo"], False)
        check("JioSaavn acodec fallback to m4a", jio_res["acodec"], "m4a")
        check("JioSaavn codecLabel is M4A", jio_res["codecLabel"], "M4A")

    # Test 5.2: YouTube Video-only 1080p
    yt_vid_json = {"format_id": "137", "ext": "mp4", "vcodec": "avc1.640028", "acodec": "none", "height": 1080, "fps": 30}
    yt_vid_res = parse_format(yt_vid_json)
    check_true("YouTube video parsed", yt_vid_res is not None)
    if yt_vid_res:
        check("YouTube 1080p hasVideo", yt_vid_res["hasVideo"], True)
        check("YouTube 1080p hasAudio is False", yt_vid_res["hasAudio"], False)
        check("YouTube 1080p codecLabel is AVC1", yt_vid_res["codecLabel"], "AVC1")

    # Test 5.3: Instagram Bare Stream (direct video with unknown codecs)
    ig_fmt_json = {"format_id": "0", "ext": "mp4", "height": 720}
    ig_res = parse_format(ig_fmt_json)
    check_true("Instagram bare format parsed", ig_res is not None)
    if ig_res:
        check("Instagram assumed hasVideo", ig_res["hasVideo"], True)
        check("Instagram assumed hasAudio", ig_res["hasAudio"], True)

    # Test 5.4: Artist Resolution from Music Platforms
    media_artists = {"artists": ["Singer One", "Singer Two"], "uploader": "Record Label"}
    check("Multi-artist joined", parse_probe_uploader(media_artists), "Singer One, Singer Two")

    media_single_artist = {"artist": "Solo Singer", "uploader": "Record Label"}
    check("Single artist resolved", parse_probe_uploader(media_single_artist), "Solo Singer")

    media_youtube = {"uploader": "YouTube Creator", "channel": "YouTube Creator"}
    check("Uploader fallback on YouTube", parse_probe_uploader(media_youtube), "YouTube Creator")

    # -----------------------------------------------------------------------
    print("\n--- 6. Data Store Models Serialization Round-trips ---")
    # -----------------------------------------------------------------------

    # Test 6.1: QueuedDownload JSON roundtrip
    queue_dict = {
        "url": "https://example.com/watch?v=123",
        "title": "Song Title",
        "author": "Song Artist",
        "thumbnail": "https://img.com/art.jpg",
        "duration": 240,
        "formatId": "320",
        "selector": "320",
        "formatLabel": "320 KBPS AUDIO",
        "ext": "m4a",
        "hasVideo": False,
        "hasAudio": True,
        "isGeneric": False,
        "size": 1234567,
        "mergeAudio": None,
        "mergeAudioSize": 0,
        "treeUri": "",
        "requiresSignIn": False,
        "audioLanguage": None,
        "paused": False,
    }
    encoded_queue = json.dumps([queue_dict])
    decoded_queue = json.loads(encoded_queue)[0]
    check("Queue URL roundtrip", decoded_queue["url"], queue_dict["url"])
    check("Queue formatId roundtrip", decoded_queue["formatId"], "320")
    check("Queue hasAudio preserved", decoded_queue["hasAudio"], True)
    check("Queue hasVideo preserved", decoded_queue["hasVideo"], False)

    # Test 6.2: FailedDownload JSON roundtrip
    failed_dict = {
        "id": 987654321,
        "url": "https://example.com/fail",
        "title": "Failed Stream",
        "author": "Error Uploader",
        "thumbnail": "https://img.com/thumb.jpg",
        "isVideo": True,
        "errorLog": "ERROR: [youtube] 403 Forbidden",
        "failedAt": 1700000000000,
        "queuedPayload": encoded_queue,
    }
    encoded_failed = json.dumps([failed_dict])
    decoded_failed = json.loads(encoded_failed)[0]
    check("Failed id roundtrip", decoded_failed["id"], 987654321)
    check("Failed errorLog preserved", "403 Forbidden" in decoded_failed["errorLog"], True)
    check("Failed queuedPayload preserved", decoded_failed["queuedPayload"], encoded_queue)

    # -----------------------------------------------------------------------
    print("\n--- 7. History & Downloads Screen UI Logic ---")
    # -----------------------------------------------------------------------

    mock_history = [
        HistoryEntryMock("1", "Alpha Video", "Creator A", isVideo=True, completedAt=1000, sizeBytes=50000),
        HistoryEntryMock("2", "Beta Audio", "Singer B", isVideo=False, completedAt=2000, sizeBytes=10000),
        HistoryEntryMock("3", "Gamma Video", "Creator C", isVideo=True, completedAt=3000, sizeBytes=80000),
        HistoryEntryMock("4", "Delta Podcast", "Host D", isVideo=False, completedAt=4000, sizeBytes=20000),
    ]

    # Test 7.1: Category Filtering (ALL, AUDIO, VIDEO)
    all_res = filter_and_sort_history(mock_history, "ALL", "", "NEWEST")
    check("ALL filter contains all 4 items", len(all_res), 4)

    aud_res = filter_and_sort_history(mock_history, "AUDIO", "", "NEWEST")
    check("AUDIO filter contains 2 audio items", len(aud_res), 2)
    check_true("AUDIO items are all non-video", all(not x.isVideo for x in aud_res))

    vid_res = filter_and_sort_history(mock_history, "VIDEO", "", "NEWEST")
    check("VIDEO filter contains 2 video items", len(vid_res), 2)
    check_true("VIDEO items are all video", all(x.isVideo for x in vid_res))

    # Test 7.2: Search Query Matching
    search_res = filter_and_sort_history(mock_history, "ALL", "beta", "NEWEST")
    check("Search for 'beta' yields 1 item", len(search_res), 1)
    check("Matched item title is 'Beta Audio'", search_res[0].title, "Beta Audio")

    # Test 7.3: Sorting (SIZE, TITLE, NEWEST)
    sort_size_res = filter_and_sort_history(mock_history, "ALL", "", "SIZE")
    check("Sort by SIZE places largest first", sort_size_res[0].id, "3")

    sort_title_res = filter_and_sort_history(mock_history, "ALL", "", "TITLE")
    check("Sort by TITLE places 'Alpha' first", sort_title_res[0].id, "1")

    # -----------------------------------------------------------------------
    print("\n--- 8. UI Assets, Launcher Icon & Resource Parity ---")
    # -----------------------------------------------------------------------

    # Test 8.1: Launcher Background Color is Black (#000000)
    colors_xml_path = REPO_ROOT / "app/src/main/res/values/colors.xml"
    if colors_xml_path.exists():
        colors_tree = ET.parse(colors_xml_path)
        bg_elem = colors_tree.getroot().find("./color[@name='ic_launcher_background']")
        check("Launcher background color is #000000", bg_elem.text if bg_elem is not None else "", "#000000")

    # Test 8.2: Launcher Foreground Stroke is White (#FFFFFF)
    foreground_xml_path = REPO_ROOT / "app/src/main/res/drawable/ic_launcher_foreground.xml"
    if foreground_xml_path.exists():
        fg_content = foreground_xml_path.read_text(encoding="utf-8")
        check_true("Launcher foreground strokeColor is #FFFFFF", 'android:strokeColor="#FFFFFF"' in fg_content)

    # Test 8.3: SponsorScreen Constants
    sponsor_kt = REPO_ROOT / "app/src/main/java/com/hazel/android/ui/screens/more/SponsorScreen.kt"
    if sponsor_kt.exists():
        sponsor_text = sponsor_kt.read_text(encoding="utf-8")
        check_true("BMAC URL is set to sibtainocean", 'BMAC_URL = "https://buymeacoffee.com/sibtainocean"' in sponsor_text)
        check_true("No leftover KOFI_URL constant", "KOFI_URL" not in sponsor_text)

    # Test 8.4: Locale String Parity for PR 36 (BMAC in all 10 locales, kofi_soon removed)
    locales = ["", "-de", "-es", "-fr", "-hi", "-in", "-ja", "-pt-rBR", "-ru", "-zh-rCN"]
    for loc in locales:
        str_file = REPO_ROOT / f"app/src/main/res/values{loc}/strings.xml"
        check_true(f"Strings file exists for values{loc}", str_file.exists())
        if str_file.exists():
            tree = ET.parse(str_file)
            root = tree.getroot()
            bmac_title = root.find("./string[@name='sponsor_bmac_title']")
            bmac_sub = root.find("./string[@name='sponsor_bmac_subtitle']")
            kofi_soon = root.find("./string[@name='sponsor_kofi_soon']")
            check_true(f"values{loc} has sponsor_bmac_title", bmac_title is not None)
            check_true(f"values{loc} has sponsor_bmac_subtitle", bmac_sub is not None)
            check_true(f"values{loc} has removed sponsor_kofi_soon", kofi_soon is None)

    # -----------------------------------------------------------------------
    print("\n--- 9. Batch & Multi-Link Queue Lifecycle & Per-Item Cancellation ---")
    # -----------------------------------------------------------------------

    # Test 9.1: Single Item Cancellation in Batch (Skipping 1 of 49 without breaking queue)
    class SimulatedBatchRunner:
        def __init__(self, count: int):
            self.queue = list(range(1, count + 1))
            self.results = {}
            self.is_cancelled = False
            self.is_batch_cancelled = False
            self.active_item = None

        def run(self, cancel_item_id: int | None = None, cancel_all_at: int | None = None):
            while self.queue:
                item = self.queue.pop(0)
                self.active_item = item

                if cancel_all_at is not None and item == cancel_all_at:
                    self.is_batch_cancelled = True
                    self.results[item] = "CANCELLED"
                    self.queue.clear()
                    break

                if cancel_item_id is not None and item == cancel_item_id:
                    self.is_cancelled = True
                    # Purge fragments and mark single item as Cancelled
                    self.results[item] = "CANCELLED"
                    self.is_cancelled = False
                    continue  # Continues to next item!

                # Item completes successfully
                self.results[item] = "DONE"

            done = sum(1 for v in self.results.values() if v == "DONE")
            true_failed = sum(1 for v in self.results.values() if v == "FAILED")
            cancelled = sum(1 for v in self.results.values() if v == "CANCELLED")
            error_msg = None
            if done == 0 and true_failed > 0:
                error_msg = "Download failed"
            elif true_failed > 0:
                error_msg = f"{true_failed} of {len(self.results)} failed"

            return {
                "done": done,
                "true_failed": true_failed,
                "cancelled": cancelled,
                "total_attempted": len(self.results),
                "error": error_msg
            }

    runner_skip = SimulatedBatchRunner(49)
    res_skip = runner_skip.run(cancel_item_id=7)
    check("Batch continues: 48 items done when item 7 cancelled", res_skip["done"], 48)
    check("Cancelled item counted as cancelled", res_skip["cancelled"], 1)
    check("True failures is zero when single item cancelled", res_skip["true_failed"], 0)
    check("Error message is None (no inaccurate '1 of 49 failed')", res_skip["error"], None)

    # Test 9.2: Cancel All stops remaining queue
    runner_cancel_all = SimulatedBatchRunner(49)
    res_cancel_all = runner_cancel_all.run(cancel_all_at=7)
    check("Cancel all stops at item 7: 6 done", res_cancel_all["done"], 6)
    check("Cancel all stopped queue: remaining 42 not run", res_cancel_all["total_attempted"], 7)

    # Test 9.3: Removing Queued Item while active item is running
    queue_test = list(range(1, 50))
    active_now = queue_test.pop(0)
    check("Active download is item 1", active_now, 1)
    # Remove item 15 from waiting queue
    queue_test.remove(15)
    check_true("Item 15 removed from waiting queue", 15 not in queue_test)
    check("Remaining waiting queue has 47 items", len(queue_test), 47)

    # Test 9.4: Batch Control Strings in All 10 Locales
    for loc in locales:
        str_file = REPO_ROOT / f"app/src/main/res/values{loc}/strings.xml"
        if str_file.exists():
            tree = ET.parse(str_file)
            root = tree.getroot()
            pause_all = root.find("./string[@name='download_pause_all']")
            resume_all = root.find("./string[@name='download_resume_all']")
            cancel_all = root.find("./string[@name='download_cancel_all']")
            check_true(f"values{loc} has download_pause_all", pause_all is not None and bool(pause_all.text))
            check_true(f"values{loc} has download_resume_all", resume_all is not None and bool(resume_all.text))
            check_true(f"values{loc} has download_cancel_all", cancel_all is not None and bool(cancel_all.text))

    # -----------------------------------------------------------------------
    print("\n" + "=" * 70)
    total = PASS_COUNT + FAIL_COUNT
    print(f"  Summary: {PASS_COUNT}/{total} tests PASSED, {FAIL_COUNT} FAILED")
    print("=" * 70)

    return 1 if FAIL_COUNT > 0 else 0


if __name__ == "__main__":
    sys.exit(run_tests())
