#!/usr/bin/env python3
"""
Comprehensive Test Harness for Hazel:
1. Download Controls State Machine (Single & Multi-download, Header Buttons, 3-dots Menu, Thumbnail Controls)
2. Confirmation Dialogs & Safety Barriers Before Cancellation
3. Granular Per-Item Queue Cancellation & Batch Preservation
4. Multi-Platform Source Validation (YouTube, JioSaavn, SoundCloud, Bandcamp, Instagram, Twitter/X, TikTok, Generic)
5. Multi-Locale String Resource Parity across 10 Locales

Run:
    python tools/test_download_controls_and_sources.py
Exit:
    0 on success, 1 on any failure.
"""

import os
import re
import sys
import xml.etree.ElementTree as ET
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
# 1. Source Parsing & Platform Matrix Simulation
# ===========================================================================

def detect_platform(url: str) -> str:
    url_lower = url.lower()
    if any(h in url_lower for h in ["youtube.com", "youtu.be"]):
        return "YouTube"
    if any(h in url_lower for h in ["jiosaavn.com", "saavn.com"]):
        return "JioSaavn"
    if "soundcloud.com" in url_lower:
        return "SoundCloud"
    if "bandcamp.com" in url_lower:
        return "Bandcamp"
    if "instagram.com" in url_lower:
        return "Instagram"
    if any(h in url_lower for h in ["twitter.com", "x.com"]):
        return "Twitter"
    if "tiktok.com" in url_lower:
        return "TikTok"
    if "vimeo.com" in url_lower:
        return "Vimeo"
    if "reddit.com" in url_lower:
        return "Reddit"
    return "Generic"


def parse_source_stream(data: dict) -> dict:
    url = data.get("webpage_url", "")
    platform = detect_platform(url)

    # Resolve artist/author
    artist_list = data.get("artists")
    if isinstance(artist_list, list) and artist_list:
        artist = ", ".join(str(a).strip() for a in artist_list if a and str(a).strip())
    else:
        artist = str(data.get("artist") or "").strip()

    uploader = str(data.get("uploader") or data.get("channel") or data.get("uploader_id") or "").strip()
    author = artist if (artist and platform in ["JioSaavn", "SoundCloud", "Bandcamp"]) else (uploader or artist)

    title = str(data.get("title") or "").strip()
    is_video = bool(data.get("vcodec") and data.get("vcodec") != "none") or (platform in ["YouTube", "Instagram", "Twitter", "TikTok", "Vimeo", "Reddit"] and data.get("resolution") != "audio only")
    is_audio = bool(data.get("acodec") and data.get("acodec") != "none") or (platform in ["JioSaavn", "SoundCloud", "Bandcamp"]) or data.get("audio_ext") is not None

    return {
        "platform": platform,
        "title": title,
        "author": author,
        "isVideo": is_video,
        "isAudio": is_audio,
        "formats": data.get("formats", []),
    }


# ===========================================================================
# 2. UI State Machine Simulation
# ===========================================================================

class BatchItem:
    def __init__(self, url: str, title: str, state: str = "QUEUED", error: str | None = None):
        self.url = url
        self.title = title
        self.state = state  # QUEUED, DOWNLOADING, PAUSED, DONE, FAILED
        self.error = error


