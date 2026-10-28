#!/usr/bin/env python3
"""
Live shared-link harness: reads real links the way Hazel Instant now does and checks that
each card it would queue downloads exactly one item, and a different one from every other.

Two kinds of link went wrong before:

  * A post holding several videos (an X post with two clips, or a quoted clip beside its
    own). yt-dlp lists the videos with no address of their own: every one carries the
    post's. Instant queued one download per video, all of the same address, and with
    --no-playlist each of them still took the whole post (yt-dlp ignores --no-playlist on
    a post link that names no /video/N). The sheet merged them into one card, which is why
    the same link worked there.
  * A YouTube video shared from inside a Mix (watch?v=ID&list=RD...): read as the Mix, so
    Instant queued every video in it.

What this script runs, mirrored from the Kotlin:

  * UrlExtractor.youTubeVideoOnly on the shared link,
  * MediaProbe.listOne's read (--flat-playlist --lazy-playlist --dump-single-json, no
    --no-playlist), then its numbering of a post's items (PostItem: the address plus
    #hazel-item=N, N being playlist_index),
  * the download request DownloadViewModel.buildDownloadRequest builds for each card: the
    entry's own payload through --load-info-json where it was listed with formats, else the
    address with --no-playlist and --playlist-items N,

and asks yt-dlp what each request would download (--simulate). Every request must give one
id, and the ids of one link must all differ. With --download it also fetches the second
item of a multi-video post with Instant's video flags and checks the file has picture and
sound.

It also shows the old behaviour, for comparison: the post's address with --no-playlist.

    python tools/tests/live/test_shared_links_live.py              # read and simulate
    python tools/tests/live/test_shared_links_live.py --download   # plus one real download
    python tools/tests/live/test_shared_links_live.py --static     # wiring check only

Needs the yt-dlp executable (and ffmpeg/ffprobe for --download). A site that refuses the
network the run is on is SKIP, not FAIL.
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

MARK = "#hazel-item="
PLAYLIST_PARAMS = ("list", "index", "start_radio", "pp")
YOUTUBE_ID = re.compile(r"(?:youtu\.be/|youtube\.com/(?:embed/|v/|watch\?v=|watch\?.+&v=|shorts/))([\w-]{11})", re.I)

LINKS = [
    # (name, shared link, items expected, whether several are expected)
    ("x post, 2 videos", "https://x.com/DavidToons_/status/1578353380363501568", 2),
    ("x post, 4 videos", "https://x.com/UltimaShadowX/status/1577719286659006464", 4),
    ("x post, 1 video", "https://x.com/oshtru/status/1577855540407197696", 1),
    ("x post, 1 video (twitter.com)", "https://twitter.com/LisPower1/status/1001551623938805763", 1),
    ("youtube from a Mix (issue 126)",
     "https://m.youtube.com/watch?v=EU1KWuGWy1o&list=RDEU1KWuGWy1o&start_radio=1&pp=ygULRmxhdmlhIHRhc2igBwE%3D", 1),
    ("youtube video", "https://www.youtube.com/watch?v=jNQXAC9IVRw", 1),
    ("youtube share form", "https://youtu.be/jNQXAC9IVRw?si=abc123", 1),
]

REFUSED = re.compile(
    r"sign in|not a bot|logged-in|cookies|geo.?restrict|HTTP Error 4(01|03|29)|"
    r"Unable to download (webpage|JSON)|timed out|rate.?limit", re.I)

results = []


def record(status: str, name: str, detail: str = ""):
    results.append(status)
    print(f"  [{status}] {name}" + (f"  ({detail})" if detail else ""))


# ── The Kotlin, mirrored ─────────────────────────────────────────────────────────────────
def youtube_video_only(url: str) -> str:
    """UrlExtractor.youTubeVideoOnly."""
    trimmed = url.strip()
    if not YOUTUBE_ID.search(trimmed) or "?" not in trimmed:
        return url
    q = trimmed.index("?")
    f = trimmed.find("#", q)
    f = len(trimmed) if f < 0 else f
    params = [p for p in trimmed[q + 1:f].split("&") if p]
    if not any(p.startswith("list=") for p in params):
        return url
    kept = [p for p in params if not any(p == k or p.startswith(k + "=") for k in PLAYLIST_PARAMS)]
    return trimmed[:q] + ("?" + "&".join(kept) if kept else "") + trimmed[f:]


def post_item(url: str, index: int) -> str:
    return url.split(MARK)[0] + MARK + str(index)


def item_of(url: str):
    tail = url.split(MARK, 1)[1] if MARK in url else ""
    return int(tail) if tail.isdigit() and int(tail) > 0 else None


def ytdlp(args: list, timeout: int = 120):
    proc = subprocess.run(["yt-dlp", "--no-warnings", "--socket-timeout", "20", *args],
                          capture_output=True, text=True, encoding="utf-8", errors="replace",
                          timeout=timeout)
    return proc.returncode, proc.stdout, proc.stderr


def list_cards(url: str, workdir: str):
    """MediaProbe.listOne and DownloadViewModel.expand: the cards a link gives."""
    code, out, err = ytdlp(["--flat-playlist", "--lazy-playlist", "--dump-single-json", url])
    if code != 0 or not out.strip():
        raise RuntimeError(err.strip().splitlines()[-1] if err.strip() else "no metadata")
    root = json.loads(out)
    entries = root.get("entries")
    if root.get("_type") != "playlist" or entries is None:
        return [{"url": url, "payload": None}], root

    read = []
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            continue
        address = next((entry.get(k) for k in ("webpage_url", "url", "original_url")
                        if (entry.get(k) or "").startswith("http")), None)
        if address:
            read.append((index, entry, address))
    post = root.get("webpage_url") or url
    sharing = {}
    for _, _, address in read:
        sharing[address] = sharing.get(address, 0) + 1

    cards = []
    for index, entry, address in read:
        own = sharing[address] < 2 and address != post
        if len(read) < 2 or own:
            cards.append({"url": address, "payload": None})
            continue
        place = entry.get("playlist_index") or (index + 1)
        payload = None
        if entry.get("formats"):
            payload = os.path.join(workdir, f"item{place}.info.json")
            with open(payload, "w", encoding="utf-8") as f:
                json.dump(entry, f)
        cards.append({"url": post_item(address, place), "payload": payload})
    # Instant's distinctBy { it.url }.
    seen, unique = set(), []
    for card in cards:
        if card["url"] not in seen:
            seen.add(card["url"])
            unique.append(card)
    return unique, root


def download_request(card: dict) -> list:
    """DownloadViewModel.buildDownloadRequest plus buildRequest's --no-playlist."""
    if card["payload"]:
        return ["--load-info-json", card["payload"], "--no-playlist"]
    args = [card["url"].split(MARK)[0], "--no-playlist"]
    item = item_of(card["url"])
    if item:
        args += ["--playlist-items", str(item)]
    return args


