#!/usr/bin/env python3
"""
Live Hazel Instant harness: runs the real yt-dlp and ffmpeg the way an Instant share does,
for audio and for video, and checks what lands on disk.

An Instant share reads the link, picks a format with nobody there to choose, and downloads
it with the saved Synthesizing settings. This script does the same for every source:

  * reads the link as the app does (a JSON dump) and picks the format Instant would:
    with "Instant saves as" set to Audio only, the source's best audio-only stream, in the
    preferred language where it has one, or the generic best audio when the source
    lists none; with Video, the video under the quality ceiling with sound,
  * downloads with the same flags DownloadViewModel.buildRequest passes for that format,
    across the audio formats and bitrates Synthesizing offers,
  * opens the file and checks it is what was asked for: Audio only gives a file with
    sound and no picture stream (a cover is not a picture stream), in the chosen format,
    with a cover where the format holds one; Video gives picture and sound together.

Sources are real sites plus local stand-ins (a single combined file, which is what makes
best audio fall back to extracting from the video, and an audio-only WebM).

It also checks, without the network, that the flags and the Instant wiring below are the
ones the Kotlin has, so the two cannot drift apart.

Needs the yt-dlp and ffmpeg/ffprobe executables on the PATH, and the network for the
real sites. Not part of test_all.py; run it by hand when Instant or the download flags
change:

    python tools/tests/live/test_instant_live.py              # full matrix
    python tools/tests/live/test_instant_live.py --quick      # one audio format per source
    python tools/tests/live/test_instant_live.py --local      # stand-ins only, no network
    python tools/tests/live/test_instant_live.py --static     # wiring check only

A site that refuses the network the run is on is reported as SKIP rather than FAIL: that
says nothing about the flags.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
KOTLIN = os.path.join(ROOT, "app/src/main/java/com/hazel/android")
VIEW_MODEL = os.path.join(KOTLIN, "download/DownloadViewModel.kt")
SETTINGS = os.path.join(KOTLIN, "data/SettingsRepository.kt")
PROCESSING = os.path.join(KOTLIN, "ui/screens/more/ProcessingScreen.kt")

# ── The app's flags, kept word for word with DownloadViewModel ──────────────────────────
COVER_SAFE_VIDEO = "mp4>mp4/mov>mov/mkv>mkv/mkv"
COVER_SAFE_AUDIO = "aiff>flac/wav>flac/wma>m4a/best"
BEST_AUDIO_SELECTOR = "ba/b"
ALBUM_TAG = "%(album,title)s:%(meta_album)s"

# Audio output (blank is Default) and bitrate (blank leaves it to the encoder), as
# Synthesizing offers them. Default extracts whatever the source has.
AUDIO_CASES = [("", ""), ("mp3", "192k"), ("m4a", ""), ("opus", ""), ("flac", ""), ("wav", "")]
QUICK_AUDIO_CASES = [("", ""), ("mp3", "192k")]
# Extensions each audio choice may land as. Default keeps the source's own codec.
AUDIO_EXTS = {
    "": {"m4a", "opus", "mp3", "ogg", "flac", "aac", "webm", "mka", "wav"},
    "mp3": {"mp3"}, "m4a": {"m4a"}, "opus": {"opus"}, "flac": {"flac"}, "wav": {"wav"},
}
VIDEO_HEIGHT = 360

SITES = [
    ("youtube", "https://www.youtube.com/watch?v=jNQXAC9IVRw"),
    ("soundcloud", "https://soundcloud.com/forss/flickermood"),
    ("bandcamp", "https://youtube-dl.bandcamp.com/track/youtube-dl-test-song"),
    ("jiosaavn", "https://www.jiosaavn.com/song/tum-hi-ho/EToxUyFpcwQ"),
    ("archive.org", "https://archive.org/details/Popeye_forPresident"),
    ("dailymotion", "https://www.dailymotion.com/video/x8j7s5d"),
    ("vimeo", "https://vimeo.com/76979871"),
]

REFUSED = re.compile(
    r"sign in|not a bot|logged-in|page needs to be reloaded|cookies|geo.?restrict|"
    r"HTTP Error 4(01|03|04|29)|Unable to download webpage|Unsupported URL|not available|"
    r"Unable to extract|timed out",
    re.I,
)


# ── What Instant picks ───────────────────────────────────────────────────────────────────
def instant_audio_pick(info: dict, language: str = ""):
    """
    The audio Instant takes from a read link, as MediaInfo.autoPick(false, ...) does: the
    best audio-only stream, in [language] where there is one, or the generic best audio
    when the source lists no audio-only stream.
    """
    audio = [f for f in info.get("formats") or []
             if f.get("vcodec") in (None, "none") and f.get("acodec") not in (None, "none")
             and f.get("format_id")]
    audio.sort(key=lambda f: (f.get("abr") or f.get("tbr") or 0), reverse=True)
    if language:
        in_language = [f for f in audio if (f.get("language") or "").startswith(language)]
        if in_language:
            return in_language[0]["format_id"]
    return audio[0]["format_id"] if audio else None


def audio_args(selector: str, chosen: str, quality: str, language: str = "") -> list:
    """What buildRequest passes for an audio format, with the cover on (the default)."""
    if language:
        args = ["-f", f"{selector}/ba[language^={language}]/ba"]
    else:
        args = ["-f", selector]
    args += ["-x"]
    if chosen and chosen not in ("default", "webm"):
        args += ["--audio-format", chosen]
    if quality:
        args += ["--audio-quality", quality]
    if chosen != "wav":
        if not chosen or chosen == "webm":
            args += ["--audio-format", COVER_SAFE_AUDIO]
        args += ["--embed-thumbnail", "--convert-thumbnails", "jpg"]
    args += ["--embed-metadata", "--parse-metadata", ALBUM_TAG]
    return args


def video_args() -> list:
    """What buildRequest passes for Instant's video under a quality ceiling."""
    return ["-f", "bv*+ba/b", "-S", f"res:{VIDEO_HEIGHT}", "--merge-output-format", "mp4",
            "--remux-video", COVER_SAFE_VIDEO, "--embed-thumbnail", "--convert-thumbnails", "jpg",
            "--embed-chapters", "--embed-metadata"]


