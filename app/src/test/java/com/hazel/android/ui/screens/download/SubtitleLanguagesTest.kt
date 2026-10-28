package com.hazel.android.ui.screens.download

import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.MediaProbe
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A link's subtitles are read for the sheet to offer, and the chips match `--sub-langs` as yt-dlp does. */
class SubtitleLanguagesTest {

    @Test
    fun `uploaded subtitles and spoken-language auto captions are read, chat and translations left out`() {
        val json = """
            {"title":"t","formats":[],
             "subtitles":{"en":[{}],"de":[{}],"live_chat":[{}]},
             "automatic_captions":{"en-orig":[{}],"en":[{}],"fr":[{}]}}
        """.trimIndent()
        val info = MediaProbe.parse("https://example.com/x", JSONObject(json))
        assertEquals(setOf("en", "de"), info.subtitles.toSet())
        assertEquals(listOf("en-orig"), info.autoCaptions)
        // No translations: the one auto track is the spoken one.
        val plain = MediaProbe.parse("https://example.com/x", JSONObject("""{"title":"t","formats":[],"automatic_captions":{"en":[{}]}}"""))
        assertEquals(listOf("en"), plain.autoCaptions)
    }

    @Test
    fun `entries match by name or as a pattern, never an exclusion`() {
        assertTrue(subLangMatches("en", "en"))
        assertTrue(subLangMatches(".*-orig", "en-orig"))
        assertFalse(subLangMatches("-live_chat", "live_chat"))
        assertFalse(subLangMatches("[", "en"))
        assertEquals(DownloadOptions.DEFAULT_SUB_LANGUAGES.split(","), subLanguageKeys(""))
    }
}
