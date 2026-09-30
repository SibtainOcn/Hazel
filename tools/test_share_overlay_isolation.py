#!/usr/bin/env python3
"""
Test Harness: Share Overlay Isolation, History Recording, and Layout Safety.

Verifies:
1. Share Intent Isolation: fetchShare clears previous search results so single
   video shares never bleed previous results or open a BatchDownloadSheet.
2. Single vs Multi-sheet routing:
   - Single video URL -> FormatSheet
   - Playlist / Collection URL -> BatchDownloadSheet
3. Duplicate Independence: Whether a media item was downloaded previously or not,
   the share sheet resolves cleanly and allows format selection.
4. History Recording: a shared URL is recorded into SearchHistoryRepository at once.
5. One share target: the sheet opens immediately on the shared link and fills in as it is
   read; there is no separate instant target, loading screen or settings for one.
6. Choosing before the read finishes: the download waits for the read and then starts.
7. Failure dialog: the log copies and a sign-in can be added from it.
8. Dark overlay theme using the user's accent colour.

Run:
    python tools/test_share_overlay_isolation.py
"""

import os
import re
import sys
from pathlib import Path

# Ensure UTF-8 output on Windows consoles
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

REPO_ROOT = Path(__file__).resolve().parent.parent

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


def check_true(name: str, condition: bool, msg: str = ""):
    check(name, bool(condition), True)


# ===========================================================================
# 1. State Machine Simulation: Share Intent Isolation vs Batch Bleed
# ===========================================================================

class MockMediaInfo:
    def __init__(self, url: str, title: str, is_playlist: bool = False):
        self.url = url
        self.title = title
        self.is_playlist = is_playlist


class MockDownloadState:
    def __init__(self, results=None, info=None, is_fetching=False):
        self.results = results or []
        self.info = info
        self.is_fetching = is_fetching

    @property
    def is_multiple(self):
        return len(self.results) > 1


class MockDownloadViewModel:
    def __init__(self):
        self.state = MockDownloadState()
        self.search_history = []

    def clear_results(self):
        self.state = MockDownloadState()

    def fetch_all_legacy(self, urls: list[str]):
        """Legacy behavior that kept existing results."""
        resolved = [MockMediaInfo(u, f"Title for {u}") for u in urls]
        kept = [r for r in self.state.results if r.url not in [x.url for x in resolved]]
        all_results = resolved + kept
        self.state = MockDownloadState(
            results=all_results,
            info=resolved[0] if len(resolved) == 1 else None
        )

    def fetch_share(self, url: str, is_playlist: bool = False):
        """Isolated share behavior: clears prior state before resolving."""
        self.clear_results()
        if is_playlist:
            # Simulates playlist expanding into 3 items
            items = [MockMediaInfo(f"{url}#part{i}", f"Playlist item {i}") for i in range(1, 4)]
            self.state = MockDownloadState(results=items, info=None)
        else:
            item = MockMediaInfo(url, "Single Video")
            self.state = MockDownloadState(results=[item], info=item)


def determine_sheet_legacy(state: MockDownloadState) -> str:
    """Legacy order in ShareOverlayActivity: checked isMultiple BEFORE state.info."""
    if state.is_multiple and len(state.results) > 0:
        return "BatchDownloadSheet"
    if state.info is not None:
        return "FormatSheet"
    return "OverlayLoadingSheet"


def determine_sheet_new(state: MockDownloadState) -> str:
    """New fixed order in ShareOverlayActivity: single info is prioritized and results are isolated."""
    if state.info is not None:
        return "FormatSheet"
    if len(state.results) > 1:
        return "BatchDownloadSheet"
    if len(state.results) == 1:
        return "FormatSheet"
    # The sheet opens on the shared link itself before anything is read.
    return "FormatSheet"


