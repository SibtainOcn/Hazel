package com.hazel.android.gallery

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GalleryLogsTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun cookieValuesTokensAndPathsAreTakenOut() {
        val path = "/data/user/0/com.hazel.android/cache/cookies-instagram.txt"
        val text = """
            [instagram][debug] cookies: Loading cookies from '$path'
            Cookie: sessionid=abc123; csrftoken=xyz
            [instagram][error] HttpError: '401' for 'https://i.example/a?sessionid=s3cr3t&x=1'
            Authorization: Bearer abcdefghijklmnop
            [twitter][warning] auth_token=deadbeef ct0=cafe
        """.trimIndent()
        val out = GalleryLogs.redact(text, listOf(path))
        listOf(path, "abc123", "xyz", "s3cr3t", "abcdefghijklmnop", "deadbeef", "cafe").forEach {
            assertFalse(it in out, it)
        }
        assertTrue("[instagram][error] HttpError" in out)
        assertTrue("x=1" in out)
    }

    @Test
    fun keptLogIsCappedAndOnlyTheLastRunsStay() {
        val dir = folder.newFolder("gallery-logs")
        repeat(GalleryLogs.KEEP + 3) { run ->
            val raw = GalleryLogs.rawFile(dir)
            raw.writeText((1..20_000).joinToString("\n") { "line $it of run $run" })
            val kept = GalleryLogs.keep(dir, raw, emptyList(), now = 1_000L + run)
            assertTrue(kept.length <= GalleryLogs.MAX_BYTES)
            assertTrue(kept.endsWith("line 20000 of run $run"))
            assertTrue(kept.startsWith("line "), "tail starts at a whole line")
            assertFalse(raw.exists())
        }
        val logs = dir.listFiles()!!.toList()
        assertEquals(GalleryLogs.KEEP, logs.size)
        assertTrue(logs.all { it.length() <= GalleryLogs.MAX_BYTES })
        assertTrue(logs.any { "run ${GalleryLogs.KEEP + 2}" in it.readText() })
        assertFalse(logs.any { "of run 0\n" in it.readText() || it.readText().endsWith("of run 0") })
    }

    @Test
    fun anEmptyRunKeepsNoFile() {
        val dir = folder.newFolder("logs")
        val raw = GalleryLogs.rawFile(dir)
        assertEquals("", GalleryLogs.keep(dir, raw, emptyList()))
        assertEquals(0, dir.listFiles()!!.size)
    }
}
