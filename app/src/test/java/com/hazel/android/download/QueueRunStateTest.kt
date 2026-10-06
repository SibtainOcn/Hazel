package com.hazel.android.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The run's own record surviving what the screen does, the list keeping a card for whatever
 * the run is working on, and the counts a run's end is reported with.
 */
class QueueRunStateTest {

    private fun info(url: String) = MediaInfo(
        url = url,
        title = url.substringAfterLast('/'),
        uploader = "",
        thumbnail = null,
        durationSeconds = 0,
        videoFormats = emptyList(),
        audioFormats = emptyList()
    )

    private val running = DownloadState(
        url = "https://example.com/search-words",
        searchQuery = "new search",
        results = listOf(info("https://example.com/r1"), info("https://example.com/r2")),
        info = info("https://example.com/r1"),
        isDownloading = true,
        active = info("https://example.com/p3"),
        progress = 0.4f,
        totalBytes = 1000L,
        eta = "00:10",
        status = "downloading",
        processingSteps = listOf(ProcessingStep.FETCH),
        batch = listOf(
            BatchItem("https://example.com/p1", "p1", BatchState.DONE),
            BatchItem("https://example.com/p2", "p2", BatchState.FAILED, "HTTP Error 403"),
            BatchItem("https://example.com/p3", "p3", BatchState.DOWNLOADING),
            BatchItem("https://example.com/p4", "p4", BatchState.QUEUED)
        )
    )

    // ── keepingRun ──

    @Test
    fun `clearing the screen mid run keeps the run on it`() {
        val cleared = running.keepingRun(running = true)

        assertTrue(cleared.isDownloading)
        assertEquals(running.active, cleared.active)
        assertEquals(running.batch, cleared.batch)
        assertEquals(running.progress, cleared.progress)
        assertEquals(running.totalBytes, cleared.totalBytes)
        assertEquals(running.eta, cleared.eta)
        assertEquals(running.status, cleared.status)
        assertEquals(running.processingSteps, cleared.processingSteps)
    }

    @Test
    fun `clearing the screen mid run still clears the screen`() {
        val cleared = running.keepingRun(running = true)

        assertEquals("", cleared.url)
        assertEquals("", cleared.searchQuery)
        assertTrue(cleared.results.isEmpty())
        assertNull(cleared.info)
        assertNull(cleared.error)
        assertNull(cleared.errorLog)
        assertFalse(cleared.isFetching)
    }

    @Test
    fun `a run between two items is kept even before its state says so`() {
        // The run owns the queue but has not marked its next item yet.
        val between = running.copy(isDownloading = false, batch = emptyList())
        val cleared = between.keepingRun(running = true)
        assertEquals(between.active, cleared.active)
    }

    @Test
    fun `a paused or waiting link keeps the record with nothing running`() {
        val paused = running.copy(
            isDownloading = false,
            batch = listOf(BatchItem("https://example.com/p3", "p3", BatchState.PAUSED))
        )
        assertEquals(paused.batch, paused.keepingRun(running = false).batch)

        val waiting = running.copy(
            isDownloading = false,
            waitingForWifi = true,
            batch = listOf(BatchItem("https://example.com/p4", "p4", BatchState.QUEUED))
        )
        val kept = waiting.keepingRun(running = false)
        assertEquals(waiting.batch, kept.batch)
        assertTrue(kept.waitingForWifi)
    }

    @Test
    fun `with nothing in hand clearing is a blank state as before`() {
        val settled = running.copy(
            isDownloading = false,
            active = null,
            isComplete = true,
            batch = listOf(
                BatchItem("https://example.com/p1", "p1", BatchState.DONE),
                BatchItem("https://example.com/p2", "p2", BatchState.FAILED, "x")
            )
        )
        assertEquals(DownloadState(), settled.keepingRun(running = false))
    }

    // ── marking ──

    @Test
    fun `marking a known link changes only that link`() {
        val marked = running.batch.marking("https://example.com/p4", BatchState.DOWNLOADING)
        assertEquals(running.batch.size, marked.size)
        assertEquals(BatchState.DOWNLOADING, marked.single { it.url.endsWith("p4") }.state)
        assertEquals(running.batch.filterNot { it.url.endsWith("p4") }, marked.filterNot { it.url.endsWith("p4") })
    }

    @Test
    fun `marking an unknown link with a title gives it a card`() {
        val marked = emptyList<BatchItem>().marking("https://example.com/p9", BatchState.DOWNLOADING, title = "p9")
        assertEquals(listOf(BatchItem("https://example.com/p9", "p9", BatchState.DOWNLOADING)), marked)
    }

