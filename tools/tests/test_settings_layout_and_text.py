#!/usr/bin/env python3
"""
Test Harness: settings screens fit any screen, any font size and any language.

Verifies:
1. Every full screen scrolls (the shared SettingsScreen, a verticalScroll or a lazy list),
   so nothing below the fold is cut off on a short screen or with a large font. Download
   settings, Appearance and Tools were plain columns and lost their last rows (issue #87).
2. The settings screens built by hand before now use SettingsScreen, and SettingsScreen
   itself scrolls and leaves room at the bottom.
3. A setting row never cuts its description short: only a value line is held to two lines.
   Titles and descriptions hyphenate, so a long word (Russian, German) breaks at a hyphen
   instead of anywhere.
4. The limit rows on Download settings keep the chip beside the title only while every word
   of the title and description still fits whole, and put it under the title otherwise,
   measuring each child once.
5. A layout model of those rows, with the app's own font where it has the glyphs, across
   phone widths 320-480dp and system font scales 0.85-2.0 in every language: no word is
   broken to make room for a chip, no chip label is cut short, English on an ordinary phone
   looks as it did, and the issue's own case (Russian, 360dp, font x1.25) is fixed where the
   old layout broke "Ограничение" in two.
6. "No limit" and the Appearance On/Off come from strings, not English literals.
7. Every language has every key, the same placeholders as English, escaped apostrophes,
   and no key that was removed; the shortened descriptions stay short in every language.
8. The download sheet's header keeps Play and Download in a row wherever the heading fits
   whole beside them, exactly as before, and otherwise stacks Play above Download or moves
   both under the heading; no word of the heading is broken in any language or font size.
   A tap anywhere in the Title or Author box focuses that box's own field, focus is let go
   only when Details closes, edits stay at sheet level, and the keyboard lift is unchanged.

Both custom layouts use one measure policy made once, and measure each child once.

Run:
    python tools/tests/test_settings_layout_and_text.py
"""

import math
import re
import sys
import unicodedata
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SRC = REPO_ROOT / "app/src/main/java/com/hazel/android"
SCREENS = SRC / "ui/screens"
MORE = SCREENS / "more"
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


def block(text: str, start: str, length: int = 2000) -> str:
    """The source from [start] on, or empty when it is not there."""
    at = text.find(start)
    return text[at:at + length] if at >= 0 else ""


# ===========================================================================
# Strings
# ===========================================================================

LOCALE_DIRS = ["values", "values-de", "values-es", "values-fr", "values-hi", "values-in",
               "values-ja", "values-pt-rBR", "values-ru", "values-zh-rCN"]
STRING_RE = re.compile(r'<string name="([^"]+)"([^>]*)>(.*?)</string>', re.S)
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[sdf]")


def load_strings(path: Path, translatable_only: bool = False) -> dict:
    out = {}
    for name, attrs, value in STRING_RE.findall(read(path)):
        if translatable_only and 'translatable="false"' in attrs:
            continue
        out[name] = value
    return out


def visible(value: str) -> str:
    return re.sub(r"\\(.)", r"\1", re.sub(r"\s+", " ", value)).strip()


# ===========================================================================
# 1. Every full screen scrolls
# ===========================================================================

SCREEN_FUN = re.compile(r"^(?:internal |private )?fun ([A-Z]\w*(?:Screen|Page))\(", re.M)
SCROLLS = re.compile(r"SettingsScreen\(|verticalScroll\(|Lazy(?:Column|VerticalGrid|VerticalStaggeredGrid)\(")
# A splash shows a logo for a moment and has nothing to scroll to.
NO_SCROLL_NEEDED = {"SplashScreen"}


def test_every_screen_scrolls():
    print("\n[1] Every full screen scrolls")
    found = 0
    for path in sorted(SCREENS.rglob("*.kt")):
        text = read(path)
        names = [n for n in SCREEN_FUN.findall(text) if n not in NO_SCROLL_NEEDED]
        if not names:
            continue
        found += 1
        check_true(f"{path.relative_to(SCREENS)} scrolls ({', '.join(names)})", SCROLLS.search(text))
    check_true("Found the app's screens to check", found >= 15)


# ===========================================================================
# 2. The hand-built settings screens use SettingsScreen
# ===========================================================================

