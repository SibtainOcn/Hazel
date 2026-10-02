#!/usr/bin/env python3
"""
Live cover-art harness: runs the real yt-dlp and ffmpeg and checks that the thumbnail
options the app passes land in the file, for any source, for audio and for video.

For every source and every output format the sheet offers, it downloads with the same
flags DownloadViewModel builds, opens the result and checks:

  * the download succeeds: asking for a cover never fails a download,
  * a cover is embedded wherever the app asks for one,
  * with "Crop to square" on the cover is square, including when the artwork is already
    a JPEG (yt-dlp skips converting, and so cropping, a thumbnail already in the target
    format, which is why the app maps jpg>png while cropping),
  * with it off the cover keeps its own shape.

Sources are real sites plus local stand-ins in awkward formats (Theora in OGV, FLV, AVI,
MPEG-TS, single-file WebM; WAV, AIFF, WMA, AC3, MKA, Opus in WebM), served as file:// URLs
with artwork, so formats no reachable site happens to serve are covered too.

It also checks, without the network, that the flags below are the ones the Kotlin passes,
so the two cannot drift apart.

Needs yt-dlp, ffmpeg/ffprobe and mutagen, and the network for the real sites. Not part of
test_all.py; run it by hand when the download flags change:

    pip install -U yt-dlp mutagen
    python tools/tests/live/test_thumbnail_embed_live.py              # full matrix
    python tools/tests/live/test_thumbnail_embed_live.py --quick      # fewer formats per source
    python tools/tests/live/test_thumbnail_embed_live.py --local      # stand-ins only, no network
    python tools/tests/live/test_thumbnail_embed_live.py --static     # flag check only

A site that refuses the network the run is on (one asking a server address to sign in,
say) is reported as SKIP rather than FAIL: that says nothing about the flags.
"""
import argparse
import base64
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
VIEW_MODEL = os.path.join(ROOT, "app/src/main/java/com/hazel/android/download/DownloadViewModel.kt")

# ── The app's flags, kept word for word with DownloadViewModel ──────────────────────────
# yt-dlp splits --ppa values like a shell; the double quotes keep the single quotes that
# ffmpeg needs around the commas. Without them every cropped download fails.
CROP_FILTER = "crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\""
CROP_PPA = f"ThumbnailsConvertor:-vf {CROP_FILTER}"
CONVERT_PLAIN = "jpg"
CONVERT_CROPPED = "jpg>png/jpg"
COVER_SAFE_VIDEO = "mp4>mp4/mov>mov/mkv>mkv/mkv"
COVER_SAFE_AUDIO = "aiff>flac/wav>flac/wma>m4a/best"
NO_ARTWORK_CONTAINERS = {"webm", "avi", "flv"}

# Output formats the sheet offers ("" is Default).
AUDIO_FORMATS = ["", "mp3", "m4a", "aac", "alac", "flac", "opus", "vorbis", "wav"]
VIDEO_CONTAINERS = ["", "mp4", "mkv", "mov", "webm", "avi", "flv"]

# Real sites covering square and wide artwork, JPEG and WebP/PNG artwork, music and video.
SITES = [
    ("soundcloud", "https://soundcloud.com/forss/flickermood", "audio"),
    ("jiosaavn", "https://www.jiosaavn.com/song/tum-hi-ho/EToxUyFpcwQ", "audio"),
    ("archive.org", "https://archive.org/details/Popeye_forPresident", "both"),
    ("dailymotion", "https://www.dailymotion.com/video/x8j7s5d", "both"),
    ("youtube", "https://www.youtube.com/watch?v=jNQXAC9IVRw", "both"),
]

# Local stand-ins: (name, extension, ffmpeg codec arguments, has video).
STAND_INS = [
    ("ogv-theora", "ogv", ["-c:v", "libtheora", "-c:a", "libvorbis"], True),
    ("flv-h264", "flv", ["-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac"], True),
    ("avi-mpeg4", "avi", ["-c:v", "mpeg4", "-c:a", "libmp3lame"], True),
    ("ts-h264", "ts", ["-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac"], True),
    ("webm-vp9", "webm", ["-c:v", "libvpx-vp9", "-deadline", "realtime", "-c:a", "libopus"], True),
    ("wav", "wav", ["-c:a", "pcm_s16le"], False),
    ("aiff", "aiff", ["-c:a", "pcm_s16be"], False),
    ("wma", "wma", ["-c:a", "wmav2"], False),
    ("ac3", "ac3", ["-c:a", "ac3"], False),
    ("mka-vorbis", "mka", ["-c:a", "libvorbis"], False),
    ("webm-opus", "webm", ["-c:a", "libopus"], False),
]

