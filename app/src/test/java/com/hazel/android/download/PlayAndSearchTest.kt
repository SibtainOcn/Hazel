package com.hazel.android.download

import com.hazel.android.download.extractor.SearchSource
import com.hazel.android.download.extractor.SearchSuggestions
import com.hazel.android.download.playback.StreamResolver
import com.hazel.android.ui.screens.download.isLinks
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Choosing a stream to play, telling links from words, and building searches. */
class PlayAndSearchTest {

    private fun format(json: String) = JSONObject(json)

    private fun media(vararg formats: String) =
        JSONObject().put("formats", org.json.JSONArray(formats.map { format(it) }))

    @Test
    fun picksTallestPictureWithinCapAndPairsSound() {
        val stream = StreamResolver.pick(
            media(
                """{"url":"https://a/360","protocol":"https","vcodec":"avc1","acodec":"mp4a","height":360,"ext":"mp4"}""",
                """{"url":"https://a/720v","protocol":"https","vcodec":"avc1","acodec":"none","height":720,"ext":"mp4"}""",
                """{"url":"https://a/1080v","protocol":"https","vcodec":"avc1","acodec":"none","height":1080,"ext":"mp4"}""",
                """{"url":"https://a/audio","protocol":"https","vcodec":"none","acodec":"mp4a","ext":"m4a","abr":128}"""
            ),
            maxHeight = 720
        )
        assertNotNull(stream)
        assertEquals("https://a/720v", stream.url)
        assertEquals("https://a/audio", stream.audioUrl)
    }

    @Test
    fun takesProgressiveFileWhenNothingTallerIsSplit() {
        val stream = StreamResolver.pick(
            media(
                """{"url":"https://a/720","protocol":"https","vcodec":"avc1","acodec":"mp4a","height":720,"ext":"mp4"}""",
                """{"url":"https://a/audio","protocol":"https","vcodec":"none","acodec":"opus","ext":"webm"}"""
            ),
            maxHeight = 720
        )
        assertEquals("https://a/720", stream?.url)
        assertNull(stream?.audioUrl)
    }

    @Test
    fun capBelowEveryStreamTakesTheSmallestPicture() {
        val split = StreamResolver.pick(
            media(
                """{"url":"https://a/360v","protocol":"https","vcodec":"avc1","acodec":"none","height":360,"ext":"mp4"}""",
                """{"url":"https://a/720v","protocol":"https","vcodec":"avc1","acodec":"none","height":720,"ext":"mp4"}""",
                """{"url":"https://a/audio","protocol":"https","vcodec":"none","acodec":"mp4a","ext":"m4a"}"""
            ),
            maxHeight = 144
        )
        assertEquals("https://a/360v", split?.url)
        assertEquals("https://a/audio", split?.audioUrl)

        val muxed = StreamResolver.pick(
            media(
                """{"url":"https://a/360","protocol":"https","vcodec":"avc1","acodec":"mp4a","height":360,"ext":"mp4"}""",
                """{"url":"https://a/audio","protocol":"https","vcodec":"none","acodec":"mp4a","ext":"m4a"}"""
            ),
            maxHeight = 144
        )
        assertEquals("https://a/360", muxed?.url)
        assertEquals(true, muxed?.hasVideo)
    }

    @Test
    fun tallVideoCountsItsShortSideAndListsItsQualities() {
        val stream = StreamResolver.pick(
            media(
                """{"url":"https://a/1080","protocol":"https","vcodec":"avc1","acodec":"mp4a","width":1080,"height":1920,"ext":"mp4"}""",
                """{"url":"https://a/720","protocol":"https","vcodec":"avc1","acodec":"mp4a","width":720,"height":1280,"ext":"mp4"}""",
                """{"url":"https://a/dash","protocol":"http_dash_segments","fragments":[{}],"vcodec":"avc1","acodec":"none","width":1440,"height":2560}"""
            ),
            maxHeight = 1080
        )
        assertEquals("https://a/1080", stream?.url)
        assertEquals(listOf(1080, 720), stream?.heights)
    }

    @Test
    fun streamWithoutCodecsCountsAsVideo() {
        // Instagram reels often report neither codec nor height.
        val stream = StreamResolver.pick(
            media("""{"url":"https://cdn/reel.mp4","ext":"mp4"}"""),
            maxHeight = 720
        )
        assertEquals("https://cdn/reel.mp4", stream?.url)
        assertEquals(true, stream?.hasVideo)
    }

    @Test
    fun soundOnlySourcePlaysWithoutPicture() {
        val stream = StreamResolver.pick(
            media("""{"url":"https://sc/track.mp3","protocol":"https","vcodec":"none","acodec":"mp3","ext":"mp3"}"""),
            maxHeight = 720
        )
        assertEquals("https://sc/track.mp3", stream?.url)
        assertEquals(false, stream?.hasVideo)
    }

    @Test
    fun segmentedStreamsAreSkippedAndHlsIsKept() {
        val stream = StreamResolver.pick(
            media(
                """{"url":"https://a/dash","protocol":"http_dash_segments","fragments":[{}],"vcodec":"avc1","acodec":"none","height":480}""",
                """{"url":"https://a/live.m3u8","protocol":"m3u8_native","vcodec":"avc1","acodec":"mp4a","height":480}"""
            ),
            maxHeight = 720
        )
        assertEquals("https://a/live.m3u8", stream?.url)
        assertEquals(true, stream?.isHls)
    }

    @Test
    fun postWithSeveralItemsPlaysTheFirst() {
        val root = JSONObject().put(
            "entries",
            org.json.JSONArray().put(media("""{"url":"https://a/first.mp4","ext":"mp4","height":640}"""))
        )
        assertEquals("https://a/first.mp4", StreamResolver.pick(root, 720)?.url)
    }

    @Test
    fun cookieStringBecomesHeader() {
        assertEquals(
            "sessionid=abc; csrftoken=xyz",
            StreamResolver.cookieHeader("sessionid=abc; Domain=.instagram.com; Path=/; csrftoken=xyz; Secure")
        )
        assertNull(StreamResolver.cookieHeader(""))
    }

    @Test
    fun wordsAreNotLinks() {
        assertTrue(isLinks("https://www.youtube.com/watch?v=abc"))
        assertTrue(isLinks("youtu.be/abc instagram.com/reel/xyz"))
        assertTrue(isLinks("www.example.com"))
        assertFalse(isLinks("oggy kitchen boy"))
        assertFalse(isLinks("lofi"))
        assertFalse(isLinks("https://youtu.be/abc and more"))
        assertFalse(isLinks("1.5"))
    }

    @Test
    fun searchTargetsForYtDlp() {
        assertEquals("ytsearch25:lofi beats", SearchSource.YOUTUBE.ytDlpTarget("lofi beats", 25))
        assertEquals("scsearch10:rain", SearchSource.SOUNDCLOUD.ytDlpTarget("rain", 10))
        assertEquals(
            "https://music.youtube.com/search?q=lofi+beats",
            SearchSource.YOUTUBE_MUSIC.ytDlpTarget("lofi beats", 25)
        )
        assertNull(SearchSource.BANDCAMP.ytDlpTarget("anything", 25))
        assertEquals(SearchSource.YOUTUBE, SearchSource.fromName("gone"))
    }

    @Test
    fun suggestionsParse() {
        assertEquals(
            listOf("oggy and the cockroaches", "oggy"),
            SearchSuggestions.parse("""["oggy",["oggy and the cockroaches","oggy"],[],{}]""")
        )
        assertEquals(emptyList(), SearchSuggestions.parse("not json"))
    }
}
