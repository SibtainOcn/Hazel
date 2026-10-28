package com.hazel.android.download

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
