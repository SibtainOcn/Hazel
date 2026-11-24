package com.hazel.android.download

/**
 * Makes a user's output template safe to hand to yt-dlp for a single file in the work folder.
 *
 * Fields (`%(…)s`, with any format inside the brackets) are kept exactly. Outside them, path
 * separators and characters Android storage rejects become `_`, so the file never lands in a
 * subfolder the save step does not look in; a lone `%` is escaped so yt-dlp does not fail on
 * it; a leading dot or space is dropped, since hidden files are cleared as leftovers; and the
 * extension is added when the template has none.
 */
object FilenameTemplate {

    private const val EXT = "%(ext)s"
    private const val REJECTED = "/\\:*?\"<>|"

    /** Fields a user is offered to insert, in yt-dlp's own names. */
    val FIELDS = listOf(
        "title", "uploader", "channel", "id", "upload_date", "release_date", "release_year",
        "duration_string", "playlist", "playlist_index", "playlist_count", "autonumber",
        "resolution", "height", "fps", "vcodec", "acodec", "abr", "format_id", "format_note",
        "artist", "album", "track", "track_number", "creator", "series", "season_number",
        "episode_number", "uploader_id", "channel_id", "display_id", "fulltitle", "alt_title",
        "extractor", "webpage_url_domain", "view_count", "like_count", "comment_count",
        "language", "timestamp", "epoch", "live_status", "availability"
    )

    /** Whole templates offered as starting points. */
    val PRESETS = listOf(
        DownloadOptions.DEFAULT_FILENAME_TEMPLATE,
        "%(uploader)s - %(title)s.%(ext)s",
        "%(upload_date>%Y-%m-%d)s %(title)s.%(ext)s",
        "%(playlist_index&{} - |)s%(title)s.%(ext)s",
        "%(title)s [%(id)s].%(ext)s",
        "%(title)s (%(resolution)s).%(ext)s",
        "%(artist,uploader)s - %(track,title)s.%(ext)s"
    )

    fun field(name: String) = "%($name)s"

    private val PLAIN_FIELD = Regex("""%\(([A-Za-z0-9_,]+)\)s""")

    /**
     * Caps free-text fields at a byte length (`%(title).120B`), alternatives such as
     * `%(track,title)s` included, so a long site text cannot pass the 255 bytes a file
     * name may have. A field with a format of its own is left as written.
     */
    fun limitBytes(template: String, limits: Map<String, Int>): String =
        PLAIN_FIELD.replace(template) { m ->
            val bytes = m.groupValues[1].split(',').mapNotNull { limits[it] }.maxOrNull()
            if (bytes == null) m.value else "%(${m.groupValues[1]}).${bytes}B"
        }

    /** True for an identifier field (`id`, `uploader_id`, `playlist_id`), which yt-dlp does not strip leading dots from. */
    fun isIdKey(key: String): Boolean = key == "id" || key.endsWith("_id")

    /** [limitBytes] for a folder name; fields the name limits don't list get [otherBytes], identifiers [idBytes]. */
    fun limitFolderBytes(template: String, limits: Map<String, Int>, otherBytes: Int, idBytes: Int): String =
        PLAIN_FIELD.replace(template) { m ->
            val keys = m.groupValues[1].split(',')
            val bytes = keys.mapNotNull { limits[it] }.maxOrNull()
                ?: if (keys.any(::isIdKey)) idBytes else otherBytes
            "%(${m.groupValues[1]}).${bytes}B"
        }

    fun normalize(template: String): String {
        var result = escape(template).trim().trimStart('.', ' ')
        if (result.isEmpty()) return DownloadOptions.DEFAULT_FILENAME_TEMPLATE
        if (EXT !in result) result = result.trimEnd('.', ' ') + ".$EXT"
        return result
    }

    /** One folder name of a user's template: rejected characters and a lone `%` escaped, dots and spaces at both ends dropped. */
    fun segment(template: String): String =
        escape(template).trim().trimStart('.', ' ').trimEnd('.', ' ')

    /** A folder name after app text went into it: dots and spaces at the ends go and an empty result becomes `_`, so it cannot be `..`. */
    fun containFolder(name: String): String =
        name.trim().trim('.', ' ').ifEmpty { "_" }

    /** Keeps fields as written; escapes a lone `%` and replaces rejected characters. */
    private fun escape(template: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c == '%' && template.getOrNull(i + 1) == '(') {
                val end = fieldEnd(template, i)
                if (end > 0) {
                    out.append(template, i, end)
                    i = end
                    continue
                }
                out.append("%%")
            } else if (c == '%' && template.getOrNull(i + 1) == '%') {
                out.append("%%")
                i++
            } else if (c == '%') {
                out.append("%%")
            } else if (c in REJECTED || c.isISOControl()) {
                out.append('_')
            } else {
                out.append(c)
            }
            i++
        }
        return out.toString()
    }

    /**
     * The index just past a field starting at [start] (`%(` … `)` then its conversion letter),
     * or -1 when the brackets never close or no conversion follows.
     */
    internal fun fieldEnd(s: String, start: Int): Int {
        var depth = 0
        var i = start + 1
        while (i < s.length) {
            when (s[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) break
            }
            i++
        }
        if (i >= s.length) return -1
        i++
        while (i < s.length && !s[i].isLetter()) {
            if (s[i] !in "0123456789.-+#") return -1
            i++
        }
        return if (i < s.length) i + 1 else -1
    }
}
