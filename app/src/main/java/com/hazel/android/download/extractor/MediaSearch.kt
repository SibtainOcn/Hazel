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
 *
 * Every search runs fresh, so it always follows the current engine and settings. Links read
 * from a result, a paste or a share are cached separately, by InfoCache.
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
        find(text, source, engine, count, cacheDir, fetchMode, forceIpv4)
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
            val found = NewPipeEngine.search(source.newPipeService, source.newPipeFilter, text, count)
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

    const val SEARCH_PROCESS_ID = "hazel_search"
}
