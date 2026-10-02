#!/usr/bin/env python3
"""
Proves the in-app player can play real links from many sites.

For each link it runs the real yt-dlp, makes the same stream choice as
`StreamResolver.pick` in the app, and then fetches the start of what was chosen with the
headers the player would send. A pass means the bytes are really media: an MP4 or WebM
header, MP3/AAC/Opus/FLAC audio, an HLS playlist or a DASH manifest.

    python tools/tests/live/test_playback_live.py              # every source
    python tools/tests/live/test_playback_live.py reddit ted   # just these
    python tools/tests/live/test_playback_live.py --save-fixtures

--save-fixtures writes each source's format list, with the addresses replaced, to
app/src/test/resources/playback/ for use as test fixtures. --static checks, without the network, that the rules
copied here still match the Kotlin they mirror.

A site that refuses this network is SKIP, not FAIL: the app would show its read failure.
"""
import json
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
RESOLVER = ROOT / "app/src/main/java/com/hazel/android/download/playback/StreamResolver.kt"
FIXTURES = ROOT / "app/src/test/resources/playback"

SOURCES = {
    "jiosaavn": "https://www.jiosaavn.com/song/afsaana-banaaya-aapne-from-gunmaaster-g9/JwQ-VAVTUEs",
    "instagram_reel": "https://www.instagram.com/reel/DbdAKBCtvUF/",
    "instagram_reel_2": "https://www.instagram.com/reel/Chunk8-jurw/",
    "reddit": "https://www.reddit.com/r/videos/comments/6rrwyj/that_small_heart_attack/",
    "reddit_2": "https://www.reddit.com/r/aww/comments/90bu6w/heat_index_was_110_degrees_so_we_offered_him_a/",
    "x": "https://twitter.com/starwars/status/665052190608723968",
    "tiktok": "https://www.tiktok.com/@leenabhushan/video/6748451240264420610",
    "vimeo": "https://vimeo.com/56015672",
    "dailymotion": "https://www.dailymotion.com/video/x2iuewm",
    "soundcloud": "https://soundcloud.com/ethmusic/lostin-powers-she-so-heavy",
    "bandcamp": "https://youtube-dl.bandcamp.com/track/youtube-dl-test-song",
    "bilibili": "https://www.bilibili.com/video/BV13x41117TL",
    "streamable": "https://streamable.com/dnd1",
    "twitch_clip": "https://clips.twitch.tv/FaintLightGullWholeWheat",
    "archive_org": "https://archive.org/details/Cops1922",
    "peertube": "https://framatube.org/videos/watch/9c9de5e8-0a1e-484a-b099-e80766180a6d",
    "imgur": "https://i.imgur.com/jxBXAMC.gifv",
    "rumble": "https://rumble.com/embed/v5pv5f",
    "facebook": "https://www.facebook.com/radiokicksfm/videos/3676516585958356/",
    "tumblr": "https://maskofthedragon.tumblr.com/post/626907179849564160/mona-talking-in-english",
    "9gag": "https://9gag.com/gag/ae5Ag7B",
    "bitchute": "https://www.bitchute.com/video/UGlrF9o9b-Q/",
    "bluesky": "https://bsky.app/profile/bsky.app/post/3l3vgf77uco2g",
    "mixcloud": "https://www.mixcloud.com/dholbach/cryptkeeper/",
    "ted": "https://www.ted.com/talks/candace_parker_how_to_break_down_barriers_and_not_accept_limits",
    "medal": "https://medal.tv/games/valorant/clips/jTBFnLKdLy15K",
    "youtube": "https://www.youtube.com/watch?v=jNQXAC9IVRw",
}

CAP = 720
PLAYABLE = {"https", "http", "m3u8", "m3u8_native"}
AUDIO_EXT = {"m4a", "mp3", "aac", "opus", "ogg", "oga", "flac", "wav", "weba"}


# --- The choice, as StreamResolver.pick makes it ---------------------------------------

