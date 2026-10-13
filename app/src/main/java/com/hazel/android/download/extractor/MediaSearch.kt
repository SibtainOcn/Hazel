package com.hazel.android.download.extractor

import android.util.Log
import com.hazel.android.download.FetchMode
import com.hazel.android.download.MediaProbe
import com.hazel.android.download.SiteAccess
import com.hazel.android.download.extractor.newpipe.NewPipeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs a search on one [SearchSource] and returns what it found as cards to show.
 *
 * The chosen engine is asked first and the other stands in when it fails or finds nothing,
 * on the sites both can search: NewPipe first by default, with yt-dlp behind it, or yt-dlp
 * first with NewPipe behind it. A site only one of them can search goes to that one.
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
        val service = source.newPipeService
        if (service != null && newPipeFirst(source, engine)) {
            val found = NewPipeEngine.search(service, source.newPipeFilter, text, count)
            if (found.isNotEmpty()) return answered(source, ListingSource.NEWPIPE, found)
        }

        val target = source.ytDlpTarget(text, count) ?: return answered(source, ListingSource.NEWPIPE, emptyList())
        val after = service != null && newPipeAfter(source, engine)

        // With NewPipe still to ask, a yt-dlp failure is held rather than shown, and shown
        // only if NewPipe finds nothing either, so a search never fails silently.
        var failure: Exception? = null
        val found = try {
            ytDlpSearch(target, count, cacheDir, fetchMode, forceIpv4)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!after) throw e
            failure = e
            emptyList()
        }
        if (found.isNotEmpty() || service == null || !after) return answered(source, ListingSource.YT_DLP, found)

        val rescued = NewPipeEngine.search(service, source.newPipeFilter, text, count)
        if (rescued.isEmpty()) failure?.let { throw it }
        return answered(source, ListingSource.NEWPIPE, rescued)
    }

    /** Notes which engine answered a search, and with how many cards; never the words. */
    private fun answered(source: SearchSource, by: ListingSource, found: List<LinkEntry>): List<LinkEntry> {
        Log.i("Hazel", "Search on ${source.name}: ${by.name} found ${found.size}")
        return found
    }

    /** What yt-dlp finds for [target], cut to [count] cards. */
    private suspend fun ytDlpSearch(
        target: String,
        count: Int,
        cacheDir: File,
        fetchMode: FetchMode,
        forceIpv4: Boolean
    ): List<LinkEntry> = when (val contents = MediaProbe.listContents(
        target, cacheDir, SiteAccess.NONE, fetchMode, forceIpv4, SEARCH_PROCESS_ID, maxItems = count
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

    /** Whether NewPipe is asked before yt-dlp: its own pick, or the only one that can search. */
    internal fun newPipeFirst(source: SearchSource, engine: ListingSource): Boolean =
        source.newPipeService != null && (engine == ListingSource.NEWPIPE || !source.readableByYtDlp)

    /** Whether NewPipe stands in after yt-dlp: yt-dlp was picked, on a site NewPipe also knows. */
    internal fun newPipeAfter(source: SearchSource, engine: ListingSource): Boolean =
        source.newPipeService != null && !newPipeFirst(source, engine)

    const val SEARCH_PROCESS_ID = "hazel_search"
}