def test_shared_settings_screen():
    print("\n[2] Settings screens share one scrolling frame")
    components = read(MORE / "SettingsComponents.kt")
    frame = block(components, "internal fun SettingsScreen(", 1800)
    check_true("SettingsScreen scrolls", ".verticalScroll(rememberScrollState())" in frame)
    check_true("SettingsScreen leaves room under the last row", "Spacer(modifier = Modifier.height(32.dp))" in frame)

    for name, fun in [
        ("StorageLocationsScreen.kt", "fun StorageLocationsScreen("),
        ("AppearanceScreen.kt", "fun AppearanceScreen("),
        ("ToolsScreen.kt", "fun ToolsScreen("),
    ]:
        text = read(MORE / name)
        body = block(text, fun, 3000)
        check_true(f"{name} is drawn in SettingsScreen", "SettingsScreen(" in body)
        check_true(f"{name} has no fixed full-size column of its own",
                   not re.search(r"Column\(\s*modifier = Modifier\s*\.fillMaxSize\(\)", text))
        check_true(f"{name} no longer draws its own back button", "R.drawable.back" not in text)

    storage = read(MORE / "StorageLocationsScreen.kt")
    check_true("Download settings keeps its subtitle",
               "description = stringResource(R.string.storage_locations_subtitle)" in storage)
    check_true("Download settings keeps the storage note at the end",
               storage.find("storage_locations_internal_note") > storage.find("storage_locations_throttled"))
    check_true("Limits are one shared section", "SettingsSection(" in storage
               and "R.string.storage_locations_limits" in storage)
    check_true("Wi-Fi only is the shared switch row", "SwitchSettingRow(" in storage
               and "R.string.storage_locations_wifi_only_description" in storage)
    for setter in ("setWifiOnly", "setSpeedLimit", "setConcurrentFragments", "setThrottledRate",
                   "resetSaveDir", "pickSaveDir(isVideo, card)", "MediaOpener.openLocation("):
        check_true(f"Download settings still calls {setter}", setter in storage)


# ===========================================================================
# 3. Setting rows never hide text
# ===========================================================================

def test_setting_row_text():
    print("\n[3] Setting rows show their whole description")
    components = read(MORE / "SettingsComponents.kt")
    row = block(components, "private fun SettingRow(", 2400)
    check_true("Found SettingRow", row)
    check_true("A description is never cut short",
               "maxLines = if (valueStyle) 2 else Int.MAX_VALUE" in row)
    check_true("No unconditional two-line cap on the summary", "maxLines = 2," not in row)
    check_true("The title hyphenates", "LocalTextStyle.current.copy(hyphens = Hyphens.Auto)" in row)
    check_true("The description hyphenates", "bodySmall.copy(hyphens = Hyphens.Auto)" in row)
    check_true("Rows still toggle and open as before",
               "clickable(enabled = enabled, onClick = onClick)" in row and "trailing()" in row)


# ===========================================================================
# 4. Limit rows: chip beside the title, or under it
# ===========================================================================

def dp_const(text: str, name: str):
    m = re.search(r"private val " + name + r" = (\d+)\.dp", text)
    return int(m.group(1)) if m else None


