#!/usr/bin/env python3
"""
Live format-fallback harness: proves a download still works when the format id picked at
read time is gone by download time.

yt-dlp names a YouTube stream by which player clients answered a run (616 in one run,
616-hls in the next), and 2026.10.21 added tv_samsung as a default client, so the read a
sheet or Hazel Instant does and the download that follows can disagree. Before the fix
every expression the app built hinged on that one id, and the download failed with
"Requested format is not available".

For every source this script:
  * reads the link as MediaProbe does (one yt-dlp run, a JSON dump),
  * picks what the app would: the tallest video under the ceiling with an audio id, a
    combined stream where the source has no separate video, and the best audio stream,
  * in a second yt-dlp run, resolves the expressions DownloadViewModel builds for those
    ids, both as read and with each id renamed so it no longer exists,
  * checks the renamed case fails the old expressions and resolves with the new ones,
    near the chosen height, and downloads it for real, checking picture and sound.

    python tools/tests/live/test_format_fallback_live.py           # full run
    python tools/tests/live/test_format_fallback_live.py --quick   # no real downloads
    python tools/tests/live/test_format_fallback_live.py --static  # wiring check only

Needs the yt-dlp and ffmpeg/ffprobe executables on the PATH. A site that refuses the
network the run is on is reported as SKIP, not FAIL.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
VIEW_MODEL = os.path.join(ROOT, "app/src/main/java/com/hazel/android/download/DownloadViewModel.kt")

# ── The app's expressions, kept word for word with DownloadViewModel ───────────────────
NEAREST_VIDEO = "bv*+ba/b"
NEAREST_AUDIO = "ba/b"
HEIGHT = 360


def video_only_old(vid, aid):
    return (f"{vid}+{aid}/" if aid else "") + f"{vid}+bestaudio/{vid}"


def video_only_new(vid, aid):
    return video_only_old(vid, aid) + "/" + NEAREST_VIDEO


def muxed_old(fid):
    return fid


def muxed_new(fid):
    return f"{fid}/{NEAREST_VIDEO}"


def audio_old(fid):
    return fid


def audio_new(fid):
    return f"{fid}/{NEAREST_AUDIO}"


SITES = [
    ("youtube (reported)", "https://www.youtube.com/watch?v=oTGrqDlW1LU"),
    ("youtube", "https://www.youtube.com/watch?v=jNQXAC9IVRw"),
    ("youtube shorts", "https://www.youtube.com/shorts/tPEE9ZwTmy0"),
    ("vimeo", "https://vimeo.com/76979871"),
    ("dailymotion", "https://www.dailymotion.com/video/x8j7s5d"),
    ("soundcloud", "https://soundcloud.com/forss/flickermood"),
    ("bandcamp", "https://youtube-dl.bandcamp.com/track/youtube-dl-test-song"),
    ("youtube music", "https://music.youtube.com/watch?v=jNQXAC9IVRw"),
    ("ted", "https://www.ted.com/talks/ken_robinson_do_schools_kill_creativity"),
    ("jiosaavn", "https://www.jiosaavn.com/song/tum-hi-ho/EToxUyFpcwQ"),
]

REFUSED = re.compile(
    r"sign in|not a bot|logged-in|cookies|geo.?restrict|not available in your country|"
    r"HTTP Error 4(01|03|29)|Unable to download|Unable to extract|blocked|login|Unsupported URL", re.I)

results = []


def report(status, name, detail=""):
    results.append(status)
    print(f"  {status:4} {name}" + (f"  ({detail})" if detail else ""))


def run(args, timeout=300):
    p = subprocess.run(["yt-dlp", *args], capture_output=True, text=True,
                       encoding="utf-8", errors="replace", timeout=timeout)
    return p.returncode, p.stdout, p.stderr


def read(url):
    code, out, err = run(["--dump-single-json", "--no-playlist", "--no-warnings",
                          "--no-check-certificates", "--skip-download",
                          "--compat-options", "manifest-filesize-approx", url])
    if code != 0:
        raise RuntimeError(err.strip().splitlines()[-1] if err.strip() else "read failed")
    return json.loads(out)


def resolve(url, selector, sort=None):
    """Format selection only, in its own run, as the download would do it."""
    args = ["--no-playlist", "--no-warnings", "--no-check-certificates", "--simulate",
            "--print", "%(format_id)s|%(height)s|%(vcodec)s|%(acodec)s", "-f", selector]
    if sort:
        args += ["-S", sort]
    code, out, err = run(args + [url])
    return code == 0, (out.strip() or err.strip().splitlines()[-1] if (out.strip() or err.strip()) else "")


def has(f, key):
    v = f.get(key)
    return v not in (None, "none")


def pick(info):
    fmts = [f for f in info.get("formats") or [] if f.get("format_id")]
    vonly = [f for f in fmts if has(f, "vcodec") and not has(f, "acodec") and (f.get("height") or 0) <= HEIGHT]
    muxed = [f for f in fmts if has(f, "vcodec") and has(f, "acodec") and (f.get("height") or 0) <= HEIGHT]
    aonly = [f for f in fmts if has(f, "acodec") and not has(f, "vcodec")]
    key = lambda f: ((f.get("height") or 0), f.get("tbr") or 0)
    akey = lambda f: (f.get("abr") or f.get("tbr") or 0)
    return (max(vonly, key=key) if vonly else None,
            max(muxed, key=key) if muxed else None,
            max(aonly, key=akey) if aonly else None)


def probe(path):
    p = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "stream=codec_type,height",
                        "-of", "json", path], capture_output=True, text=True)
    streams = json.loads(p.stdout or "{}").get("streams", [])
    kinds = {s["codec_type"] for s in streams}
    heights = [s.get("height") for s in streams if s["codec_type"] == "video" and s.get("height")]
    return kinds, max(heights) if heights else 0


def download(url, selector, sort, workdir, audio):
    out = os.path.join(workdir, "%(id)s.%(ext)s")
    args = ["--no-playlist", "--no-mtime", "--no-warnings", "--no-check-certificates",
            "-o", out, "-f", selector]
    if sort:
        args += ["-S", sort]
    if audio:
        args += ["-x"]
    else:
        args += ["--merge-output-format", "mp4"]
    code, _, err = run(args + [url], timeout=900)
    if code != 0:
        return None, err.strip().splitlines()[-1] if err.strip() else "download failed"
    files = [os.path.join(workdir, f) for f in os.listdir(workdir) if not f.endswith((".part", ".ytdl"))]
    return (max(files, key=os.path.getsize) if files else None), ""


def check_site(name, url, quick):
    print(f"\n{name}: {url}")
    try:
        info = read(url)
    except Exception as e:
        msg = str(e)
        report("SKIP" if REFUSED.search(msg) else "FAIL", "read", msg[:160])
        return
    vid, mux, aud = pick(info)
    sort = f"res:{HEIGHT}"

    cases = []
    if vid:
        aid = aud["format_id"] if aud else None
        cases.append((f"video-only as read ({vid['format_id']}+{aid})", video_only_new(vid["format_id"], aid), sort, True, False))
        gone_v, gone_a = vid["format_id"] + "-gone", (aid + "-gone") if aid else None
        cases.append(("video-only, ids gone, old", video_only_old(gone_v, gone_a), None, False, False))
        cases.append(("video-only, ids gone, new", video_only_new(gone_v, gone_a), sort, True, False))
    elif mux:
        cases.append(("combined as read", muxed_new(mux["format_id"]), sort, True, False))
        cases.append(("combined, id gone, old", muxed_old(mux["format_id"] + "-gone"), None, False, False))
        cases.append(("combined, id gone, new", muxed_new(mux["format_id"] + "-gone"), sort, True, False))
    if aud:
        cases.append((f"audio as read ({aud['format_id']})", audio_new(aud["format_id"]), None, True, True))
        cases.append(("audio, id gone, old", audio_old(aud["format_id"] + "-gone"), None, False, True))
        cases.append(("audio, id gone, new", audio_new(aud["format_id"] + "-gone"), None, True, True))
    if not cases:
        report("SKIP", "pick", "no formats listed")
        return

    for label, selector, s, expect_ok, audio in cases:
        ok, detail = resolve(url, selector, s)
        if ok != expect_ok:
            report("FAIL", label, f"{selector} -> {detail[:140]}")
            continue
        if not ok:
            report("PASS", label, "fails as it did in the app")
            continue
        fid, h = (detail.split("|") + ["", ""])[:2]
        h = int(h) if h.isdigit() else 0
        if not audio and h and h > HEIGHT and "gone" in label and (vid or mux):
            low = [f for f in info["formats"] if (f.get("height") or 0) and f["height"] <= HEIGHT]
            if low:
                report("FAIL", label, f"took {h}p over the {HEIGHT}p ceiling")
                continue
        report("PASS", label, f"{fid} {h or ''}".strip())

        if quick or "new" not in label:
            continue
        work = tempfile.mkdtemp(prefix="hazel_fallback_")
        try:
            path, err = download(url, selector, s, work, audio)
            if not path:
                report("SKIP" if REFUSED.search(err) else "FAIL", label + " download", err[:160])
                continue
            kinds, height = probe(path)
            want = {"audio"} if audio else {"video", "audio"}
            if audio and "video" in kinds and height:
                report("FAIL", label + " download", f"picture stream in audio file {kinds}")
            elif not want <= kinds and not (not audio and kinds == {"video"} and not aud):
                report("FAIL", label + " download", f"streams {kinds}")
            else:
                report("PASS", label + " download", f"{os.path.basename(path)} {sorted(kinds)} {height or ''}".strip())
        finally:
            shutil.rmtree(work, ignore_errors=True)


def static_check():
    print("static: expressions match DownloadViewModel")
    src = open(VIEW_MODEL, encoding="utf-8").read()
    for name, value in (("NEAREST_VIDEO", NEAREST_VIDEO), ("NEAREST_AUDIO", NEAREST_AUDIO)):
        report("PASS" if f'const val {name} = "{value}"' in src else "FAIL", name)
    needles = [
        'append("${format.selector}+bestaudio/${format.selector}/")',
        "append(NEAREST_VIDEO)",
        'addOption("-f", "${format.selector}/$NEAREST_VIDEO")',
        'addOption("-f", "${format.selector}/$NEAREST_AUDIO")',
        'addOption("-f", "${format.selector}/$languageFilter/$NEAREST_AUDIO")',
        '"res:$it"',
    ]
    for n in needles:
        report("PASS" if n in src else "FAIL", n)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--quick", action="store_true")
    ap.add_argument("--static", action="store_true")
    a = ap.parse_args()
    static_check()
    if not a.static:
        code, out, _ = run(["--version"])
        print(f"\nyt-dlp {out.strip()}")
        for name, url in SITES:
            check_site(name, url, a.quick)
    fails = results.count("FAIL")
    print(f"\n{results.count('PASS')} passed, {fails} failed, {results.count('SKIP')} skipped")
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