# ── Reading the result ───────────────────────────────────────────────────────────────────
def streams_of(path: str) -> list:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries",
         "stream=codec_type,codec_name:stream_disposition=attached_pic:format=duration",
         "-of", "json", path],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    ).stdout
    return json.loads(out or "{}").get("streams") or []


def has_cover(path: str, streams: list) -> bool:
    if any(s.get("disposition", {}).get("attached_pic") for s in streams):
        return True
    # Ogg and Opus carry the cover as a tag rather than a stream.
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "stream_tags:format_tags",
                          "-of", "json", path],
                         capture_output=True, text=True, encoding="utf-8", errors="replace").stdout
    return "metadata_block_picture" in out.lower()


def check_audio(path: str, chosen: str) -> tuple:
    name = os.path.basename(path)
    ext = os.path.splitext(path)[1].lstrip(".").lower()
    if ext not in AUDIO_EXTS[chosen]:
        return "FAIL", f"{name}: .{ext} is not what {chosen or 'Default'} gives"
    streams = streams_of(path)
    sound = [s for s in streams if s.get("codec_type") == "audio"]
    picture = [s for s in streams if s.get("codec_type") == "video"
               and not s.get("disposition", {}).get("attached_pic")]
    if not sound:
        return "FAIL", f"{name}: no audio stream"
    if picture:
        return "FAIL", f"{name}: Audio only kept a video stream ({picture[0].get('codec_name')})"
    cover = has_cover(path, streams)
    if chosen != "wav" and not cover:
        return "FAIL", f"{name}: no embedded cover"
    return "PASS", f"{name}: {sound[0].get('codec_name')}, " + ("cover" if cover else "no cover (wav)")


def check_video(path: str) -> tuple:
    name = os.path.basename(path)
    streams = streams_of(path)
    kinds = {s.get("codec_type") for s in streams if not s.get("disposition", {}).get("attached_pic")}
    if "video" not in kinds:
        return "FAIL", f"{name}: Video gave no picture"
    if "audio" not in kinds:
        return "FAIL", f"{name}: Video gave no sound"
    return "PASS", f"{name}: picture and sound"