def test_limit_row_source():
    print("\n[4] Limit rows move the chip under the title rather than break a word")
    storage = read(MORE / "StorageLocationsScreen.kt")
    row = block(storage, "private fun <T> LimitDropdownRow(", 4500)
    layout = block(storage, "private val TextBesideOrAboveChipPolicy", 2000)
    check_true("Limit rows share one measure policy, made once",
               "measurePolicy = TextBesideOrAboveChipPolicy" in storage
               and "private val TextBesideOrAboveChipPolicy = MeasurePolicy {" in storage)
    check_true("Found LimitDropdownRow", row)
    check_true("Found TextBesideOrAboveChip", layout)
    check_true("The row lays its text and chip out with it",
               "TextBesideOrAboveChip(modifier = Modifier.weight(1f))" in row)
    check_true("The title hyphenates", "LocalTextStyle.current.copy(hyphens = Hyphens.Auto)" in row)
    check_true("The description hyphenates", "bodySmall.copy(hyphens = Hyphens.Auto)" in row)
    label = block(row, "selectedLabel,", 400)
    check_true("The chip's label wraps to a second line before it is cut", "maxLines = 2" in label)
    check_true("The chip's label gives way to the arrow", "Modifier.weight(1f, fill = false)" in label)
    check_true("No fixed cap on the chip", "widthIn(max" not in row)
    check_true("The chip still opens the list and saves the pick",
               "onClick = { menuOpen = true }" in row and "onSelect(value)" in row)

    check("Minimum text width beside a chip", dp_const(storage, "MinTextBesideChip"), MIN_TEXT_DP)
    check("Gap beside the chip", dp_const(storage, "ChipGap"), CHIP_GAP_DP)
    check("Gap above a chip moved under", dp_const(storage, "ChipGapBelow"), 8)
    check_true("The chip is measured at most the row's width",
               "chipMeasurable.measure(Constraints(maxWidth = width))" in layout)
    check_true("Each child is measured once per branch",
               layout.count("chipMeasurable.measure(") == 1 and layout.count("textMeasurable.measure(") == 2)
    check_true("Beside only while the longest word fits",
               "val longestWord = textMeasurable.minIntrinsicWidth(Constraints.Infinity)" in layout
               and "if (besideWidth >= maxOf(longestWord, MinTextBesideChip.roundToPx()))" in layout
               and "textMeasurable.measure(Constraints(maxWidth = besideWidth))" in layout)
    check_true("Below: text gets the full width, chip under it",
               "textMeasurable.measure(Constraints(maxWidth = width))" in layout
               and "layout(width, text.height + below + chip.height)" in layout)
    check_true("Placement follows the layout direction", "placeRelative(" in layout and ".place(" not in layout)
    check_true("Chip padding matches the model",
               "padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)" in row)


# ===========================================================================
# 5. Layout model
# ===========================================================================

# Everything in a limit row's width outside the text-and-chip layout: the screen's side
# padding (2 x 20), the card row's padding (2 x 16), the icon (20) and the gap after it (16).
ROW_FIXED_DP = 2 * 20 + 2 * 16 + 20 + 16
MIN_TEXT_DP = 80
CHIP_GAP_DP = 12
# The chip around its label: start and end padding, the gap and the arrow.
CHIP_FRAME_DP = 14 + 8 + 4 + 20
# (size sp, letter spacing sp) of the chip label (bodyMedium), title (bodyLarge in a card)
# and description (bodySmall).
CHIP_TEXT = (14, 0.25)
TITLE_TEXT = (16, 0.15)
DESC_TEXT = (12, 0.4)
WIDTHS_DP = (320, 360, 393, 411, 480)
FONT_SCALES = (0.85, 1.0, 1.15, 1.3, 1.5, 1.8, 2.0)
LIMIT_ROWS = [
    ("storage_locations_speed_limit", "storage_locations_speed_limit_description", "speeds"),
    ("storage_locations_fragments", "storage_locations_fragments_description", "fragments"),
    ("storage_locations_throttled", "storage_locations_throttled_description", "rates"),
]


def load_inter():
    """Advance widths of Inter Medium, the app's font, or None without fontTools."""
    try:
        from fontTools.ttLib import TTFont
    except ImportError:
        return None
    font = TTFont(str(RES / "font/inter_medium.ttf"))
    cmap, hmtx, upm = font.getBestCmap(), font["hmtx"], font["head"].unitsPerEm
    return {cp: hmtx[name][0] / upm for cp, name in cmap.items()}


INTER = load_inter()


def is_cjk(ch: str) -> bool:
    return ord(ch) >= 0x2E80


def glyph_em(ch: str) -> float:
    cp = ord(ch)
    if INTER and cp in INTER:
        return INTER[cp]
    category = unicodedata.category(ch)
    if category == "Mn":            # a mark drawn over its letter
        return 0.0
    if category == "Mc":            # a mark with a little width of its own
        return 0.3
    if 0x0900 <= cp <= 0x097F:      # Devanagari, from the system font
        return 0.62
    if is_cjk(ch):                  # CJK and kana, full width
        return 1.0
    return 0.62                      # Cyrillic or anything else, from the system font


def text_dp(text: str, style, scale: float) -> float:
    size, spacing = style
    return (sum(glyph_em(c) for c in text) * size + len(text) * spacing) * scale


def words(text: str) -> list:
    """What a line may not break inside: a word, or one character of CJK."""
    out = []
    for w in text.split():
        if any(is_cjk(c) for c in w):
            out.extend(c for c in w if not unicodedata.category(c).startswith("P"))
        else:
            out.append(w)
    return out


