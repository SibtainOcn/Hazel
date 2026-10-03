#!/usr/bin/env python3
"""
Test Harness: switching the format list between NewPipe and yt-dlp.

Verifies:
1. Saved sign-in notice: picking NewPipe for a link whose own site has a saved, enabled
   sign-in with Use cookies on opens a notice instead of a read. Every other pick reads as
   before, and the notice itself reads nothing and changes no setting.
2. The signed-in test matches the one a fetch uses (CookieRepository.accessFor), so the
   notice shows exactly when NewPipe would be passed over.
3. A switch picked while the link is still being read is queued behind that read and wins,
   rather than being dropped; the list stays loading from one read to the next, and the
   superseded read's answer is not applied. An opening read (resolveFormats) never queues.
4. The batch audio list hands its links to the sheet, so it gets the notice too.
5. The notice's text exists in every language, with apostrophes escaped.

Run:
    python tools/tests/test_format_source_switch.py
"""

import re
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SRC = REPO_ROOT / "app/src/main/java/com/hazel/android"
RES = REPO_ROOT / "app/src/main/res"

PASS_COUNT = 0
FAIL_COUNT = 0


def check(name: str, got, expected):
    global PASS_COUNT, FAIL_COUNT
    if got == expected:
        PASS_COUNT += 1
        print(f"  PASS  {name}")
    else:
        FAIL_COUNT += 1
        print(f"  FAIL  {name}")
        print(f"        Expected: {expected!r}")
        print(f"        Got:      {got!r}")


def check_true(name: str, condition: bool):
    check(name, bool(condition), True)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def block(text: str, start: str, length: int = 1600) -> str:
    """The source from [start] on, or empty when it is not there."""
    at = text.find(start)
    return text[at:at + length] if at >= 0 else ""


# ===========================================================================
# 1. A model of the format list's source pick
# ===========================================================================

def pick(picked: str, read_source: str, resolved: bool, signed_in: bool):
    """What a pick in the filter sheet does: 'notice', ('read', source) or None."""
    if picked == "NEWPIPE" and signed_in:
        return "notice"
    if picked != read_source or not resolved:
        return ("read", picked)
    return None


def test_pick_model():
    print("\n--- Suite 1: What a source pick does ---")
    check("NewPipe on a signed-in site shows the notice",
          pick("NEWPIPE", "YT_DLP", True, signed_in=True), "notice")
    check("NewPipe on any other site reads with NewPipe",
          pick("NEWPIPE", "YT_DLP", True, signed_in=False), ("read", "NEWPIPE"))
    check("yt-dlp on a signed-in site still reads",
          pick("YT_DLP", "NEWPIPE", True, signed_in=True), ("read", "YT_DLP"))
    check("The reader already shown reads nothing",
          pick("YT_DLP", "YT_DLP", True, signed_in=False), None)


def test_sheet_source():
    print("\n--- Suite 1b: FormatSelectionSheet wiring ---")
    sheet = read(SRC / "ui/screens/download/FormatSelectionSheet.kt")
    on_source = block(sheet, "onSource = { picked ->", 500)
    check_true("onSource handler found", on_source)
    check_true("Notice only for NewPipe with a sign-in in use",
               "picked == ListingSource.NEWPIPE && signedIn" in on_source)
    check_true("Every other pick still reads through refresh",
               "refresh(picked, false)" in on_source and "} else if (picked != readSource" in on_source)
    check_true("Signed-in state comes from CookieRepository.signsIn on the sheet's links",
               "CookieRepository.signsIn(context, linkUrls)" in sheet)
    check_true("Links default to the sheet's own link",
               "linkUrls: List<String> = listOf(info.url)" in sheet)

    notice = block(sheet, "if (signInNotice) {", 900)
    check_true("Notice dialog found", "AlertDialog(" in notice)
    check_true("Notice uses its own strings",
               "format_source_signed_in_title" in notice and "format_source_signed_in_body" in notice)
    check_true("Notice reads nothing and changes no setting",
               "refresh(" not in notice and "onRefresh" not in notice
               and "setUseCookies" not in notice and "SettingsRepository" not in notice)
    check_true("The update button still reads with the reader shown",
               "onClick = { refresh(readSource, true) }" in sheet)


# ===========================================================================
# 2. The signed-in test matches the one a fetch uses
# ===========================================================================

def test_signed_in_matches_fetch():
    print("\n--- Suite 2: signsIn matches accessFor ---")
    repo = read(SRC / "data/CookieRepository.kt")
    access = block(repo, "suspend fun accessFor(", 700)
    signs = block(repo, "internal fun signsIn(", 500)
    check_true("accessFor and signsIn found", access and signs)
    check_true("Both need Use cookies on",
               "getUseCookies(context)" in access and "useCookies &&" in signs)
    for cond in ("it.enabled", "it.content.isNotBlank()", "covers(it, site)", "siteKeyOf(hostOf(url))"):
        check_true(f"Both test {cond}", cond in access and cond in signs)
    check_true("signsIn writes no cookie file", "writeSiteFile" not in signs)

    vm = read(SRC / "download/DownloadViewModel.kt")
    check_true("A read still passes NewPipe over when a sign-in is in use",
               "reader == ListingSource.NEWPIPE && !access.hasCookies" in vm)


