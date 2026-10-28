package com.hazel.android.download.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChunkedDataSourceTest {

    /** Serves [body] like a server answering range requests, noting each range asked for. */
    private class FakeServer(private val body: ByteArray, private val sendsTotal: Boolean = true) : DataSource {
        val asked = mutableListOf<Pair<Long, Long>>()
        private var at = 0L
        private var end = 0L
        private var uri: Uri? = null

        override fun addTransferListener(transferListener: TransferListener) = Unit

        override fun open(dataSpec: DataSpec): Long {
            asked += dataSpec.position to dataSpec.length
            require(dataSpec.position < body.size) { "416" }
            uri = dataSpec.uri
            at = dataSpec.position
            end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) body.size.toLong()
            else minOf(body.size.toLong(), dataSpec.position + dataSpec.length)
            return end - at
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (at >= end) return C.RESULT_END_OF_INPUT
            val n = minOf(length.toLong(), end - at).toInt()
            System.arraycopy(body, at.toInt(), buffer, offset, n)
            at += n
            return n
        }

        override fun getUri(): Uri? = uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            if (sendsTotal) mapOf("Content-Range" to listOf("bytes $at-${end - 1}/${body.size}")) else emptyMap()

        override fun close() = Unit
    }

    private val body = ByteArray(2_500) { (it % 251).toByte() }
    private val uri = Uri.parse("https://rr1.googlevideo.com/videoplayback")

    private fun readAll(source: DataSource): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(333)
        while (true) {
            val n = source.read(buffer, 0, buffer.size)
            if (n == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    @Test
    fun readsTheWholeStreamInBoundedPieces() {
        val server = FakeServer(body)
        val source = ChunkedDataSource(server, chunk = 1_000)
        assertEquals(2_500L, source.open(DataSpec(uri)))
        assertArrayEquals(body, readAll(source))
        assertEquals(listOf(0L to 1_000L, 1_000L to 1_000L, 2_000L to 500L), server.asked)
        source.close()
    }

    @Test
    fun stopsAtAShortPieceWhenTheSizeIsUnknown() {
        val server = FakeServer(body, sendsTotal = false)
        val source = ChunkedDataSource(server, chunk = 1_000)
        assertEquals(C.LENGTH_UNSET.toLong(), source.open(DataSpec(uri)))
        assertArrayEquals(body, readAll(source))
        assertEquals(3, server.asked.size)
    }

    @Test
    fun startsFromASeekAndKeepsToABoundedRange() {
        val server = FakeServer(body)
        val source = ChunkedDataSource(server, chunk = 1_000)
        source.open(DataSpec.Builder().setUri(uri).setPosition(700).setLength(1_500).build())
        assertArrayEquals(body.copyOfRange(700, 2_200), readAll(source))
        assertEquals(listOf(700L to 1_000L, 1_700L to 500L), server.asked)
    }

    @Test
    fun onlyYouTubeVideoHostsAreChunked() {
        assertTrue(ChunkedDataSource.needsChunks("https://rr3---sn-abc.googlevideo.com/videoplayback?x=1"))
        assertFalse(ChunkedDataSource.needsChunks("https://example.com/googlevideo.com/a.mp4"))
        assertFalse(ChunkedDataSource.needsChunks("https://cdn.example.com/v.mp4"))
    }
}