def simulate_ids(args: list):
    code, out, err = ytdlp([*args, "--simulate", "--print", "id"])
    if code != 0:
        raise RuntimeError(err.strip().splitlines()[-1] if err.strip() else "simulate failed")
    return [line.strip() for line in out.splitlines() if line.strip()]


# ── Runs ─────────────────────────────────────────────────────────────────────────────────
def run_link(name: str, shared: str, expected: int, workdir: str):
    print(f"\n{name}\n  {shared}")
    url = youtube_video_only(shared)
    if url != shared:
        print(f"  read as {url}")
    try:
        cards, root = list_cards(url, workdir)
    except Exception as e:  # noqa: BLE001
        msg = str(e)
        record("SKIP" if REFUSED.search(msg) else "FAIL", f"{name}: read", msg[:160])
        return
    record("PASS" if len(cards) == expected else "FAIL",
           f"{name}: {len(cards)} card(s), {expected} expected")

    ids = []
    for card in cards:
        try:
            got = simulate_ids(download_request(card))
        except Exception as e:  # noqa: BLE001
            msg = str(e)
            record("SKIP" if REFUSED.search(msg) else "FAIL", f"{name}: {card['url']}", msg[:160])
            return
        record("PASS" if len(got) == 1 else "FAIL",
               f"{name}: {card['url']} downloads {len(got)} item(s)", ",".join(got))
        ids += got
    if len(cards) > 1:
        record("PASS" if len(set(ids)) == len(ids) else "FAIL",
               f"{name}: every card downloads a different item", ",".join(ids))
        # The old request for comparison: the post's address with --no-playlist.
        old = simulate_ids([url, "--no-playlist"])
        print(f"  before the fix each of the {len(cards)} cards fetched {len(old)} item(s): {','.join(old)}")