REFUSED = re.compile(
    r"sign in|not a bot|logged-in|page needs to be reloaded|cookies|geo.?restrict|"
    r"HTTP Error 4(01|03|29)|Unable to download webpage|Unsupported URL|not available",
    re.I,
)


def app_args(is_video: bool, chosen: str, embed: bool = True, crop: bool = False) -> list:
    """What DownloadViewModel passes for the format, container and thumbnail options."""
    chosen = "" if chosen == "default" else chosen
    if is_video:
        # A generic "best" row: two streams where the site has them, so a merge.
        args = ["-f", "bv*+ba/b", "-S", "res:360,+size", "--merge-output-format", chosen or "mp4"]
    else:
        args = ["-f", "ba/b", "-x"]
        if chosen and chosen != "webm":
            args += ["--audio-format", chosen]
    wants_cover = embed and (chosen not in NO_ARTWORK_CONTAINERS if is_video else chosen != "wav")
    if wants_cover:
        if is_video:
            args += ["--remux-video", "mkv" if chosen == "mkv" else COVER_SAFE_VIDEO]
        elif not chosen or chosen == "webm":
            args += ["--audio-format", COVER_SAFE_AUDIO]
        args += ["--embed-thumbnail"]
        if crop:
            args += ["--convert-thumbnails", CONVERT_CROPPED, "--ppa", CROP_PPA]
        else:
            args += ["--convert-thumbnails", CONVERT_PLAIN]
    return args


def wants_cover(is_video: bool, chosen: str) -> bool:
    return chosen not in NO_ARTWORK_CONTAINERS if is_video else chosen != "wav"


# ── Reading the cover back out of the file ───────────────────────────────────────────────
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


def image_size(data: bytes, workdir: str):
    path = os.path.join(workdir, "cover.bin")
    with open(path, "wb") as f:
        f.write(data)
    return probe_size(path)


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
        # Newer ffmpeg shows an image attachment as an attached picture stream instead;
        # that is read below.

    import mutagen
    from mutagen.flac import Picture

    media = mutagen.File(path) if ext not in ("mkv", "mka", "webm") else None
    tags = media.tags if media is not None else None

    if ext == "mp3" and tags is not None:
        for key in tags.keys():
            if key.startswith("APIC"):
                return image_size(tags[key].data, workdir)
    if ext in ("m4a", "mp4", "m4v", "mov") and tags is not None and "covr" in tags:
        return image_size(bytes(tags["covr"][0]), workdir)
    if ext == "flac" and media is not None and getattr(media, "pictures", None):
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


# ── Local stand-in sources ───────────────────────────────────────────────────────────────
def build_stand_in(name: str, ext: str, codecs: list, has_video: bool, root: str) -> str:
    """A short file in an awkward format with wide JPEG artwork, as an info record."""
    media = os.path.join(root, f"{name}.{ext}")
    art = os.path.join(root, "art.jpg")
    if not os.path.exists(art):
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i",
                        "testsrc=size=640x360:duration=1", "-frames:v", "1", art], check=True)
    inputs = ["-f", "lavfi", "-i", "sine=frequency=440:duration=3"]
    if has_video:
        inputs = ["-f", "lavfi", "-i", "testsrc=size=320x240:rate=25:duration=3"] + inputs
    subprocess.run(["ffmpeg", "-v", "error", "-y", *inputs, *codecs, "-shortest", media], check=True)
    fmt = {"format_id": "local", "url": "file://" + media, "ext": ext,
           "acodec": "unknown", "vcodec": "unknown" if has_video else "none"}
    info = {"id": name, "title": name, "extractor": "generic", "extractor_key": "Generic",
            "webpage_url": "file://" + media, "formats": [fmt],
            "thumbnails": [{"id": "0", "url": "file://" + art}]}
    path = os.path.join(root, f"{name}.info.json")
    with open(path, "w") as f:
        json.dump(info, f)
    return path


