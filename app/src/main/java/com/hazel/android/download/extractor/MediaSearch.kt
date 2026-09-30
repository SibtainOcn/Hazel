package com.hazel.android.download.extractor

import com.hazel.android.download.FetchMode
import com.hazel.android.download.MediaProbe
import com.hazel.android.download.SiteAccess
import com.hazel.android.download.extractor.newpipe.NewPipeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs a search on one [SearchSource] and returns what it found as cards to show.
 *
 * NewPipe answers in-process for the sites it knows; yt-dlp answers everywhere else, and
 * stands in whenever NewPipe fails or finds nothing.
 */
object MediaSearch {

    suspend fun search(
        query: String,
        source: SearchSource,
        engine: ListingSource,
        count: Int,
        cacheDir: File,
        fetchMode: FetchMode,
        forceIpv4: Boolean
    ): List<LinkEntry> = withContext(Dispatchers.IO) {
        val text = query.trim()
        if (text.isEmpty()) return@withContext emptyList()
        val key = "${source.name}:$count:${text.lowercase()}"
        recent(key)?.let { return@withContext it }
        find(text, source, engine, count, cacheDir, fetchMode, forceIpv4).also { found ->
            if (found.isNotEmpty()) remember(key, found)
        }
    }

    private suspend fun find(
        text: String,
        source: SearchSource,
        engine: ListingSource,
        count: Int,
        cacheDir: File,
        fetchMode: FetchMode,
        forceIpv4: Boolean
    ): List<LinkEntry> {
        val canUseNewPipe = source.newPipeService != null &&
            (engine == ListingSource.NEWPIPE || !source.readableByYtDlp)

        if (canUseNewPipe) {
            val found = NewPipeEngine.search(source.newPipeService!!, source.newPipeFilter, text, count)
            if (found.isNotEmpty()) return found
        }

        val target = source.ytDlpTarget(text, count) ?: return emptyList()
        return when (val contents = MediaProbe.listContents(
            target, cacheDir, SiteAccess.NONE, fetchMode, forceIpv4, SEARCH_PROCESS_ID
        )) {
            is LinkContents.Many -> contents.entries.take(count)
            is LinkContents.Single -> listOf(
                LinkEntry(
                    url = contents.info.url,
                    title = contents.info.title,
                    uploader = contents.info.uploader,
                    thumbnail = contents.info.thumbnail,
                    durationSeconds = contents.info.durationSeconds
                )
            )
        }
    }

    private class Stored(val entries: List<LinkEntry>, val at: Long)

    /** Recent searches, so going back to one does not run it again. */
    private val cache = object : LinkedHashMap<String, Stored>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Stored>) = size > MAX_CACHED
    }

    @Synchronized
    private fun recent(key: String): List<LinkEntry>? =
        cache[key]?.takeIf { System.currentTimeMillis() - it.at < CACHE_TTL_MS }?.entries

    @Synchronized
    private fun remember(key: String, entries: List<LinkEntry>) {
        cache[key] = Stored(entries, System.currentTimeMillis())
    }

    private const val MAX_CACHED = 20
    private const val CACHE_TTL_MS = 30 * 60 * 1000L
    const val SEARCH_PROCESS_ID = "hazel_search"
}
