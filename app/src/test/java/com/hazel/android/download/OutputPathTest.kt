package com.hazel.android.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A user's -o becomes folders and a name that stay inside the work folder. */
class OutputPathTest {

    private fun p(s: String) = OutputPath.parse(s)

    @Test
    fun `the issue's template makes an uploader folder and an id name`() {
        val path = p("%(uploader)s/%(id)s.%(ext)s")!!
        assertEquals(listOf("%(uploader)s"), path.folders)
        assertEquals("%(id)s.%(ext)s", path.fileTemplate)
        assertEquals("%(uploader)s/%(id)s.%(ext)s", path.template)
        assertEquals("%(uploader)s/", path.folderPrefix)
    }







    @Test
    fun `a slash inside a field is not a separator`() {
        val path = p("%(upload_date>%Y/%m)s/%(id)s.%(ext)s")!!
        assertEquals(listOf("%(upload_date>%Y/%m)s"), path.folders)
    }



    @Test
    fun `anything that could leave the work folder is refused`() {
        assertNull(p("/sdcard/%(id)s.%(ext)s"))
        assertNull(p("\\%(id)s.%(ext)s"))
        assertNull(p("~/%(id)s.%(ext)s"))
        assertNull(p("../%(id)s.%(ext)s"))
        assertNull(p("a/../../b.%(ext)s"))
        assertNull(p("a/ .. /b.%(ext)s"))
        assertNull(p("a/.../b.%(ext)s"))
        assertNull(p("a/.."))
    }

    @Test
    fun `too deep is refused, the limit itself is allowed`() {
        assertNotNull(p("a/b/c/d/%(id)s.%(ext)s"))
        assertNull(p("a/b/c/d/e/%(id)s.%(ext)s"))
    }



    @Test
    fun `every side file type yt-dlp knows is refused, in any case and in a list`() {
        for (t in listOf("subtitle", "thumbnail", "description", "annotation", "infojson", "link",
            "pl_video", "pl_thumbnail", "pl_description", "pl_infojson", "chapter")) {
            assertNull(t, p("$t:x.%(ext)s"))
        }
        assertNull(p("THUMBNAIL:x"))
        assertNull(p("subtitle,thumbnail:x"))
        assertNull(p("subtitle,thumbnail,chapter:%(id)s/x"))
    }









    @Test
    fun `an unclosed field is escaped rather than passed on`() {
        assertEquals("%%(upl/x.%(ext)s", p("%(upl/x")!!.template)
    }

    // -- identifiers in folders: yt-dlp keeps a ".." there even with filename-sanitization --





    @Test
    fun `a nested identifier cannot be cleaned and is refused`() {
        assertNull(p("%(playlist.id)s/%(id)s.%(ext)s"))
        assertNull(p("%(entries.0.id)s/x.%(ext)s"))
    }
}