# ── Running ──────────────────────────────────────────────────────────────────────────────
def run_case(source: str, is_video: bool, fmt: str, crop: bool, workdir: str, local: bool):
    shutil.rmtree(workdir, ignore_errors=True)
    os.makedirs(workdir)
    cmd = [sys.executable, "-m", "yt_dlp", "--no-warnings", "--no-playlist",
           "--socket-timeout", "20", "-o", "media.%(ext)s", *app_args(is_video, fmt, True, crop)]
    cmd += ["--enable-file-urls", "--load-info-json", source] if local else [source]
    proc = subprocess.run(cmd, cwd=workdir, capture_output=True, text=True, timeout=900)
    log = proc.stdout + proc.stderr
    if proc.returncode != 0:
        if not local and REFUSED.search(log):
            return "SKIP", "source refused this network: " + log.strip().splitlines()[-1][:120]
        return "FAIL", "yt-dlp failed: " + (log.strip().splitlines() or ["?"])[-1][:200]

    media = [f for f in os.listdir(workdir)
             if f.startswith("media.") and not f.endswith((".jpg", ".png", ".webp", ".part"))]
    if not media:
        return "FAIL", "no output file"
    path = os.path.join(workdir, media[0])
    if not wants_cover(is_video, fmt):
        return "PASS", f"{media[0]}: no cover asked for (format cannot hold one)"
    cover = embedded_cover(path, workdir)
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
        "plain conversion is jpg": f'"--convert-thumbnails", "{CONVERT_PLAIN}"' in kotlin,
        "cropped conversion maps jpg>png": f'"{CONVERT_CROPPED}"' in kotlin,
        "crop filter matches": CROP_FILTER.replace('"', '\\"') in kotlin,
        "crop goes to ThumbnailsConvertor": '"ThumbnailsConvertor:-vf ' in kotlin,
        "cropThumbnail option read": "options.cropThumbnail" in kotlin,
        "video remux rules match": f'"{COVER_SAFE_VIDEO}"' in kotlin,
        "audio extraction rules match": f'"{COVER_SAFE_AUDIO}"' in kotlin,
        "no-artwork containers match": all(
            f'"{c}"' in kotlin.split("NO_ARTWORK_CONTAINERS =")[-1][:80] for c in NO_ARTWORK_CONTAINERS),
    }
    ok = True
    for name, passed in checks.items():
        print(f"  [{'PASS' if passed else 'FAIL'}] {name}")
        ok &= passed
    return ok


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--quick", action="store_true", help="fewer formats per source")
    parser.add_argument("--static", action="store_true", help="flag check only, no network")
    parser.add_argument("--local", action="store_true", help="local stand-ins only, no network")
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
    video_containers = ["", "mkv"] if opts.quick else VIDEO_CONTAINERS
    counts = {"PASS": 0, "FAIL": 0, "SKIP": 0}
    root = tempfile.mkdtemp(prefix="hazel-thumbs-")
    work = os.path.join(root, "case")

    sources = []
    for name, ext, codecs, has_video in STAND_INS:
        sources.append((f"local {name}", (name, ext, codecs, has_video), "video" if has_video else "audio", True))
    if not opts.local:
        sources += [(name, url, kinds, False) for name, url, kinds in SITES]

    for name, source, kinds, local in sources:
        if opts.source and not any(s in name for s in opts.source):
            continue
        print(f"\n{name}")
        if local:
            source = build_stand_in(*source, root)
        cases = []
        if kinds in ("audio", "both"):
            cases += [(False, f) for f in audio_formats]
        # A video file can be downloaded as audio too; a stand-in is one or the other.
        if kinds in ("video", "both"):
            cases += [(True, c) for c in video_containers]
            if local:
                cases += [(False, f) for f in audio_formats]
        refused = False
        for is_video, fmt in cases:
            for crop in (False, True):
                label = f"{'video' if is_video else 'audio'}:{fmt or 'default'} crop={'on' if crop else 'off'}"
                if refused:
                    counts["SKIP"] += 1
                    continue
                status, detail = run_case(source, is_video, fmt, crop, work, local)
                counts[status] += 1
                print(f"  [{status}] {label:28} {detail}")
                # A site that refuses this network refuses every case; stop asking.
                refused = status == "SKIP"

    shutil.rmtree(root, ignore_errors=True)
    print(f"\n{counts['PASS']} passed, {counts['FAIL']} failed, {counts['SKIP']} skipped")
    return 0 if ok and counts["FAIL"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
