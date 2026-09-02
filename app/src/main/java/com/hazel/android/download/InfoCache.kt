package com.hazel.android.download

import com.hazel.android.HazelApp
import com.hazel.android.download.extractor.LinkContents
import com.hazel.android.download.extractor.LinkEntry
import com.hazel.android.util.LinkKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Keeps what a metadata read produced, so the same link is not read twice.
 *
 * Reading a link is the slowest thing the app does, and measurement on a mid-range device
 * put it at roughly six seconds: about two spawning the engine and importing it, and about
 * four doing the extraction. Almost none of that is the app's own work, so the only way to
 * make a repeat cheaper is not to do it again.
 *
 * Three things are kept, for three different readers:
 *
 *  - the parsed [MediaInfo], so pasting a link again fills the card and the format sheet
 *    without a read at all;
 *  - the engine's own JSON, which the download hands straight back with `--load-info-json`
 *    so it skips its extraction. That measured 5.8 seconds down to 3.2 before a byte moved,
 *    the remainder being the engine starting up;
 *  - what a collection held, so a playlist pasted again opens as the set of cards it opened
 *    as last time instead of being walked a second time.
 *
 * All of it survives the app being closed, because the engine's payload is on disk and the
 * parsed form is rebuilt from it on the first miss. Only the last [MAX_ENTRIES] links are
 * kept: this is a convenience for links in current use, not a library.
 *
 * The JSON holds signed stream addresses that stop working after a few hours, so replaying
 * it into a download has the shorter life of the two windows below. Every reader treats a
 * miss as ordinary: nothing here is required to be present, and nothing here is trusted
 * once it is old.
 */
object InfoCache {

    /**
     * How long a read stands in for a fresh one. A title and a format list are stable for
     * hours, and the worst a stale one costs is a download that re-reads the link.
     */
    private const val METADATA_TTL_MS = 6 * 60 * 60 * 1000L

    /**
     * How long the engine's JSON may be replayed into a download. Much shorter, and
     * deliberately well inside the life of the signed addresses inside it, because the cost
     * of being wrong here is a failed download rather than a stale title.
     */
    private const val INFO_JSON_TTL_MS = 60 * 60 * 1000L

    /** How many links are remembered. The oldest goes when a new one arrives. */
    private const val MAX_ENTRIES = 10

    private data class Entry(val info: MediaInfo, val storedAt: Long)

    private val metadata = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) =
            size > MAX_ENTRIES
    }

    private val directory: File
        get() = File(HazelApp.instance.cacheDir, "info").apply { if (!exists()) mkdirs() }

    /**
     * Parsed metadata for [url], or null when nothing fresh is held.
     *
     * A miss in memory is not the end of the question: the payload the last read wrote is
     * still on disk after the app has been closed and reopened, and parsing it again costs
     * nothing next to reading the link. That is what makes a link pasted yesterday open its
     * sheet at once today.
     */
    @Synchronized
    fun metadataFor(url: String): MediaInfo? {
        val key = LinkKey.canonical(url)
        metadata[key]?.let { entry ->
            if (System.currentTimeMillis() - entry.storedAt <= METADATA_TTL_MS) return entry.info
            metadata.remove(key)
        }

        val restored = restoreFromDisk(url) ?: return null
        metadata[key] = Entry(restored, fileFor(url).lastModified())
        return restored
    }

    /**
     * The engine's own JSON for [url], ready to be replayed, or null when there is nothing
     * recent enough to trust.
     */
    fun infoJsonFor(url: String): File? {
        val file = fileFor(url)
        if (!file.exists()) return null
        if (System.currentTimeMillis() - file.lastModified() > INFO_JSON_TTL_MS) return null
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

        // Which reads needed the sign-in is the app's own finding rather than anything in
        // the payload, so it is kept beside it: the file is there or it is not.
        runCatching {
            val marker = signInMarkerFor(url)
            if (info.requiresSignIn) marker.writeText("1") else marker.delete()
        }
        trimToLimit()
    }

    /** What a collection held, so pasting it again does not walk it a second time. */
    @Synchronized
    fun putListing(url: String, contents: LinkContents.Many) {
        val json = JSONObject().apply {
            put("title", contents.title)
            put(
                "entries",
                JSONArray().apply {
                    contents.entries.forEach { entry ->
                        put(
                            JSONObject().apply {
                                put("url", entry.url)
                                put("title", entry.title)
                                put("uploader", entry.uploader)
                                put("thumbnail", entry.thumbnail ?: JSONObject.NULL)
                                put("duration", entry.durationSeconds)
                            }
                        )
                    }
                }
            )
        }
        runCatching { listingFileFor(url).writeText(json.toString()) }
        trimToLimit()
    }

    /** The remembered contents of a collection, or null when there are none worth using. */
    @Synchronized
    fun listingFor(url: String): LinkContents.Many? {
        val file = listingFileFor(url)
        if (!file.exists()) return null
        if (System.currentTimeMillis() - file.lastModified() > METADATA_TTL_MS) {
            file.delete()
            return null
        }

        return runCatching {
            val json = JSONObject(file.readText())
            val array = json.optJSONArray("entries") ?: return null
            val entries = (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { entry ->
                    LinkEntry(
                        url = entry.optString("url"),
                        title = entry.optString("title"),
                        uploader = entry.optString("uploader"),
                        thumbnail = entry.optString("thumbnail")
                            .takeIf { it.isNotBlank() && it != "null" },
                        durationSeconds = entry.optInt("duration")
                    )
                }
            }.filter { it.url.isNotBlank() }

            if (entries.isEmpty()) null
            else LinkContents.Many(title = json.optString("title"), entries = entries)
        }.getOrNull()
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
        runCatching { signInMarkerFor(url).delete() }
        runCatching { listingFileFor(url).delete() }
    }

    @Synchronized
    fun clear() {
        metadata.clear()
        runCatching { directory.listFiles()?.forEach { it.delete() } }
    }

    /** Removes what is past using, so the directory cannot grow unbounded. */
    @Synchronized
    fun prune() {
        runCatching {
            val cutoff = System.currentTimeMillis() - METADATA_TTL_MS
            directory.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        }
        trimToLimit()
    }

    /** Rebuilds the parsed form from the payload the last read left on disk. */
    private fun restoreFromDisk(url: String): MediaInfo? {
        val file = fileFor(url)
        if (!file.exists()) return null
        if (System.currentTimeMillis() - file.lastModified() > METADATA_TTL_MS) {
            file.delete()
            return null
        }

        return runCatching {
            MediaProbe.parse(url, JSONObject(file.readText()))
                .copy(requiresSignIn = signInMarkerFor(url).exists())
        }.getOrNull()
    }

    /**
     * Keeps the newest [MAX_ENTRIES] links and deletes the rest.
     *
     * Counted by link rather than by file, since one link leaves a payload and may leave a
     * marker beside it, and dropping half of a link would leave a record that says the
     * wrong thing about it.
     */
    private fun trimToLimit() {
        runCatching {
            val files = directory.listFiles()?.toList().orEmpty()
            val newestFirst = files
                .groupBy { it.name.substringBefore('.') }
                .entries
                .sortedByDescending { group -> group.value.maxOf { it.lastModified() } }

            newestFirst.drop(MAX_ENTRIES).forEach { group ->
                group.value.forEach { it.delete() }
            }
        }
    }

    private fun fileFor(url: String) = File(directory, "${LinkKey.digest(url)}.info.json")

    private fun signInMarkerFor(url: String) = File(directory, "${LinkKey.digest(url)}.signin")

    private fun listingFileFor(url: String) = File(directory, "${LinkKey.digest(url)}.list.json")
}
