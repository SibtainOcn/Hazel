package com.hazel.android.download

import com.hazel.android.HazelApp
import com.hazel.android.util.LinkKey
import java.io.File

/**
 * Keeps what a metadata read produced, so the same link is not read twice.
 *
 * Reading a link is the slowest thing the app does, and measurement on a mid-range device
 * put it at roughly six seconds: about two spawning the engine and importing it, and about
 * four doing the extraction. Almost none of that is the app's own work, so the only way to
 * make a repeat cheaper is not to do it again.
 *
 * Two things are kept, for two different readers:
 *
 *  - the parsed [MediaInfo], so pasting a link again fills the card and the format sheet
 *    without a read at all;
 *  - the engine's own JSON, which the download hands straight back with `--load-info-json`
 *    so it skips its extraction. That measured 5.8 seconds down to 3.2 before a byte moved,
 *    the remainder being the engine starting up.
 *
 * The JSON holds signed stream addresses that stop working after a few hours, so it is given
 * the shorter life of the two and every reader treats a miss as ordinary. Nothing here is
 * required to be present, and nothing here is trusted once it is old.
 */
object InfoCache {

    /**
     * How long parsed metadata stands in for a fresh read. Titles and formats do change,
     * so this is short enough that a stale card is a matter of minutes.
     */
    private const val METADATA_TTL_MS = 30 * 60 * 1000L

    /**
     * How long the engine's JSON may be replayed into a download. Shorter, and deliberately
     * well inside the life of the signed addresses inside it, because the cost of being
     * wrong here is a failed download rather than a stale title.
     */
    private const val INFO_JSON_TTL_MS = 60 * 60 * 1000L

    /** Kept small: this is a convenience for links in current use, not a library. */
    private const val MAX_ENTRIES = 60

    private data class Entry(val info: MediaInfo, val storedAt: Long)

    private val metadata = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) =
            size > MAX_ENTRIES
    }

    private val directory: File
        get() = File(HazelApp.instance.cacheDir, "info").apply { if (!exists()) mkdirs() }

    /** Parsed metadata for [url], or null when nothing fresh is held. */
    @Synchronized
    fun metadataFor(url: String): MediaInfo? {
        val key = LinkKey.canonical(url)
        val entry = metadata[key] ?: return null
        if (System.currentTimeMillis() - entry.storedAt > METADATA_TTL_MS) {
            metadata.remove(key)
            return null
        }
        return entry.info
    }

    /**
     * The engine's own JSON for [url], ready to be replayed, or null when there is nothing
     * recent enough to trust.
     */
    fun infoJsonFor(url: String): File? {
        val file = fileFor(url)
        if (!file.exists()) return null
        if (System.currentTimeMillis() - file.lastModified() > INFO_JSON_TTL_MS) {
            file.delete()
            return null
        }
        return file
    }

    /** Records both halves of a completed read. */
    @Synchronized
    fun put(url: String, info: MediaInfo, rawJson: String?) {
        metadata[LinkKey.canonical(url)] = Entry(info, System.currentTimeMillis())

        // A collection's payload describes the collection rather than a playable item, so
        // replaying it into a download would select nothing.
        if (rawJson.isNullOrBlank()) return
        runCatching { fileFor(url).writeText(rawJson) }
    }

    /**
     * Drops everything held for [url].
     *
     * Called when a download refuses the replayed metadata, which is the signal that the
     * addresses inside it have expired ahead of the window above.
     */
    @Synchronized
    fun invalidate(url: String) {
        metadata.remove(LinkKey.canonical(url))
        runCatching { fileFor(url).delete() }
    }

    @Synchronized
    fun clear() {
        metadata.clear()
        runCatching { directory.listFiles()?.forEach { it.delete() } }
    }

    /** Removes payloads that are past replaying, so the directory cannot grow unbounded. */
    fun prune() {
        runCatching {
            val cutoff = System.currentTimeMillis() - INFO_JSON_TTL_MS
            directory.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        }
    }

    private fun fileFor(url: String) = File(directory, "${LinkKey.digest(url)}.info.json")
}
