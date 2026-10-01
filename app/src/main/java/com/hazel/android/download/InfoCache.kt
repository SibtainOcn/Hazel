package com.hazel.android.download

import com.hazel.android.HazelApp
import com.hazel.android.download.extractor.LinkContents
import com.hazel.android.download.extractor.LinkEntry
import com.hazel.android.download.extractor.ListingSource
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
 * parsed form is rebuilt from it on the first miss. Only recent links are kept: this is a
 * convenience for links in current use, not a library.
 *
 * Each reader keeps its own last read of a link ([MediaInfo.readBy] says which), so a format
 * list switched from one reader to another and back finds the first read still here rather
 * than reading the link again. Only the reader that leaves an engine payload, yt-dlp, goes to
 * disk: it is the one that costs seconds of engine start-up, while an in-process reader such
 * as NewPipe answers quickly enough that a file for it would buy nothing. A reader added
 * later gets a slot of its own with no change here.
 *
 * Every read is stamped with the settings it depended on ([ReadProfile]), and one made under
 * other settings is not served back. A read made in incognito is kept in memory only, so it
 * leaves nothing on the device once the app is gone.
 *
 * The JSON holds signed stream addresses that stop working after a few hours, so replaying
 * it into a download has the shorter life of the two windows below. Every reader treats a
 * miss as ordinary: nothing here is required to be present, and nothing here is trusted
 * once it is old, made under other settings, or unreadable.
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

    /**
     * How many links keep their payload on disk. A payload can run to a megabyte, so this
     * stays modest; the oldest goes when a new one arrives.
     */
    private const val MAX_ENTRIES = 25

    /**
     * How many parsed reads each reader holds in memory. These are small, and a search or
     * playlist opens many cards in a row, so far more are kept than payloads.
     */
    private const val MAX_MEMORY_ENTRIES = 200

    /** The reader whose reads leave a payload, and so the one kept on disk. */
    private val PERSISTED = ListingSource.YT_DLP

    private data class Entry(val info: MediaInfo, val storedAt: Long, val stamp: String)

    /** Each reader's reads, newest last, bounded to [MAX_MEMORY_ENTRIES] apiece. */
    private val reads: Map<ListingSource, LinkedHashMap<String, Entry>> =
        ListingSource.entries.associateWith {
            object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) =
                    size > MAX_MEMORY_ENTRIES
            }
        }

    /**
     * Where reads are kept. The name carries a layout version: a build that changes how links
     * are keyed or what a read contains moves to a new name, so what an older build stored
     * under the old rules is dropped once instead of being served back.
     */
    private val directory: File by lazy {
        val cacheRoot = HazelApp.instance.cacheDir
        LEGACY_DIRECTORIES.forEach { runCatching { File(cacheRoot, it).deleteRecursively() } }
        File(cacheRoot, DIRECTORY_NAME)
    }

    private fun ensureDirectory(): File = directory.apply { if (!exists()) mkdirs() }

    /** Also how the cleanup screen tells this folder apart from the rest of the cache. */
    const val DIRECTORY_NAME = "info-v2"

    /** Layouts earlier builds wrote, removed on first use. */
    private val LEGACY_DIRECTORIES = listOf("info")

    /**
     * The latest read of [url] by any reader, or null when nothing fresh is held.
     *
     * The latest, because that is what the link was last shown as. A miss in memory is not
     * the end of the question: the payload the last read wrote is still on disk after the
     * app has been closed and reopened, and parsing it again costs nothing next to reading
     * the link. That is what makes a link pasted yesterday open its sheet at once today.
     */
    @Synchronized
    fun metadataFor(url: String): MediaInfo? {
        val stamp = ReadProfile.stampFor(url)
        return ListingSource.entries
            .mapNotNull { entryFor(url, it, stamp) }
            .maxByOrNull { it.storedAt }
            ?.info
    }

    /** What [source] read of [url] last, or null when it holds nothing fresh. */
    @Synchronized
    fun metadataFor(url: String, source: ListingSource): MediaInfo? =
        entryFor(url, source, ReadProfile.stampFor(url))?.info

    private fun entryFor(url: String, source: ListingSource, stamp: String): Entry? {
        val key = LinkKey.readKey(url)
        val memory = reads.getValue(source)
        memory[key]?.let { entry ->
            if (System.currentTimeMillis() - entry.storedAt > METADATA_TTL_MS) {
                memory.remove(key)
                return null
            }
            // Held, but made under other settings: the disk holds nothing newer, so this
            // is a miss, and the next read replaces it.
            return entry.takeIf { it.stamp == stamp }
        }

        if (source != PERSISTED) return null
        val restored = restoreFromDisk(url, stamp) ?: return null
        return Entry(restored, fileFor(url).lastModified(), stamp).also { memory[key] = it }
    }


    /**
     * The engine's own JSON for [url], ready to be replayed, or null when there is nothing
     * recent enough to trust or it was read under other settings.
     */
    @Synchronized
    fun infoJsonFor(url: String): File? {
        val file = fileFor(url)
        if (!file.exists()) return null
        if (System.currentTimeMillis() - file.lastModified() > INFO_JSON_TTL_MS) return null
        if (storedStamp(url) != ReadProfile.stampFor(url)) return null
        return file
    }

    /** Records both halves of a completed read, in the slot of the reader that made it. */
    @Synchronized
    fun put(url: String, info: MediaInfo, rawJson: String?) {
        val stamp = ReadProfile.stampFor(url)
        reads.getValue(info.readBy)[LinkKey.readKey(url)] = Entry(info, System.currentTimeMillis(), stamp)

        // A collection's payload describes the collection rather than a playable item, so
        // replaying it into a download would select nothing.
        if (rawJson.isNullOrBlank() || info.readBy != PERSISTED) return

        if (ReadProfile.incognito) {
            // Nothing of this read goes to disk. An older payload for the link is dropped as
            // well, so a download replays this read's formats rather than an earlier one's.
            deleteFilesFor(url)
            return
        }

        if (!writeAtomically(fileFor(url), rawJson)) return

        // Which reads needed the sign-in is the app's own finding rather than anything in
        // the payload, so it is kept beside it: the file is there or it is not. The same
        // goes for the settings it was read under.
        runCatching {
            val marker = signInMarkerFor(url)
            if (info.requiresSignIn) marker.writeText("1") else marker.delete()
        }
        runCatching {
            val stampFile = stampFileFor(url)
            if (stamp.isNotEmpty()) stampFile.writeText(stamp) else stampFile.delete()
        }
        trimToLimit()
    }

    /** What a collection held, so pasting it again does not walk it a second time. */
    @Synchronized
    fun putListing(url: String, contents: LinkContents.Many) {
        if (ReadProfile.incognito) return
        val json = JSONObject().apply {
            put("title", contents.title)
            put("stamp", ReadProfile.stampFor(url))
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
        writeAtomically(listingFileFor(url), json.toString())
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
            if (json.optString("stamp") != ReadProfile.stampFor(url)) return null
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
     * Drops everything held for [url], from every reader.
     *
     * Called when a download refuses the replayed metadata, which is the signal that the
     * addresses inside it have expired ahead of the window above.
     */
    @Synchronized
    fun invalidate(url: String) {
        val key = LinkKey.readKey(url)
        reads.values.forEach { it.remove(key) }
        deleteFilesFor(url)
    }

    /**
     * Drops what [source] read of [url], leaving the other readers' reads in place. For a
     * fresh read from one reader, which should not cost the others theirs.
     */
    @Synchronized
    fun invalidate(url: String, source: ListingSource) {
        reads.getValue(source).remove(LinkKey.readKey(url))
        if (source == PERSISTED) deleteFilesFor(url)
    }

    /** What the reads take on disk, for the cleanup screen. */
    @Synchronized
    fun diskBytes(): Long = runCatching {
        directory.listFiles()?.sumOf { it.length() } ?: 0L
    }.getOrDefault(0L)

    @Synchronized
    fun clear() {
        reads.values.forEach { it.clear() }
        runCatching { ensureDirectory().listFiles()?.forEach { it.delete() } }
    }

    /** Removes what is past using, so the directory cannot grow unbounded. */
    @Synchronized
    fun prune() {
        runCatching {
            val cutoff = System.currentTimeMillis() - METADATA_TTL_MS
            ensureDirectory().listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        }
        trimToLimit()
    }

    /** Rebuilds the parsed form from the payload the last read left on disk. */
    private fun restoreFromDisk(url: String, stamp: String): MediaInfo? {
        val file = fileFor(url)
        if (!file.exists()) return null
        if (System.currentTimeMillis() - file.lastModified() > METADATA_TTL_MS) {
            deleteFilesFor(url)
            return null
        }
        if (storedStamp(url) != stamp) return null

        return runCatching {
            MediaProbe.parse(url, JSONObject(file.readText()))
                .copy(requiresSignIn = signInMarkerFor(url).exists())
        }.onFailure {
            // A payload that no longer parses is of no use to anyone, the download included.
            deleteFilesFor(url)
        }.getOrNull()
    }

    /** The settings stamp the payload on disk was read under; blank for the defaults. */
    private fun storedStamp(url: String): String =
        runCatching { stampFileFor(url).takeIf { it.exists() }?.readText()?.trim() }.getOrNull().orEmpty()

    private fun deleteFilesFor(url: String) {
        runCatching { fileFor(url).delete() }
        runCatching { signInMarkerFor(url).delete() }
        runCatching { stampFileFor(url).delete() }
        runCatching { listingFileFor(url).delete() }
    }

    /**
     * Written beside the file and moved into place, so a reader that opens it meanwhile (a
     * download replaying it, or another read) never meets half a file.
     */
    private fun writeAtomically(file: File, text: String): Boolean = runCatching {
        val staging = File(file.parentFile, "${file.name}.tmp")
        staging.writeText(text)
        if (!staging.renameTo(file)) {
            file.writeText(text)
            staging.delete()
        }
        true
    }.getOrDefault(false)

    /**
     * Keeps the newest [MAX_ENTRIES] links and deletes the rest.
     *
     * Counted by link rather than by file, since one link leaves a payload and may leave a
     * marker and a stamp beside it, and dropping part of a link would leave a record that
     * says the wrong thing about it.
     */
    private fun trimToLimit() {
        runCatching {
            val files = ensureDirectory().listFiles()?.toList().orEmpty()
            val newestFirst = files
                .groupBy { it.name.substringBefore('.') }
                .entries
                .sortedByDescending { group -> group.value.maxOf { it.lastModified() } }

            newestFirst.drop(MAX_ENTRIES).forEach { group ->
                group.value.forEach { it.delete() }
            }
        }
    }

    private fun fileFor(url: String) = File(ensureDirectory(), "${LinkKey.digest(url)}.info.json")

    private fun signInMarkerFor(url: String) = File(ensureDirectory(), "${LinkKey.digest(url)}.signin")

    private fun stampFileFor(url: String) = File(ensureDirectory(), "${LinkKey.digest(url)}.stamp")

    private fun listingFileFor(url: String) = File(ensureDirectory(), "${LinkKey.digest(url)}.list.json")
}