# ── Running ──────────────────────────────────────────────────────────────────────────────
def yt_dlp(args: list, cwd: str, timeout: int = 900):
    proc = subprocess.run(["yt-dlp", "--no-warnings", "--no-playlist", "--socket-timeout", "20",
                           *args], cwd=cwd, capture_output=True, text=True,
                          encoding="utf-8", errors="replace", timeout=timeout)
    return proc.returncode, proc.stdout + proc.stderr


def refused(log: str, local: bool) -> bool:
    return not local and bool(REFUSED.search(log))


def last_line(log: str) -> str:
    return (log.strip().splitlines() or ["?"])[-1][:200]


def read_link(source: str, workdir: str, local: bool):
    """The read Instant starts with: the link's metadata and formats."""
    if local:
        with open(source, encoding="utf-8") as f:
            return json.load(f), ""
    code, log = yt_dlp(["-J", source], workdir, timeout=300)
    if code != 0:
        return None, log
    try:
        return json.loads(log[log.index("{"):log.rindex("}") + 1]), ""
    except ValueError:
        return None, log


def download(source: str, args: list, workdir: str, local: bool):
    out = os.path.join(workdir, "dl")
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(out)
    where = ["--enable-file-urls", "--load-info-json", source] if local else [source]
    code, log = yt_dlp(["-o", "media.%(ext)s", *args, *where], out)
    files = [f for f in os.listdir(out)
             if f.startswith("media.") and not f.endswith((".jpg", ".png", ".webp", ".part", ".json"))]
    return code, log, (os.path.join(out, files[0]) if files else None)


def run_source(name: str, source: str, workdir: str, local: bool, cases: list, results: list):
    def report(case: str, status: str, detail: str):
        results.append((name, case, status, detail))
        print(f"  [{status}] {name:<12} {case:<22} {detail}", flush=True)

    os.makedirs(workdir, exist_ok=True)
    info, log = read_link(source, workdir, local)
    if info is None:
        status = "SKIP" if refused(log, local) else "FAIL"
        report("read", status, last_line(log))
        return

    pick = instant_audio_pick(info)
    selector = pick or BEST_AUDIO_SELECTOR
    for chosen, quality in cases:
        case = f"audio {chosen or 'default'}{' ' + quality if quality else ''}"
        code, log, path = download(source, audio_args(selector, chosen, quality), workdir, local)
        if code != 0 or path is None:
            report(case, "SKIP" if refused(log, local) else "FAIL", "yt-dlp: " + last_line(log))
            continue
        status, detail = check_audio(path, chosen)
        report(case, status, f"-f {selector} -> {detail}")

    # A preferred language the source does not have must still give its sound.
    code, log, path = download(source, audio_args(selector, "", "", "zz"), workdir, local)
    if code != 0 or path is None:
        report("audio, missing language", "SKIP" if refused(log, local) else "FAIL", last_line(log))
    else:
        report("audio, missing language", *check_audio(path, ""))

    # Video is what Instant did before the choice existed, and must still do.
    if any(f.get("vcodec") not in (None, "none") for f in info.get("formats") or []):
        code, log, path = download(source, video_args(), workdir, local)
        if code != 0 or path is None:
            report("video", "SKIP" if refused(log, local) else "FAIL", "yt-dlp: " + last_line(log))
        else:
            report("video", *check_video(path))


