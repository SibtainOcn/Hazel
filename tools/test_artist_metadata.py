#!/usr/bin/env python3
"""
Test harness for Hazel-DLP artist metadata resolution logic.

Simulates the same resolution order as MediaProbe.resolveArtist + firstNonBlank
to verify the logic independently of the Kotlin/Android toolchain. Also validates
that the applyMetadata colon-escaping approach works correctly.

Run:  python test_artist_metadata.py
Exit: 0 on success, 1 on any failure.
"""

import sys


# ---------- Port of the Kotlin resolution helpers ----------

def resolve_artist(obj: dict) -> str:
    """
    Port of MediaProbe.resolveArtist().
    Checks 'artists' (JSONArray) first, then 'artist' (String).
    """
    artists = obj.get("artists")
    if isinstance(artists, list) and len(artists) > 0:
        joined = ", ".join(
            s for s in artists
            if isinstance(s, str) and s.strip() and s != "null"
        )
        if joined:
            return joined
    artist = obj.get("artist", "")
    if isinstance(artist, str) and artist.strip() and artist != "null":
        return artist
    return ""


def first_non_blank(*values: str) -> str:
    """Port of MediaProbe.firstNonBlank()."""
    for v in values:
        if isinstance(v, str) and v.strip() and v != "null":
            return v
    return ""


def resolve_uploader(media: dict) -> str:
    """
    Port of the uploader resolution in MediaProbe.parse(), applied to a single
    JSON object (no root fallback needed for these tests).
    """
    return first_non_blank(
        resolve_artist(media),
        media.get("uploader", ""),
        media.get("channel", ""),
        media.get("creator", ""),
        media.get("uploader_id", ""),
    )


def resolve_uploader_entry(entry: dict) -> str:
    """Port of the uploader resolution in MediaProbe.toEntry()."""
    return first_non_blank(
        resolve_artist(entry),
        entry.get("uploader", ""),
        entry.get("channel", ""),
        entry.get("uploader_id", ""),
    )


# ---------- applyMetadata simulation ----------

def apply_metadata_args(title: str, author: str) -> list[tuple[str, str]]:
    """
    Simulates the yt-dlp CLI arguments that the rewritten applyMetadata() would
    produce. Returns a list of (option, value) tuples.

    Colons in the FROM portion of --parse-metadata are escaped as \\: so yt-dlp's
    first-colon split treats them as literal characters.
    """
    if not title.strip() and not author.strip():
        return []

    args: list[tuple[str, str]] = [("--embed-metadata", "")]

    if title.strip():
        escaped = title.replace(":", "\\:")
        args.append(("--parse-metadata", f"{escaped}:%(title)s"))

    if author.strip():
        escaped = author.replace(":", "\\:")
        args.append(("--parse-metadata", f"{escaped}:%(uploader)s"))
        # Map uploader -> artist tag
        args.append(("--parse-metadata", "%(uploader)s:%(artist)s"))

    return args


def parse_metadata_from(value: str) -> str:
    """
    Simulates yt-dlp's first-unescaped-colon split on a --parse-metadata value.
    Returns the FROM portion with escape sequences resolved.
    """
    # Walk the string looking for the first unescaped colon
    i = 0
    from_chars = []
    while i < len(value):
        if value[i] == '\\' and i + 1 < len(value) and value[i + 1] == ':':
            # Escaped colon -> literal colon
            from_chars.append(':')
            i += 2
        elif value[i] == ':':
            # First unescaped colon -> split point
            break
        else:
            from_chars.append(value[i])
            i += 1
    return ''.join(from_chars)


# ---------- Test cases ----------

PASS = 0
FAIL = 0


def check(name: str, got, expected):
    global PASS, FAIL
    if got == expected:
        PASS += 1
        print(f"  PASS  {name}")
    else:
        FAIL += 1
        print(f"  FAIL  {name}")
        print(f"    expected: {expected!r}")
        print(f"    got:      {got!r}")


# ===== Artist Resolution (MediaProbe.parse) =====

def test_artist_preferred_over_uploader():
    """JioSaavn shape: artist holds performer, channel holds label."""
    media = {
        "title": "Tum Hi Ho",
        "artist": "Arijit Singh",
        "channel": "T-Series",
        "uploader": "T-Series",
    }
    check(
        "artist preferred over uploader/channel",
        resolve_uploader(media),
        "Arijit Singh",
    )


def test_artists_array_joined():
    """Multi-artist track: artists array joined with commas."""
    media = {
        "title": "Collab Track",
        "artists": ["Artist A", "Artist B", "Artist C"],
        "channel": "Music Label",
    }
    check(
        "artists array joined as comma-separated string",
        resolve_uploader(media),
        "Artist A, Artist B, Artist C",
    )


