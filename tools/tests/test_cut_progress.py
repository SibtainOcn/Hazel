#!/usr/bin/env python3
"""
Test Harness: a cut (trimmed) download shows real progress, in the card and the shade.

Background (issue #161): a cut is passed to yt-dlp as `--download-sections`, which hands the
transfer to ffmpeg. ffmpeg's progress is on stderr, which the engine does not forward, and yt-dlp
prints no `[download]  42.5%` line for it, so the percentage never arrived and the bar sat at its
start while the file was being written. The fix measures the file ffmpeg is writing against the
size the kept part should come to, on a small watcher thread.

Verifies:
1. The size estimate (a Python port of CutProgress.expectedBytes, case for case with
   CutProgressTest.kt): scales by the kept share, clamps a cut past the end, and returns 0 for
   anything it cannot know (so nothing is ever guessed).
2. A simulated run, using the same tick rules as the watcher: the bar only moves forward, stays
   short of whole, never throws on odd input, and is left unmeasured (-1 in the shade) when there
   is no estimate.
3. The Kotlin source is wired as designed: the watcher starts only for a measurable cut, is
   stopped and joined in a `finally`, both run sites still go through `executeYtDlp`, the uncut
   path is untouched, and the estimate is used for the run's expected total.
4. The Kotlin source parses structurally (balanced braces) and the Kotlin unit test exists.
5. Side files / stale part files: the media-only measure, tested on a real temp folder.
6. Real ffmpeg (skipped when ffmpeg is not installed): a stream-copy cut written the way
   yt-dlp's ffmpeg downloader writes it grows steadily, and lands near the estimate.

Run:
    python tools/tests/test_cut_progress.py
"""

import math
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
DL = REPO_ROOT / "app/src/main/java/com/hazel/android/download"
VM = DL / "DownloadViewModel.kt"
CUT = DL / "CutProgress.kt"
KT_TEST = REPO_ROOT / "app/src/test/java/com/hazel/android/download/CutProgressTest.kt"
MASTER = REPO_ROOT / "tools/tests/test_all.py"

PASS_COUNT = 0
FAIL_COUNT = 0
SKIP_COUNT = 0


def check(name, got, expected):
    global PASS_COUNT, FAIL_COUNT
    if got == expected:
        PASS_COUNT += 1
        print(f"  PASS  {name}")
    else:
        FAIL_COUNT += 1
        print(f"  FAIL  {name}")
        print(f"        Expected: {expected!r}")
        print(f"        Got:      {got!r}")


def check_true(name, condition):
    check(name, bool(condition), True)


def skip(name, why):
    global SKIP_COUNT
    SKIP_COUNT += 1
    print(f"  SKIP  {name}  ({why})")


def block(text, start, length):
    at = text.find(start)
    return text[at:at + length] if at >= 0 else ""