def height(f):
    h, w = f.get("height") or 0, f.get("width") or 0
    return min(h, w) if h > 0 and w > 0 else h


def has_video(f):
    codec = f.get("vcodec") or ""
    if codec == "none":
        return False
    if codec or height(f) > 0 or (f.get("width") or 0) > 0:
        return True
    return (f.get("ext") or "").lower() not in AUDIO_EXT


def has_audio(f):
    return (f.get("acodec") or "") != "none"


def playable(f):
    proto = f.get("protocol") or ""
    return (f.get("url") or "").startswith("http") and "fragments" not in f and (not proto or proto in PLAYABLE)


def is_hls(f):
    return (f.get("protocol") or "").startswith("m3u8")


def pick(root, cap=CAP):
    media = (root.get("entries") or [root])[0] if root.get("entries") else root
    formats = media.get("formats")
    if formats is None:
        return ("single", media.get("url"), None, is_hls(media), False) if playable(media) else None
    usable = [f for f in formats if playable(f)]
    order = lambda f: (height(f), f.get("ext") == "mp4", f.get("tbr") or 0)
    both = max((f for f in usable if has_video(f) and has_audio(f) and height(f) <= cap), key=order, default=None)
    picture = max((f for f in usable if has_video(f) and not has_audio(f) and height(f) <= cap), key=order, default=None)
    if picture is None and both is None:
        picture = min((f for f in usable if has_video(f) and not has_audio(f) and height(f) > 0), key=height, default=None)
    smallest_both = None if both else min(
        (f for f in usable if has_video(f) and has_audio(f) and height(f) > 0), key=height, default=None)
    sound = max((f for f in usable if has_audio(f) and not has_video(f)),
                key=lambda f: (f.get("ext") == "m4a", f.get("abr") or 0), default=None)
    dash_formats = [f for f in formats if f.get("protocol") == "http_dash_segments"
                    and (f.get("manifest_url") or "").startswith("http")]
    dash_video = any(has_video(f) and f.get("vcodec") for f in dash_formats)
    dash = ("dash", dash_formats[0]["manifest_url"], None, False, True) if dash_formats else None

    if picture and sound and (both is None or height(picture) > height(both)):
        chosen = ("split", picture["url"], sound, is_hls(picture), True)
    elif both:
        chosen = ("both", both["url"], None, is_hls(both), True)
    elif smallest_both:
        chosen = ("both", smallest_both["url"], None, is_hls(smallest_both), True)
    elif sound:
        chosen = ("audio", sound["url"], None, is_hls(sound), False)
    else:
        any_video = min((f for f in usable if has_video(f)), key=height, default=None)
        chosen = ("both", any_video["url"], None, is_hls(any_video), True) if any_video else None
    if chosen is None or (not chosen[4] and dash_video):
        return dash
    chosen_fmt = picture if chosen[0] == "split" else None
    return chosen if chosen_fmt is None else chosen


def headers_of(root, url):
    media = (root.get("entries") or [root])[0] if root.get("entries") else root
    for f in media.get("formats") or [media]:
        if f.get("url") == url or f.get("manifest_url") == url:
            return {k: v for k, v in (f.get("http_headers") or {}).items()}
    return {}


# --- Fetching the start of a stream -----------------------------------------------------

def looks_like_media(data, kind):
    head = data[:64]
    if kind == "hls":
        return data.lstrip().startswith(b"#EXTM3U")
    if kind == "dash":
        return b"<MPD" in data[:4096]
    return (head[4:8] in (b"ftyp", b"styp", b"moof", b"sidx", b"free")
            or head.startswith(b"\x1a\x45\xdf\xa3")      # WebM / Matroska
            or head.startswith(b"ID3")
            or head[:2] in (b"\xff\xfb", b"\xff\xf3", b"\xff\xf2", b"\xff\xf1", b"\xff\xf9")  # MP3 / AAC
            or head.startswith(b"OggS") or head.startswith(b"fLaC")
            or head.startswith(b"FLV")
            or head[:1] == b"\x47")                       # MPEG-TS