def test_uploader_fallback():
    """YouTube shape: no artist fields, uploader used."""
    media = {
        "title": "Tutorial",
        "uploader": "TechChannel",
        "channel": "TechChannel",
    }
    check(
        "uploader fallback when no artist fields",
        resolve_uploader(media),
        "TechChannel",
    )


def test_blank_artists_cleaned():
    """Blank and 'null' entries in artists array are skipped."""
    media = {
        "title": "Edge",
        "artists": ["", "Real Artist", "null"],
    }
    check(
        "blank/null artists filtered out",
        resolve_uploader(media),
        "Real Artist",
    )


def test_artists_array_wins_over_artist_string():
    """When both artists (array) and artist (string) exist, array wins."""
    media = {
        "title": "Both",
        "artist": "Solo Name",
        "artists": ["Arr A", "Arr B"],
        "uploader": "Label Inc",
    }
    check(
        "artists array wins over artist string",
        resolve_uploader(media),
        "Arr A, Arr B",
    )


def test_empty_artists_array_falls_to_artist():
    """An empty artists array should fall through to artist string."""
    media = {
        "title": "Empty arr",
        "artists": [],
        "artist": "Fallback Singer",
        "uploader": "Label",
    }
    check(
        "empty artists array falls to artist string",
        resolve_uploader(media),
        "Fallback Singer",
    )


def test_no_artist_no_uploader():
    """No artist and no uploader returns blank, channel used."""
    media = {
        "title": "Orphan",
        "channel": "Some Channel",
    }
    check(
        "no artist/uploader -> channel",
        resolve_uploader(media),
        "Some Channel",
    )


def test_all_blank():
    """Everything blank returns empty string."""
    media = {"title": "Nothing"}
    check(
        "all blank -> empty string",
        resolve_uploader(media),
        "",
    )


def test_existing_complete_payload_still_works():
    """The original 'complete' test payload must still resolve uploader correctly."""
    media = {
        "id": "kUox2TPnpzo",
        "title": "Games Everyone Hated",
        "uploader": "L321",
        "duration": 1187,
    }
    check(
        "existing complete payload: uploader = L321",
        resolve_uploader(media),
        "L321",
    )


def test_soundcloud_artist():
    """SoundCloud shape: artist present, no uploader."""
    media = {
        "title": "Lo-Fi Beat",
        "artist": "ChillProducer",
        "uploader_id": "chillproducer",
    }
    check(
        "SoundCloud: artist preferred over uploader_id",
        resolve_uploader(media),
        "ChillProducer",
    )


def test_bandcamp_artists_array():
    """Bandcamp shape: artists array with one entry."""
    media = {
        "title": "Album Track",
        "artists": ["Indie Band"],
        "uploader": "indie-band",
    }
    check(
        "Bandcamp: single-element artists array",
        resolve_uploader(media),
        "Indie Band",
    )


# ===== Entry Resolution (MediaProbe.toEntry) =====

def test_entry_resolution_with_artist():
    """toEntry() also uses artist before uploader."""
    entry = {
        "title": "Song",
        "artist": "Singer",
        "uploader": "Record Co",
        "channel": "Record Co",
    }
    check(
        "toEntry: artist preferred over uploader",
        resolve_uploader_entry(entry),
        "Singer",
    )


def test_entry_resolution_fallback():
    """toEntry() falls back to uploader when no artist."""
    entry = {
        "title": "Video",
        "uploader": "Creator123",
    }
    check(
        "toEntry: uploader fallback",
        resolve_uploader_entry(entry),
        "Creator123",
    )


# ===== applyMetadata Colon Handling (Issue #2) =====

def test_colon_in_title_produces_escaped_arg():
    """A title with a colon is escaped, not skipped."""
    args = apply_metadata_args("Episode 1: Pilot", "")
    pm = [(k, v) for k, v in args if k == "--parse-metadata"]
    check(
        "title with colon produces --parse-metadata",
        len(pm) > 0,
        True,
    )
    # The FROM portion, when parsed, should resolve back to the original title
    from_part = parse_metadata_from(pm[0][1])
    check(
        "colon in title round-trips correctly",
        from_part,
        "Episode 1: Pilot",
    )


def test_colon_in_author_produces_escaped_arg():
    """An author with a colon is escaped, not skipped."""
    args = apply_metadata_args("", "Artist A : Feat B")
    pm = [(k, v) for k, v in args if k == "--parse-metadata" and "uploader" in v]
    check(
        "author with colon produces --parse-metadata (uploader)",
        len(pm) > 0,
        True,
    )
    from_part = parse_metadata_from(pm[0][1])
    check(
        "colon in author round-trips correctly",
        from_part,
        "Artist A : Feat B",
    )


