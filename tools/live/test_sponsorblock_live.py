#!/usr/bin/env python3
"""
Live SponsorBlock harness: runs the real yt-dlp and ffmpeg with the SponsorBlock and chapter
flags DownloadViewModel builds, and checks what comes out.

  * On a YouTube video with community segments, removing categories really shortens the
    file by about the length of those segments, for audio and for video.
  * Marking (the Chapters option on a video) writes the segments as chapters.
  * On any other site SponsorBlock has no data, and the download still succeeds untouched:
    yt-dlp skips it rather than failing.
  * A download with a cookie file (a signed-in site) behaves the same. Incognito adds no
    download flags at all, which the static check confirms.

Without the network it checks that the category ids the app offers are ones yt-dlp accepts,
and that the flags below are the ones the Kotlin passes.

Needs network, yt-dlp and ffmpeg/ffprobe, so it is not part of test_all.py:

    pip install -U yt-dlp
    python tools/live/test_sponsorblock_live.py            # full run
    python tools/live/test_sponsorblock_live.py --static   # flag check only, no network

A source the network refuses (YouTube asking a server address to sign in, say) is
reported as SKIP rather than FAIL: that says nothing about the flags.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
KOTLIN = os.path.join(ROOT, "app/src/main/java/com/hazel/android/download")
VIEW_MODEL = os.path.join(KOTLIN, "DownloadViewModel.kt")
OPTIONS = os.path.join(KOTLIN, "DownloadOptions.kt")

API_URL = "https://sponsor.ajay.app"
TOLERANCE_S = 3.0

# YouTube videos with community segments (short, so the run stays quick), then sources
# SponsorBlock knows nothing about.
YOUTUBE = ["lZ0dUuapvtI", "QmxcvJCrJlg"]
OTHER_SOURCES = [
    ("soundcloud", "https://soundcloud.com/forss/flickermood", False),
    ("dailymotion", "https://www.dailymotion.com/video/x8j7s5d", True),
]

REFUSED = re.compile(
    r"sign in|not a bot|logged-in|page needs to be reloaded|geo.?restrict|"
    r"HTTP Error 4(01|03|29)|Unable to download webpage",
    re.I,
)


def app_categories() -> list:
    with open(OPTIONS, encoding="utf-8") as f:
        text = f.read()
    block = text.split("val CATEGORIES")[1].split(")\n    )")[0]
    return re.findall(r'"([a-z_]+)" to "', block)


# ── The app's flags, kept in step with applyChapters / applySponsorBlock ────────────────
def sponsor_args(filters: list, add_chapters: bool, is_video: bool) -> list:
    args = []
    if is_video and add_chapters:
        args += ["--sponsorblock-mark", "all", "--embed-chapters"]
    if filters:
        args += ["--sponsorblock-remove", ",".join(filters)]
    if filters or add_chapters:
        args += ["--sponsorblock-api", API_URL]
    return args


def media_args(is_video: bool) -> list:
    if is_video:
        return ["-f", "bv*+ba/b", "-S", "res:240,+size", "--merge-output-format", "mp4"]
    return ["-f", "ba/b", "-x"]


def segments(video_id: str, categories: list):
    """Seconds the chosen categories cover (overlaps merged), and the video's length."""
    query = urllib.request.quote(json.dumps(categories))
    url = f"{API_URL}/api/skipSegments?videoID={video_id}&categories={query}"
    with urllib.request.urlopen(url, timeout=20) as r:
        data = json.load(r)
    skips = [s for s in data if s.get("actionType", "skip") == "skip"]
    length = max((float(s.get("videoDuration") or 0) for s in skips), default=0.0)
    spans = sorted(s["segment"] for s in skips)
    total, end = 0.0, -1.0
    for a, b in spans:
        if a > end:
            total += b - a
            end = b
        elif b > end:
            total += b - end
            end = b
    return total, length


