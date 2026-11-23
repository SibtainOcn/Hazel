#!/usr/bin/env python3
"""
Test Harness: trimmed (cut) download progress, pause, stop and size.

Python ports of CutProgress checked case for case, source wiring checks on the Kotlin,
and a real ffmpeg run (skipped when ffmpeg is not installed).

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
    check_true("watcher follows every trimmed or audio run, with or without an estimate",
               "if (liveRun) startCutWatcher() else null" in wrapper)
    check_true("the run is inside try/finally", "try {" in wrapper and "finally {" in wrapper)
    check_true("the watcher is cancelled in finally", re.search(r"finally\s*\{\s*watcher\?\.cancel\(\)", wrapper))
    check_true("strays are cleared before a run, only when idle", "if (!YtDlpEngine.isBusy) YtDlpEngine.killStrayFfmpeg()" in wrapper)
    check_true("the wrapper runs the original body", "runYtDlp(request)" in wrapper)

    run = function_body(vm, "private fun runYtDlp(")
    check_true("the original engine call is untouched", "YtDlpEngine.execute(request, processId)" in run)
    check_true("the callback keeps its own percentage for ordinary downloads", "else percent.toInt()" in run)
    check_true("the callback still applies the floor", "coerceAtLeast(progressFloor * 100f)" in run)

    # watcher lifecycle
    start = function_body(vm, "private fun startCutWatcher(")
    check_true("watcher is a coroutine, not a thread of its own", "downloadScope.launch" in start and "thread(" not in start)
    check_true("watcher waits between ticks", "delay(CUT_POLL_INTERVAL_MS)" in start and "while (isActive && liveRun)" in start)
    check_true("a failed tick cannot kill the watcher or the app", "catch (e: Exception)" in start)
    check_true("no thread join is left", "stopCutWatcher" not in vm and "CUT_WATCHER_JOIN_MS" not in vm)

    publish = function_body(vm, "private fun publishCutProgress(")
    check_true("a tick does nothing once the run is over or for an ordinary download",
               "!current.isDownloading || !current.unmeasured" in publish)
    check_true("a tick is not gated on the stage a line names", "isProcessing" not in publish)
    check_true("a tick only moves the size forward",
               "maxOf(CutProgress.mediaBytesIn(downloadDir), current.liveBytes)" in publish)
    check_true("a tick shows an indeterminate notification", "progress = -1" in publish)
    check_true("the shade keeps its pacing", "NOTIFICATION_INTERVAL_MS" in publish)

    # expected total
    check_true("the run's expected total uses the cut",
               "expectedTotalBytes = expectedTotalFor(plan, options)" in vm)
    check_true("cutRun is set before the expected total is worked out",
               vm.find("cutRun = options.cutsWhileDownloading") != -1
               and vm.find("cutRun = options.cutsWhileDownloading") < vm.find("expectedTotalBytes = expectedTotalFor(plan, options)"))
    total = function_body(vm, "private fun expectedTotalFor(")
    check_true("an uncut download returns the whole size as before", "if (!options.cutsWhileDownloading) return whole" in total)
    check_true("a cut is scaled by CutProgress", "CutProgress.expectedBytes(" in total)
    check_true("the size is still 0 when the format has none", "if (format.fileSizeBytes <= 0L) return 0L" in total)

    # the opening notification and the callback agree on an unmeasured cut
    check_true("only a cut made while downloading is live: audio and video keep their percentage",
               "liveRun = cutRun\n" in vm and "liveRun = cutRun ||" not in vm)
    check_true("a cut drops the part file a pause left, as ffmpeg starts over",
               "if (cutRun) purgeFragments()" in vm
               and vm.index("liveRun = cutRun") < vm.index("if (cutRun) purgeFragments()"))
    check_true("a live run is never shown as processing", "isProcessing = postProcessing && !unmeasured" in vm)
    check("the callback and the opening read liveRun the same way", vm.count("if (liveRun) -1"), 1)
    check_true("a cut has no merge stage of its own", "needsMerge && !options.cutsWhileDownloading" in vm)
    check_true("every stop goes through the engine", "destroyProcessById(processId)" not in vm and vm.count("YtDlpEngine.stop(processId)") == 4)
    check_true("the poll constant exists", "CUT_POLL_INTERVAL_MS = 1000L" in vm)

    # no user-facing text added in Kotlin (strings live in strings.xml)
    added_literals = re.findall(r'"([^"\n]{12,})"', publish + start)
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


def media_bytes_in(directory):
    """Port of CutProgress.mediaBytesIn: the largest media file."""
    try:
        largest = 0
        for f in Path(directory).iterdir():
            if not f.is_file() or f.name.endswith(".ytdl"):
                continue
            ext = f.suffix[1:].lower() if f.suffix else ""
            if ext in SIDE_FILE_EXTENSIONS:
                continue
            largest = max(largest, f.stat().st_size)
        return largest
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
        (d / "clip.mp4").write_bytes(b"0" * 200)
        (d / "clip.temp.mp4").write_bytes(b"0" * 190)
        check("a copy written while the cover or tags go in does not double the cut", media_bytes_in(d), 200)
    with tempfile.TemporaryDirectory() as tmp:
        d = Path(tmp)
        (d / "clip.webm").write_bytes(b"0" * 120)
        (d / "clip.mp3").write_bytes(b"0" * 150)
        check("an audio cut being converted is not counted twice", media_bytes_in(d), 150)
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
    check_true("a cut starts from zero, an ordinary download from what is on disk",
               "progressFloor = if (cutRun) 0f else fractionOnDisk()" in vm)
    check_true("a cut skips the per-line disk floor",
               "if (!cutRun) refreshFloorFromDisk()" in vm)
    check_true("the live size is the cut's largest media file",
               "CutProgress.mediaBytesIn(downloadDir)" in function_body(vm, "private fun publishCutProgress("))
    check_true("a stale-link retry shows the cut as unmeasured again",
               "liveRun = true" in vm and "unmeasured = true, liveBytes = 0L" in vm)
    check_true("history keeps the kept length clamped to the source",
               "CutProgress.keptSeconds(info.durationSeconds, options.sectionStart, options.sectionEnd)" in vm)
    start = function_body(vm, "private fun startCutWatcher(")
    check_true("the tick's try block holds no suspension point, so cancel is never swallowed",
               start.index("delay(CUT_POLL_INTERVAL_MS)") < start.index("try {"))
    check_true("mediaBytesIn exists in CutProgress", "fun mediaBytesIn(dir: File)" in cut)
    check_true("a cut is weighed by its largest media file, not the sum",
               ".maxOfOrNull { it.length() }" in function_body(cut, "fun mediaBytesIn(") and ".sumOf" not in function_body(cut, "fun mediaBytesIn("))


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


ENGINE = DL / "YtDlpEngine.kt"


def is_download_ffmpeg(args, roots):
    """Port of the filter in YtDlpEngine.killStrayFfmpeg."""
    if not args or not args[0].endswith("libffmpeg.so"):
        return False
    return any(root in arg for arg in args for root in roots)


def test_stop_spares_converter():
    print("\n[7] Stopping a download ends only a download's ffmpeg")
    roots = ["/storage/emulated/0/Android/data/com.hazel.android/files/Hazel/"]
    ffmpeg = "/data/app/lib/arm64/libffmpeg.so"
    cut = [ffmpeg, "-y", "-ss", "12.0", "-t", "13.5", "-i", "https://cdn/v.mp4", "-c", "copy", "-f", "mp4",
           "file:" + roots[0] + "dl_0123456789abcdef/Clip.mp4.part"]
    embed = [ffmpeg, "-y", "-i", "file:" + roots[0] + "dl_0123456789abcdef/Clip.mp4",
             "file:" + roots[0] + "dl_0123456789abcdef/Clip.temp.mp4"]
    convert = [ffmpeg, "-y", "-i", "file:/data/user/0/com.hazel.android/cache/convert_input_1.m4a",
               "-vn", "-acodec", "libmp3lame",
               "file:/storage/emulated/0/Android/data/com.hazel.android/files/Music/Song.mp3"]
    probe = ["/data/app/lib/arm64/libffprobe.so", "file:" + roots[0] + "dl_0123456789abcdef/Clip.mp4"]
    check_true("a cut being written is ended", is_download_ffmpeg(cut, roots))
    check_true("a post-processing step on a download is ended", is_download_ffmpeg(embed, roots))
    check_true("a conversion running beside the download is left alone", not is_download_ffmpeg(convert, roots))
    check_true("ffprobe is never touched", not is_download_ffmpeg(probe, roots))
    check_true("an empty command line is skipped", not is_download_ffmpeg([""], roots))

    engine = ENGINE.read_text(encoding="utf-8")
    body = function_body(engine, "fun killStrayFfmpeg(")
    check_true("the filter reads the download work roots", "SdCards.workRoots()" in body)
    check_true("only ffmpeg naming a work root is ended", "args.none { arg -> roots.any { root -> root in arg } }" in body)
    check_true("no roots means nothing is ended", "if (roots.isEmpty()) return" in body)
    check_true("every stop still ends a download's ffmpeg", "killStrayFfmpeg()" in function_body(engine, "fun stop("))


QUEUE_SCREEN = REPO_ROOT / "app/src/main/java/com/hazel/android/ui/screens/queue/QueueScreen.kt"


def test_paused_cut():
    print("\n[8] A paused cut shows no stale size, and a retry starts its size over")
    vm = VM.read_text(encoding="utf-8")
    queue = QUEUE_SCREEN.read_text(encoding="utf-8")
    fraction = function_body(vm, "internal fun pausedFraction(")
    check_true("a paused cut has no share on disk", "if (item.options.cutsWhileDownloading) return 0f" in fraction)
    check_true("an ordinary paused download is still measured", "workDirsFor(item.url).sumOf { bytesIn(it) }" in fraction)
    hold = function_body(vm, "private fun holdForResume(")
    check_true("a paused cut drops its useless part file", "val cut = item.options.cutsWhileDownloading" in hold and "if (cut) purgeFragments()" in hold)
    check_true("a paused cut card shows no size against the whole link",
               "queued?.takeUnless { it.options.cutsWhileDownloading }" in queue)
    check_true("a retry starts a cut's size over",
               "unmeasured = true, liveBytes = 0L, progress = 0f" in vm
               and vm.index("InfoCache.invalidate(plan.info.url)") < vm.index("unmeasured = true, liveBytes = 0L, progress = 0f"))
    check_true("a paused cut shows nothing in hand, on the card and in the shade",
               "progress = if (cut) 0f else s.progress" in hold and "val total = if (cut) 0L else _state.value.totalBytes" in hold)
    check_true("a paused cut's figure is never kept as the link's size", "val held = if (!cut &&" in hold)


PROGRESS_LINE = re.compile(
    r"^(frame|fps|stream_\d+_\d+_q|bitrate|total_size|out_time_us|out_time_ms|out_time|"
    r"dup_frames|drop_frames|speed|progress)=")


def out_time_seconds(line):
    """Port of CutProgress.outTimeSeconds."""
    text = line.strip()
    if text.startswith(("out_time_us=", "out_time_ms=")):
        try:
            micros = int(text.split("=", 1)[1])
        except ValueError:
            return None
        return micros / 1_000_000 if micros >= 0 else None
    if text.startswith("out_time="):
        clock = text.split("=", 1)[1].strip()
        parts = clock.split(":")
        if len(parts) != 3 or clock.startswith("-"):
            return None
        try:
            return int(parts[0]) * 3600 + int(parts[1]) * 60 + float(parts[2])
        except ValueError:
            return None
    return None


def speed_factor(line):
    """Port of CutProgress.speedFactor."""
    text = line.strip()
    if not text.startswith("speed="):
        return None
    try:
        v = float(text.split("=", 1)[1].strip().removesuffix("x").strip())
    except ValueError:
        return None
    return v if math.isfinite(v) and v > 0 else None


def kept_seconds(duration, start, end):
    """Port of CutProgress.keptSeconds."""
    if not (math.isfinite(start) and math.isfinite(end)) or start < 0 or end <= start:
        return 0.0
    if duration <= 0:
        return end - start
    return max(0.0, min(end, duration) - min(start, duration))


def cut_fraction(written, kept):
    """Port of CutProgress.fraction."""
    if kept <= 0 or not math.isfinite(written):
        return 0.0
    return min(max(written / kept, 0.0), 0.99)


def test_real_percentage():
    print("\n[9] A cut shows a real percentage, read from ffmpeg's own report")
    check("out_time_us is read in microseconds", out_time_seconds("out_time_us=2283537"), 2.283537)
    check("out_time_ms is microseconds too, as ffmpeg writes it", out_time_seconds("out_time_ms=4744853"), 4.744853)
    check("out_time clock form is read", out_time_seconds("out_time=00:01:02.500000"), 62.5)
    check("the below-zero figure before the first packet is ignored", out_time_seconds("out_time_us=-15238"), None)
    check("N/A is ignored", out_time_seconds("out_time_us=N/A"), None)
    check("a negative clock is ignored", out_time_seconds("out_time=-00:00:00.015238"), None)
    check("a yt-dlp line is not a report", out_time_seconds("[download] Destination: clip.mp4"), None)
    check("speed with padding is read", speed_factor("speed=   1.49x"), 1.49)
    check("speed 0x is no speed", speed_factor("speed=   0x"), None)
    check("speed N/A is no speed", speed_factor("speed=N/A"), None)
    check("a cut's length is clamped to the end of the link", kept_seconds(120, 90.0, 400.0), 30.0)
    check("with no running time the cut is taken as asked", kept_seconds(0, 40.0, 70.0), 30.0)
    check("a cut that is not a part of the link has no length", kept_seconds(120, 30.0, 10.0), 0.0)
    check("the share stays short of whole", cut_fraction(31.0, 30.0), 0.99)
    check("nothing is measured with no length", cut_fraction(10.0, 0.0), 0.0)
    for line in ("progress=continue", "out_time_us=1", "stream_0_0_q=-1.0", "total_size=48", "speed=N/A"):
        check_true(f"report line recognised: {line}", PROGRESS_LINE.search(line) is not None)
    for line in ("[download] Destination: /x/clip.mp4", "[info] src: Downloading 1 time ranges: 40.0-70.0",
                 "[download] 100% of    7.55MiB in 00:00:14 at 543.34KiB/s", "ERROR: unable to download"):
        check_true(f"yt-dlp line not taken for a report: {line[:40]}", PROGRESS_LINE.search(line) is None)

    vm = VM.read_text(encoding="utf-8")
    cut = CUT.read_text(encoding="utf-8")
    check_true("the ffmpeg downloader is asked for its report, as output options only",
               'const val FFMPEG_PROGRESS_ARGS = "ffmpeg_o:-progress pipe:1 -nostats"' in cut)
    one_off = function_body(vm, "private fun YoutubeDLRequest.applyOneOff(")
    check_true("only a cut asks for the report",
               'addOption("--downloader-args", CutProgress.FFMPEG_PROGRESS_ARGS)' in one_off
               and one_off.index("if (options.cutsWhileDownloading)") < one_off.index("CutProgress.FFMPEG_PROGRESS_ARGS"))
    run = function_body(vm, "private fun runYtDlp(")
    check_true("a report line is read on its own and never becomes the status",
               "if (cutRun && CutProgress.isProgressLine(line))" in run and "return@execute" in run
               and run.index("return@execute") < run.index("cleanProgressLine(line)"))
    check_true("a cut's percentage is ffmpeg's alone", "val percent = if (cutRun) cutFraction * 100f" in run)
    report = function_body(vm, "private fun onCutReport(")
    check_true("the first report turns the moving line into a percentage",
               "liveRun = false" in report and "unmeasured = false" in report)
    check_true("the percentage only moves forward", "maxOf(cutFraction, CutProgress.fraction(written, kept))" in report)
    check_true("a cut of unknown length stays on the moving line", "if (kept <= 0.0) return" in report)
    check_true("the shade keeps its pacing", "NOTIFICATION_INTERVAL_MS" in report)
    check_true("every run starts its report afresh",
               "cutFraction = 0f\n                cutSpeed = 0.0" in vm)

    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        skip("real ffmpeg -progress report", "ffmpeg not installed")
        return
    with tempfile.TemporaryDirectory() as tmp:
        src = os.path.join(tmp, "src.mp4")
        gen = subprocess.run(
            [ffmpeg, "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=30",
             "-f", "lavfi", "-i", "sine=frequency=440", "-t", "40", "-c:v", "libx264", "-preset", "ultrafast",
             "-g", "30", "-c:a", "aac", src], capture_output=True)
        if gen.returncode != 0:
            skip("real ffmpeg -progress report", "could not make a test video")
            return
        # Laid out as yt-dlp's ffmpeg downloader lays it out: input options, the input, the
        # output format, then the `_o` downloader args, then the output file.
        out = os.path.join(tmp, "clip.mp4.part")
        proc = subprocess.run(
            [ffmpeg, "-y", "-hide_banner", "-ss", "10", "-t", "20", "-i", "file:" + src, "-c", "copy", "-f", "mp4",
             "-progress", "pipe:1", "-nostats", "file:" + out], capture_output=True, text=True)
        lines = proc.stdout.splitlines()
        seen = [s for s in (out_time_seconds(l) for l in lines) if s is not None]
        fractions = [cut_fraction(s, kept_seconds(40, 10.0, 30.0)) for s in seen]
        check_true("ffmpeg accepts the report options in the downloader's place", proc.returncode == 0)
        check_true("the report comes on standard output, which yt-dlp's output carries", len(seen) > 0)
        check_true("every report line is recognised as one",
                   all(PROGRESS_LINE.search(l) for l in lines if l.strip()))
        check_true("the share climbs and ends near whole",
                   all(b >= a for a, b in zip(fractions, fractions[1:])) and fractions and fractions[-1] >= 0.9)
        check_true("-nostats keeps the periodic statistics off stderr", "speed=" not in proc.stderr.split("\n")[0])


LOCAL_TRIM = DL / "LocalTrim.kt"


STREAM_LINE = re.compile(r"Stream #0:(\d+)\S*: (Video|Audio|Subtitle|Attachment|Data):")
VIDEO_TUNING = {"libx264": ["-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p"], "mpeg4": ["-q:v", "2"],
                "libvpx-vp9": ["-deadline", "realtime", "-cpu-used", "6", "-crf", "30", "-b:v", "0"]}
AUDIO_TUNING = {"aac": ["-b:a", "192k"], "libopus": ["-b:a", "160k"]}


def parse_streams(listing):
    """Port of LocalTrim.parseStreams."""
    out = []
    for line in listing.splitlines():
        m = STREAM_LINE.search(line)
        if m:
            out.append((int(m.group(1)), m.group(2), "(attached pic)" in line))
    return out


def trim_command(ffmpeg, src, out, streams, start, end, encoders):
    """Port of LocalTrim.trimCommand; encoders is (video, audio) or None."""
    cut = ["-ss", f"{start:.3f}", "-t", f"{end - start:.3f}", "-i", src]
    main = [s for s in streams if not s[2] and s[1] != "Data"]
    covers = [s for s in streams if s[2]]
    videos = sum(1 for s in main if s[1] == "Video")
    meta = "1" if encoders else "0"
    cmd = [ffmpeg, "-y", "-hide_banner", "-loglevel", "error"] + cut + (cut if encoders else [])
    for s in main + covers:
        cmd += ["-map", f"0:{s[0]}"]
    cmd += ["-map_metadata", meta, "-map_chapters", meta, "-c", "copy"]
    if encoders:
        for k in range(videos):
            cmd += [f"-c:v:{k}", encoders[0]]
        if videos:
            cmd += VIDEO_TUNING.get(encoders[0], [])
        cmd += ["-c:a", encoders[1]] + AUDIO_TUNING.get(encoders[1], [])
    for j in range(len(covers)):
        cmd += [f"-disposition:v:{videos + j}", "attached_pic"]
    return cmd + [out]


def test_trim_command_port():
    print("\n[11] LocalTrim's command line matches its port")
    trim = LOCAL_TRIM.read_text(encoding="utf-8")
    body = function_body(trim, "internal fun trimCommand(")
    check_true("covers are mapped last and flagged again",
               "(main + covers).forEach" in body and '"-disposition:v:${videos + it}", "attached_pic"' in body)
    check_true("precise re-encodes audio and reads chapters from a second copy",
               '"-c:a", encoders.audio' in body and "if (encoders != null) addAll(cut)" in body
               and 'val meta = if (encoders != null) "1" else "0"' in body)
    check_true("data streams are left to the muxer", 'it.type != "Data"' in body)
    listing = ("  Stream #0:0[0x1](und): Video: h264 (avc1), yuv420p, 320x180 (default)\n"
               "  Stream #0:1[0x2](eng): Audio: aac (mp4a), 44100 Hz (default)\n"
               "  Stream #0:2[0x4](und): Subtitle: mov_text (tx3g)\n"
               "  Stream #0:3[0x0]: Video: mjpeg (Baseline), 300x300, 90k tbn (attached pic) (timed thumbnails)\n"
               "  Stream #0:4: Data: bin_data (text / 0x74786574)\n")
    streams = parse_streams(listing)
    check("the listing is read stream by stream", streams,
          [(0, "Video", False), (1, "Audio", False), (2, "Subtitle", False), (3, "Video", True), (4, "Data", False)])
    cmd = trim_command("ffmpeg", "in.mp4", "out.mp4", streams, 7.0, 17.0, None)
    check("a copy trim maps the cover last and flags it", cmd[cmd.index("-map"):],
          ["-map", "0:0", "-map", "0:1", "-map", "0:2", "-map", "0:3", "-map_metadata", "0", "-map_chapters", "0",
           "-c", "copy", "-disposition:v:1", "attached_pic", "out.mp4"])


def test_trim_after_download():
    print("\n[10] Trim after download")
    vm = VM.read_text(encoding="utf-8")
    trim = LOCAL_TRIM.read_text(encoding="utf-8")
    engine = ENGINE.read_text(encoding="utf-8")
    opt = (DL / "DownloadOptions.kt").read_text(encoding="utf-8")
    queue = QUEUE_SCREEN.read_text(encoding="utf-8")

    check_true("the option is carried from the sheet",
               "trimAfterDownload = oneOff.hasSection && oneOff.trimAfterDownload" in opt)
    check_true("yt-dlp cuts only when the app is not going to",
               "get() = hasSection && !trimsAfterDownload" in opt
               and "if (options.cutsWhileDownloading) {" in vm
               and "cutRun = options.cutsWhileDownloading" in vm
               and "if (!options.cutsWhileDownloading) return whole" in vm)
    hold = function_body(vm, "private fun holdForResume(")
    check_true("pausing a trim after download keeps its resumable part file",
               "val cut = item.options.cutsWhileDownloading" in hold)
    check_true("a paused trim after download still shows what it has",
               "if (item.options.cutsWhileDownloading) return 0f" in function_body(vm, "internal fun pausedFraction(")
               and "queued?.takeUnless { it.options.cutsWhileDownloading }" in queue)
    check_true("one Trim stage, before Save", "if (options.trimsAfterDownload) add(ProcessingStep.TRIM)" in vm
               and "TIDY" not in vm)
    run = vm.index("trimLocally(app, plan, options)")
    check_true("the trim runs after every stop check and before the save",
               vm.index("if (isCancelled) {", vm.index("if (isBatchCancelled) {", vm.index("restartUrl = null"))) < run
               < vm.index("finishDownload(app, plan, options)\n                    markBatch"))
    check_true("every download arms the trim's stop flag", "LocalTrim.arm()" in vm)
    body = function_body(vm, "private fun trimLocally(")
    check_true("a stopped trim is a cancel, a failed one a failure with a plain reason",
               'throw CancellationException("Trim stopped")' in body and '"\\nERROR: " + sanitizeError(raw)' in body)
    check_true("a stop ends the trim as well as the download", "LocalTrim.stop()" in function_body(engine, "fun stop("))
    body = function_body(trim, "fun trim(")
    check_true("the source is replaced only after a complete cut",
               body.index("output.length() <= 0L") < body.index("output.renameTo(source)"))
    check_true("an unreadable download fails before ffmpeg runs", "if (streams.isEmpty()) return Result(false, false" in body)
    check_true("a copy trim does not shift its timestamps", "avoid_negative_ts" not in trim)
    check_true("a stalled ffmpeg is stopped without a thread of its own",
               "waitFor(WATCH_MS, TimeUnit.MILLISECONDS)" in trim and "Thread {" not in trim)
    check_true("the encoder list is read once", "encoders ?: listEncoders(" in trim)
    check_true("a trim leftover is never taken for the download",
               "!f.name.startsWith(TRIM_PREFIX)" in function_body(trim, "fun mainMedia("))

    ffmpeg = shutil.which("ffmpeg")
    ffprobe = shutil.which("ffprobe")
    if not (ffmpeg and ffprobe):
        skip("real ffmpeg trim", "ffmpeg or ffprobe not available")
        return

    import json

    def run_cmd(cmd):
        try:
            return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120)
        except subprocess.TimeoutExpired:
            return subprocess.CompletedProcess(cmd, 124, "", "timed out")

    def probe(path):
        out = run_cmd([ffprobe, "-v", "error", "-show_streams", "-show_format", "-show_chapters", "-of", "json", path]).stdout
        return json.loads(out or "{}")

    def first_video_pts(path):
        out = run_cmd([ffprobe, "-v", "error", "-select_streams", "V:0", "-show_entries", "frame=pts_time",
                       "-read_intervals", "%+#1", "-of", "csv=p=0", path]).stdout.strip().split("\n")[0]
        return float(out.strip(",") or 0)

    encoders = {l.split()[1] for l in run_cmd([ffmpeg, "-hide_banner", "-encoders"]).stdout.splitlines() if len(l.split()) > 1}

    with tempfile.TemporaryDirectory() as tmp:
        cover = os.path.join(tmp, "cover.jpg")
        run_cmd([ffmpeg, "-v", "error", "-y", "-f", "lavfi", "-i", "color=c=red:s=300x300", "-frames:v", "1", cover])
        sub = os.path.join(tmp, "s.srt")
        with open(sub, "w", encoding="utf-8") as fh:
            fh.write("1\n00:00:00,000 --> 00:00:23,000\nspans\n\n2\n00:00:07,000 --> 00:00:09,000\ninside\n")
        meta = os.path.join(tmp, "m.txt")
        with open(meta, "w", encoding="utf-8") as fh:
            fh.write(";FFMETADATA1\ntitle=Kept\n[CHAPTER]\nTIMEBASE=1/1000\nSTART=0\nEND=12000\ntitle=A\n"
                     "[CHAPTER]\nTIMEBASE=1/1000\nSTART=12000\nEND=24000\ntitle=B\n")
        V = ["-f", "lavfi", "-i", "testsrc2=size=320x180:rate=30"]
        A = ["-f", "lavfi", "-i", "sine=frequency=440"]
        sources = {
            "video_cover_subs_chapters.mp4": V + A + ["-i", cover, "-i", sub, "-i", meta, "-t", "24",
                "-map", "0:v", "-map", "1:a", "-map", "2:v", "-map", "3:s", "-map_metadata", "4", "-map_chapters", "4",
                "-c:v:0", "libx264", "-preset", "ultrafast", "-g", "60", "-c:a", "aac", "-c:v:1", "mjpeg",
                "-disposition:v:1", "attached_pic", "-c:s", "mov_text"],
            "audio_cover.m4a": A + ["-i", cover, "-t", "24", "-map", "0:a", "-map", "1:v", "-c:a", "aac",
                "-c:v", "mjpeg", "-disposition:v", "attached_pic", "-metadata", "title=Kept"],
            "plain.mkv": V + A + ["-t", "24", "-c:v", "libx264", "-preset", "ultrafast", "-g", "60", "-c:a", "aac"],
            "plain.webm": V + A + ["-t", "24", "-c:v", "libvpx-vp9", "-deadline", "realtime", "-cpu-used", "8", "-g", "60",
                "-c:a", "libopus"],
            "unicode_हिंदी 😀 a:b%d 'q'.mp4": V + A + ["-t", "24", "-c:v", "libx264", "-preset", "ultrafast",
                "-g", "60", "-c:a", "aac"],
        }
        for name, args in sources.items():
            src = os.path.join(tmp, name)
            if run_cmd([ffmpeg, "-v", "error", "-y"] + args + [src]).returncode != 0:
                skip(f"real ffmpeg trim of {name}", "could not make a test file")
                continue
            ext = name.rsplit(".", 1)[1]
            before = probe(src)
            streams = parse_streams(run_cmd([ffmpeg, "-hide_banner", "-i", src]).stderr)
            plans = [None]
            if ext in ("mp4", "mkv") and "audio" not in name:
                plans += [p for p in (("libx264", "aac"), ("mpeg4", "aac")) if p[0] in encoders]
            if ext == "webm" and "libvpx-vp9" in encoders and "libopus" in encoders:
                plans.append(("libvpx-vp9", "libopus"))
            for plan in plans:
                label = f"{name} {plan[0] if plan else 'copy'}"
                out = os.path.join(tmp, "hazel.trim." + ext)
                done = run_cmd(trim_command(ffmpeg, src, out, streams, 7.0, 17.0, plan))
                check(f"{label}: succeeds", done.returncode, 0)
                if done.returncode != 0 or not os.path.exists(out):
                    continue
                after = probe(out)
                length = float(after["format"].get("duration", 0))
                check_true(f"{label}: about as long as asked ({length:.1f}s for 10s)", 9.0 <= length <= 12.5)
                has_art = lambda doc: any(x.get("disposition", {}).get("attached_pic") for x in doc["streams"])
                check_true(f"{label}: cover art kept", has_art(before) == has_art(after))
                check_true(f"{label}: audio kept", any(x["codec_type"] == "audio" for x in after["streams"]))
                kinds = lambda doc, kind: any(x["codec_type"] == kind for x in doc["streams"])
                check_true(f"{label}: subtitles kept", kinds(before, "subtitle") == kinds(after, "subtitle"))
                if kinds(before, "video") and not "audio" in name:
                    check_true(f"{label}: video starts at zero", first_video_pts(out) < 0.5)
                if before.get("chapters"):
                    first = (after.get("chapters") or [{}])[0].get("tags", {}).get("title")
                    check(f"{label}: the chapter the cut starts in comes first", first, "A")
                if "Kept" in json.dumps(before["format"].get("tags", {})):
                    check_true(f"{label}: tags kept", "Kept" in json.dumps(after["format"].get("tags", {})))
                os.remove(out)


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
    test_stop_spares_converter()
    test_paused_cut()
    test_real_percentage()
    test_trim_after_download()
    test_trim_command_port()
    print("\n" + "=" * 70)
    print(f"  {PASS_COUNT} passed, {FAIL_COUNT} failed, {SKIP_COUNT} skipped")
    print("=" * 70)
    return 0 if FAIL_COUNT == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
