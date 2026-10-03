#!/usr/bin/env python3
"""
Live best / worst harness: runs the real yt-dlp with the expressions the format list's
Best quality and Worst quality rows hand to a download, and checks that what yt-dlp picks
is genuinely the best or the worst the link offers.

  * Video: Best (`bv*+ba/b`) gets the tallest picture the link has, Worst (`wv*+wa/w`) the
    shortest, each with sound.
  * Audio: Best (`ba/b`) gets the highest audio bitrate, Worst (`wa/w`) the lowest.
  * With a preferred codec, the app adds a sort (`res,vcodec:h264`, `abr,acodec:opus`):
    the codec only decides between streams of that quality and never costs resolution or
    bitrate, for Best and for Worst.
  * Both ways a download reads a link: replaying the read the sheet made (`--load-info-json`,
    the usual case) and reading it again (incognito, where no read is kept on disk, or a
    read that has expired). Incognito adds no download flags of its own; the static check
    confirms that.
  * With a cookie file (`--cookies`, a signed-in site) the choice is the same. A real
    sign-in can be given with HAZEL_COOKIES=<Netscape cookie file>; otherwise a file with
    a harmless cookie for the site stands in, which proves the flag changes nothing about
    the choice.

Nothing is downloaded: yt-dlp is asked which formats it would take (`-J`).

Needs network and yt-dlp, so it is not part of test_all.py:

    pip install -U yt-dlp
    python tools/tests/live/test_best_worst_live.py            # full run
    python tools/tests/live/test_best_worst_live.py --static   # flag check only, no network

A source the network refuses is reported as SKIP rather than FAIL: that says nothing about
the expressions.
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
KOTLIN = os.path.join(ROOT, "app/src/main/java/com/hazel/android/download")

# Short videos with several qualities, from sources that read differently.
LINKS = [
    ("youtube", "https://www.youtube.com/watch?v=jNQXAC9IVRw", ".youtube.com"),
    ("vimeo", "https://vimeo.com/76979871", ".vimeo.com"),
    ("dailymotion", "https://www.dailymotion.com/video/x8j7s5d", ".dailymotion.com"),
]

REFUSED = re.compile(
    r"sign in|not a bot|logged-in|page needs to be reloaded|geo.?restrict|"
    r"HTTP Error 4(01|03|29)|Unable to download webpage|Unable to extract|timed out|"
    r"only works when logged-in",
    re.I,
)

PASS = FAIL = SKIP = 0


def ok(name):
    global PASS
    PASS += 1
    print(f"  PASS  {name}")


def bad(name, detail=""):
    global FAIL
    FAIL += 1
    print(f"  FAIL  {name}")
    if detail:
        print(f"        {detail}")


def skip(name, why):
    global SKIP
    SKIP += 1
    print(f"  SKIP  {name}: {why}")


def check(name, cond, detail=""):
    ok(name) if cond else bad(name, detail)


def read(path):
    with open(os.path.join(KOTLIN, path), encoding="utf-8") as f:
        return f.read()


def selector_of(source, format_id):
    """The selector of the MediaFormat whose formatId is [format_id] in [source]."""
    m = re.search(r'formatId = "' + re.escape(format_id) + r'",\s*selector = "([^"]+)"', source)
    return m.group(1) if m else None


# ---------------------------------------------------------------------------
# What the app sends
# ---------------------------------------------------------------------------

def app_expressions():
    probe = read("MediaProbe.kt")
    info = read("MediaInfo.kt")
    audio = read("BatchAudioFormats.kt")
    return {
        "best video": selector_of(probe, "best"),
        "worst video": selector_of(info, "worst"),
        "best audio": selector_of(probe, "bestaudio"),
        "worst audio": selector_of(audio, "worstaudio"),
    }


def test_static():
    print("\n--- Static: the expressions and flags the app passes ---")
    ex = app_expressions()
    check("Best video row asks for bv*+ba/b", ex["best video"] == "bv*+ba/b", repr(ex["best video"]))
    check("Worst video row asks for wv*+wa/w", ex["worst video"] == "wv*+wa/w", repr(ex["worst video"]))
    check("Best audio row asks for ba/b", ex["best audio"] == "ba/b", repr(ex["best audio"]))
    check("Worst audio row asks for wa/w", ex["worst audio"] == "wa/w", repr(ex["worst audio"]))

    vm = read("DownloadViewModel.kt")
    generic = vm[vm.find("format.isGeneric -> {"):][:1400]
    check("A generic row's expression goes to -f as it is", 'addOption("-f", format.selector)' in generic)
    check("A video codec preference sorts after resolution",
          '"${format.sort ?: "res"},$codecSort"' in generic and '"vcodec:${it.sortKey}"' in generic)
    check("An audio codec preference sorts after bitrate",
          '"${format.sort ?: "abr"},$codecSort"' in generic and '"acodec:${it.sortKey}"' in generic)
    check("A replayed read is handed back with --load-info-json",
          'addOption("--load-info-json", cached.absolutePath)' in vm)
    build = vm[vm.find("private fun buildRequest"):] if "private fun buildRequest" in vm else vm
    check("Incognito adds no download flags", "incognito" not in build[:12000].lower())
    return ex


# ---------------------------------------------------------------------------
# What yt-dlp picks
# ---------------------------------------------------------------------------

def run(args, timeout=180):
    p = subprocess.run(["yt-dlp", "--no-warnings", *args], capture_output=True, text=True,
                       encoding="utf-8", errors="replace", timeout=timeout)
    return p.returncode, p.stdout, p.stderr


def picked(info):
    """The formats a selection resolved to: one, or the pair to merge."""
    return info.get("requested_formats") or [info]


def has_video(f):
    return (f.get("vcodec") or "none") != "none"


def has_audio(f):
    # YouTube's HLS audio (233, 234) names no codec, but is audio all the same.
    return (f.get("acodec") or "none") != "none" or f.get("resolution") == "audio only"


def heights(formats):
    return [f["height"] for f in formats if has_video(f) and f.get("height")]


def audio_only(formats):
    return [f for f in formats if has_audio(f) and not has_video(f)]


def bitrates(formats):
    return [f["abr"] for f in audio_only(formats) if f.get("abr")]


def describe(fs):
    return " + ".join(f"{f.get('format_id')}({f.get('height') or ''}p {f.get('vcodec')}/{f.get('acodec')} "
                      f"abr={f.get('abr')})" for f in fs)


def choose(source_args, selector, sort=None, cookies=None):
    args = ["-J", "-f", selector, *source_args]
    if sort:
        args[0:0] = ["-S", sort]
    if cookies:
        args[0:0] = ["--cookies", cookies]
    code, out, err = run(args)
    if code != 0:
        return None, err.strip().splitlines()[-1] if err.strip() else f"exit {code}"
    return json.loads(out), None


def verify(label, all_formats, chosen, kind):
    fs = picked(chosen)
    video = [f for f in fs if has_video(f)]
    audio = [f for f in fs if has_audio(f)]
    hs, rs = heights(all_formats), bitrates(all_formats)

    if kind in ("best video", "worst video"):
        if not hs:
            return skip(label, "the link reports no heights")
        want = max(hs) if kind == "best video" else min(hs)
        got = video[0].get("height") if video else None
        check(f"{label}: {got}p, the {'tallest' if kind == 'best video' else 'shortest'} is {want}p",
              got == want, describe(fs))
        check(f"{label}: has sound", bool(audio), describe(fs))
    else:
        chosen_audio = audio_only(fs)
        offered = audio_only(all_formats)
        if not offered or not chosen_audio:
            # A source with no separate audio answers with a file that has it, by design.
            return check(f"{label}: falls back to a file with sound", bool(audio), describe(fs))
        best = kind == "best audio"
        got = chosen_audio[0]
        tiers = [f["quality"] for f in offered if f.get("quality") is not None]
        if tiers and got.get("quality") is not None:
            # The source's own ranking where it gives one (YouTube does): Opus 106 kbps and
            # AAC 130 kbps share the top tier, and the HLS streams with no bitrate sit at
            # the bottom. Within a tier yt-dlp prefers the better codec.
            want = max(tiers) if best else min(tiers)
            return check(f"{label}: {got['format_id']} is in the {'top' if best else 'bottom'} quality tier ({want})",
                         got["quality"] == want, describe(fs))
        if not rs or got.get("abr") is None:
            return skip(label, f"picked {describe(fs)}; the source ranks nothing to compare")
        want = max(rs) if best else min(rs)
        close = abs(got["abr"] - want) <= max(2.0, want * 0.05)
        check(f"{label}: {got['abr']:.0f} kbps, the {'highest' if best else 'lowest'} is {want:.0f}",
              close, describe(fs))


def cookie_file(tmp, domain):
    real = os.environ.get("HAZEL_COOKIES")
    if real and os.path.isfile(real):
        return real
    path = os.path.join(tmp, f"cookies{domain}.txt")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("# Netscape HTTP Cookie File\n")
        f.write(f"{domain}\tTRUE\t/\tTRUE\t2147483647\thazel_harness\t1\n")
    return path


def test_live(ex):
    if not shutil.which("yt-dlp"):
        print("\n  yt-dlp is not on the PATH; live checks need it.")
        return
    code, out, _ = run(["--version"])
    print(f"\n  yt-dlp {out.strip()}")

    with tempfile.TemporaryDirectory() as tmp:
        for name, url, domain in LINKS:
            print(f"\n--- Live: {name} ---")
            code, out, err = run(["-J", url])
            if code != 0:
                why = err.strip().splitlines()[-1] if err.strip() else f"exit {code}"
                skip(name, why if REFUSED.search(why) else f"read failed: {why}")
                continue
            info = json.loads(out)
            formats = info.get("formats") or []
            print(f"  {len(formats)} formats, heights {sorted(set(heights(formats)))}, "
                  f"audio kbps {sorted(round(r) for r in set(bitrates(formats)))}")
            replay = os.path.join(tmp, f"{name}.info.json")
            with open(replay, "w", encoding="utf-8") as f:
                f.write(out)
            cookies = cookie_file(tmp, domain)

            ways = [
                ("replayed read", ["--load-info-json", replay], None),
                ("fresh read (incognito)", [url], None),
                ("replayed read, cookies", ["--load-info-json", replay], cookies),
                ("fresh read, cookies", [url], cookies),
            ]
            sorts = {
                "best video": [None, "res,vcodec:h264", "res,vcodec:vp9"],
                "worst video": [None, "res,vcodec:h264"],
                "best audio": [None, "abr,acodec:opus", "abr,acodec:aac"],
                "worst audio": [None, "abr,acodec:aac"],
            }
            for way, source_args, cookie in ways:
                for kind, selector in ex.items():
                    # Every sort on the replayed read; the plain expression on the rest,
                    # which keeps the run to a few minutes.
                    for sort in sorts[kind] if way == "replayed read" else [None]:
                        label = f"{way}, {kind}" + (f", -S {sort}" if sort else "")
                        chosen, why = choose(source_args, selector, sort, cookie)
                        if chosen is None:
                            if REFUSED.search(why or ""):
                                skip(label, why)
                            else:
                                bad(label, why)
                            continue
                        verify(label, formats, chosen, kind)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--static", action="store_true", help="flag check only, no network")
    args = parser.parse_args()

    print("=" * 70)
    print("  Hazel Best / Worst Live Harness")
    print("=" * 70)
    ex = test_static()
    if not args.static:
        test_live(ex)

    print("\n" + "=" * 70)
    print(f"  PASSED: {PASS}   FAILED: {FAIL}   SKIPPED: {SKIP}")
    print("=" * 70)
    return 0 if FAIL == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