def test_both_colons_produce_args():
    """Both title and author with colons: nothing is skipped."""
    args = apply_metadata_args("Movie: Soundtrack", "Artist: Remix")
    pm = [(k, v) for k, v in args if k == "--parse-metadata"]
    # title -> %(title)s, author -> %(uploader)s, uploader -> %(artist)s = 3
    check(
        "both colons -> three --parse-metadata args",
        len(pm),
        3,
    )


def test_blank_title_and_author_no_args():
    """Blank title and author produce no arguments at all."""
    args = apply_metadata_args("", "")
    check(
        "blank title+author -> no args",
        args,
        [],
    )


def test_author_produces_artist_mapping():
    """Author mapping produces %(uploader)s:%(artist)s parse-metadata."""
    args = apply_metadata_args("", "Some Author")
    pm_values = [v for k, v in args if k == "--parse-metadata"]
    check(
        "author -> %(uploader)s:%(artist)s mapping present",
        "%(uploader)s:%(artist)s" in pm_values,
        True,
    )


def test_title_without_colon_unchanged():
    """A title without colons passes through unmodified."""
    args = apply_metadata_args("Simple Title", "")
    pm = [(k, v) for k, v in args if k == "--parse-metadata"]
    check(
        "title without colon -> no escaping needed",
        pm[0][1],
        "Simple Title:%(title)s",
    )


def test_multiple_colons_all_escaped():
    """Multiple colons in a value are all escaped."""
    args = apply_metadata_args("A:B:C:D", "")
    pm = [(k, v) for k, v in args if k == "--parse-metadata"]
    check(
        "multiple colons all escaped",
        pm[0][1],
        r"A\:B\:C\:D:%(title)s",
    )
    from_part = parse_metadata_from(pm[0][1])
    check(
        "multiple colons round-trip correctly",
        from_part,
        "A:B:C:D",
    )


# ===== Regression: existing behaviour preserved =====

def test_normal_title_and_author():
    """Normal (no colon) title and author still work."""
    args = apply_metadata_args("My Video", "My Channel")
    pm_values = [v for k, v in args if k == "--parse-metadata"]
    check(
        "normal title -> correct --parse-metadata",
        "My Video:%(title)s" in pm_values,
        True,
    )
    check(
        "normal author -> correct --parse-metadata",
        "My Channel:%(uploader)s" in pm_values,
        True,
    )
    check(
        "artist mapping present",
        "%(uploader)s:%(artist)s" in pm_values,
        True,
    )


def test_only_title_no_artist_mapping():
    """When only title is provided, no artist mapping is added."""
    args = apply_metadata_args("Title Only", "")
    pm_values = [v for k, v in args if k == "--parse-metadata"]
    check(
        "title only -> no artist mapping",
        "%(uploader)s:%(artist)s" not in pm_values,
        True,
    )


def test_only_author_produces_embed_and_mapping():
    """When only author is provided, both uploader and artist tags are set."""
    args = apply_metadata_args("", "Author Only")
    options = [k for k, v in args]
    check(
        "author only -> --embed-metadata present",
        "--embed-metadata" in options,
        True,
    )
    pm_values = [v for k, v in args if k == "--parse-metadata"]
    check(
        "author only -> uploader mapping",
        "Author Only:%(uploader)s" in pm_values,
        True,
    )
    check(
        "author only -> artist mapping",
        "%(uploader)s:%(artist)s" in pm_values,
        True,
    )


# ---------- Format Parsing Logic (Audio Stream Detection) ----------

AUDIO_CONTAINERS = {"m4a", "mp3", "opus", "flac", "wav", "aac", "ogg"}
VIDEO_CONTAINERS = {"mp4", "webm", "mkv", "mov", "flv", "avi", "3gp", "ts"}