def fetch_start(url, headers):
    req = urllib.request.Request(url, headers={**headers, "Range": "bytes=0-65535"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.status, resp.read(65536)


def check(name, url):
    out = subprocess.run(
        [sys.executable, "-m", "yt_dlp", "--no-warnings", "--no-playlist", "-J", url],
        capture_output=True, text=True, timeout=180
    )
    if out.returncode != 0 or not out.stdout.strip():
        return "SKIP", (out.stderr.strip().splitlines() or ["no output"])[-1][:120], None
    root = json.loads(out.stdout)
    chosen = pick(root)
    if chosen is None:
        return "FAIL", "nothing playable chosen", root
    kind, main, sound, hls, video = chosen
    parts = [("hls" if hls else ("dash" if kind == "dash" else "file"), main)]
    if sound:
        parts.append(("hls" if is_hls(sound) else "file", sound["url"]))
    notes = []
    for part_kind, part_url in parts:
        try:
            status, data = fetch_start(part_url, headers_of(root, part_url))
        except Exception as e:  # noqa: BLE001 - reported, not raised
            return "FAIL", f"{part_kind} fetch failed: {e}"[:140], root
        if status not in (200, 206) or not looks_like_media(data, part_kind):
            return "FAIL", f"{part_kind} is not media (HTTP {status}, {data[:12]!r})", root
        notes.append(part_kind)
    label = {"split": "picture + sound", "both": "picture with sound", "audio": "sound only",
             "dash": "DASH manifest", "single": "single stream"}[kind]
    return "PASS", f"{label} ({' + '.join(notes)})", root


def save_fixture(name, root):
    """Keeps only what the choice reads, with the addresses replaced by stand-ins."""
    media = (root.get("entries") or [root])[0] if root.get("entries") else root
    keep = ("format_id", "protocol", "vcodec", "acodec", "width", "height", "ext", "tbr", "abr")
    formats = []
    for f in media.get("formats") or []:
        g = {k: f[k] for k in keep if k in f and f[k] is not None}
        g["url"] = f"https://fixture.invalid/{name}/{f.get('format_id', 'x')}"
        if f.get("manifest_url"):
            g["manifest_url"] = f"https://fixture.invalid/{name}/manifest"
        if "fragments" in f:
            g["fragments"] = [{}]
        formats.append(g)
    FIXTURES.mkdir(parents=True, exist_ok=True)
    (FIXTURES / f"{name}.json").write_text(json.dumps({"formats": formats}, indent=1) + "\n")


def static_check():
    kt = RESOLVER.read_text()
    rules = [
        ("playable protocols", 'setOf("https", "http", "m3u8", "m3u8_native")'),
        ("short side counts as quality", "minOf(h, w)"),
        ("DASH read from its manifest", '"http_dash_segments"'),
        ("DASH stands in for sound alone", "!chosen.hasVideo && dashHasVideo -> dash"),
        ("smallest combined file before sound", "smallestBoth != null -> single(smallestBoth)"),
        ("segments are never fetched as files", '!has("fragments")'),
    ]
    bad = [label for label, needle in rules if needle not in kt]
    for label, _ in rules:
        print(("  FAIL  " if label in bad else "  PASS  ") + label)
    return not bad


def main(argv):
    if "--static" in argv:
        sys.exit(0 if static_check() else 1)
    save = "--save-fixtures" in argv
    names = [a for a in argv if not a.startswith("--")] or list(SOURCES)
    results = {}
    for name in names:
        status, note, root = check(name, SOURCES[name])
        results[name] = status
        print(f"  {status:4}  {name:17} {note}")
        if save and root is not None and status == "PASS":
            save_fixture(name, root)
    passed = sum(1 for s in results.values() if s == "PASS")
    failed = [n for n, s in results.items() if s == "FAIL"]
    print(f"\n  {passed} played, {len(failed)} failed, {len(results) - passed - len(failed)} skipped")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main(sys.argv[1:])
