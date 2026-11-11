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
    fun `a name alone has no folders`() {
        val path = p("%(title)s [%(id)s].%(ext)s")!!
        assertEquals(emptyList<String>(), path.folders)
        assertEquals("", path.folderPrefix)
        assertEquals("%(title)s [%(id)s].%(ext)s", path.template)
    }

    @Test
    fun `several folders keep their order`() {
        val path = p("%(extractor)s/%(uploader)s/%(title)s.%(ext)s")!!
        assertEquals(listOf("%(extractor)s", "%(uploader)s"), path.folders)
    }

    @Test
    fun `missing extension is added and a trailing slash uses the default name`() {
        assertEquals("%(uploader)s/%(id)s.%(ext)s", p("%(uploader)s/%(id)s")!!.template)
        assertEquals("Clips/${DownloadOptions.DEFAULT_FILENAME_TEMPLATE}", p("Clips/")!!.template)
    }

    @Test
    fun `a slash inside a field is not a separator`() {
        val path = p("%(upload_date>%Y/%m)s/%(id)s.%(ext)s")!!
        assertEquals(listOf("%(upload_date>%Y/%m)s"), path.folders)
    }

    @Test
    fun `repeated and current-folder separators collapse`() {
        assertEquals("a/b.%(ext)s", p("a//./b")!!.template)
        assertEquals("a/%(id)s.%(ext)s", p("./a/%(id)s.%(ext)s")!!.template)
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
    fun `blank and side-file templates are not the media file`() {
        assertNull(p(""))
        assertNull(p("   "))
        assertNull(p("thumbnail:%(uploader)s/%(id)s.%(ext)s"))
        assertNull(p("subtitle:x.%(ext)s"))
        assertNull(p("chapter:%(title)s.%(ext)s"))
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
    fun `a word yt-dlp does not know before a colon is part of the name, as it is there`() {
        // yt-dlp treats "Series:" as text; the colon is then cleaned like any rejected character.
        assertEquals("Series_ %(title)s.%(ext)s", p("Series: %(title)s")!!.template)
        assertEquals("a_b/x.%(ext)s", p("a:b/x")!!.template)
        // One known and one unknown is not a side-file prefix either.
        assertEquals("thumbnail,foo_x.%(ext)s", p("thumbnail,foo:x")!!.template)
    }

    @Test
    fun `isSideFileTemplate reads only a prefix of known types`() {
        assertTrue(OutputPath.isSideFileTemplate("thumbnail:x"))
        assertTrue(OutputPath.isSideFileTemplate("  Subtitle,Chapter:x"))
        assertFalse(OutputPath.isSideFileTemplate("x:thumbnail"))
        assertFalse(OutputPath.isSideFileTemplate("%(uploader)s/%(id)s.%(ext)s"))
        assertFalse(OutputPath.isSideFileTemplate("default:x"))
        assertFalse(OutputPath.isSideFileTemplate(""))
    }

    @Test
    fun `default prefix is the media file`() {
        assertEquals("%(uploader)s/%(id)s.%(ext)s", p("default:%(uploader)s/%(id)s.%(ext)s")!!.template)
    }

    @Test
    fun `folder names are cleaned like file names`() {
        assertEquals("100%% a_b/x.%(ext)s", p("100% a:b/x")!!.template)
        assertEquals("My Folder/x.%(ext)s", p(".  My Folder . /x")!!.template)
    }

    @Test
    fun `an unclosed field is escaped rather than passed on`() {
        assertEquals("%%(upl/x.%(ext)s", p("%(upl/x")!!.template)
    }

    // -- identifiers in folders: yt-dlp keeps a ".." there even with filename-sanitization --

    @Test
    fun `identifier fields used as folders are found`() {
        assertEquals(setOf("uploader_id"), p("%(uploader_id)s/%(id)s.%(ext)s")!!.idKeys)
        assertEquals(setOf("channel_id", "id"), p("%(channel_id|NA)s/%(id)s/%(title)s.%(ext)s")!!.idKeys)
        assertEquals(setOf("uploader_id"), p("%(uploader_id,uploader)s/%(title)s.%(ext)s")!!.idKeys)
        // After the app has limited it, as it does before a download.
        assertEquals(setOf("uploader_id"), OutputPath(listOf("%(uploader_id).120B"), "%(id)s.%(ext)s").idKeys)
    }

    @Test
    fun `an identifier in the file name only, or no identifier, needs nothing`() {
        assertTrue(p("%(uploader)s/%(id)s.%(ext)s")!!.idKeys.isEmpty())
        assertTrue(p("%(id)s.%(ext)s")!!.idKeys.isEmpty())
        assertTrue(p("%(upload_date>%Y)s/%(title)s.%(ext)s")!!.idKeys.isEmpty())
        assertTrue(p("%(width)s/%(video)s/x.%(ext)s")!!.idKeys.isEmpty())
    }

    @Test
    fun `a nested identifier cannot be cleaned and is refused`() {
        assertNull(p("%(playlist.id)s/%(id)s.%(ext)s"))
        assertNull(p("%(entries.0.id)s/x.%(ext)s"))
    }
}