def stand_in(video_id: str, length: float, is_video: bool, root: str) -> str:
    """
    A local file standing in for a YouTube video this network may not fetch: same id, same
    length, served as a file:// URL through an info record that says it is YouTube. yt-dlp
    then asks the real SponsorBlock API for this id and cuts the real segments, so
    everything after the fetch is the same path the app takes.
    """
    name = f"{video_id}_{'v' if is_video else 'a'}"
    media = os.path.join(root, name + (".mp4" if is_video else ".m4a"))
    if not os.path.exists(media):
        inputs = ["-f", "lavfi", "-i", f"sine=frequency=440:duration={length}"]
        if is_video:
            inputs = ["-f", "lavfi", "-i", f"testsrc=size=320x240:rate=25:duration={length}"] + inputs
            codecs = ["-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "aac"]
        else:
            codecs = ["-c:a", "aac"]
        subprocess.run(["ffmpeg", "-v", "error", "-y", *inputs, *codecs, "-shortest", media], check=True)
    fmt = {"format_id": "local", "url": "file://" + media, "ext": os.path.splitext(media)[1][1:],
           "acodec": "aac", "vcodec": "h264" if is_video else "none"}
    if is_video:
        fmt.update(width=320, height=240)
    info = {"id": video_id, "title": "stand-in", "extractor": "youtube", "extractor_key": "Youtube",
            "webpage_url": f"https://www.youtube.com/watch?v={video_id}",
            "webpage_url_basename": "watch", "duration": length, "formats": [fmt]}
    path = os.path.join(root, name + ".info.json")
    with open(path, "w") as f:
        json.dump(info, f)
    return path


def probe(path: str) -> dict:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_format", "-show_chapters", "-of", "json", path],
        capture_output=True, text=True,
    ).stdout
    return json.loads(out or "{}")


def download(url: str, args: list, workdir: str, cookies: str = None, info_json: str = None):
    shutil.rmtree(workdir, ignore_errors=True)
    os.makedirs(workdir)
    cmd = [sys.executable, "-m", "yt_dlp", "--no-warnings", "--no-playlist",
           "--socket-timeout", "20", "-o", "media.%(ext)s", *args]
    if cookies:
        cmd += ["--cookies", cookies]
    if info_json:
        cmd += ["--enable-file-urls", "--load-info-json", info_json]
    else:
        cmd.append(url)
    proc = subprocess.run(cmd, cwd=workdir, capture_output=True, text=True, timeout=900)
    log = proc.stdout + proc.stderr
    # YouTube asks some networks to sign in, depending on the client. Removal and
    # marking happen after the download and do not depend on the client, so the harness
    # (not the app) retries with one that is usually let through.
    if proc.returncode != 0 and not info_json and REFUSED.search(log) and "youtube.com" in url:
        cmd[3:3] = ["--extractor-args", "youtube:player_client=android_vr"]
        proc = subprocess.run(cmd, cwd=workdir, capture_output=True, text=True, timeout=900)
        log = proc.stdout + proc.stderr
    if proc.returncode != 0:
        status = "SKIP" if REFUSED.search(log) else "FAIL"
        return status, (log.strip().splitlines() or ["?"])[-1][:200], None
    files = [f for f in os.listdir(workdir) if f.startswith("media.")
             and not f.endswith((".part", ".jpg", ".png", ".webp", ".ytdl"))]
    if not files:
        return "FAIL", "no output file", None
    return "OK", log, os.path.join(workdir, files[0])