def parse_format_type(fmt: dict) -> tuple[bool, bool]:
    """
    Port of MediaProbe.toFormat hasVideo / hasAudio detection.
    Returns (has_video, has_audio).
    """
    vcodec = fmt.get("vcodec")
    if vcodec == "none" or vcodec == "null" or not vcodec:
        vcodec = None
    acodec = fmt.get("acodec")
    if acodec == "none" or acodec == "null" or not acodec:
        acodec = None

    has_video_key = "vcodec" in fmt
    has_audio_key = "acodec" in fmt
    ext = fmt.get("ext", "")
    audio_ext = fmt.get("audio_ext")
    if audio_ext == "none" or audio_ext == "null":
        audio_ext = None
    resolution = str(fmt.get("resolution", "")).lower()
    abr = fmt.get("abr")
    if abr is not None and (not isinstance(abr, (int, float)) or abr <= 0):
        abr = None

    codecs_unknown = not has_video_key and not has_audio_key
    has_video = bool(
        vcodec is not None if vcodec else
        False if has_video_key else
        (fmt.get("height", 0) > 0 or fmt.get("fps", 0) > 0 or ext in VIDEO_CONTAINERS) if codecs_unknown else
        False
    )
    has_audio = (
        True if acodec is not None else
        False if has_audio_key else
        True if (audio_ext is not None or "audio only" in resolution or abr is not None or ext in AUDIO_CONTAINERS) else
        True if not has_video else
        False
    )
    return (has_video, has_audio)


def test_jiosaavn_audio_format_detected_without_acodec():
    fmt_128 = {
        "format_id": "128", "ext": "m4a", "abr": 128, "tbr": 128,
        "vcodec": "none", "audio_ext": "m4a", "video_ext": "none",
        "resolution": "audio only", "filesize_approx": 4704000
    }
    fmt_320 = {
        "format_id": "320", "ext": "m4a", "abr": 320, "tbr": 320,
        "vcodec": "none", "audio_ext": "m4a", "video_ext": "none",
        "resolution": "audio only", "filesize_approx": 11760000
    }
    has_video_128, has_audio_128 = parse_format_type(fmt_128)
    check("JioSaavn 128 hasAudio", has_audio_128, True)
    check("JioSaavn 128 hasVideo", has_video_128, False)

    has_video_320, has_audio_320 = parse_format_type(fmt_320)
    check("JioSaavn 320 hasAudio", has_audio_320, True)
    check("JioSaavn 320 hasVideo", has_video_320, False)


def test_standard_video_and_audio_streams_still_separated():
    video_only = {"format_id": "137", "ext": "mp4", "vcodec": "avc1.640028", "acodec": "none", "height": 1080}
    audio_only = {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "abr": 128}
    muxed = {"format_id": "22", "ext": "mp4", "vcodec": "avc1.64001F", "acodec": "mp4a.40.2", "height": 720}

    v_vid, v_aud = parse_format_type(video_only)
    check("Video-only stream hasVideo", v_vid, True)
    check("Video-only stream hasAudio", v_aud, False)

    a_vid, a_aud = parse_format_type(audio_only)
    check("Audio-only stream hasVideo", a_vid, False)
    check("Audio-only stream hasAudio", a_aud, True)

    m_vid, m_aud = parse_format_type(muxed)
    check("Muxed stream hasVideo", m_vid, True)
    check("Muxed stream hasAudio", m_aud, True)



# ---------- Runner ----------

def main():
    print("=" * 65)
    print("  Hazel-DLP Artist Metadata Test Harness")
    print("=" * 65)

    print("\n--- Artist Resolution (MediaProbe.parse) ---")
    test_artist_preferred_over_uploader()
    test_artists_array_joined()
    test_uploader_fallback()
    test_blank_artists_cleaned()
    test_artists_array_wins_over_artist_string()
    test_empty_artists_array_falls_to_artist()
    test_no_artist_no_uploader()
    test_all_blank()
    test_existing_complete_payload_still_works()
    test_soundcloud_artist()
    test_bandcamp_artists_array()

    print("\n--- Entry Resolution (MediaProbe.toEntry) ---")
    test_entry_resolution_with_artist()
    test_entry_resolution_fallback()

    print("\n--- applyMetadata Colon Handling (Issue #2) ---")
    test_colon_in_title_produces_escaped_arg()
    test_colon_in_author_produces_escaped_arg()
    test_both_colons_produce_args()
    test_blank_title_and_author_no_args()
    test_author_produces_artist_mapping()
    test_title_without_colon_unchanged()
    test_multiple_colons_all_escaped()

    print("\n--- Audio Format Resolution (Issue #3) ---")
    test_jiosaavn_audio_format_detected_without_acodec()
    test_standard_video_and_audio_streams_still_separated()

    print("\n--- Regression: Existing Behaviour ---")
    test_normal_title_and_author()
    test_only_title_no_artist_mapping()
    test_only_author_produces_embed_and_mapping()

    print("\n" + "=" * 65)
    total = PASS + FAIL
    print(f"  Results: {PASS}/{total} passed, {FAIL} failed")
    print("=" * 65)

    return 1 if FAIL > 0 else 0


if __name__ == "__main__":
    sys.exit(main())