    @Test
    fun `marking with a title does not duplicate a known link`() {
        val marked = running.batch.marking("https://example.com/p3", BatchState.DONE, title = "p3")
        assertEquals(running.batch.size, marked.size)
        assertEquals(BatchState.DONE, marked.single { it.url.endsWith("p3") }.state)
    }

    @Test
    fun `marking an unknown link without a title changes nothing`() {
        val marked = running.batch.marking("https://example.com/zz", BatchState.FAILED, "x")
        assertEquals(running.batch, marked)
    }

    @Test
    fun `marking updates every entry of a link queued twice`() {
        val twice = listOf(
            BatchItem("https://example.com/a", "a"),
            BatchItem("https://example.com/a", "a")
        )
        val marked = twice.marking("https://example.com/a", BatchState.DONE, title = "a")
        assertEquals(2, marked.size)
        assertTrue(marked.all { it.state == BatchState.DONE })
    }

    // ── RunCounts ──

    private fun items(done: Int, failed: Int, cancelled: Int, waiting: Int = 0) =
        List(done) { BatchItem("d$it", "d$it", BatchState.DONE) } +
            List(failed) { BatchItem("f$it", "f$it", BatchState.FAILED, "HTTP Error 403") } +
            List(cancelled) { BatchItem("c$it", "c$it", BatchState.FAILED, CANCELLED_REASON) } +
            List(waiting) { BatchItem("w$it", "w$it", BatchState.QUEUED) }

    @Test
    fun `a playlist cancelled part way counts saved failed and cancelled apart`() {
        // The case reported: 83 links, some saved, one failed, the rest cancelled.
        val counts = RunCounts.of(items(done = 12, failed = 1, cancelled = 70))
        assertEquals(RunCounts(total = 83, done = 12, failed = 1, cancelled = 70), counts)
        assertTrue(counts.wantsSummary)
    }

    @Test
    fun `a cancel is never counted as a failure`() {
        val counts = RunCounts.of(items(done = 0, failed = 0, cancelled = 5))
        assertEquals(0, counts.failed)
        assertEquals(5, counts.cancelled)
        assertTrue(counts.wantsSummary)
    }

    @Test
    fun `several links with failures and no cancel get a summary`() {
        assertTrue(RunCounts.of(items(done = 3, failed = 2, cancelled = 0)).wantsSummary)
        assertTrue(RunCounts.of(items(done = 0, failed = 4, cancelled = 0)).wantsSummary)
    }

    @Test
    fun `several links all saved keep the completion notification`() {
        assertFalse(RunCounts.of(items(done = 10, failed = 0, cancelled = 0)).wantsSummary)
    }

    @Test
    fun `a single link keeps its own notifications`() {
        assertFalse(RunCounts.of(items(done = 0, failed = 1, cancelled = 0)).wantsSummary)
        assertFalse(RunCounts.of(items(done = 0, failed = 0, cancelled = 1)).wantsSummary)
        assertFalse(RunCounts.of(items(done = 1, failed = 0, cancelled = 0)).wantsSummary)
        assertFalse(RunCounts.of(emptyList()).wantsSummary)
    }

    @Test
    fun `counts agree with the state's own figures`() {
        val batch = items(done = 4, failed = 2, cancelled = 3, waiting = 1)
        val state = DownloadState(batch = batch)
        val counts = RunCounts.of(batch)
        assertEquals(state.batchDone, counts.done)
        assertEquals(state.batchFailed, counts.failed + counts.cancelled)
        assertEquals(10, counts.total)
    }

    // ── bytesIn ──

    @Test
    fun `a work folder counts its files and not the engine's bookkeeping`() {
        val dir = kotlin.io.path.createTempDirectory("hazel_work").toFile()
        try {
            java.io.File(dir, "song.m4a.part").writeBytes(ByteArray(300))
            java.io.File(dir, "song.f251.webm").writeBytes(ByteArray(200))
            java.io.File(dir, "song.m4a.part.ytdl").writeBytes(ByteArray(50))
            java.io.File(dir, "sub").mkdirs()
            assertEquals(500L, bytesIn(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a missing work folder is nothing rather than a failure`() {
        val gone = java.io.File(System.getProperty("java.io.tmpdir"), "hazel_missing_${System.nanoTime()}")
        assertEquals(0L, bytesIn(gone))
    }
}
