package com.hazel.android.download

/**
 * The `--parse-metadata` values that write an edited title and author into the file's tags.
 *
 * yt-dlp reads each as `FROM:TO`, splitting on the first unescaped colon, and FROM as an
 * output template, so the value is written as one: see [asLiteralFrom].
 *
 * The author is also copied into the `artist` metadata slot, which is what
 * `FFmpegMetadataPP` writes as the ID3 / Vorbis / MP4 artist tag. Without this,
 * music platforms that populate `artist` instead of `uploader` (JioSaavn, SoundCloud,
 * Bandcamp) would leave audio files with no artist tag at all.
 */
internal fun metadataParseArgs(title: String, author: String): List<String> = buildList {
    if (title.isNotBlank()) {
        add("${title.asLiteralFrom()}:%(title)s ")
    }
    if (author.isNotBlank()) {
        add("${author.asLiteralFrom()}:%(uploader)s ")
        add("%(uploader)s:%(artist)s")
    }
}

/**
 * [this] as the FROM of a `--parse-metadata` that sets a field to exactly this text.
 *
 * Colons are escaped so the FROM:TO split passes them through, and percent signs so a
 * title holding "%(" is not read as a field. A FROM made only of letters is taken by
 * yt-dlp as the name of a field rather than as text, so a one-word title such as
 * "Flickermood" read the missing field "Flickermood" and was saved as "NA". The space
 * added at the end keeps it text, and the TO it is paired with ends in the same space,
 * so the space is matched off again rather than kept.
 */
internal fun String.asLiteralFrom(): String =
    replace("%", "%%").replace(":", """\:""") + " "