def run_download(workdir: str):
    print("\nreal download: second video of a two-video post, Instant's video flags")
    url = "https://x.com/DavidToons_/status/1578353380363501568"
    try:
        cards, _ = list_cards(url, workdir)
    except Exception as e:  # noqa: BLE001
        record("SKIP", "download: read", str(e)[:160])
        return
    if len(cards) < 2:
        record("FAIL", "download: post has two cards")
        return
    out = os.path.join(workdir, "dl")
    os.makedirs(out, exist_ok=True)
    args = download_request(cards[1]) + [
        "-f", "bv*+ba/b", "-S", "res:360", "--merge-output-format", "mp4",
        "-o", os.path.join(out, "%(id)s.%(ext)s")]
    code, _, err = ytdlp(args, timeout=300)
    files = [f for f in os.listdir(out) if f.endswith(".mp4")]
    if code != 0:
        record("SKIP" if REFUSED.search(err) else "FAIL", "download ran", err.strip()[-160:])
        return
    record("PASS" if len(files) == 1 else "FAIL", f"download: one file ({','.join(files)})")
    if files:
        probe = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "stream=codec_type",
                                "-of", "json", os.path.join(out, files[0])],
                               capture_output=True, text=True).stdout
        kinds = {s.get("codec_type") for s in json.loads(probe or "{}").get("streams") or []}
        record("PASS" if {"video", "audio"} <= kinds else "FAIL", f"download: picture and sound ({sorted(kinds)})")


def run_static():
    print("\nwiring (no network)")

    def read(rel):
        with open(os.path.join(KOTLIN, rel), encoding="utf-8") as f:
            return f.read()

    post = read("util/PostItem.kt")
    probe = read("download/MediaProbe.kt")
    vm = read("download/DownloadViewModel.kt")
    urls = read("util/UrlExtractor.kt")
    batch = read("ui/screens/download/batch/BatchDownloadState.kt")
    queue = read("ui/screens/queue/QueueScreen.kt")
    checks = [
        ("the item mark matches", f'MARK = "{MARK}"' in post),
        ("the playlist params match", 'listOf("list", "index", "start_radio", "pp")' in urls),
        ("reads narrow to the item", 'PostItem.indexOf(url)?.let { addOption("--playlist-items", it.toString()) }' in probe),
        ("reads ask the engine for the post's address", "YoutubeDLRequest(PostItem.address(url))" in probe),
        ("items are numbered by playlist_index", 'json.optInt("playlist_index")' in probe),
        ("a one-item wrapper keeps its address", "if (read.size < 2 || ownAddress) return@map entry" in probe),
        ("downloads narrow to the item", "YoutubeDLRequest(PostItem.address(url)).apply" in vm
         and 'addOption("--playlist-items", it.toString())' in vm),
        ("downloads keep --no-playlist", 'addOption("--no-playlist")' in vm),
        ("listed formats are kept", "InfoCache.metadataFor(entry.url) ?: MediaProbe.pendingFor(entry)" in vm),
        ("Instant reads the video alone", "UrlExtractor.youTubeVideoOnly(sharedUrl)" in vm),
        ("Instant drops repeats and reads in full", ".distinctBy { it.url }.let { readInFull(it) }" in vm),
        ("the sheet downloads the ticked links", "targets.mapNotNull { info ->" in batch),
        ("Running shows links being read", "if (item.reading && shown.add(item.url))" in queue),
    ]
    for name, ok in checks:
        record("PASS" if ok else "FAIL", name)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--static", action="store_true")
    parser.add_argument("--download", action="store_true")
    args = parser.parse_args()

    run_static()
    if not args.static:
        if not shutil.which("yt-dlp"):
            print("yt-dlp is not on the PATH")
            return 1
        print("\nyt-dlp " + subprocess.run(["yt-dlp", "--version"], capture_output=True, text=True).stdout.strip())
        workdir = tempfile.mkdtemp(prefix="hazel_shared_links_")
        try:
            for name, link, expected in LINKS:
                run_link(name, link, expected, workdir)
            if args.download:
                run_download(workdir)
        finally:
            shutil.rmtree(workdir, ignore_errors=True)

    passed, failed, skipped = (results.count(s) for s in ("PASS", "FAIL", "SKIP"))
    print("\n" + "=" * 70)
    print(f"  PASSED: {passed}   FAILED: {failed}   SKIPPED: {skipped}")
    print("=" * 70)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