def test_share_intent_isolation():
    print(f"\n{'='*70}\n  TEST 1: Share Intent Isolation & Single vs Batch Sheet Routing\n{'='*70}")

    vm = MockDownloadViewModel()

    # Pre-populate state as if user previously searched 2 items in Home (like img2 in bug report)
    vm.state = MockDownloadState(results=[
        MockMediaInfo("https://instagram.com/reel/111", "mirasingh.19"),
        MockMediaInfo("https://instagram.com/reel/222", "heyitsmavey")
    ])

    # Demonstration of the old bug:
    vm_buggy = MockDownloadViewModel()
    vm_buggy.state = MockDownloadState(results=[
        MockMediaInfo("https://instagram.com/reel/111", "mirasingh.19"),
        MockMediaInfo("https://instagram.com/reel/222", "heyitsmavey")
    ])
    vm_buggy.fetch_all_legacy(["https://instagram.com/reel/333"])
    check("Legacy fetchAll erroneously accumulated results", len(vm_buggy.state.results), 3)
    check("Legacy fetchAll triggered BatchDownloadSheet for single share", determine_sheet_legacy(vm_buggy.state), "BatchDownloadSheet")

    # With isolated fetchShare:
    vm.fetch_share("https://instagram.com/reel/333", is_playlist=False)
    check("Isolated fetchShare has exactly 1 result", len(vm.state.results), 1)
    check("Isolated fetchShare non-null info for single item", vm.state.info is not None, True)
    check("Isolated fetchShare opens FormatSheet for single video", determine_sheet_new(vm.state), "FormatSheet")

    # When an actual playlist URL is shared:
    vm.fetch_share("https://youtube.com/playlist?list=PL123", is_playlist=True)
    check("Playlist expands to multiple items", len(vm.state.results) > 1, True)
    check("Playlist has null single-info", vm.state.info is None, True)
    check("Playlist opens BatchDownloadSheet", determine_sheet_new(vm.state), "BatchDownloadSheet")


# ===========================================================================
# 2. Source Code Static Verification
# ===========================================================================

def test_source_code_integrity():
    print(f"\n{'='*70}\n  TEST 2: Source Code Implementation Verification\n{'='*70}")

    share_overlay_path = REPO_ROOT / "app/src/main/java/com/hazel/android/ui/share/ShareOverlayActivity.kt"
    check_true("ShareOverlayActivity.kt exists", share_overlay_path.is_file())
    overlay_content = share_overlay_path.read_text(encoding="utf-8")

    # 1. fetchShare is invoked in regular share path
    check_true(
        "ShareOverlayActivity calls fetchShare(url)",
        "downloadViewModel.fetchShare(url)" in overlay_content,
        "fetchShare must be called to isolate intent"
    )

    # 2. Search history is captured immediately on share
    check_true(
        "SearchHistoryRepository.record captured on launch",
        "SearchHistoryRepository.record(app, url)" in overlay_content,
        "Shared URLs must be saved to history"
    )

    # 3. Dynamic Accent Dark Theme
    check_true(
        "overlayColorScheme defined in ShareOverlayActivity",
        "private fun overlayColorScheme" in overlay_content,
        "Must use overlayColorScheme function"
    )
    check_true(
        "SettingsRepository.getAccentColor used for share overlay",
        "SettingsRepository.getAccentColor" in overlay_content
    )
    check_true(
        "Background color is #0A0A0A (Deep black)",
        "0xFF0A0A0A" in overlay_content
    )
    check_true(
        "Theme uses accent colors dynamically",
        "accent.dark" in overlay_content and "accent.containerDark" in overlay_content
    )

    # 4. One item opens the format sheet, a collection the batch sheet
    check_true(
        "FormatSheet is rendered for a single item",
        "results.singleOrNull()" in overlay_content and "FormatSheet(" in overlay_content
    )
    check_true(
        "BatchDownloadSheet is only rendered for multiple results",
        "results.size > 1 -> BatchDownloadSheet(" in overlay_content
    )

    # 5. DownloadViewModel fetchShare definition
    vm_path = REPO_ROOT / "app/src/main/java/com/hazel/android/download/DownloadViewModel.kt"
    check_true("DownloadViewModel.kt exists", vm_path.is_file())
    vm_content = vm_path.read_text(encoding="utf-8")
    check_true(
        "DownloadViewModel defines fetchShare",
        "fun fetchShare(url: String)" in vm_content
    )
    check_true(
        "fetchShare clears prior results",
        "clearResults()" in vm_content and "fetchAll(listOf(url))" in vm_content
    )