def function_body(text, signature):
    """The text of a Kotlin function from its signature to its matching closing brace."""
    at = text.find(signature)
    if at < 0:
        return ""
    open_at = text.find("{", at)
    depth = 0
    for i in range(open_at, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return text[at:i + 1]
    return ""


# --------------------------------------------------------------------------------------
# Python ports of the Kotlin logic. Kept literal so a reader can lay them side by side.
# --------------------------------------------------------------------------------------

def expected_bytes(source_bytes, duration_seconds, start, end):
    """Port of CutProgress.expectedBytes."""
    if source_bytes <= 0 or duration_seconds <= 0:
        return 0
    if not math.isfinite(start) or not math.isfinite(end):
        return 0
    if start < 0.0 or end <= start:
        return 0
    duration = float(duration_seconds)
    kept = min(end, duration) - min(start, duration)
    if kept <= 0.0:
        return 0
    return max(int(source_bytes * (kept / duration)), 1)


def fraction_on_disk(on_disk, expected):
    """Port of fractionOnDisk: capped short of whole."""
    if expected <= 0 or on_disk <= 0:
        return 0.0
    return min(max(on_disk / expected, 0.0), 0.99)


class Watcher:
    """Port of the watcher's tick: publishCutProgress + refreshFloorFromDisk, forward only."""

    def __init__(self, expected, start_floor=0.0):
        self.expected = expected
        self.floor = start_floor
        self.progress = start_floor
        self.is_downloading = True
        self.is_processing = False
        self.shade = []  # progress values posted to the shade

    def tick(self, on_disk):
        if not self.is_downloading or self.is_processing:
            return
        if self.expected <= 0:
            return
        self.floor = max(self.floor, fraction_on_disk(on_disk, self.expected))
        if self.floor > self.progress:
            self.progress = self.floor
        self.shade.append(int(self.floor * 100))


# --------------------------------------------------------------------------------------

def test_estimate():
    print("\n[1] Size estimate")
    H = 100_000_000
    check("a cut keeps its share (30 s of 120 s = a quarter)", expected_bytes(H, 120, 30.0, 60.0), 25_000_000)
    check("a cut of the whole link weighs the whole link", expected_bytes(H, 120, 0.0, 120.0), H)
    check("a cut past the end is measured to the end",
          expected_bytes(H, 120, 90.0, 400.0), expected_bytes(H, 120, 90.0, 120.0))
    check("unknown size gives 0", expected_bytes(0, 120, 0.0, 10.0), 0)
    check("negative size gives 0", expected_bytes(-1, 120, 0.0, 10.0), 0)
    check("unknown running time gives 0", expected_bytes(H, 0, 0.0, 10.0), 0)
    check("negative running time gives 0", expected_bytes(H, -5, 0.0, 10.0), 0)
    check("empty cut gives 0", expected_bytes(H, 120, 10.0, 10.0), 0)
    check("backwards cut gives 0", expected_bytes(H, 120, 30.0, 10.0), 0)
    check("negative start gives 0", expected_bytes(H, 120, -1.0, 10.0), 0)
    check("a cut wholly past the end gives 0", expected_bytes(H, 120, 200.0, 300.0), 0)
    check("NaN start gives 0", expected_bytes(H, 120, float("nan"), 10.0), 0)
    check("infinite end gives 0", expected_bytes(H, 120, 0.0, float("inf")), 0)
    check_true("a very short cut still has something to measure against",
               expected_bytes(1_000, 36_000, 0.0, 0.001) >= 1)
    twenty_gb = 20 * 1024 ** 3
    check("a 20 GB link does not overflow (Kotlin Long)",
          expected_bytes(twenty_gb, 7_200, 0.0, 3_600.0), twenty_gb // 2)
    check_true("result always fits a signed 64-bit Long",
               expected_bytes(2 ** 62, 1, 0.0, 1.0) <= 2 ** 63 - 1)


def test_simulated_run():
    print("\n[2] Simulated cut run")
    expected = expected_bytes(413_605_708, 120, 55.0, 100.0)  # ~155 MB cut
    w = Watcher(expected)
    final = int(expected * 1.04)  # real files land a little over the estimate
    steps = 40
    seen = []
    for i in range(1, steps + 1):
        w.tick(final * i // steps)
        seen.append(w.progress)
    check_true("the bar only moves forward", all(b >= a for a, b in zip(seen, seen[1:])))
    check_true("the bar leaves its start", seen[0] > 0.0)
    check_true("the bar stays short of whole", max(seen) <= 0.99)
    check_true("the shade percentages never fall", all(b >= a for a, b in zip(w.shade, w.shade[1:])))

    w = Watcher(expected)
    for on_disk in (0, expected // 2, expected // 4, expected // 2, int(expected * 3)):
        w.tick(on_disk)
    check_true("a smaller reading (file replaced) never pulls the bar back", w.progress >= 0.5)
    check("a file far over the estimate parks at 99%, not past whole", w.progress, 0.99)

    w = Watcher(expected)
    w.is_processing = True
    w.tick(expected)
    check("no tick moves the bar once the file is being worked on", w.progress, 0.0)

    w = Watcher(expected)
    w.is_downloading = False
    w.tick(expected)
    check("no tick moves the bar once the run is over (cancelled)", w.progress, 0.0)

    w = Watcher(0)
    w.tick(10_000_000)
    check("nothing to measure against: the bar is left alone", (w.progress, w.shade), (0.0, []))

    w = Watcher(expected)
    w.tick(0)
    check("an empty directory reads as the start", w.progress, 0.0)

    # The shade shows an unmeasured bar, not a frozen 0%, when there is no estimate.
    def shade_progress(cut_run, expected_total, percent):
        return -1 if (cut_run and expected_total <= 0) else int(percent)

    check("an unmeasurable cut shows an unmeasured bar in the shade", shade_progress(True, 0, 0.0), -1)
    check("a measurable cut shows its percentage", shade_progress(True, 5, 42.0), 42)
    check("an ordinary download is unchanged", shade_progress(False, 0, 42.0), 42)
    check("an ordinary download with no size is unchanged (still its own %)", shade_progress(False, 0, 0.0), 0)


def test_source_wiring():
    print("\n[3] Kotlin wiring")
    vm = VM.read_text(encoding="utf-8")
    cut = CUT.read_text(encoding="utf-8")

    # estimate guards mirror the port
    for guard in (
        "sourceBytes <= 0L || durationSeconds <= 0",
        "!startSeconds.isFinite() || !endSeconds.isFinite()",
        "startSeconds < 0.0 || endSeconds <= startSeconds",
        "minOf(endSeconds, duration) - minOf(startSeconds, duration)",
        "coerceAtLeast(1L)",
    ):
        check_true(f"CutProgress keeps the guard: {guard}", guard in cut)

    # run sites still go through executeYtDlp, and only the wrapper calls runYtDlp
    check("both run sites still call executeYtDlp", len(re.findall(r"\bexecuteYtDlp\(\s*\n?\s*buildRequest", vm)), 2)
    check("only the wrapper calls runYtDlp", len(re.findall(r"\brunYtDlp\(", vm)), 2)  # call + definition
    wrapper = function_body(vm, "private fun executeYtDlp(")
    check_true("watcher starts only for a measurable cut",
               "if (cutRun && expectedTotalBytes > 0L) startCutWatcher() else null" in wrapper)
    check_true("the run is inside try/finally", "try {" in wrapper and "finally {" in wrapper)
    check_true("the watcher is stopped in finally", re.search(r"finally\s*\{\s*watcher\?\.let \{ stopCutWatcher\(it\) \}", wrapper))
    check_true("the wrapper runs the original body", "runYtDlp(request)" in wrapper)

    run = function_body(vm, "private fun runYtDlp(")
    check_true("the original engine call is untouched", "YtDlpEngine.execute(request, processId)" in run)
    check_true("the callback keeps its own percentage for ordinary downloads", "else percent.toInt()" in run)
    check_true("the callback still applies the floor", "coerceAtLeast(progressFloor * 100f)" in run)

    # watcher lifecycle
    start = function_body(vm, "private fun startCutWatcher(")
    check_true("watcher is a daemon thread", "isDaemon = true" in start)
    check_true("watcher sleeps between ticks", "Thread.sleep(CUT_POLL_INTERVAL_MS)" in start)
    check_true("a failed tick cannot kill the thread or the app", "catch (e: Exception)" in start)
    check_true("interrupt ends the loop quietly", "catch (_: InterruptedException)" in start)
    stop = function_body(vm, "private fun stopCutWatcher(")
    check_true("stop interrupts then joins with a timeout",
               "watcher.interrupt()" in stop and "watcher.join(CUT_WATCHER_JOIN_MS)" in stop)
    check_true("stop keeps the caller's interrupt flag", "Thread.currentThread().interrupt()" in stop)

    publish = function_body(vm, "private fun publishCutProgress(")
    check_true("a tick does nothing once the run is over or processing",
               "!current.isDownloading || current.isProcessing" in publish)
    check_true("a tick does nothing without an estimate", "expectedTotalBytes <= 0L" in publish)
    check_true("a tick only moves the bar forward",
               "if (share > s.progress) s.copy(progress = share) else s" in publish)
    check_true("the shade keeps its pacing", "NOTIFICATION_INTERVAL_MS" in publish)

    # expected total
    check_true("the run's expected total uses the cut",
               "expectedTotalBytes = expectedTotalFor(plan, options)" in vm)
    check_true("cutRun is set before the expected total is worked out",
               vm.find("cutRun = options.hasSection") != -1
               and vm.find("cutRun = options.hasSection") < vm.find("expectedTotalBytes = expectedTotalFor(plan, options)"))
    total = function_body(vm, "private fun expectedTotalFor(")
    check_true("an uncut download returns the whole size as before", "if (!options.hasSection) return whole" in total)
    check_true("a cut is scaled by CutProgress", "CutProgress.expectedBytes(" in total)
    check_true("the size is still 0 when the format has none", "if (format.fileSizeBytes <= 0L) return 0L" in total)

    # the opening notification and the callback agree on an unmeasured cut
    check("unmeasuredCut is used at the opening notification and in the callback", vm.count("unmeasuredCut()"), 3)  # 2 uses + definition
    check_true("the watcher import is present", "import kotlin.concurrent.thread" in vm)
    check_true("the poll constants exist", "CUT_POLL_INTERVAL_MS = 1000L" in vm and "CUT_WATCHER_JOIN_MS = 2000L" in vm)

    # no user-facing text added in Kotlin (strings live in strings.xml)
    added_literals = re.findall(r'"([^"\n]{12,})"', publish + start + stop)
    check_true("no new on-screen text in the watcher", all("Cut progress read failed" in s or "hazel-cut-progress" in s or s == "Hazel" for s in added_literals))


def strip_kotlin(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r'"""(.*?)"""', '""', text, flags=re.S)
    text = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', text)
    text = re.sub(r"'(?:\\.|[^'\\\n])'", "''", text)
    text = re.sub(r"//[^\n]*", "", text)
    return text


def test_structure():
    print("\n[4] Structure")
    for path in (VM, CUT, KT_TEST):
        text = strip_kotlin(path.read_text(encoding="utf-8"))
        check(f"{path.name}: braces balance", text.count("{") - text.count("}"), 0)
        check(f"{path.name}: parentheses balance", text.count("(") - text.count(")"), 0)
    check_true("Kotlin unit test exists and covers overflow and bad input",
               KT_TEST.exists() and "does not overflow" in KT_TEST.read_text(encoding="utf-8")
               and "Double.NaN" in KT_TEST.read_text(encoding="utf-8"))
    check_true("package line matches the folder", CUT.read_text(encoding="utf-8").startswith("package com.hazel.android.download"))
    check_true("the master runner runs this harness", "test_cut_progress.py" in MASTER.read_text(encoding="utf-8"))


SIDE_FILE_EXTENSIONS = {
    "jpg", "jpeg", "png", "webp", "gif",
    "vtt", "srt", "ass", "ssa", "ttml", "srv1", "srv2", "srv3", "json3", "lrc", "sbv",
    "json", "description", "txt",
}


def media_bytes_in(directory, include_part_files=True):
    """Port of CutProgress.mediaBytesIn."""
    try:
        total = 0
        for f in Path(directory).iterdir():
            if not f.is_file() or f.name.endswith(".ytdl"):
                continue
            ext = f.suffix[1:].lower() if f.suffix else ""
            if ext in SIDE_FILE_EXTENSIONS:
                continue
            if not include_part_files and f.name.endswith(".part"):
                continue
            total += f.stat().st_size
        return total
    except OSError:
        return 0


def test_side_files():
    print("\n[5] Side files and stale part files (review fixes)")
    with tempfile.TemporaryDirectory() as tmp:
        d = Path(tmp)
        (d / "clip.webp").write_bytes(b"0" * 100)
        (d / "clip.en.vtt").write_bytes(b"0" * 50)
        (d / "clip.f137.mp4.part").write_bytes(b"0" * 30)
        check("a thumbnail and subtitles are not counted as the cut", media_bytes_in(d), 30)
        (d / "clip.f137.mp4.ytdl").write_bytes(b"0" * 500)
        check("engine bookkeeping never counts", media_bytes_in(d), 30)
    with tempfile.TemporaryDirectory() as tmp:
        d = Path(tmp)
        (d / "clip.f137.mp4").write_bytes(b"0" * 200)
        (d / "clip.f140.m4a.part").write_bytes(b"0" * 10)
        check("a finished stream and the part file in hand both count", media_bytes_in(d), 210)
        check("part files are left out when a run starts", media_bytes_in(d, include_part_files=False), 200)
    check("a folder that is missing weighs nothing", media_bytes_in("/no/such/cut/folder"), 0)

    # What the fix is for: a 3 s audio clip (about 52 KB) beside a 100 KB thumbnail.
    est = expected_bytes(4_200_000, 240, 60.0, 63.0)
    with tempfile.TemporaryDirectory() as tmp:
        d = Path(tmp)
        (d / "clip.jpg").write_bytes(b"0" * 100_000)
        old_bar = fraction_on_disk(sum(f.stat().st_size for f in d.iterdir()), est)
        new_bar = fraction_on_disk(media_bytes_in(d), est)
        check("before the fix a thumbnail alone parked a 3 s clip at 99%", old_bar, 0.99)
        check("after the fix the bar starts at 0", new_bar, 0.0)

    vm = VM.read_text(encoding="utf-8")
    cut = CUT.read_text(encoding="utf-8")
    body = function_body(vm, "private fun fractionOnDisk(")
    check_true("a cut is measured by its media, an ordinary download as before",
               "if (cutRun) {" in body and "CutProgress.mediaBytesIn(downloadDir, includePartFiles = !seeding)" in body
               and "bytesIn(downloadDir)" in body)
    check_true("the run start seeds the floor without part files",
               "progressFloor = fractionOnDisk(seeding = true)" in vm)
    check_true("the per-tick refresh still reads everything the media has written",
               "progressFloor = maxOf(progressFloor, fractionOnDisk())" in vm)
    start = function_body(vm, "private fun startCutWatcher(")
    check_true("a failed tick cannot swallow the stop signal",
               start.find("catch (e: InterruptedException)") != -1
               and start.find("catch (e: InterruptedException)") < start.find("catch (e: Exception)"))
    check_true("mediaBytesIn exists in CutProgress", "fun mediaBytesIn(dir: File, includePartFiles: Boolean = true)" in cut)


def test_real_ffmpeg():
    print("\n[6] Real ffmpeg (cut written the way yt-dlp's ffmpeg downloader writes it)")
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        skip("real ffmpeg cut growth and size", "ffmpeg not installed")
        return
    with tempfile.TemporaryDirectory() as tmp:
        src = os.path.join(tmp, "src.mp4")
        duration = 40
        gen = subprocess.run(
            [ffmpeg, "-v", "error", "-y",
             "-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=30,noise=alls=20:allf=t",
             "-f", "lavfi", "-i", "sine=frequency=440",
             "-t", str(duration), "-c:v", "libx264", "-preset", "ultrafast", "-crf", "26", "-g", "60",
             "-c:a", "aac", "-b:a", "128k", src],
            capture_output=True)
        if gen.returncode != 0:
            skip("real ffmpeg cut growth and size", "could not make a test video")
            return
        size = os.path.getsize(src)
        for start, end in ((0, 10), (12, 25.5), (30, 90)):
            out = os.path.join(tmp, f"cut_{start}.mp4.part")
            proc = subprocess.Popen([ffmpeg, "-v", "error", "-y", "-ss", str(start), "-to", str(min(end, duration)),
                                     "-i", src, "-c", "copy", "-f", "mp4", out])
            samples = []
            while proc.poll() is None:
                if os.path.exists(out):
                    samples.append(os.path.getsize(out))
                time.sleep(0.005)
            final = os.path.getsize(out)
            est = expected_bytes(size, duration, float(start), float(end))
            ratio = final / est
            check_true(f"cut {start}-{end}: the file only grows while ffmpeg writes", all(b >= a for a, b in zip(samples, samples[1:])))
            check_true(f"cut {start}-{end}: lands within 20% of the estimate (ratio {ratio:.2f})", 0.8 <= ratio <= 1.2)


def main():
    print("=" * 70)
    print("  Cut download progress harness (issue #161)")
    print("=" * 70)
    test_estimate()
    test_simulated_run()
    test_source_wiring()
    test_structure()
    test_side_files()
    test_real_ffmpeg()
    print("\n" + "=" * 70)
    print(f"  {PASS_COUNT} passed, {FAIL_COUNT} failed, {SKIP_COUNT} skipped")
    print("=" * 70)
    return 0 if FAIL_COUNT == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