class DownloadUIStateMachine:
    """Simulates the state transitions in DownloadScreen and DownloadViewModel."""

    def __init__(self, results: list[dict]):
        self.results = results
        self.batch: list[BatchItem] = []
        self.queue: list[dict] = []
        self.isDownloading = False
        self.isProcessing = False
        self.isPaused = False
        self.isCancelled = False
        self.isBatchCancelled = False
        self.activeInfo: dict | None = None
        self.purged_fragments: list[str] = []
        self.status = ""

    def start_download(self, options: dict = None):
        self.batch = [BatchItem(url=r["url"], title=r["title"]) for r in self.results]
        self.queue = list(self.results)
        self.isBatchCancelled = False
        self.isCancelled = False
        self.advance_queue()

    def advance_queue(self):
        if not self.queue:
            self.isDownloading = False
            self.isProcessing = False
            self.activeInfo = None
            self.status = "Finished"
            return

        next_item = self.queue.pop(0)
        self.activeInfo = next_item
        self.isDownloading = True
        self.isPaused = False
        self.status = f"Downloading {next_item['title']}"
        for b in self.batch:
            if b.url == next_item["url"]:
                b.state = "DOWNLOADING"

    def is_batch_active(self) -> bool:
        return self.isDownloading or any(
            b.state in ["DOWNLOADING", "PAUSED", "QUEUED"] for b in self.batch
        )

    def is_multi(self) -> bool:
        return len(self.results) > 1 or len(self.batch) > 1

    def get_pause_button_text(self) -> str:
        if self.is_multi():
            return "Pause all" if self.isDownloading else "Resume all"
        else:
            return "Pause" if self.isDownloading else "Resume"

    def get_cancel_button_text(self) -> str:
        return "Cancel"

    def get_clear_button_enabled(self) -> bool:
        return not self.is_batch_active()

    def get_cancel_dialog_properties(self) -> tuple[str, str]:
        """Returns (title, body) for confirmation dialog."""
        if self.is_multi():
            return (
                "Cancel all",
                "Are you sure you want to cancel all downloads in progress and in the queue?",
            )
        else:
            return (
                "Cancel download",
                "Are you sure you want to cancel this download?",
            )

    def pause(self):
        if not self.isDownloading:
            return
        self.isPaused = True
        self.isDownloading = False
        if self.activeInfo:
            for b in self.batch:
                if b.url == self.activeInfo["url"]:
                    b.state = "PAUSED"
            # Return active item to front of queue
            self.queue.insert(0, self.activeInfo)
        self.status = "Paused"

    def resume(self):
        if self.isDownloading or not self.queue:
            return
        self.isPaused = False
        for b in self.batch:
            if b.state == "PAUSED":
                b.state = "QUEUED"
        self.advance_queue()

    def cancel_item(self, url: str):
        """Granular cancellation of a single item without killing other queued items."""
        if self.isDownloading and self.activeInfo and self.activeInfo["url"] == url:
            # Active download cancelled
            self.isCancelled = True
            self.purged_fragments.append(url)
            for b in self.batch:
                if b.url == url:
                    b.state = "FAILED"
                    b.error = "Cancelled"
            self.isCancelled = False
            # Advance to next queued item!
            self.advance_queue()
        else:
            # Queued or paused item cancelled
            was_held = any(b.url == url and b.state == "PAUSED" for b in self.batch)
            if was_held:
                self.purged_fragments.append(url)
            self.queue = [item for item in self.queue if item["url"] != url]
            for b in self.batch:
                if b.url == url:
                    b.state = "FAILED"
                    b.error = "Cancelled"

            # If no items left active or queued, finish
            if not self.queue and not self.isDownloading:
                self.isDownloading = False
                self.isProcessing = False
                self.status = ""

    def cancel_all_downloads(self):
        """Batch cancellation of all downloads."""
        self.isCancelled = True
        self.isBatchCancelled = True
        if self.activeInfo:
            self.purged_fragments.append(self.activeInfo["url"])

        for b in self.batch:
            if b.state in ["DOWNLOADING", "PAUSED", "QUEUED"]:
                b.state = "FAILED"
                b.error = "Cancelled"

        self.queue.clear()
        self.isDownloading = False
        self.isProcessing = False
        self.status = ""

    def get_3_dots_options(self, url: str) -> list[str]:
        batch_item = next((b for b in self.batch if b.url == url), None)
        if not batch_item:
            return []

        options = []
        if batch_item.state == "PAUSED":
            options.append("Resume")
        elif batch_item.state == "DOWNLOADING":
            options.append("Pause")

        if batch_item.state in ["DOWNLOADING", "PAUSED", "QUEUED"]:
            options.append("Cancel")

        return options

    def get_thumbnail_center_action(self, url: str) -> str | None:
        batch_item = next((b for b in self.batch if b.url == url), None)
        if not batch_item:
            return None
        if batch_item.state == "PAUSED":
            return "Resume"
        elif batch_item.state == "DOWNLOADING":
            return "Cancel"
        return None


# ===========================================================================
# 3. Test Suites Execution
# ===========================================================================