def longest_word_dp(title: str, desc: str, scale: float) -> float:
    return max([text_dp(w, TITLE_TEXT, scale) for w in words(title)] +
               [text_dp(w, DESC_TEXT, scale) for w in words(desc)] + [0])


def place(screen_dp: int, scale: float, title: str, desc: str, label: str):
    """(mode, text width, label lines) for one limit row, as TextBesideOrAboveChip lays it."""
    width = screen_dp - ROW_FIXED_DP
    chip = min(CHIP_FRAME_DP + text_dp(label, CHIP_TEXT, scale), width)
    beside = width - chip - CHIP_GAP_DP
    lines = math.ceil(text_dp(label, CHIP_TEXT, scale) / max(width - CHIP_FRAME_DP, 1))
    if beside >= max(longest_word_dp(title, desc, scale), MIN_TEXT_DP):
        return "beside", beside, lines
    return "below", width, lines


def old_text_width(screen_dp: int, scale: float, label: str) -> float:
    """The title column before: whatever a chip of any width left over."""
    return screen_dp - ROW_FIXED_DP - CHIP_GAP_DP - (CHIP_FRAME_DP + text_dp(label, CHIP_TEXT, scale))


def chip_labels(repo: str, strings: dict) -> dict:
    fixed = lambda name: re.findall(r'"\w+" to "([^"]+)"', block(repo, f"val {name}", 400))
    return {
        "speeds": [visible(strings.get("storage_locations_no_limit", ""))] + fixed("SPEED_LIMITS"),
        "fragments": ["1", "2", "4", "8"],
        "rates": [visible(strings.get("advanced_sleep_off", ""))] + fixed("THROTTLED_RATES"),
    }


def test_layout_model():
    print(f"\n[5] Layout model ({'Inter metrics' if INTER else 'estimated metrics'}): "
          f"{WIDTHS_DP[0]}-{WIDTHS_DP[-1]}dp, font scale {FONT_SCALES[0]}-{FONT_SCALES[-1]}")
    repo = read(SRC / "data/SettingsRepository.kt")
    check_true("Read the fixed chip labels", len(chip_labels(repo, {})["speeds"]) >= 7)

    for d in LOCALE_DIRS:
        s = load_strings(RES / d / "strings.xml")
        labels = chip_labels(repo, s)
        broken, cut, moved_normal, below = [], [], [], 0
        for title_key, desc_key, kind in LIMIT_ROWS:
            title, desc = visible(s.get(title_key, "")), visible(s.get(desc_key, ""))
            for w in WIDTHS_DP:
                for scale in FONT_SCALES:
                    for label in labels[kind]:
                        mode, text_w, lines = place(w, scale, title, desc, label)
                        if mode == "beside" and text_w < longest_word_dp(title, desc, scale):
                            broken.append(f"{title_key}@{w}dp x{scale}")
                        if lines > 2:
                            cut.append(f"{label}@{w}dp x{scale}")
                        if mode == "below":
                            below += 1
                            if d == "values" and scale <= 1.0 and w >= 360:
                                moved_normal.append(f"{title_key}/{label}@{w}dp")
        check(f"{d}: no word broken to fit a chip beside it", broken, [])
        check(f"{d}: no chip label cut short (wraps at most to two lines)", cut, [])
        if d == "values":
            check("English: on a 360dp+ phone at normal font every chip stays beside its title",
                  moved_normal, [])
        print(f"        {d}: chip moved under the title in {below} of "
              f"{sum(len(labels[k]) for _, _, k in LIMIT_ROWS) * len(WIDTHS_DP) * len(FONT_SCALES)} cases")

    # The issue's own phone: 360dp wide, font x1.25, Russian, with the English "No limit"
    # chip it showed. The old layout left the title too narrow for "Ограничение"; the new one
    # never does.
    ru = load_strings(RES / "values-ru/strings.xml")
    title = visible(ru["storage_locations_speed_limit"])
    word = max(words(title), key=lambda w: text_dp(w, TITLE_TEXT, 1.25))
    need = text_dp(word, TITLE_TEXT, 1.25)
    old = old_text_width(360, 1.25, "No limit")
    check_true(f"Model reproduces the issue: old title column {old:.0f}dp < '{word}' {need:.0f}dp", old < need)
    mode, text_w, _ = place(360, 1.25, title, visible(ru["storage_locations_speed_limit_description"]),
                            visible(ru["storage_locations_no_limit"]))
    check_true(f"New layout keeps '{word}' whole ({mode}, {text_w:.0f}dp)", text_w >= need)