def build_stand_in(name: str, ext: str, codecs: list, has_video: bool, root: str) -> str:
    """A short local file with artwork, as an info record with one combined format."""
    media = os.path.join(root, f"{name}.{ext}")
    art = os.path.join(root, "art.jpg")
    if not os.path.exists(art):
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i",
                        "testsrc=size=640x360:duration=1", "-frames:v", "1", art], check=True)
    inputs = ["-f", "lavfi", "-i", "sine=frequency=440:duration=3"]
    if has_video:
        inputs = ["-f", "lavfi", "-i", "testsrc=size=320x240:rate=25:duration=3"] + inputs
    subprocess.run(["ffmpeg", "-v", "error", "-y", *inputs, *codecs, "-shortest", media], check=True)
    url = "file:///" + media.replace("\\", "/").lstrip("/")
    art_url = "file:///" + art.replace("\\", "/").lstrip("/")
    fmt = {"format_id": "local", "url": url, "ext": ext,
           "acodec": "aac" if has_video else "opus", "vcodec": "h264" if has_video else "none"}
    info = {"id": name, "title": name, "uploader": "Hazel", "extractor": "generic",
            "extractor_key": "Generic", "webpage_url": url, "formats": [fmt],
            "thumbnails": [{"id": "0", "url": art_url}]}
    path = os.path.join(root, f"{name}.info.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(info, f)
    return path


STAND_INS = [
    # One combined file and no audio-only stream: best audio falls back to it, and the
    # sound is extracted out of the video.
    ("combined-mp4", "mp4", ["-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac"], True),
    ("audio-webm", "webm", ["-c:a", "libopus"], False),
]


# ── Wiring ───────────────────────────────────────────────────────────────────────────────
def static_check() -> bool:
    """The flags above, and the Instant choice, are what the Kotlin has."""
    def read(path):
        with open(path, encoding="utf-8") as f:
            return f.read()
    vm, settings, screen = read(VIEW_MODEL), read(SETTINGS), read(PROCESSING)
    instant = vm[vm.index("fun instantDownload"):]
    instant = instant[:instant.index("\n    }\n")]
    checks = {
        "Instant reads its audio-only choice": "getInstantAudioOnly(app)" in instant,
        "Audio only picks an audio format": "autoPick(false" in instant,
        "Audio only falls back to best audio": "BatchAudioFormats.BEST" in instant,
        "Audio only keeps the preferred language": "preferredAudioLanguage" in instant,
        "Audio only keeps the preferred codec": "audioCodecPreference" in instant,
        "a failed Instant is filed under its kind": "isVideo = !audioOnly" in instant,
        "best audio is ba/b": f'selector = "{BEST_AUDIO_SELECTOR}"' in read(
            os.path.join(KOTLIN, "download/MediaProbe.kt")),
        "audio is extracted": 'addOption("-x")' in vm,
        "audio bitrate passed": '"--audio-quality", options.audioQuality' in vm,
        "audio cover rules match": f'"{COVER_SAFE_AUDIO}"' in vm,
        "video remux rules match": f'"{COVER_SAFE_VIDEO}"' in vm,
        "language fallback matches": '"${format.selector}/$languageFilter/ba"' in vm
                                     and '"ba[language^=$it]"' in vm,
        "album tag matches": f'"{ALBUM_TAG}"' in vm,
        "choice is stored": '"instant_audio_only"' in settings,
        "Synthesizing offers the choice": "setInstantAudioOnly" in screen
                                          and "processing_instant_audio_only" in screen,
    }
    ok = True
    for name, passed in checks.items():
        print(f"  [{'PASS' if passed else 'FAIL'}] {name}")
        ok = ok and passed
    return ok


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--static", action="store_true", help="wiring check only, no downloads")
    parser.add_argument("--local", action="store_true", help="local stand-ins only, no network")
    parser.add_argument("--quick", action="store_true", help="fewer audio formats per source")
    parser.add_argument("--site", help="only the site with this name")
    args = parser.parse_args()

    print("Static wiring check")
    static_ok = static_check()
    if args.static:
        return 0 if static_ok else 1

    for tool in ("yt-dlp", "ffmpeg", "ffprobe"):
        if not shutil.which(tool):
            print(f"{tool} not found on PATH")
            return 1
    print("\nyt-dlp " + subprocess.run(["yt-dlp", "--version"], capture_output=True, text=True).stdout.strip())

    cases = QUICK_AUDIO_CASES if args.quick else AUDIO_CASES
    results = []
    root = tempfile.mkdtemp(prefix="hazel-instant-")
    try:
        print("\nLocal stand-ins")
        for name, ext, codecs, has_video in STAND_INS:
            source = build_stand_in(name, ext, codecs, has_video, root)
            run_source(name, source, os.path.join(root, name), True, cases, results)
        if not args.local:
            print("\nReal sites")
            for name, url in SITES:
                if args.site and args.site != name:
                    continue
                run_source(name, url, os.path.join(root, name), False, cases, results)
    finally:
        shutil.rmtree(root, ignore_errors=True)

    counts = {s: sum(1 for r in results if r[2] == s) for s in ("PASS", "FAIL", "SKIP")}
    print(f"\nSummary: {counts['PASS']} passed, {counts['FAIL']} failed, {counts['SKIP']} skipped"
          f"{'' if static_ok else ', static check FAILED'}")
    return 0 if static_ok and counts["FAIL"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
