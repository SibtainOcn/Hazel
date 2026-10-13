package com.hazel.android.download

import com.hazel.android.download.extractor.SupportedSites
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** yt-dlp's link rules, as the app reads them, decide which Web results are kept. */
class SupportedSitesTest {

    private val parsed = SupportedSites.parse(File("src/main/assets/ytdlp_sites.txt").readText())

    private fun supported(url: String) = SupportedSites.matches(parsed.rules, url)

    @Test
    fun everyRuleCompilesInJava() {
        assertEquals(0, parsed.failed, "rules that did not compile")
        assertTrue(parsed.rules.size > 1000, "only ${parsed.rules.size} rules")
    }

    @Test
    fun keepsLinksOfSitesYtDlpKnows() {
        listOf(
            "https://www.youtube.com/watch?v=jNQXAC9IVRw",
            "https://youtu.be/jNQXAC9IVRw",
            "https://vimeo.com/76979871",
            "https://soundcloud.com/forss/flickermood",
            "https://www.dailymotion.com/video/x8abcd1",
            "https://www.twitch.tv/videos/123456789",
            "https://www.bilibili.com/video/BV1xx411c7mD",
            "https://archive.org/details/Sita_Sings_the_Blues"
        ).forEach { assertTrue(supported(it), it) }
    }

    @Test
    fun dropsPagesNoSiteRuleTakes() {
        listOf(
            "https://en.wikipedia.org/wiki/Video",
            "https://www.google.com/search?q=video",
            "https://example.com/watch/abc123.html",
            "https://news.ycombinator.com/item?id=1"
        ).forEach { assertFalse(supported(it), it) }
    }

    @Test
    fun checksAPageOfResultsQuickly() {
        val urls = List(25) { "https://some-blog-$it.example.org/posts/$it/a-video-review" }
        val start = System.nanoTime()
        urls.forEach { supported(it) }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue(ms < 2_000, "25 links took $ms ms")
    }
}