# ===========================================================================
# 6. No English literals for the new labels
# ===========================================================================

def test_no_literals():
    print("\n[6] Labels come from strings")
    repo = read(SRC / "data/SettingsRepository.kt")
    storage = read(MORE / "StorageLocationsScreen.kt")
    appearance = read(MORE / "AppearanceScreen.kt")
    check_true("No 'No limit' literal in SettingsRepository", '"No limit"' not in repo)
    check_true("speedLimitLabel is gone with its literal", "fun speedLimitLabel" not in repo)
    speeds = block(repo, "val SPEED_LIMITS", 400)
    check_true("SPEED_LIMITS holds only real ceilings", speeds and '"" to' not in speeds)
    check_true("The screen names no limit from strings",
               'listOf("" to noLimit) + SettingsRepository.SPEED_LIMITS' in storage
               and "R.string.storage_locations_no_limit" in storage)
    check_true("An unset limit still shows as no limit",
               "?: speedLimit.ifBlank { noLimit }" in storage)
    check_true("Appearance On/Off come from strings",
               "R.string.appearance_on" in appearance and "R.string.appearance_off" in appearance
               and '"On"' not in appearance and '"Off"' not in appearance)
    check_true("Appearance still toggles the theme and opens the accent picker",
               appearance.count("onToggleTheme()") >= 2 and "AccentPickerDialog(" in appearance)


# ===========================================================================
# 7. Strings in every language
# ===========================================================================

SHORTENED = [
    "storage_locations_internal_note", "listing_source_newpipe_description",
    "listing_source_ytdlp_description", "advanced_extra_args_hint", "options_cut_precise_summary",
    "options_subtitles_languages_hint", "advanced_auto_po_tokens_summary", "advanced_po_tokens_hint",
    "backup_description", "cleanup_category_link_reads_description",
    "fetch_settings_force_ipv4_description", "fetch_settings_engine_newpipe_summary",
    "cleanup_category_engine_description", "advanced_description", "more_battery_body",
    "cookies_description", "cleanup_header_description", "advanced_no_check_certificates_summary",
    "processing_description", "fetch_settings_screen_description", "fetch_mode_fast_description",
    "advanced_impersonate_none", "cleanup_category_partial_downloads_description",
    "cleanup_category_conversions_description", "guide_step_share_instant", "guide_step_share_sheet",
    "guide_step_paste_link", "guide_step_battery_unrestricted", "guide_step_pick_format",
]
NEW_KEYS = ["storage_locations_no_limit", "appearance_on", "appearance_off"]
REMOVED_KEYS = ["appearance_back", "tools_back", "storage_locations_back"]
# English is held to about one line and a half of a settings row; translations get room
# for languages that simply need more letters.
EN_MAX = 90
TRANSLATED_MAX = 115


def test_strings():
    print("\n[7] Strings in every language")
    en = load_strings(RES / "values/strings.xml", translatable_only=True)
    for key in NEW_KEYS:
        check_true(f"English has {key}", key in en)
    for key in SHORTENED:
        text = visible(en.get(key, ""))
        check_true(f"English {key} is short ({len(text)} <= {EN_MAX})", text and len(text) <= EN_MAX)

    code = "\n".join(read(p) for p in SRC.rglob("*.kt"))
    for key in REMOVED_KEYS:
        check_true(f"{key} is not used in code", f"R.string.{key}" not in code)
    for key in NEW_KEYS + SHORTENED:
        check_true(f"{key} is used in code", f"R.string.{key}" in code)

    for d in LOCALE_DIRS:
        path = RES / d / "strings.xml"
        raw = read(path)
        strings = load_strings(path)
        for key in REMOVED_KEYS:
            check_true(f"{d}: {key} removed", key not in strings)
        if d == "values":
            continue
        missing = sorted(k for k in en if k not in strings)
        check(f"{d}: every English key is translated", missing, [])
        wrong_args = sorted(k for k in en if k in strings
                            and sorted(PLACEHOLDER.findall(en[k])) != sorted(PLACEHOLDER.findall(strings[k])))
        check(f"{d}: placeholders match English", wrong_args, [])
        unescaped = sorted(k for k, v in strings.items() if re.search(r"(?<!\\)'", v))
        check(f"{d}: apostrophes are escaped", unescaped, [])
        too_long = sorted(k for k in SHORTENED if len(visible(strings.get(k, ""))) > TRANSLATED_MAX)
        check(f"{d}: shortened descriptions stay short", too_long, [])
        check_true(f"{d}: file is well formed", raw.strip().endswith("</resources>"))