def static_check() -> bool:
    with open(VIEW_MODEL, encoding="utf-8") as f:
        kotlin = f.read()
    help_text = subprocess.run([sys.executable, "-m", "yt_dlp", "--help"],
                               capture_output=True, text=True).stdout
    accepted = set()
    m = re.search(r"--sponsorblock-mark CATS(.*?)--sponsorblock-remove", help_text, re.S)
    if m:
        accepted = set(re.findall(r"[a-z_]+", m.group(1).split("Available categories are")[-1]))
    cats = app_categories()
    apply_block = kotlin.split("private fun YoutubeDLRequest.applySponsorBlock")[1].split("\n    }\n")[0]
    chapter_block = kotlin.split("private fun YoutubeDLRequest.applyChapters")[1].split("\n    }\n")[0]
    checks = {
        f"app offers {len(cats)} categories": len(cats) >= 9,
        "every category is one yt-dlp accepts": bool(accepted) and all(c in accepted for c in cats),
        "removal passes --sponsorblock-remove": '"--sponsorblock-remove"' in apply_block,
        "API endpoint passed": '"--sponsorblock-api"' in apply_block and f'"{API_URL}"' in open(OPTIONS, encoding="utf-8").read(),
        "chapters mark all and embed": '"--sponsorblock-mark", "all"' in chapter_block and '"--embed-chapters"' in chapter_block,
        "incognito adds no download flags": "incognito" not in (apply_block + chapter_block).lower(),
    }
    ok = True
    for name, passed in checks.items():
        print(f"  [{'PASS' if passed else 'FAIL'}] {name}")
        ok &= passed
    return ok


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--static", action="store_true")
    opts = parser.parse_args()

    print("Static: app flags and categories")
    ok = static_check()
    if opts.static:
        return 0 if ok else 1
    for tool in ("ffmpeg", "ffprobe"):
        if not shutil.which(tool):
            print(f"{tool} not found")
            return 1

    cats = app_categories()
    root = tempfile.mkdtemp(prefix="hazel-sb-")
    work = os.path.join(root, "case")
    # A cookie file in the format the app writes, standing in for a signed-in site.
    cookies = os.path.join(root, "cookies.txt")
    with open(cookies, "w") as f:
        f.write("# Netscape HTTP Cookie File\n"
                ".youtube.com\tTRUE\t/\tTRUE\t2147483647\tPREF\thl=en\n")
    counts = {"PASS": 0, "FAIL": 0, "SKIP": 0}

    def report(status, label, detail):
        counts[status] += 1
        print(f"  [{status}] {label:40} {detail}")

    for vid in YOUTUBE:
        url = f"https://www.youtube.com/watch?v={vid}"
        print(f"\nyoutube {vid}")
        try:
            cut, length = segments(vid, cats)
        except Exception as e:  # noqa: BLE001
            report("SKIP", "segments", f"API unreachable: {e}")
            continue
        for is_video in (False, True):
            kind = "video" if is_video else "audio"
            source = None
            status, detail, plain = download(url, media_args(is_video), work)
            if status == "SKIP" and length > 0:
                print(f"  (YouTube refused this network; using a local {length:.1f}s stand-in with the real id)")
                source = stand_in(vid, length, is_video, root)
                status, detail, plain = download(url, media_args(is_video), work, info_json=source)
            if status != "OK":
                report(status, f"{kind} baseline", detail)
                continue
            base = float(probe(plain)["format"]["duration"])

            for label, cookie in (("remove all", None), ("remove all + cookies", cookies)):
                status, detail, path = download(
                    url, media_args(is_video) + sponsor_args(cats, is_video, is_video), work, cookie,
                    info_json=source)
                if status != "OK":
                    report(status, f"{kind} {label}", detail)
                    continue
                info = probe(path)
                got = float(info["format"]["duration"])
                expected = base - cut
                good = abs(got - expected) <= TOLERANCE_S and got < base - 1
                report("PASS" if good else "FAIL", f"{kind} {label}",
                       f"{base:.1f}s -> {got:.1f}s (segments {cut:.1f}s)")

            if is_video:
                status, detail, path = download(
                    url, media_args(True) + sponsor_args([], True, True), work, info_json=source)
                if status != "OK":
                    report(status, "video mark as chapters", detail)
                else:
                    chapters = probe(path).get("chapters") or []
                    report("PASS" if len(chapters) >= 2 else "FAIL", "video mark as chapters",
                           f"{len(chapters)} chapters")

    for name, url, is_video in OTHER_SOURCES:
        print(f"\n{name}")
        kind = "video" if is_video else "audio"
        for label, cookie in (("remove all", None), ("remove all + cookies", cookies)):
            status, detail, path = download(
                url, media_args(is_video) + sponsor_args(cats, True, is_video), work, cookie)
            if status != "OK":
                report(status, f"{kind} {label}", detail)
                continue
            skipped = "SponsorBlock is not supported" in detail or "not supported for" in detail
            report("PASS", f"{kind} {label}",
                   "downloaded" + (", SponsorBlock skipped for this site" if skipped else ""))

    shutil.rmtree(root, ignore_errors=True)
    print(f"\n{counts['PASS']} passed, {counts['FAIL']} failed, {counts['SKIP']} skipped")
    return 0 if ok and counts["FAIL"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
