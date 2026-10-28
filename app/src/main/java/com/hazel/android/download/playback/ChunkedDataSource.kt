package com.hazel.android.download.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Reads a stream as a run of bounded requests instead of one open-ended one.
 *
 * YouTube sends the start of a long request quickly, then slows it below the video's own
 * rate, so playback stalls a few seconds in until a seek opens a new request. Asking for
 * [chunk] bytes at a time keeps every request inside that fast start.
 */
@OptIn(UnstableApi::class)
internal class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunk: Long = CHUNK_BYTES
) : DataSource {

    private var spec: DataSpec? = null
    private var position = 0L
    /** Bytes left in the whole request, or [C.LENGTH_UNSET] while unknown. */
    private var remaining = C.LENGTH_UNSET.toLong()
    private var leftInChunk = 0L
    private var upstreamOpen = false
    private var lastPiece = false

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        position = dataSpec.position
        remaining = dataSpec.length
        openChunk()
        if (remaining == C.LENGTH_UNSET.toLong()) {
            totalLength()?.let { remaining = (it - position).coerceAtLeast(0L) }
        }
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        if (leftInChunk == 0L) {
            if (lastPiece) return C.RESULT_END_OF_INPUT
            closeUpstream()
            openChunk()
            if (leftInChunk == 0L) return C.RESULT_END_OF_INPUT
        }
        val read = upstream.read(buffer, offset, minOf(length.toLong(), leftInChunk).toInt())
        if (read == C.RESULT_END_OF_INPUT) {
            // A short piece is the end of the stream, whatever its size was thought to be.
            remaining = 0L
            return C.RESULT_END_OF_INPUT
        }
        position += read
        leftInChunk -= read
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= read
        return read
    }

    private fun openChunk() {
        val base = spec ?: return
        val want = if (remaining == C.LENGTH_UNSET.toLong()) chunk else minOf(chunk, remaining)
        if (want <= 0L) {
            leftInChunk = 0L
            return
        }
        val opened = upstream.open(base.subrange(position - base.position, want))
        upstreamOpen = true
        leftInChunk = if (opened == C.LENGTH_UNSET.toLong()) want else minOf(opened, want)
        // Fewer bytes than asked means the server has nothing after this piece.
        lastPiece = opened != C.LENGTH_UNSET.toLong() && opened < want
    }

    /** The full size from the server's Content-Range, as in "bytes 0-1023/52428800". */
    private fun totalLength(): Long? =
        upstream.responseHeaders.entries
            .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
            ?.value?.firstOrNull()
            ?.substringAfterLast('/')?.trim()?.toLongOrNull()

    private fun closeUpstream() {
        if (upstreamOpen) {
            upstreamOpen = false
            upstream.close()
        }
    }

    override fun getUri(): Uri? = upstream.uri ?: spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        spec = null
        leftInChunk = 0L
        closeUpstream()
    }

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream.createDataSource())
    }

    companion object {
        /** The piece size yt-dlp uses for YouTube. */
        const val CHUNK_BYTES = 10L shl 20

        /** Whether [url] is served by YouTube's video hosts, which slow long requests. */
        fun needsChunks(url: String): Boolean =
            Uri.parse(url).host?.let { it == "googlevideo.com" || it.endsWith(".googlevideo.com") } == true
    }
}
