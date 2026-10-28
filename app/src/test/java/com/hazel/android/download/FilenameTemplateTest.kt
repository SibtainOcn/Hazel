package com.hazel.android.download

import org.junit.Assert.assertEquals
import org.junit.Test

class FilenameTemplateTest {

    private fun n(s: String) = FilenameTemplate.normalize(s)

    @Test
    fun `default and plain templates stay as written`() {
        assertEquals("%(title)s.%(ext)s", n("%(title)s.%(ext)s"))
        assertEquals("%(uploader)s - %(title)s.%(ext)s", n("%(uploader)s - %(title)s.%(ext)s"))
    }

    @Test
    fun `blank falls back to the default`() {
        assertEquals(DownloadOptions.DEFAULT_FILENAME_TEMPLATE, n("   "))
        assertEquals(DownloadOptions.DEFAULT_FILENAME_TEMPLATE, n("..."))
    }

    @Test
    fun `missing extension is added`() {
        assertEquals("%(title)s.%(ext)s", n("%(title)s"))
        assertEquals("%(title)s.%(ext)s", n("%(title)s."))
    }

    @Test
    fun `path separators and rejected characters never leave the work folder`() {
        assertEquals("%(uploader)s_%(title)s.%(ext)s", n("%(uploader)s/%(title)s.%(ext)s"))
        assertEquals("a_b_c_d.%(ext)s", n("a\\b:c?d"))
    }

    @Test
    fun `lone percent is escaped, fields are not`() {
        assertEquals("100%% %(title)s.%(ext)s", n("100% %(title)s.%(ext)s"))
        assertEquals("50%% off.%(ext)s", n("50%% off"))
        assertEquals("%%(ti.%(ext)s", n("%(ti"))
    }

    @Test
    fun `formats and conditionals inside a field are kept`() {
        for (preset in FilenameTemplate.PRESETS) assertEquals(preset, n(preset))
        assertEquals("%(title).50s.%(ext)s", n("%(title).50s.%(ext)s"))
        assertEquals("%(view_count)05d.%(ext)s", n("%(view_count)05d.%(ext)s"))
    }

    @Test
    fun `leading dot never makes a hidden file`() {
        assertEquals("%(title)s.%(ext)s", n(".%(title)s.%(ext)s"))
    }

    @Test
    fun `free text fields get a byte cap, alternatives included`() {
        val limits = mapOf("title" to 120, "track" to 120, "artist" to 60, "uploader" to 60)
        assertEquals(
            "%(artist,uploader).60B - %(track,title).120B [%(id)s] %(title).20B.%(ext)s",
            FilenameTemplate.limitBytes("%(artist,uploader)s - %(track,title)s [%(id)s] %(title).20B.%(ext)s", limits)
        )
        assertEquals(
            "%(upload_date>%Y-%m-%d)s %(title).120B.%(ext)s",
            FilenameTemplate.limitBytes("%(upload_date>%Y-%m-%d)s %(title)s.%(ext)s", limits)
        )
    }
}