# ===========================================================================
# 8. Download sheet: header actions and the title / author fields
# ===========================================================================

SHEET = SCREENS / "download/FormatSheet.kt"
HEADER = SCREENS / "download/SheetHeaderLayout.kt"
KEYBOARD = SRC / "ui/components/KeyboardOverSheet.kt"
HEADING_TEXT = (24, 0.0)      # headlineSmall, bold
HEADING_BOLD = 1.06           # bold runs a little wider than the medium metrics
SUBTITLE_TEXT = (12, 0.4)     # bodySmall
BUTTON_TEXT = (14, 0.1)       # labelLarge
SHEET_SIDE_DP = 2 * 20


def header_mode(screen_dp: int, scale: float, heading: str, subtitle: str, play: str, download: str,
                has_play: bool = True):
    """('row' | 'stack' | 'below', heading width, longest word) as SheetHeaderLayout lays it."""
    width = screen_dp - SHEET_SIDE_DP
    play_w = 2 * 18 + text_dp(play, BUTTON_TEXT, scale)
    download_w = 2 * 20 + 18 + 8 + text_dp(download, BUTTON_TEXT, scale)
    actions = [play_w, download_w] if has_play else [download_w]
    row = sum(actions) + 10 * (len(actions) - 1)
    stack = max(actions)
    longest = max([text_dp(w, HEADING_TEXT, scale) * HEADING_BOLD for w in words(heading)] +
                  [text_dp(w, SUBTITLE_TEXT, scale) for w in words(subtitle)])
    if width - row >= longest:
        return "row", width - row, longest
    if len(actions) > 1 and width - stack >= longest:
        return "stack", width - stack, longest
    return "below", width, longest


