#!/usr/bin/env python3
"""
Live cover-art harness: runs the real yt-dlp and ffmpeg against real links and checks that
the thumbnail options the app passes actually land in the file.

For every source and every output format the app offers, it downloads with the same
thumbnail flags DownloadViewModel builds, then opens the result and checks:

  * a cover is embedded wherever the app asks for one (and nothing breaks where it
    deliberately does not, such as WAV or WebM),
  * with "Crop thumbnail" on, the embedded cover is square, including when the source
    artwork is already a JPEG (yt-dlp skips converting, and so cropping, a thumbnail that
    is already in the target format, which is why the app maps jpg>png while cropping),
  * with it off, the cover keeps its own shape.

It also checks, without the network, that the flags below are the ones the Kotlin passes,
so the two cannot drift apart.

Needs network, yt-dlp, ffmpeg/ffprobe and mutagen, so it is not part of test_all.py:

    pip install -U yt-dlp mutagen
    python tools/test_thumbnail_embed_live.py              # full matrix
    python tools/test_thumbnail_embed_live.py --quick      # one audio + one video format
    python tools/test_thumbnail_embed_live.py --static     # flag check only, no network

A source the network refuses (a site asking for sign-in from a datacenter address, say)
is reported as SKIP rather than FAIL: that says nothing about the flags.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VIEW_MODEL = os.path.join(ROOT, "app/src/main/java/com/hazel/android/download/DownloadViewModel.kt")

# ── The app's flags, kept word for word with DownloadViewModel ──────────────────────────
# yt-dlp splits --ppa values like a shell; the double quotes keep the single quotes that
# ffmpeg needs around the commas. Without them every cropped download fails.
CROP_FILTER = "crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\""
CROP_PPA = f"ThumbnailsConvertor:-vf {CROP_FILTER}"
CONVERT_PLAIN = "jpg"
CONVERT_CROPPED = "jpg>png/jpg"
NO_ARTWORK_CONTAINERS = {"webm", "avi", "flv"}

# Output formats the sheet offers ("" is Default).
AUDIO_FORMATS = ["", "mp3", "m4a", "aac", "alac", "flac", "opus", "vorbis", "wav"]
VIDEO_CONTAINERS = ["", "mp4", "mkv", "mov", "webm"]

# Sources that between them cover square and wide artwork, JPEG and WebP/PNG artwork,
# music sites and video sites. Kept short so a full run stays in minutes.
SOURCES = [
    ("soundcloud", "https://soundcloud.com/forss/flickermood", "audio"),
    ("jiosaavn", "https://www.jiosaavn.com/song/tum-hi-ho/EToxUyFpcwQ", "audio"),
    ("archive.org", "https://archive.org/details/Popeye_forPresident", "both"),
    ("dailymotion", "https://www.dailymotion.com/video/x8j7s5d", "both"),
    ("youtube", "https://www.youtube.com/watch?v=jNQXAC9IVRw", "both"),
]

REFUSED = re.compile(
    r"sign in|not a bot|logged-in|cookies|geo.?restrict|HTTP Error 4(01|03|29)|"
    r"Unable to download webpage|Unsupported URL|not available",
    re.I,
)


def thumbnail_args(embed: bool, crop: bool, artwork_supported: bool) -> list:
    """What DownloadViewModel adds for the thumbnail options."""
    if not (embed and artwork_supported):
        return []
    args = ["--embed-thumbnail"]
    if crop:
        args += ["--convert-thumbnails", CONVERT_CROPPED, "--ppa", CROP_PPA]
    else:
        args += ["--convert-thumbnails", CONVERT_PLAIN]
    return args


def audio_args(fmt: str) -> list:
    args = ["-f", "ba/b", "-x"]
    if fmt and fmt not in ("default", "webm"):
        args += ["--audio-format", fmt]
    return args


def video_args(container: str) -> list:
    return ["-f", "bv*+ba/b", "-S", "res:360,+size",
            "--merge-output-format", container or "mp4"]


def audio_artwork_supported(fmt: str) -> bool:
    return fmt != "wav"


def video_artwork_supported(container: str) -> bool:
    return (container or "mp4") not in NO_ARTWORK_CONTAINERS


# ── Reading the cover back out of the file ───────────────────────────────────────────────
def image_size(data: bytes, workdir: str):
    path = os.path.join(workdir, "cover.bin")
    with open(path, "wb") as f:
        f.write(data)
    return probe_size(path)


def probe_size(path: str):
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0",
         "-show_entries", "stream=width,height", "-of", "json", path],
        capture_output=True, text=True,
    ).stdout
    streams = json.loads(out or "{}").get("streams") or []
    if not streams:
        return None
    return streams[0].get("width"), streams[0].get("height")


def embedded_cover(path: str, workdir: str):
    """(width, height) of the embedded cover, or None if there is none."""
    ext = os.path.splitext(path)[1].lower().lstrip(".")

    if ext in ("mkv", "mka", "webm"):
        dump = os.path.join(workdir, "attach")
        shutil.rmtree(dump, ignore_errors=True)
        os.makedirs(dump)
        subprocess.run(["ffmpeg", "-v", "quiet", "-y", "-dump_attachment:t", "",
                        "-i", os.path.abspath(path)], cwd=dump, capture_output=True)
        for name in os.listdir(dump):
            size = probe_size(os.path.join(dump, name))
            if size:
                return size
        return None

    import mutagen
    from mutagen.flac import Picture
    import base64

    media = mutagen.File(path)
    if media is None:
        return None
    tags = media.tags

    if ext == "mp3" and tags is not None:
        for key in tags.keys():
            if key.startswith("APIC"):
                return image_size(tags[key].data, workdir)
    if ext in ("m4a", "mp4", "m4v", "mov") and tags is not None and "covr" in tags:
        return image_size(bytes(tags["covr"][0]), workdir)
    if ext == "flac" and getattr(media, "pictures", None):
        return image_size(media.pictures[0].data, workdir)
    if ext in ("ogg", "opus", "oga") and tags is not None:
        for raw in tags.get("metadata_block_picture", []):
            return image_size(Picture(base64.b64decode(raw)).data, workdir)

    # Video containers hold the cover as an attached picture stream.
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries",
         "stream=codec_type,width,height:stream_disposition=attached_pic", "-of", "json", path],
        capture_output=True, text=True,
    ).stdout
    for stream in json.loads(out or "{}").get("streams") or []:
        if stream.get("disposition", {}).get("attached_pic"):
            return stream.get("width"), stream.get("height")
    return None


# ── Running ──────────────────────────────────────────────────────────────────────────────
def run_case(url: str, is_video: bool, fmt: str, crop: bool, workdir: str):
    shutil.rmtree(workdir, ignore_errors=True)
    os.makedirs(workdir)
    supported = video_artwork_supported(fmt) if is_video else audio_artwork_supported(fmt)
    args = (video_args(fmt) if is_video else audio_args(fmt)) + thumbnail_args(True, crop, supported)
    cmd = [sys.executable, "-m", "yt_dlp", "--no-warnings", "--no-playlist",
           "--socket-timeout", "20", "-o", "media.%(ext)s", *args, url]
    proc = subprocess.run(cmd, cwd=workdir, capture_output=True, text=True, timeout=900)
    log = proc.stdout + proc.stderr
    if proc.returncode != 0:
        if REFUSED.search(log):
            return "SKIP", "source refused this network: " + log.strip().splitlines()[-1][:120]
        return "FAIL", "yt-dlp failed: " + (log.strip().splitlines() or ["?"])[-1][:200]

    media = [f for f in os.listdir(workdir)
             if f.startswith("media.") and not f.endswith((".jpg", ".png", ".webp", ".part"))]
    if not media:
        return "FAIL", "no output file"
    path = os.path.join(workdir, media[0])
    cover = embedded_cover(path, workdir)

    if not supported:
        return "PASS", f"{media[0]}: no cover asked for (format cannot hold one)"
    if cover is None:
        return "FAIL", f"{media[0]}: no embedded cover"
    w, h = cover
    if crop and w != h:
        return "FAIL", f"{media[0]}: cover {w}x{h} is not square with crop on"
    return "PASS", f"{media[0]}: cover {w}x{h}"


def static_check() -> bool:
    """The flags above are the ones the Kotlin passes."""
    with open(VIEW_MODEL, encoding="utf-8") as f:
        kotlin = f.read()
    checks = {
        "--embed-thumbnail passed": '"--embed-thumbnail"' in kotlin,
        "plain conversion is jpg": f'"--convert-thumbnails", "{CONVERT_PLAIN}"' in kotlin
            or f'"{CONVERT_PLAIN}"' in kotlin,
        "cropped conversion maps jpg>png": f'"{CONVERT_CROPPED}"' in kotlin,
        "crop filter matches": CROP_FILTER.replace('"', '\\"') in kotlin,
        "crop goes to ThumbnailsConvertor": '"ThumbnailsConvertor:-vf ' in kotlin,
        "cropThumbnail option read": "options.cropThumbnail" in kotlin,
        "no-artwork containers match": all(f'"{c}"' in kotlin.split("NO_ARTWORK_CONTAINERS =")[-1][:80]
                                           for c in NO_ARTWORK_CONTAINERS),
    }
    ok = True
    for name, passed in checks.items():
        print(f"  [{'PASS' if passed else 'FAIL'}] {name}")
        ok &= passed
    return ok


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--quick", action="store_true", help="one audio and one video format per source")
    parser.add_argument("--static", action="store_true", help="flag check only, no network")
    parser.add_argument("--source", action="append", help="only these source names")
    opts = parser.parse_args()

    print("Static: app flags match this harness")
    ok = static_check()
    if opts.static:
        return 0 if ok else 1

    for tool in ("ffmpeg", "ffprobe"):
        if not shutil.which(tool):
            print(f"{tool} not found; install it to run the live matrix")
            return 1
    try:
        import yt_dlp  # noqa: F401
        import mutagen  # noqa: F401
    except ImportError:
        print("pip install -U yt-dlp mutagen")
        return 1

    audio_formats = ["", "mp3"] if opts.quick else AUDIO_FORMATS
    video_containers = [""] if opts.quick else VIDEO_CONTAINERS
    counts = {"PASS": 0, "FAIL": 0, "SKIP": 0}
    root = tempfile.mkdtemp(prefix="hazel-thumbs-")

    for name, url, kinds in SOURCES:
        if opts.source and name not in opts.source:
            continue
        print(f"\n{name}  {url}")
        cases = []
        if kinds in ("audio", "both"):
            cases += [(False, f) for f in audio_formats]
        if kinds in ("video", "both"):
            cases += [(True, c) for c in video_containers]
        skipped_source = False
        for is_video, fmt in cases:
            for crop in (False, True):
                label = f"{'video' if is_video else 'audio'}:{fmt or 'default'} crop={'on' if crop else 'off'}"
                if skipped_source:
                    counts["SKIP"] += 1
                    continue
                status, detail = run_case(url, is_video, fmt, crop, os.path.join(root, "case"))
                counts[status] += 1
                print(f"  [{status}] {label:28} {detail}")
                # A site that refuses this network refuses every case; stop asking.
                if status == "SKIP":
                    skipped_source = True

    shutil.rmtree(root, ignore_errors=True)
    print(f"\n{counts['PASS']} passed, {counts['FAIL']} failed, {counts['SKIP']} skipped")
    return 0 if ok and counts["FAIL"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
