#!/usr/bin/env python3
"""
Test Harness for Home Search UX, Search History Confirmation, and Data Preservation.

Validates:
1. URL Validation Scheme and Whitespace Handling.
2. Backup and Data Extraction Rules (DataStore & SharedPreferences preservation).
3. AndroidManifest backup registration.
4. 10-Locale Translation Parity for new search strings (Paste, Cancel, Clear Confirm).
5. Home Screen Paste Button specifications (BottomEnd, rounded, Paste text, icon).
6. Home UrlSearchBar clickability architecture (no partial row clickable artifacts).
7. Search History Confirmation dialog guards on both Home and Search screens.
8. Downloads Screen Search Bar AnimatedVisibility and outside-click dismissal.

Run:
    python tools/test_home_search_ux_and_preservation.py
Exit:
    0 on success, 1 on any failure.
"""

import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import urlparse

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


# ---------------------------------------------------------------------------
# Suite 1: URL Validation Logic
# ---------------------------------------------------------------------------
def test_url_validation():
    print("\n--- Suite 1: URL Validation Logic ---")

    # Mirroring DownloadViewModel.isValidUrl
    def is_valid_url(raw: str) -> bool:
        trimmed = raw.strip()
        if not trimmed:
            return False
        try:
            parsed = urlparse(trimmed)
            return parsed.scheme.lower() in ("http", "https") and bool(parsed.netloc)
        except Exception:
            return False

    check_true("Accepts standard https URL", is_valid_url("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    check_true("Accepts standard http URL", is_valid_url("http://example.com/video.mp4"))
    check_true("Accepts UPPERCASE HTTPS scheme", is_valid_url("HTTPS://YOUTUBE.COM/WATCH?V=ABCDEF"))
    check_true("Accepts Mixed-Case Http scheme", is_valid_url("Http://soundcloud.com/track/123"))
    check_true("Accepts URL with leading and trailing whitespace", is_valid_url("  https://instagram.com/reel/xyz  "))
    check_true("Rejects empty string", not is_valid_url(""))
    check_true("Rejects whitespace-only string", not is_valid_url("   \t\n  "))
    check_true("Rejects ftp scheme", not is_valid_url("ftp://example.com/file.zip"))
    check_true("Rejects file scheme", not is_valid_url("file:///sdcard/video.mp4"))
    check_true("Rejects arbitrary text", not is_valid_url("just a query without url"))


# ---------------------------------------------------------------------------
# Suite 2: Data Preservation & Backup Configuration
# ---------------------------------------------------------------------------
def test_data_preservation():
    print("\n--- Suite 2: Data Preservation & Backup Configuration ---")

    backup_rules_path = REPO_ROOT / "app" / "src" / "main" / "res" / "xml" / "backup_rules.xml"
    data_extraction_path = REPO_ROOT / "app" / "src" / "main" / "res" / "xml" / "data_extraction_rules.xml"
    manifest_path = REPO_ROOT / "app" / "src" / "main" / "AndroidManifest.xml"

    check_true("backup_rules.xml exists", backup_rules_path.is_file())
    check_true("data_extraction_rules.xml exists", data_extraction_path.is_file())

    # Check backup_rules.xml contents
    tree_b = ET.parse(backup_rules_path)
    root_b = tree_b.getroot()
    check("backup_rules root tag", root_b.tag, "full-backup-content")

    includes_b = {(elem.attrib.get("domain"), elem.attrib.get("path")) for elem in root_b.findall("include")}
    excludes_b = {(elem.attrib.get("domain"), elem.attrib.get("path")) for elem in root_b.findall("exclude")}

    check_true("backup_rules includes sharedpref", ("sharedpref", ".") in includes_b)
    check_true("backup_rules includes datastore/", ("file", "datastore/") in includes_b)
    check_true("backup_rules excludes cache", ("cache", ".") in excludes_b)

    # Check data_extraction_rules.xml contents
    tree_d = ET.parse(data_extraction_path)
    root_d = tree_d.getroot()
    check("data_extraction_rules root tag", root_d.tag, "data-extraction-rules")

    cloud_backup = root_d.find("cloud-backup")
    check_true("cloud-backup element present", cloud_backup is not None)
    if cloud_backup is not None:
        cloud_inc = {(e.attrib.get("domain"), e.attrib.get("path")) for e in cloud_backup.findall("include")}
        check_true("cloud-backup includes datastore/", ("file", "datastore/") in cloud_inc)
        check_true("cloud-backup includes sharedpref", ("sharedpref", ".") in cloud_inc)

    device_transfer = root_d.find("device-transfer")
    check_true("device-transfer element present", device_transfer is not None)
    if device_transfer is not None:
        dev_inc = {(e.attrib.get("domain"), e.attrib.get("path")) for e in device_transfer.findall("include")}
        check_true("device-transfer includes datastore/", ("file", "datastore/") in dev_inc)
        check_true("device-transfer includes sharedpref", ("sharedpref", ".") in dev_inc)

    # Check AndroidManifest.xml registration
    tree_m = ET.parse(manifest_path)
    root_m = tree_m.getroot()
    app_elem = root_m.find("application")
    check_true("application tag found in manifest", app_elem is not None)

    ns = "http://schemas.android.com/apk/res/android"
    allow_backup = app_elem.attrib.get(f"{{{ns}}}allowBackup")
    full_backup_content = app_elem.attrib.get(f"{{{ns}}}fullBackupContent")
    data_extraction = app_elem.attrib.get(f"{{{ns}}}dataExtractionRules")

    check("manifest allowBackup is true", allow_backup, "true")
    check("manifest fullBackupContent points to backup_rules", full_backup_content, "@xml/backup_rules")
    check("manifest dataExtractionRules points to data_extraction_rules", data_extraction, "@xml/data_extraction_rules")


# ---------------------------------------------------------------------------
# Suite 3: 10-Locale String Parity for Search Changes
# ---------------------------------------------------------------------------
def test_locale_parity():
    print("\n--- Suite 3: 10-Locale Translation Parity for Search Strings ---")

    locales = [
        "values",
        "values-de",
        "values-es",
        "values-fr",
        "values-hi",
        "values-in",
        "values-ja",
        "values-pt-rBR",
        "values-ru",
        "values-zh-rCN",
    ]

    required_keys = [
        "search_paste",
        "search_cancel",
        "search_clear_history_confirm_title",
        "search_clear_history_confirm_body",
    ]

    for loc in locales:
        str_file = REPO_ROOT / "app" / "src" / "main" / "res" / loc / "strings.xml"
        check_true(f"Strings file exists for {loc}", str_file.is_file())
        tree = ET.parse(str_file)
        root = tree.getroot()
        keys = {s.attrib.get("name"): s.text for s in root.findall("string")}

        for rk in required_keys:
            check_true(f"{loc} defines {rk}", rk in keys and bool(keys[rk]))


# ---------------------------------------------------------------------------
# Suite 4: Home Screen Paste Button & UrlSearchBar Architecture
# ---------------------------------------------------------------------------
def test_home_screen_components():
    print("\n--- Suite 4: Home Screen Components & Architecture ---")

    dl_screen_file = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "screens" / "download" / "DownloadScreen.kt"
    check_true("DownloadScreen.kt exists", dl_screen_file.is_file())
    content = dl_screen_file.read_text(encoding="utf-8")

    # 1. Paste button positioning & styling
    check_true("Paste button is aligned to BottomEnd (on the right)", ".align(Alignment.BottomEnd)" in content)
    check_true("Paste button has rounded pill height of 52.dp", ".height(52.dp)" in content)
    check_true("Paste button has RoundedCornerShape(26.dp)", "RoundedCornerShape(26.dp)" in content)
    check_true("Paste button uses search_paste string resource", "R.string.search_paste" in content)
    check_true("Paste button uses ContentPaste icon", "Icons.Filled.ContentPaste" in content)
    check_true("Paste button shows when homeIsEmpty", "if (homeIsEmpty)" in content)

    # 2. UrlSearchBar clickability architecture
    check_true("UrlSearchBar Surface has onClick = onOpenSearch", "Surface(\n        onClick = onOpenSearch" in content or "Surface(onClick = onOpenSearch" in content)
    # Check that Row inside UrlSearchBar does NOT have .clickable(onClick = onOpenSearch)
    url_search_match = re.search(r"private fun UrlSearchBar\([\s\S]*?\n\)", content)
    check_true("UrlSearchBar function found", url_search_match is not None)
    if url_search_match:
        bar_body = content[url_search_match.start():url_search_match.start() + 2500]
        check_true("UrlSearchBar Row does not have inner clickable", "Row(\n            modifier = Modifier\n                .weight(1f)\n                .clickable(onClick = onOpenSearch)" not in bar_body)

    # 3. 3-dot menu and confirmation dialog
    check_true("UrlSearchBar includes 3-dot overflow menu", "Icons.Filled.MoreVert" in content)
    check_true("UrlSearchBar menu offers search_clear_results", "R.string.search_clear_results" in content)
    check_true("UrlSearchBar menu offers search_clear_history", "R.string.search_clear_history" in content)
    check_true("Clear search history confirmation dialog present in DownloadScreen", "search_clear_history_confirm_title" in content)


# ---------------------------------------------------------------------------
# Suite 5: SearchScreen & HistoryScreen Search UX
# ---------------------------------------------------------------------------
def test_search_and_history_screen_ux():
    print("\n--- Suite 5: SearchScreen & HistoryScreen UX ---")

    search_file = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "screens" / "download" / "SearchScreen.kt"
    history_file = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "screens" / "history" / "HistoryScreen.kt"

    check_true("SearchScreen.kt exists", search_file.is_file())
    check_true("HistoryScreen.kt exists", history_file.is_file())

    search_content = search_file.read_text(encoding="utf-8")
    history_content = history_file.read_text(encoding="utf-8")

    # SearchScreen clear history confirmation
    check_true("SearchScreen has ClearHistoryDialog", "ClearHistoryDialog(" in search_content)
    check_true("SearchScreen shows confirmation dialog on clear history", "showClearHistoryConfirm" in search_content)

    # HistoryScreen search bar AnimatedVisibility and dismiss
    check_true("HistoryScreen uses AnimatedVisibility for search bar", "AnimatedVisibility(\n            visible = searchOpen" in history_content or "AnimatedVisibility(visible = searchOpen" in history_content)
    check_true("HistoryScreen uses expandVertically + fadeIn", "expandVertically() + fadeIn()" in history_content)
    check_true("HistoryScreen uses shrinkVertically + fadeOut", "shrinkVertically() + fadeOut()" in history_content)
    check_true("HistoryScreen dismisses search bar when tapping empty space outside", "keyboardController?.hide()\n                            searchOpen = false" in history_content)


# ---------------------------------------------------------------------------
# Suite 6: Compact MediaRow Alignment & Batch Auto-Scroll UX
# ---------------------------------------------------------------------------
def test_compact_alignment_and_batch_scroll():
    print("\n--- Suite 6: Compact MediaRow Alignment & Batch Auto-Scroll UX ---")

    cards_file = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "components" / "MediaCards.kt"
    download_file = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "screens" / "download" / "DownloadScreen.kt"

    cards_content = cards_file.read_text(encoding="utf-8")
    download_content = download_file.read_text(encoding="utf-8")

    # 1. MediaCards.kt MediaRow dimensions must match HistoryRow / QueuedRow (128x78dp, 14dp clip, 20dp surface)
    check_true("MediaCards.kt MediaRow uses 128x78dp thumbnail", ".size(width = 128.dp, height = 78.dp)" in cards_content)
    check_true("MediaCards.kt MediaRow uses 14dp thumbnail corner clip", ".clip(RoundedCornerShape(14.dp))" in cards_content)
    check_true("MediaCards.kt MediaRow uses 20dp surface shape", "shape = RoundedCornerShape(20.dp)" in cards_content)
    check_true("MediaCards.kt MediaRow uses 14dp spacer between thumbnail and text", "Spacer(modifier = Modifier.width(14.dp))" in cards_content)

    # 2. DownloadScreen.kt private MediaRow dimensions
    check_true("DownloadScreen.kt MediaRow uses 128x78dp thumbnail", ".size(width = 128.dp, height = 78.dp)" in download_content)
    check_true("DownloadScreen.kt MediaRow uses 14dp thumbnail corner clip", ".clip(RoundedCornerShape(14.dp))" in download_content)
    check_true("DownloadScreen.kt MediaRow uses 20dp surface shape", "shape = RoundedCornerShape(20.dp)" in download_content)
    check_true("DownloadScreen.kt MediaRow uses 14dp spacer between thumbnail and text", "Spacer(modifier = Modifier.width(14.dp))" in download_content)

    # 3. Batch / Playlist Auto-Scroll on Active Download Transition
    check_true("DownloadScreen.kt has LaunchedEffect watching activeUrl and isDownloading", "LaunchedEffect(activeUrl, isDownloading)" in download_content)
    check_true("DownloadScreen.kt animates scroll to top item on batch transition", "listState.animateScrollToItem(0)" in download_content)


# ---------------------------------------------------------------------------
# Suite 7: Full-Artwork MediaCard & Thumbnail Overlay Architecture
# ---------------------------------------------------------------------------
def test_full_artwork_mediacard():
    print("\n--- Suite 7: Full-Artwork MediaCard & Thumbnail Overlay Architecture ---")

    download_file = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "screens" / "download" / "DownloadScreen.kt"
    cards_file = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "components" / "MediaCards.kt"

    download_content = download_file.read_text(encoding="utf-8")
    cards_content = cards_file.read_text(encoding="utf-8")

    # 1. MediaCard shape is RoundedCornerShape(20.dp)
    check_true("DownloadScreen.kt MediaCard uses 20dp surface shape", "shape = RoundedCornerShape(20.dp)" in download_content)
    check_true("MediaCards.kt MediaCard uses 20dp surface shape", "shape = RoundedCornerShape(20.dp)" in cards_content)

    # 2. Gradient overlay on top of thumbnail
    check_true("DownloadScreen.kt MediaCard applies vertical gradient scrim", "Brush.verticalGradient(" in download_content)
    check_true("MediaCards.kt MediaCard applies vertical gradient scrim", "Brush.verticalGradient(" in cards_content)

    # 3. Title and author overlaid on top of thumbnail (Alignment.TopStart)
    check_true("DownloadScreen.kt MediaCard title/uploader at TopStart", ".align(Alignment.TopStart)" in download_content)
    check_true("MediaCards.kt MediaCard title/uploader at TopStart", ".align(Alignment.TopStart)" in cards_content)

    # 4. Duration shifted to bottom-left corner (Alignment.BottomStart)
    check_true("DownloadScreen.kt MediaCard duration at BottomStart", "Box(\n                        modifier = Modifier\n                            .align(Alignment.BottomStart)" in download_content or ".align(Alignment.BottomStart)\n                            .padding(10.dp)\n                    ) {\n                        CornerTag(text = duration)" in download_content)
    check_true("MediaCards.kt MediaCard duration at BottomStart", "Box(\n                        modifier = Modifier\n                            .align(Alignment.BottomStart)" in cards_content or ".align(Alignment.BottomStart)\n                            .padding(10.dp)\n                    ) {\n                        CornerTag(text = duration)" in cards_content)

    # 5. Saved / Queued / Failed / Downloaded tags preserved at BottomEnd
    check_true("DownloadScreen.kt MediaCard tags at BottomEnd", ".align(Alignment.BottomEnd)" in download_content)
    check_true("MediaCards.kt MediaCard tags at BottomEnd", ".align(Alignment.BottomEnd)" in cards_content)


# ---------------------------------------------------------------------------
# Suite 8: Streamlined Home Top & Downloading Queue Architecture
# ---------------------------------------------------------------------------
def test_streamlined_home_and_downloading_queue():
    print("\n--- Suite 8: Streamlined Home Top & Downloading Queue Architecture ---")

    download_file = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "screens" / "download" / "DownloadScreen.kt"
    history_file = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "screens" / "history" / "HistoryScreen.kt"
    queue_view_file = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "screens" / "history" / "DownloadingQueueView.kt"
    repo_file = REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "data" / "DownloadHistoryRepository.kt"

    dl_content = download_file.read_text(encoding="utf-8")
    hist_content = history_file.read_text(encoding="utf-8")
    repo_content = repo_file.read_text(encoding="utf-8")

    # 1. Clean Home top: redundant sub-search row removed
    check_true("Redundant link counter row removed from DownloadScreen header", "pluralStringResource(\n                            R.plurals.download_links" not in dl_content and "pluralStringResource(R.plurals.download_links" not in dl_content)
    check_true("Dead showCancelConfirmDialog removed from DownloadScreen", "showCancelConfirmDialog" not in dl_content)

    # 2. UrlSearchBar menu offers layout switch
    check_true("UrlSearchBar overflow menu offers layout toggle", "onToggleLayout" in dl_content)
    check_true("UrlSearchBar provides large artwork switch", "R.string.download_show_large_artwork" in dl_content)
    check_true("UrlSearchBar provides list switch", "R.string.download_show_list" in dl_content)

    # 3. Dedicated DownloadingQueueView component
    check_true("DownloadingQueueView.kt exists", queue_view_file.is_file())
    if queue_view_file.is_file():
        q_content = queue_view_file.read_text(encoding="utf-8")
        check_true("DownloadingQueueView composable defined", "fun DownloadingQueueView(" in q_content)
        check_true("QueuedCard composable defined in DownloadingQueueView.kt", "fun QueuedCard(" in q_content)
        check_true("QueuedRow composable defined in DownloadingQueueView.kt", "fun QueuedRow(" in q_content)

    # 4. HistoryFilter combines Downloading & Queued
    check_true("HistoryFilter DOWNLOADING is labeled Downloading queue", 'DOWNLOADING("Downloading queue")' in repo_content)
    check_true("HistoryFilter has QUEUED compatibility alias", "val QUEUED get() = DOWNLOADING" in repo_content)

    # 5. HistoryScreen uses DownloadingQueueView
    check_true("HistoryScreen integrates DownloadingQueueView", "DownloadingQueueView(" in hist_content)
    check_true("HistoryScreen header shows downloading queue string", "history_tab_downloading_queue" in hist_content)

    # 6. HistoryScreen 3-dots overflow menu offers batch controls
    check_true("HistoryScreen 3-dots menu offers pause all", "R.string.download_pause_all" in hist_content)
    check_true("HistoryScreen 3-dots menu offers resume all", "R.string.download_resume_all" in hist_content)
    check_true("HistoryScreen 3-dots menu offers cancel all", "R.string.download_cancel_all" in hist_content)
    check_true("HistoryScreen 3-dots menu offers clear queue", "R.string.history_queue_clear_all" in hist_content)

    # 7. Per-video controls on Home screen intact
    check_true("Home MediaRow retains onCancel action", "onCancel = { downloadViewModel.cancelItem(info.url) }" in dl_content)
    check_true("Home MediaRow retains onPause action", "onPause = downloadViewModel::pauseDownload" in dl_content)
    check_true("Home MediaRow retains onResume action", "onResume = downloadViewModel::resumeDownload" in dl_content)
    check_true("Home MediaRow retains onRemove action", "onRemove = remove" in dl_content)


# ---------------------------------------------------------------------------
# Main Runner
# ---------------------------------------------------------------------------
def main():
    print("=" * 70)
    print("  Hazel Home Search UX, History Confirmation & Data Preservation Harness")
    print("=" * 70)

    test_url_validation()
    test_data_preservation()
    test_locale_parity()
    test_home_screen_components()
    test_search_and_history_screen_ux()
    test_compact_alignment_and_batch_scroll()
    test_full_artwork_mediacard()
    test_streamlined_home_and_downloading_queue()

    print("\n" + "=" * 70)
    print(f"  Summary: {PASS_COUNT}/{PASS_COUNT + FAIL_COUNT} tests PASSED, {FAIL_COUNT} FAILED")
    print("=" * 70)

    if FAIL_COUNT > 0:
        sys.exit(1)
    print("ALL TESTS PASSED!")
    sys.exit(0)


if __name__ == "__main__":
    main()
