package com.hazel.android.download.extractor.newpipe

import com.hazel.android.download.MediaInfo
import com.hazel.android.download.extractor.LinkContents
import com.hazel.android.download.extractor.LinkEntry
import com.hazel.android.download.playback.PlayableStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything the app asks of NewPipe, in one place.
 *
 * Only this package touches the NewPipe library, so an update that changes its API is
 * fixed here and nowhere else. Every call returns null or an empty list on failure: NewPipe
 * is always the fast path, never the only one, and callers fall back to yt-dlp.
 */
object NewPipeEngine {

    /** Whether NewPipe knows this address as a single item. Decided without a request. */
    fun handlesStream(url: String): Boolean = NewPipeLister.handlesStream(url)

    /** Whether NewPipe knows this address as a playlist or channel. Decided without a request. */
    fun handlesCollection(url: String): Boolean = NewPipeLister.handlesCollection(url)

    suspend fun list(url: String): LinkContents.Many? = NewPipeLister.list(url)

    suspend fun single(url: String): MediaInfo? = NewPipeLister.single(url)

    suspend fun search(serviceName: String, filter: String?, query: String, count: Int): List<LinkEntry> =
        withContext(Dispatchers.IO) {
            runCatching { NewPipeSearch.search(serviceName, filter, query, count) }
                .getOrDefault(emptyList())
        }

    suspend fun playable(url: String, maxHeight: Int): PlayableStream? =
        withContext(Dispatchers.IO) {
            if (!handlesStream(url)) return@withContext null
            runCatching { NewPipeStreams.playable(url, maxHeight) }.getOrNull()
        }
}
