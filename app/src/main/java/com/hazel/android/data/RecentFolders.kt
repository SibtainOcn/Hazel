package com.hazel.android.data

/**
 * The recently picked folders, kept as one preference: one folder per line, its tree URI
 * and readable label split by a tab. A tree URI is percent-encoded, so it never holds
 * either character; a label has them replaced so it cannot break the list.
 */
object RecentFolders {

    const val MAX = 5

    fun decode(raw: String?): List<SaveDir> = raw.orEmpty()
        .lineSequence()
        .mapNotNull { line ->
            val uri = line.substringBefore('\t').trim()
            if (uri.isEmpty()) null else SaveDir(uri, line.substringAfter('\t', "").trim())
        }
        .distinctBy { it.uri }
        .take(MAX)
        .toList()

    fun encode(folders: List<SaveDir>): String =
        folders.take(MAX).joinToString("\n") { it.uri + "\t" + it.label.replace(Regex("[\\t\\r\\n]+"), " ") }

    /** [uri] moved to the front (added if new), keeping at most [MAX]. */
    fun List<SaveDir>.with(uri: String, label: String): List<SaveDir> =
        (listOf(SaveDir(uri, label)) + filterNot { it.uri == uri }).take(MAX)
}