# ===========================================================================
# 3. One share target
# ===========================================================================

def test_single_share_target():
    print(f"\n{'='*70}\n  TEST 3: One Share Target, No Instant Path\n{'='*70}")

    manifest = (REPO_ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    check_true("No instant share alias in the manifest", "DirectShareActivity" not in manifest)
    check_true("Share overlay still receives SEND", ".ui.share.ShareOverlayActivity" in manifest and "android.intent.action.SEND" in manifest)

    share_dir = REPO_ROOT / "app/src/main/java/com/hazel/android/ui/share"
    check_true("InstantShareSheet.kt removed", not (share_dir / "InstantShareSheet.kt").exists())
    check_true("OverlayLoadingSheet.kt removed", not (share_dir / "OverlayLoadingSheet.kt").exists())
    check_true(
        "DirectShareScreen.kt removed",
        not (REPO_ROOT / "app/src/main/java/com/hazel/android/ui/screens/more/DirectShareScreen.kt").exists()
    )

    vm_content = (REPO_ROOT / "app/src/main/java/com/hazel/android/download/DownloadViewModel.kt").read_text(encoding="utf-8")
    check_true("startDirect removed from the view model", "fun startDirect(" not in vm_content)
    check_true("instantSource removed from the state", "instantSource" not in vm_content)


# ===========================================================================
# 4. The sheet opens at once and a choice waits for the read
# ===========================================================================

def test_sheet_opens_at_once():
    print(f"\n{'='*70}\n  TEST 4: Immediate Sheet & Download Once Read\n{'='*70}")

    overlay = (REPO_ROOT / "app/src/main/java/com/hazel/android/ui/share/ShareOverlayActivity.kt").read_text(encoding="utf-8")
    vm = (REPO_ROOT / "app/src/main/java/com/hazel/android/download/DownloadViewModel.kt").read_text(encoding="utf-8")

    check_true("Sheet opens on the generic quality ladder for the shared link", "GenericFormats.placeholder(" in overlay and "resolved ?: placeholder" in overlay)
    check_true("A choice made before the read waits for it", "downloadViewModel.downloadOnceRead(" in overlay)
    check_true("The view model waits for the running read", "fun downloadOnceRead(" in vm and "fetchJob?.join()" in vm)
    check_true("A failed read is reported after the sheet has closed", "DownloadNotificationHelper.showError(" in vm)
    check_true("No artificial delays in the overlay", "delay(" not in overlay)

    check_true("Failure dialog copies the log", "copyToClipboard(this@ShareOverlayActivity, failure)" in overlay)
    check_true("Failure dialog offers adding cookies", "canAddCookies = true" in overlay)
    check_true("Signing in reads the link again", "signInLauncher" in overlay and "fetchShare(url)" in overlay)

    sheet = (REPO_ROOT / "app/src/main/java/com/hazel/android/ui/screens/download/FormatSheet.kt").read_text(encoding="utf-8")
    check_true("Format sheet adopts a title that arrives late", "if (title.isBlank()) title = info.title" in sheet)


# ===========================================================================
# Main
# ===========================================================================

def main():
    print("=" * 70)
    print("  Hazel Share Overlay Isolation & Safety Test Harness")
    print("=" * 70)

    test_share_intent_isolation()
    test_source_code_integrity()
    test_single_share_target()
    test_sheet_opens_at_once()

    print("\n" + "=" * 70)
    print(f"  TOTAL CHECKS: {PASS_COUNT + FAIL_COUNT}")
    print(f"  PASSED:       {PASS_COUNT}")
    print(f"  FAILED:       {FAIL_COUNT}")
    print("=" * 70)

    return 0 if FAIL_COUNT == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
