package com.hazel.android.download

/**
 * A user's `-o` template split into folders and a file name. yt-dlp is given it rebuilt
 * under the work folder, with every part cleaned and anything that could climb out refused.
 * A `/` inside a `%(...)s` field is part of the field, not a separator.
 */
data class OutputPath(
    /** Folder templates, outermost first. */
    val folders: List<String>,
    /** The file name template; it always ends in the extension field. */
    val fileTemplate: String
) {
    /** The folders each followed by `/`, ready to put before a file name. Empty for none. */
    val folderPrefix: String get() = folders.joinToString(separator = "") { "$it/" }

    /** The whole template, as it is given to yt-dlp after the work folder. */
    val template: String get() = folderPrefix + fileTemplate

    /** Identifier fields the folders use, cleaned by the download; see [idFolderKeys]. */
    val idKeys: Set<String> get() = idFolderKeys(folders).orEmpty()

    companion object {
        /** Folders deep a template may go; a guard against typos. */
        const val MAX_FOLDERS = 4

        /** yt-dlp's `OUTTMPL_TYPES`: `-o` prefixes for side files. `default:` is also accepted here as the media file. */
        private val SIDE_FILE_TYPES = setOf(
            "chapter", "subtitle", "thumbnail", "description", "annotation", "infojson", "link",
            "pl_video", "pl_thumbnail", "pl_description", "pl_infojson"
        )

        /** A `word:` or `word,word:` at the start. Only a known type makes it a prefix. */
        private val TYPE_PREFIX = Regex("""^([A-Za-z_]+(?:,[A-Za-z_]+)*):""")

        /** True for a `-o` value for a side file (`thumbnail:...`), which never replaces the media file's template. */
        fun isSideFileTemplate(raw: String): Boolean {
            val m = TYPE_PREFIX.find(raw.trim()) ?: return false
            return m.groupValues[1].split(',').all { it.lowercase() in SIDE_FILE_TYPES }
        }

        /** Reads a `-o` value. Null for a blank one, a side-file one, or one that cannot stay inside the work folder. */
        fun parse(raw: String): OutputPath? {
            var text = raw.trim()
            // Side-file templates stay out.
            if (isSideFileTemplate(text)) return null
            TYPE_PREFIX.find(text)?.let { m ->
                if (m.groupValues[1].equals("default", ignoreCase = true)) {
                    text = text.substring(m.value.length).trim()
                }
            }
            if (text.isEmpty() || text.startsWith("/") || text.startsWith("\\") || text.startsWith("~")) {
                return null
            }

            val parts = splitOutsideFields(text)
            // An empty last part (trailing "/") means the default name inside those folders.
            val folderParts = parts.dropLast(1)
            val filePart = parts.last()

            val folders = ArrayList<String>(folderParts.size)
            for (part in folderParts) {
                val trimmed = part.trim()
                if (trimmed.isEmpty() || trimmed == ".") continue
                // "..", "...": nothing but dots is never a folder.
                if (trimmed.all { it == '.' }) return null
                val clean = FilenameTemplate.segment(trimmed)
                if (clean.isEmpty()) return null
                folders += clean
            }
            if (folders.size > MAX_FOLDERS) return null
            // An identifier that cannot be made safe leaves the folder open to "..".
            if (idFolderKeys(folders) == null) return null

            val trimmedFile = filePart.trim()
            if (trimmedFile.isNotEmpty() && trimmedFile.all { it == '.' }) return null
            val file = FilenameTemplate.normalize(trimmedFile)
            return OutputPath(folders, file)
        }

        private val KEY_TOKEN = Regex("[A-Za-z_][A-Za-z0-9_]*")

        /** Identifier fields used in [folders], or null when one cannot be cleaned (nested, e.g. `%(playlist.id)s`). */
        internal fun idFolderKeys(folders: List<String>): Set<String>? {
            val keys = sortedSetOf<String>()
            for (folder in folders) {
                var i = 0
                while (i < folder.length) {
                    if (folder[i] == '%' && folder.getOrNull(i + 1) == '(') {
                        val end = FilenameTemplate.fieldEnd(folder, i)
                        if (end > 0) {
                            val whole = folder.substring(i + 2, end)
                            val inner = whole.substring(0, whole.lastIndexOf(')').coerceAtLeast(0))
                            for (m in KEY_TOKEN.findAll(inner)) {
                                if (!FilenameTemplate.isIdKey(m.value)) continue
                                if (inner.getOrNull(m.range.first - 1) == '.' ||
                                    inner.getOrNull(m.range.last + 1) == '.'
                                ) return null
                                keys += m.value
                            }
                            i = end
                            continue
                        }
                    }
                    i++
                }
            }
            return keys
        }

        /** Splits on `/` except inside a `%(...)s` field; the last part is what follows the final separator. */
        internal fun splitOutsideFields(text: String): List<String> {
            val parts = ArrayList<String>()
            val current = StringBuilder()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '%' && text.getOrNull(i + 1) == '(') {
                    val end = FilenameTemplate.fieldEnd(text, i)
                    if (end > 0) {
                        current.append(text, i, end)
                        i = end
                        continue
                    }
                }
                if (c == '/') {
                    parts += current.toString()
                    current.clear()
                } else {
                    current.append(c)
                }
                i++
            }
            parts += current.toString()
            return parts
        }
    }
}