def test_sheet_header_and_fields():
    print("\n[8] Download sheet: header actions and the title / author fields")
    sheet = read(SHEET)
    header = read(HEADER)
    keyboard = read(KEYBOARD)

    head = block(sheet, "// ── Header: title block on the left", 6000)
    check_true("The header is laid out by SheetHeaderLayout", "SheetHeaderLayout {" in head)
    check_true("The heading no longer takes a weight beside the buttons",
               "Column(modifier = Modifier.weight(1f))" not in head[:600])
    check_true("Play is still offered only for media on the device", "if (onPlay != null)" in head)
    check_true("Play comes before the download action", head.find("R.string.format_sheet_play")
               < head.find("R.string.format_sheet_download"))
    check_true("Download still sends title, author and the one-off options",
               "onDownload(it, audioLanguage, title.trim(), author.trim(), sent)" in head)
    check_true("Header: actions measured once, at most the sheet's width",
               "measurables.drop(1).map { it.measure(Constraints(maxWidth = width)) }" in header)
    check_true("Header: beside only while the heading's longest word fits",
               "val longestWord = heading.minIntrinsicWidth(Constraints.Infinity)" in header
               and "width - rowWidth >= longestWord" in header
               and "width - stackWidth >= longestWord" in header)
    check_true("Header: stacked only when there are two actions", "actions.size > 1 &&" in header)
    check_true("Header: one measure policy, made once",
               "measurePolicy = SheetHeaderPolicy" in header
               and "private val SheetHeaderPolicy = MeasurePolicy {" in header)
    check_true("Header: placement follows the layout direction",
               "placeRelative(" in header and ".place(" not in header)

    for d in LOCALE_DIRS:
        s = load_strings(RES / d / "strings.xml")
        heading, subtitle = visible(s["format_sheet_title"]), visible(s["format_sheet_subtitle"])
        play, download = visible(s["format_sheet_play"]), visible(s["format_sheet_download"])
        broken, changed = [], []
        for w in WIDTHS_DP:
            for scale in FONT_SCALES:
                for has_play in (True, False):
                    mode, room, longest = header_mode(w, scale, heading, subtitle, play, download, has_play)
                    if mode != "below" and room < longest:
                        broken.append(f"{w}dp x{scale}")
                    # Wherever the old row left the heading whole, the header looks as before.
                    old_room = header_mode(w, scale, heading, subtitle, play, download, has_play)[2]
                    width = w - SHEET_SIDE_DP
                    old_row = (2 * 18 + text_dp(play, BUTTON_TEXT, scale) + 10 if has_play else 0)                         + 2 * 20 + 18 + 8 + text_dp(download, BUTTON_TEXT, scale)
                    if width - old_row >= old_room and mode != "row":
                        changed.append(f"{w}dp x{scale}")
        check(f"{d}: the sheet heading never has a word broken", broken, [])
        check(f"{d}: wherever the old header fit, it looks exactly as before", changed, [])
    en = load_strings(RES / "values/strings.xml")
    en_args = [visible(en[k]) for k in ("format_sheet_title", "format_sheet_subtitle",
                                        "format_sheet_play", "format_sheet_download")]
    moved = [f"{w}dp" for w in (393, 411, 480) if header_mode(w, 1.0, *en_args)[0] != "row"]
    check("English: Play and Download stay in a row beside the heading on a normal phone", moved, [])
    for w in WIDTHS_DP:
        check_true(f"English without Play keeps the row at {w}dp x1.0",
                   header_mode(w, 1.0, *en_args, has_play=False)[0] == "row")
    ru = load_strings(RES / "values-ru/strings.xml")
    ru_args = [visible(ru[k]) for k in ("format_sheet_title", "format_sheet_subtitle",
                                        "format_sheet_play", "format_sheet_download")]
    mode, room, longest = header_mode(400, 1.35, *ru_args)
    check_true(f"The reported case (Russian, font x1.35) keeps 'Загрузка' whole ({mode})", room >= longest)

    field = block(sheet, "internal fun EditableField(", 2200)
    check_true("Each field has its own focus requester", "val focusRequester = remember { FocusRequester() }" in field)
    check_true("A tap anywhere in the box focuses that box's field",
               "focusRequester.requestFocus()" in field and ".focusRequester(focusRequester)" in field)
    check_true("The tap brings a put-away keyboard back", "keyboardController?.show()" in field)
    check_true("The box tap draws no ripple over the field", "indication = null" in field)
    details = block(sheet, "// ── Details: what the file is called ──", 1400)
    check_true("Title and author are two separate fields",
               details.count("EditableField(") == 2
               and "value = title," in details and "onValueChange = { title = it }" in details
               and "value = author," in details and "onValueChange = { author = it }" in details)
    check_true("Title and author live at sheet level, not inside the section",
               "var title by remember(info.url)" in sheet and "var author by remember(info.url)" in sheet
               and sheet.find("var title by remember(info.url)") < sheet.find("// ── Details"))
    toggle = block(sheet, "fun toggle(section: String)", 500)
    check_true("Focus is let go only when Details is the section closing",
               "if (SECTION_DETAILS in openList && SECTION_DETAILS !in next) focusManager.clearFocus()" in toggle)
    check_true("Sections still open and close as before",
               "takeLast(MAX_OPEN_SECTIONS)" in toggle and 'openSections = next.joinToString(",")' in toggle)
    check_true("The keyboard lift is unchanged",
               "Modifier.keptAboveKeyboard(keyboard)" in details
               and "keyboard.fieldBottom = SheetKeyboard.NONE" in keyboard
               and "onDispose" not in block(keyboard, "fun Modifier.keptAboveKeyboard(", 1200))


def main():
    print("=" * 70)
    print("  Hazel Settings Layout & Text Test Harness")
    print("=" * 70)

    test_every_screen_scrolls()
    test_shared_settings_screen()
    test_setting_row_text()
    test_limit_row_source()
    test_layout_model()
    test_no_literals()
    test_strings()
    test_sheet_header_and_fields()

    print("\n" + "=" * 70)
    print(f"  TOTAL CHECKS: {PASS_COUNT + FAIL_COUNT}")
    print(f"  PASSED:       {PASS_COUNT}")
    print(f"  FAILED:       {FAIL_COUNT}")
    print("=" * 70)

    return 0 if FAIL_COUNT == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
