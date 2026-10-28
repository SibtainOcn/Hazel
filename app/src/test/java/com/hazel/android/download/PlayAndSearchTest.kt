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
    fun startsOnTheWishedLanguageOrAPickedTrack() {
        val byLanguage = StreamResolver.pick(media(*dubbed), 720, AudioWish(language = "hi-IN"))
        assertEquals("https://a/hi", byLanguage?.audioUrl)
        val missing = StreamResolver.pick(media(*dubbed), 720, AudioWish(language = "fr"))
        assertEquals("https://a/en", missing?.audioUrl)
        val picked = StreamResolver.pick(media(*dubbed), 720, AudioWish(trackId = "en-US-desc", language = "hi"))
        assertEquals("https://a/en-ad", picked?.audioUrl)
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
    fun choosingSkipsDescriptionUnlessAskedAndFallsBackToFirst() {
        val tracks = listOf(
            AudioTrack("a", "en", descriptive = true, url = "u1"),
            AudioTrack("b", "es", url = "u2"),
            AudioTrack("c", "de", url = "u3")
        )
        assertEquals("b", AudioTracks.choose(tracks, AudioWish())?.id)
        assertEquals("b", AudioTracks.choose(tracks, AudioWish(language = "en"))?.id)
        assertEquals("a", AudioTracks.choose(tracks, AudioWish(trackId = "a"))?.id)
        assertNull(AudioTracks.choose(emptyList(), AudioWish(language = "en")))
    }

    @Test
    fun oneLanguageOffersNoChoice() {
        val stream = StreamResolver.pick(
            media(
                """{"url":"https://a/720v","protocol":"https","vcodec":"avc1","acodec":"none","height":720,"ext":"mp4"}""",
                """{"url":"https://a/audio","protocol":"https","vcodec":"none","acodec":"mp4a","ext":"m4a","language":"en"}"""
            ),
            maxHeight = 720
        )
        assertTrue(stream!!.audioTracks.isEmpty())
        assertNull(stream.audioTrackId)
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
            "https://music.youtube.com/search?q=lofi+beats#songs",
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

    @Test
    fun eachSearchEngineHasTheOtherBehindItWhereBothCanSearch() {
        val newPipe = ListingSource.NEWPIPE
        val ytDlp = ListingSource.YT_DLP
        // Both can search these: the picked one first, the other behind it.
        listOf(SearchSource.YOUTUBE, SearchSource.YOUTUBE_MUSIC, SearchSource.SOUNDCLOUD).forEach { site ->
            assertTrue(MediaSearch.newPipeFirst(site, newPipe), "$site, NewPipe picked")
            assertFalse(MediaSearch.newPipeAfter(site, newPipe), "$site, NewPipe picked")
            assertFalse(MediaSearch.newPipeFirst(site, ytDlp), "$site, yt-dlp picked")
            assertTrue(MediaSearch.newPipeAfter(site, ytDlp), "$site, yt-dlp picked")
        }
        // Only NewPipe can search Bandcamp, whatever is picked.
        listOf(newPipe, ytDlp).forEach { engine ->
            assertTrue(MediaSearch.newPipeFirst(SearchSource.BANDCAMP, engine))
            assertFalse(MediaSearch.newPipeAfter(SearchSource.BANDCAMP, engine))
        }
        // Every site search offers is one NewPipe knows; none is left to yt-dlp alone.
        SearchSource.entries.forEach { assertTrue(it.newPipeService != null, "$it") }
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
