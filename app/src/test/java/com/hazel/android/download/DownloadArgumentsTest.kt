package com.hazel.android.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Extra arguments never take over the file name or where the file lands. */
class DownloadArgumentsTest {

    private fun args(line: String) = AdvancedSettings(downloadExtraArgs = line).downloadArguments()

    @Test
    fun `output and paths are dropped in every spelling`() {
        assertEquals(
            listOf("--no-mtime", "--restrict-filenames"),
            args("-o x.%(ext)s --no-mtime --output=y -oz.%(ext)s -P /sdcard -P/tmp --restrict-filenames")
        )
    }

    @Test
    fun `other options and their values are kept`() {
        assertEquals(listOf("--retries", "5", "-N", "4"), args("--retries 5 -N 4"))
    }

    // -- the -o the user typed is read, not passed on --

    private fun override(line: String) = AdvancedSettings(downloadExtraArgs = line).outputPathOverride()

    @Test
    fun `output template is read in every spelling`() {
        val want = "%(uploader)s/%(id)s.%(ext)s"
        assertEquals(want, override("-o \"%(uploader)s/%(id)s.%(ext)s\"")!!.template)
        assertEquals(want, override("--output %(uploader)s/%(id)s.%(ext)s")!!.template)
        assertEquals(want, override("--output=%(uploader)s/%(id)s.%(ext)s")!!.template)
        assertEquals(want, override("-o%(uploader)s/%(id)s.%(ext)s")!!.template)
    }

    @Test
    fun `the last output template wins`() {
        assertEquals("b.%(ext)s", override("-o a.%(ext)s --no-mtime -o b.%(ext)s")!!.template)
    }

    @Test
    fun `no output template, or an unsafe one, is no override`() {
        assertNull(override(""))
        assertNull(override("--retries 5"))
        assertNull(override("-o ../x.%(ext)s"))
        assertNull(override("-o /sdcard/x.%(ext)s"))
        assertNull(override("-o thumbnail:x.%(ext)s"))
        // A bare -o with nothing after it, or only another option, has no value to read.
        assertNull(override("-o"))
        assertNull(override("-o --no-mtime"))
    }

    @Test
    fun `an option that only starts like output is not one`() {
        assertNull(override("--output-na-placeholder X"))
    }

    @Test
    fun `reading the output template leaves it out of what yt-dlp is given`() {
        assertEquals(
            listOf("--no-mtime"),
            AdvancedSettings(downloadExtraArgs = "-o a/b.%(ext)s --no-mtime").downloadArguments()
        )
    }

    // -- compat options: the user's and the app's go out as one --

    private fun compat(line: String, vararg required: String) =
        AdvancedSettings(downloadExtraArgs = line).compatOptions(required.toList())

    @Test
    fun `compat options of the user are taken out of the arguments and kept`() {
        assertEquals(
            listOf("--no-mtime"),
            args("--compat-options no-live-chat --no-mtime --compat-options=playlist-index")
        )
        assertEquals(listOf("no-live-chat", "playlist-index"), compat("--compat-options no-live-chat --compat-options=playlist-index"))
    }

    @Test
    fun `the app's compat option is added beside the user's and not repeated`() {
        assertEquals(
            listOf("no-live-chat", "filename-sanitization"),
            compat("--compat-options no-live-chat", "filename-sanitization")
        )
        // The app's own go last, since yt-dlp reads the list in order (see the next test).
        assertEquals(
            listOf("no-live-chat", "filename-sanitization"),
            compat("--compat-options filename-sanitization,no-live-chat", "filename-sanitization")
        )
    }

    @Test
    fun `a later minus from the user cannot take the app's compat option away`() {
        // yt-dlp: ["filename-sanitization", "-filename-sanitization"] leaves nothing on.
        assertEquals(
            listOf("-filename-sanitization", "filename-sanitization"),
            compat("--compat-options filename-sanitization,-filename-sanitization", "filename-sanitization")
        )
    }

    @Test
    fun `an output template for a side file never replaces the media file's`() {
        assertEquals(
            "%(uploader)s/%(id)s.%(ext)s",
            override("-o %(uploader)s/%(id)s.%(ext)s -o thumbnail:t/%(id)s.%(ext)s")!!.template
        )
        assertEquals(
            "b.%(ext)s",
            override("-o a.%(ext)s -o subtitle,chapter:s/%(id)s -o b.%(ext)s")!!.template
        )
        assertNull(override("-o thumbnail:t/%(id)s.%(ext)s"))
        assertNull(AdvancedSettings(downloadExtraArgs = "-o thumbnail:x").outputTemplateText())
    }

    @Test
    fun `no-windows-filenames is dropped only while the template has folders`() {
        val flag = "--no-windows-filenames"
        assertEquals(listOf(flag), args(flag))
        assertEquals(listOf(flag), args("-o %(id)s.%(ext)s $flag"))
        assertEquals(emptyList<String>(), args("-o %(uploader)s/%(id)s.%(ext)s $flag"))
        // Kept in front of another option, and the other option is kept too.
        assertEquals(listOf("--retries", "5"), args("$flag -o a/b.%(ext)s --retries 5"))
    }

    @Test
    fun `with none given nothing is passed unless the app needs one`() {
        assertEquals(emptyList<String>(), compat(""))
        assertEquals(listOf("filename-sanitization"), compat("--retries 5", "filename-sanitization"))
        // A bare option with no value has nothing to read.
        assertEquals(emptyList<String>(), compat("--compat-options"))
        assertEquals(emptyList<String>(), compat("--compat-options --no-mtime"))
    }

    // -- a value that starts with a dash is still a value, as yt-dlp's parser reads it --

    private fun compatValues(line: String, vararg required: String) =
        AdvancedSettings(downloadExtraArgs = line).compatOptions(required.toList())

    @Test
    fun `a compat option that starts with a dash is read and does not leak`() {
        assertEquals(listOf("-multistreams"), compatValues("--compat-options -multistreams"))
        assertEquals(
            listOf("-multistreams", "filename-sanitization"),
            compatValues("--compat-options -multistreams", "filename-sanitization")
        )
        // The value goes with its option: nothing of it is left behind for yt-dlp to choke on.
        assertEquals(listOf("--retries", "5"), args("--compat-options -multistreams --retries 5"))
        assertEquals(listOf("--retries", "5"), args("--retries 5 --compat-options all,-multistreams"))
    }

    @Test
    fun `an output template that starts with a dash is dropped whole`() {
        assertEquals(emptyList<String>(), args("-o -x.%(ext)s"))
        assertEquals("-x.%(ext)s", AdvancedSettings(downloadExtraArgs = "-o -x.%(ext)s").outputTemplateText())
    }

    @Test
    fun `print-to-file drops both of its values`() {
        assertEquals(listOf("--retries", "5"), args("--print-to-file %(id)s out.txt --retries 5"))
        assertEquals(listOf("--retries", "5"), args("--print-to-file=%(id)s --retries 5"))
    }

    @Test
    fun `an option where a value should be is read as the option it is`() {
        assertEquals(listOf("--retries", "5"), args("-P --retries 5"))
        assertEquals(listOf("--retries", "5"), args("--print-to-file %(id)s --retries 5"))
    }
}
