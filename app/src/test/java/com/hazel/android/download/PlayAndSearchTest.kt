package com.hazel.android.download

import com.hazel.android.download.extractor.ListingSource
import com.hazel.android.download.extractor.MediaSearch
import com.hazel.android.download.extractor.SearchSource
import com.hazel.android.download.extractor.SearchSuggestions
import com.hazel.android.download.playback.AudioTrack
import com.hazel.android.download.playback.AudioTracks
import com.hazel.android.download.playback.AudioWish
import com.hazel.android.download.playback.StreamResolver
import com.hazel.android.ui.screens.download.isLinks
import com.hazel.android.ui.screens.download.linkParts
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

    private val dubbed = arrayOf(
        """{"url":"https://a/360","protocol":"https","vcodec":"avc1","acodec":"mp4a","height":360,"ext":"mp4"}""",
        """{"url":"https://a/360v","protocol":"https","vcodec":"avc1","acodec":"none","height":360,"ext":"mp4"}""",
        """{"url":"https://a/hi","protocol":"https","vcodec":"none","acodec":"mp4a","ext":"m4a","abr":130,"language":"hi","language_preference":-1}""",
        """{"url":"https://a/en","protocol":"https","vcodec":"none","acodec":"mp4a","ext":"m4a","abr":128,"language":"en-US","language_preference":10}""",
        """{"url":"https://a/en-ad","protocol":"https","vcodec":"none","acodec":"mp4a","ext":"m4a","abr":140,"language":"en-US-desc","language_preference":-10}"""
    )

    @Test
    fun startsOnTheOriginalTrackAndListsEveryLanguage() {
        val stream = StreamResolver.pick(media(*dubbed), maxHeight = 720)
        assertNotNull(stream)
        assertEquals("https://a/360v", stream.url)
        assertEquals("https://a/en", stream.audioUrl)
        assertEquals("en-US", stream.audioTrackId)
        assertEquals(3, stream.audioTracks.size)
        assertTrue(stream.audioTracks.single { it.id == "en-US-desc" }.descriptive)
    }



    @Test
    fun swapsOnlyTheSound() {
        val stream = StreamResolver.pick(media(*dubbed), maxHeight = 720)!!
        val hindi = stream.audioTracks.single { it.language == "hi" }
        val swapped = stream.withAudio(hindi)
        assertEquals(stream.url, swapped?.url)
        assertEquals("https://a/hi", swapped?.audioUrl)
        assertEquals("hi", swapped?.audioTrackId)
        assertNull(stream.withAudio(hindi.copy(url = "")))
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
            "https://music.youtube.com/search?q=lofi+beats#songs",
            SearchSource.YOUTUBE_MUSIC.ytDlpTarget("lofi beats", 25)
        )
        assertNull(SearchSource.BANDCAMP.ytDlpTarget("anything", 25))
        assertEquals(SearchSource.YOUTUBE, SearchSource.fromName("gone"))
    }





    @Test
    fun splitsQueuedLinkIntoSiteAndRest() {
        assertEquals("youtu.be" to "/ftkic7Tdpcg?si=NP7e", linkParts("https://youtu.be/ftkic7Tdpcg?si=NP7e"))
        assertEquals("youtube.com" to "/watch?v=abc", linkParts("https://www.youtube.com/watch?v=abc"))
        assertEquals("youtu.be" to "/abc", linkParts("youtu.be/abc"))
        assertEquals("example.com" to "", linkParts("https://example.com"))
        // Not an address: shown whole rather than lost.
        assertEquals("not a link" to "", linkParts("not a link"))
    }
}