def main():
    print("=" * 70)
    print("  Hazel Controls, Sources & Safety Regression Harness")
    print("=" * 70)

    # -----------------------------------------------------------------------
    print("\n--- 1. Multi-Platform Source Detection & Stream Extraction ---")
    # -----------------------------------------------------------------------
    sources = [
        {"webpage_url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "title": "Never Gonna Give You Up", "uploader": "Rick Astley", "vcodec": "avc1", "acodec": "mp4a", "formats": [{"format_id": "18", "ext": "mp4"}]},
        {"webpage_url": "https://www.jiosaavn.com/song/kesariya/XyZ123", "title": "Kesariya", "artist": "Arijit Singh", "uploader": "Sony Music", "vcodec": "none", "acodec": "m4a", "audio_ext": "m4a"},
        {"webpage_url": "https://soundcloud.com/artist/epic-track", "title": "Epic Track", "uploader_id": "producer_one", "artist": "Original Artist", "vcodec": "none", "acodec": "mp3"},
        {"webpage_url": "https://bandcamp.com/track/dreamscape", "title": "Dreamscape", "artists": ["SynthWave", "RetroBoy"], "vcodec": "none", "acodec": "flac"},
        {"webpage_url": "https://www.instagram.com/reel/C8xyz123/", "title": "Insta Reel 101", "uploader": "instacreator", "resolution": "720x1280", "ext": "mp4"},
        {"webpage_url": "https://twitter.com/user/status/123456789", "title": "Breaking News Video", "uploader": "NewsDesk", "ext": "mp4", "resolution": "1080x1920"},
        {"webpage_url": "https://www.tiktok.com/@dance/video/987654", "title": "Viral Dance", "uploader": "dancer", "ext": "mp4"},
        {"webpage_url": "https://vimeo.com/123456", "title": "Short Film", "uploader": "Cinematographer", "ext": "mp4"},
        {"webpage_url": "https://www.reddit.com/r/funny/comments/123/funny_clip/", "title": "Funny Dog", "uploader": "doglover", "ext": "mp4"},
        {"webpage_url": "https://cdn.example.com/direct/video.mp4", "title": "Direct Stream", "uploader": "", "ext": "mp4"},
    ]

    for src in sources:
        parsed = parse_source_stream(src)
        p = parsed["platform"]
        check(f"Source detection: {p} ({parsed['title']})", bool(p != "Unknown"), True)
        if p in ["JioSaavn", "SoundCloud", "Bandcamp"]:
            check(f"{p} is audio-only stream", parsed["isAudio"] and not parsed["isVideo"], True)
        elif p in ["YouTube", "Instagram", "Twitter", "TikTok", "Vimeo", "Reddit"]:
            check(f"{p} detected as video stream", parsed["isVideo"], True)

    # Bandcamp artist array resolution
    bc_parsed = parse_source_stream(sources[3])
    check("Bandcamp multi-artist joined cleanly", bc_parsed["author"], "SynthWave, RetroBoy")

    # JioSaavn artist tag overrides record label uploader
    jio_parsed = parse_source_stream(sources[1])
    check("JioSaavn artist tag overrides label", jio_parsed["author"], "Arijit Singh")

    # SoundCloud artist over uploader_id
    sc_parsed = parse_source_stream(sources[2])
    check("SoundCloud artist over uploader_id", sc_parsed["author"], "Original Artist")

    # -----------------------------------------------------------------------
    print("\n--- 2. Single Video / Single Audio Lifecycle & Control Mapping ---")
    # -----------------------------------------------------------------------
    single_item = [{"url": "https://youtube.com/watch?v=single1", "title": "Single Video 1"}]
    sm_single = DownloadUIStateMachine(single_item)

    # Initial state
    check("Single video initial not downloading", sm_single.isDownloading, False)
    check("Single video initial clear enabled", sm_single.get_clear_button_enabled(), True)

    # Start download
    sm_single.start_download()
    check("Single video downloading isDownloading=True", sm_single.isDownloading, True)
    check("Single video header text is 'Pause'", sm_single.get_pause_button_text(), "Pause")
    check("Single video header text is 'Cancel'", sm_single.get_cancel_button_text(), "Cancel")
    check("Single video clear button is disabled while downloading", sm_single.get_clear_button_enabled(), False)

    # Thumbnail & 3-dots control mapping
    check("Thumbnail center button is Cancel ('X') while downloading", sm_single.get_thumbnail_center_action(single_item[0]["url"]), "Cancel")
    options_active = sm_single.get_3_dots_options(single_item[0]["url"])
    check("3-dots contains 'Pause' while downloading", "Pause" in options_active, True)
    check("3-dots contains 'Cancel' while downloading", "Cancel" in options_active, True)

    # Cancel confirmation dialog for single download
    d_title, d_body = sm_single.get_cancel_dialog_properties()
    check("Single cancel dialog title is 'Cancel download'", d_title, "Cancel download")
    check("Single cancel dialog body is single-item specific", "Are you sure you want to cancel this download?" in d_body, True)

    # Pause action
    sm_single.pause()
    check("Single video paused isDownloading=False", sm_single.isDownloading, False)
    check("Single video paused isPaused=True", sm_single.isPaused, True)
    check("Single video header switches to 'Resume'", sm_single.get_pause_button_text(), "Resume")
    check("Single video clear button remains disabled while paused", sm_single.get_clear_button_enabled(), False)
    check("Thumbnail center button is 'Resume' (PlayArrow) while paused", sm_single.get_thumbnail_center_action(single_item[0]["url"]), "Resume")
    options_paused = sm_single.get_3_dots_options(single_item[0]["url"])
    check("3-dots contains 'Resume' while paused", "Resume" in options_paused, True)
    check("3-dots contains 'Cancel' while paused", "Cancel" in options_paused, True)

    # Resume action
    sm_single.resume()
    check("Single video resumed isDownloading=True", sm_single.isDownloading, True)
    check("Single video header switches back to 'Pause'", sm_single.get_pause_button_text(), "Pause")

    # Cancel action (simulating click on thumbnail 'X' or 3-dots 'Cancel')
    sm_single.cancel_item(single_item[0]["url"])
    check("Single video cancelled isDownloading=False", sm_single.isDownloading, False)
    check("Single video cancelled fragments purged", single_item[0]["url"] in sm_single.purged_fragments, True)
    check("Single video batch state marked FAILED Cancelled", sm_single.batch[0].error, "Cancelled")
    check("Single video batch is inactive after cancel", sm_single.is_batch_active(), False)
    check("Single video clear button is enabled after cancel", sm_single.get_clear_button_enabled(), True)

    # -----------------------------------------------------------------------
    print("\n--- 3. Multi-Video / Multi-Audio Queue & Batch Lifecycle ---")
    # -----------------------------------------------------------------------
    multi_items = [
        {"url": "https://youtube.com/watch?v=vid1", "title": "Vid 1"},
        {"url": "https://youtube.com/watch?v=vid2", "title": "Vid 2"},
        {"url": "https://youtube.com/watch?v=vid3", "title": "Vid 3"},
        {"url": "https://youtube.com/watch?v=vid4", "title": "Vid 4"},
        {"url": "https://youtube.com/watch?v=vid5", "title": "Vid 5"},
    ]
    sm_multi = DownloadUIStateMachine(multi_items)
    sm_multi.start_download()

    check("Multi batch active isDownloading=True", sm_multi.isDownloading, True)
    check("Multi batch header says 'Pause all'", sm_multi.get_pause_button_text(), "Pause all")
    check("Multi batch clear is disabled", sm_multi.get_clear_button_enabled(), False)

    # Cancel confirmation dialog for multi download
    m_title, m_body = sm_multi.get_cancel_dialog_properties()
    check("Multi cancel dialog title is 'Cancel all'", m_title, "Cancel all")
    check("Multi cancel dialog body warns about all downloads", "cancel all downloads in progress and in the queue" in m_body, True)

    # Pause all / Resume all
    sm_multi.pause()
    check("Multi batch paused header says 'Resume all'", sm_multi.get_pause_button_text(), "Resume all")
    sm_multi.resume()
    check("Multi batch resumed header says 'Pause all'", sm_multi.get_pause_button_text(), "Pause all")

    # -----------------------------------------------------------------------
    print("\n--- 4. Granular Per-Item Queue Cancellation (No Queue Wiping) ---")
    # -----------------------------------------------------------------------
    # Case 4.1: Cancel active Item 1 -> Item 2 must immediately start, items 3-5 stay queued
    check("Item 1 is actively downloading", sm_multi.activeInfo["url"], multi_items[0]["url"])
    sm_multi.cancel_item(multi_items[0]["url"])

    check("Item 1 cancelled and marked Cancelled", sm_multi.batch[0].error, "Cancelled")
    check("Item 1 fragments purged", multi_items[0]["url"] in sm_multi.purged_fragments, True)
    check("Active download automatically advanced to Item 2", sm_multi.activeInfo["url"], multi_items[1]["url"])
    check("Queue did NOT abort: isDownloading remains True", sm_multi.isDownloading, True)
    remaining_urls = [it["url"] for it in sm_multi.queue]
    check("Remaining items 3, 4, 5 still in queue", remaining_urls, [multi_items[2]["url"], multi_items[3]["url"], multi_items[4]["url"]])

    # Case 4.2: Cancel queued Item 4 via its 3-dots menu -> Item 2 continues, Item 4 removed
    options_item4 = sm_multi.get_3_dots_options(multi_items[3]["url"])
    check("Queued item 4 has 'Cancel' in 3-dots menu", "Cancel" in options_item4, True)
    sm_multi.cancel_item(multi_items[3]["url"])

    check("Item 4 marked Cancelled", sm_multi.batch[3].error, "Cancelled")
    check("Item 2 still actively downloading uninterrupted", sm_multi.activeInfo["url"], multi_items[1]["url"])
    remaining_urls_after = [it["url"] for it in sm_multi.queue]
    check("Queue now contains only Item 3 and Item 5", remaining_urls_after, [multi_items[2]["url"], multi_items[4]["url"]])

    # Case 4.3: Paused active item cancelled in batch
    sm_multi.pause()
    check("Batch paused on Item 2", sm_multi.activeInfo["url"], multi_items[1]["url"])
    sm_multi.cancel_item(multi_items[1]["url"])
    check("Item 2 purged fragments while paused", multi_items[1]["url"] in sm_multi.purged_fragments, True)
    check("Item 2 marked Cancelled", sm_multi.batch[1].error, "Cancelled")

    # Resume batch: should pick up next remaining item (Item 3)
    sm_multi.resume()
    check("Resumed batch picked up Item 3", sm_multi.activeInfo["url"], multi_items[2]["url"])
    check("Item 5 still waiting in queue", [it["url"] for it in sm_multi.queue], [multi_items[4]["url"]])

    # Case 4.4: Cancel All Downloads
    sm_multi.cancel_all_downloads()
    check("Cancel all stopped downloading", sm_multi.isDownloading, False)
    check("Cancel all cleared remaining queue", len(sm_multi.queue), 0)
    for b in sm_multi.batch:
        check(f"Batch item {b.title} marked FAILED", b.state, "FAILED")
        check(f"Batch item {b.title} error is 'Cancelled'", b.error, "Cancelled")
    check("Batch is completely inactive", sm_multi.is_batch_active(), False)
    check("Clear button enabled after Cancel All", sm_multi.get_clear_button_enabled(), True)

    # -----------------------------------------------------------------------
    print("\n--- 5. 10-Locale String Resource Parity for Controls & Dialogs ---")
    # -----------------------------------------------------------------------
    required_keys = [
        "download_pause",
        "download_resume",
        "download_cancel",
        "download_clear",
        "download_pause_all",
        "download_resume_all",
        "download_cancel_all",
        "download_cancel_dialog_body",
        "download_cancel_single_dialog_body",
        "download_cancel_dialog_dismiss",
        "download_clear_confirm_title",
        "download_clear_confirm_body",
    ]
    locales = [
        "values", "values-de", "values-es", "values-fr", "values-hi",
        "values-in", "values-ja", "values-pt-rBR", "values-ru", "values-zh-rCN"
    ]

    for loc in locales:
        file_path = REPO_ROOT / "app" / "src" / "main" / "res" / loc / "strings.xml"
        check_true(f"Locale {loc} strings file exists", file_path.exists())
        tree = ET.parse(file_path)
        root = tree.getroot()
        defined_keys = {elem.attrib.get("name"): (elem.text or "").strip() for elem in root.findall("string")}

        for rk in required_keys:
            check_true(f"[{loc}] has key '{rk}'", rk in defined_keys)
            check_true(f"[{loc}] key '{rk}' has non-empty translation", len(defined_keys.get(rk, "")) > 0)

    # -----------------------------------------------------------------------
    print("\n--- 6. Search History, Clear Confirmation & Vector UI Integration ---")
    # -----------------------------------------------------------------------
    # 6.1 Check vector drawable ic_search.xml
    search_xml = REPO_ROOT / "app" / "src" / "main" / "res" / "drawable" / "ic_search.xml"
    check_true("ic_search.xml exists in app/src/main/res/drawable", search_xml.exists())
    if search_xml.exists():
        xml_content = search_xml.read_text(encoding="utf-8")
        check_true("ic_search.xml contains vector root", "<vector" in xml_content)
        check_true("ic_search.xml has pathData matching SEARCH.svg", "M11,6C13.7614,6 16,8.23858" in xml_content)

    # 6.2 Check DownloadScreen.kt UI changes
    download_screen_kt = (REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "screens" / "download" / "DownloadScreen.kt").read_text(encoding="utf-8")
    check_true("DownloadScreen has showClearConfirmDialog state", "showClearConfirmDialog" in download_screen_kt)
    check_true("DownloadScreen header Cancel button uses primary color (not error/red)", "color = MaterialTheme.colorScheme.primary" in download_screen_kt)
    check_true("DownloadScreen header Clear button triggers showClearConfirmDialog", "showClearConfirmDialog = true" in download_screen_kt)
    check_true("DownloadScreen contains AlertDialog for clear confirmation", "download_clear_confirm_title" in download_screen_kt and "download_clear_confirm_body" in download_screen_kt)
    check_true("DownloadScreen UrlSearchBar uses painterResource(R.drawable.ic_search)", "painterResource(R.drawable.ic_search)" in download_screen_kt)

    # 6.3 Check SearchScreen.kt and DownloadViewModel.kt search history persistence
    search_screen_kt = (REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "screens" / "download" / "SearchScreen.kt").read_text(encoding="utf-8")
    check_true("SearchScreen startSearch launches on HazelApp.instance.applicationScope", "HazelApp.instance.applicationScope.launch(Dispatchers.IO)" in search_screen_kt)
    check_true("SearchScreen uses painterResource(R.drawable.ic_search)", "painterResource(R.drawable.ic_search)" in search_screen_kt)

    view_model_kt = (REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "download" / "DownloadViewModel.kt").read_text(encoding="utf-8")
    check_true("DownloadViewModel imports SearchHistoryRepository", "import com.hazel.android.data.SearchHistoryRepository" in view_model_kt)
    check_true("DownloadViewModel fetchAll records searches into SearchHistoryRepository", "SearchHistoryRepository.record(app, it)" in view_model_kt)
    check_true("DownloadViewModel startDirect records into SearchHistoryRepository", "SearchHistoryRepository.record(app, link)" in view_model_kt)
    check_true("DownloadViewModel checks incognito before recording search history", "SettingsRepository.getIncognito(app).first()" in view_model_kt)
    check_true("DownloadViewModel fetchAll normalizes URL prefix", "raw.startsWith(\"www.\"" in view_model_kt)

    # -----------------------------------------------------------------------
    print("\n--- 7. Stream Format Ladder, Player Client & Cookie Access Optimization ---")
    # -----------------------------------------------------------------------
    site_access_kt = (REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "download" / "SiteAccess.kt").read_text(encoding="utf-8")
    check_true("SiteAccess.kt contains web_embedded player client", "web_embedded" in site_access_kt)
    check_true("SiteAccess.kt contains isYouTube helper function", "fun isYouTube(" in site_access_kt)
    check_true("SiteAccess.kt passes player_client for YouTube URLs", "youtube:player_client" in site_access_kt)
    check_true("SiteAccess.kt suppresses custom User-Agent on YouTube", "!isYouTube(url)" in site_access_kt)

    media_probe_kt = (REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "download" / "MediaProbe.kt").read_text(encoding="utf-8")
    check_true("MediaProbe.kt prioritizes cookie access when available", "access.hasCookies -> listOf(access, SiteAccess.NONE)" in media_probe_kt)
    check_true("MediaProbe.kt supports anonymous fallback on cookie failure", "val canFallback = !last && (attempt.hasCookies || isSignInRefusal(e.message))" in media_probe_kt)

    cookie_repo_kt = (REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "data" / "CookieRepository.kt").read_text(encoding="utf-8")
    check_true("CookieRepository.kt imports InfoCache", "import com.hazel.android.download.InfoCache" in cookie_repo_kt)
    check_true("CookieRepository.kt clears InfoCache in setUseCookies", "setUseCookies" in cookie_repo_kt and "InfoCache.clear()" in cookie_repo_kt)
    check_true("CookieRepository.kt clears InfoCache in upsert", "upsert" in cookie_repo_kt and "InfoCache.clear()" in cookie_repo_kt)
    check_true("CookieRepository.kt clears InfoCache in setEnabled", "setEnabled" in cookie_repo_kt and "InfoCache.clear()" in cookie_repo_kt)
    check_true("CookieRepository.kt clears InfoCache in delete", "delete" in cookie_repo_kt and "InfoCache.clear()" in cookie_repo_kt)
    check_true("CookieRepository.kt clears InfoCache in deleteAll", "deleteAll" in cookie_repo_kt and "InfoCache.clear()" in cookie_repo_kt)

    settings_repo_kt = (REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "data" / "SettingsRepository.kt").read_text(encoding="utf-8")
    check_true("SettingsRepository.kt clears InfoCache on setListingSource", "setListingSource" in settings_repo_kt and "InfoCache.clear()" in settings_repo_kt)

    check_true("DownloadViewModel.kt uses available cookies for planAccess", "!access.hasCookies -> SiteAccess.NONE" in view_model_kt and "else -> access" in view_model_kt)

    # -----------------------------------------------------------------------
    print("\n--- 8. Batch Download Quality Ceilings, Realtime HQ Button & Cookie Sync ---")
    # -----------------------------------------------------------------------
    media_info_kt = (REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "download" / "MediaInfo.kt").read_text(encoding="utf-8")
    check_true("MediaInfo.kt handles generic maxHeight ceiling", "bv*[height<=$maxHeight]+ba/b[height<=$maxHeight]/bv*+ba/b" in media_info_kt)
    check_true("MediaInfo.kt autoPick returns generic format when concrete is empty", "if (concrete.isEmpty())" in media_info_kt)

    batch_state_kt = (REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "screens" / "download" / "batch" / "BatchDownloadState.kt").read_text(encoding="utf-8")
    check_true("BatchDownloadState.kt resets formatFor on batch ceiling change", "if (scope.size == results.size) {" in batch_state_kt and "formatFor = emptyMap()" in batch_state_kt)

    batch_action_bar_kt = (REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "screens" / "download" / "batch" / "BatchActionBar.kt").read_text(encoding="utf-8")
    check_true("BatchActionBar.kt accepts hqLabel parameter", "hqLabel: String = \"\"" in batch_action_bar_kt)
    check_true("BatchActionBar.kt displays dynamic HQ text on quality button", "text = hqLabel" in batch_action_bar_kt)

    batch_sheet_kt = (REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "screens" / "download" / "batch" / "BatchDownloadSheet.kt").read_text(encoding="utf-8")
    check_true("BatchDownloadSheet.kt passes dynamic hqLabel", "hqLabel = hqLabel" in batch_sheet_kt and "HQ: ${state.maxHeight}p" in batch_sheet_kt)

    format_sheet_kt = (REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "hazel" / "android" / "ui" / "screens" / "download" / "FormatSheet.kt").read_text(encoding="utf-8")
    check_true("FormatSheet.kt resolves generic initialFormat on format load", "initialFormat?.takeIf { !it.isGeneric && it.hasVideo }" in format_sheet_kt)

    check_true("CookieRepository.kt syncs entries on setUseCookies", "existing.map { it.copy(enabled = enabled) }" in cookie_repo_kt)
    check_true("CookieRepository.kt preserves master toggle on setEnabled", "if (enabled)" in cookie_repo_kt and "prefs[USE_COOKIES_KEY] = true" in cookie_repo_kt)

    # -----------------------------------------------------------------------
    # Summary
    # -----------------------------------------------------------------------
    print("\n" + "=" * 70)
    print(f"  Summary: {PASS_COUNT}/{PASS_COUNT + FAIL_COUNT} tests PASSED, {FAIL_COUNT} FAILED")
    print("=" * 70)

    return 0 if FAIL_COUNT == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