# ===========================================================================
# 3. Reads asked for while one is running
# ===========================================================================

class FormatReads:
    """A model of DownloadViewModel's per-link reads: claim, queue and handoff."""

    def __init__(self):
        self.reading = set()
        self.queued = {}
        self.running = {}
        self.applied = []
        self.idle_seen = []

    def read_formats_of(self, url, fresh, source, queue_if_running=False):
        if url in self.reading:
            if queue_if_running:
                self.queued[url] = (fresh, source)
            return
        self.reading.add(url)
        self.running[url] = (fresh, source)

    def finish(self, url, answer):
        nxt = self.queued.pop(url, None)
        if nxt is None:
            self.reading.discard(url)
            self.idle_seen.append(url)
        if nxt is not None:
            self.running[url] = nxt
            return
        del self.running[url]
        self.applied.append((url, answer))

    # The two entry points
    def resolve(self, url):
        self.read_formats_of(url, False, None)

    def refresh(self, url, source, fresh):
        self.read_formats_of(url, fresh, source, queue_if_running=True)


def test_read_queue_model():
    print("\n--- Suite 3: A switch picked mid-read ---")
    u = "https://youtu.be/x"

    r = FormatReads()
    r.resolve(u)
    r.refresh(u, "NEWPIPE", False)
    check("Switch during the opening read is queued", r.queued.get(u), (False, "NEWPIPE"))
    r.finish(u, "yt-dlp answer")
    check("The opening read's answer is not applied", r.applied, [])
    check("The list stays loading across the handoff", u in r.reading and u not in r.idle_seen, True)
    check("The queued switch runs next", r.running.get(u), (False, "NEWPIPE"))
    r.finish(u, "newpipe answer")
    check("The switch's answer is applied", r.applied, [(u, "newpipe answer")])
    check("Loading ends after the last read", u in r.reading, False)

    r = FormatReads()
    r.resolve(u)
    r.refresh(u, "NEWPIPE", False)
    r.refresh(u, "YT_DLP", True)
    check("The newest request wins", r.queued.get(u), (True, "YT_DLP"))

    r = FormatReads()
    r.resolve(u)
    r.resolve(u)
    check("An opening read never queues", r.queued, {})

    vm = read(SRC / "download/DownloadViewModel.kt")
    refresh = block(vm, "fun refreshFormats(", 600)
    check_true("refreshFormats queues behind a running read",
               "readFormatsOf(info, fresh = fresh, source = source, queueIfRunning = true)" in refresh)
    resolve = block(vm, "fun resolveFormats(", 300)
    check_true("resolveFormats does not queue",
               "readFormatsOf(info, fresh = false, source = null)" in resolve and "queueIfRunning" not in resolve)
    start = block(vm, "private fun startFormatRead(", 4000)
    check_true("A finished read hands its claim to the queued one",
               "queuedFormatReads.remove(info.url)" in start
               and "if (queued == null) _formatsReading.update { it - info.url }" in start)
    check_true("The handoff skips applying the superseded answer",
               start.find("startFormatRead(info, queuedFresh, queuedSource)") < start.find("_state.update")
               and "return@launch" in start)
    claim = block(vm, "private fun readFormatsOf(", 1200)
    check_true("Claiming and queueing share one lock",
               "synchronized(queuedFormatReads)" in claim and "claimFormatRead(info.url)" in claim)


# ===========================================================================
# 4. Batch audio list
# ===========================================================================

def test_batch_wiring():
    print("\n--- Suite 4: Batch audio list ---")
    batch = read(SRC / "ui/screens/download/batch/BatchDownloadSheet.kt")
    check_true("Batch audio list passes its links to the sheet",
               "linkUrls = remember(targets) { targets.map { it.url } }" in batch)


# ===========================================================================
# 5. Strings
# ===========================================================================

def test_strings():
    print("\n--- Suite 5: Notice text in every language ---")
    dirs = sorted(d for d in RES.iterdir() if d.is_dir() and d.name.startswith("values")
                  and (d / "strings.xml").is_file())
    for d in dirs:
        xml = read(d / "strings.xml")
        for key in ("format_source_signed_in_title", "format_source_signed_in_body"):
            m = re.search(rf'<string name="{key}">([^<]*)</string>', xml)
            check_true(f"{d.name}: {key} present", m)
            if m:
                check_true(f"{d.name}: {key} has no unescaped apostrophe",
                           not re.search(r"(?<!\\)'", m.group(1)))


def main():
    print("=" * 70)
    print("  Hazel Format Source Switch Test Harness")
    print("=" * 70)

    test_pick_model()
    test_sheet_source()
    test_signed_in_matches_fetch()
    test_read_queue_model()
    test_batch_wiring()
    test_strings()

    print("\n" + "=" * 70)
    print(f"  TOTAL CHECKS: {PASS_COUNT + FAIL_COUNT}")
    print(f"  PASSED:       {PASS_COUNT}")
    print(f"  FAILED:       {FAIL_COUNT}")
    print("=" * 70)

    return 0 if FAIL_COUNT == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
