package com.hazel.android.util

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * The link key decides two things the user notices: whether a metadata read is reused, and
 * whether a repeat download is caught before it starts. Both are silent when they go wrong.
 * A share sheet link and an address bar link for the same video once produced two different
 * keys, so the cache missed every time and the same file downloaded twice without a word.
 */
class LinkKeyTest {

    @Test
    fun `share and address bar forms of one video agree`() {
        assertTrue(
            LinkKey.sameMedia(
                "https://youtu.be/kUox2TPnpzo",
                "https://www.youtube.com/watch?v=kUox2TPnpzo"
            )
        )
    }



    @Test
    fun `a video opened from inside a playlist is the same video`() {
        assertTrue(
            LinkKey.sameMedia(
                "https://www.youtube.com/watch?v=kUox2TPnpzo",
                "https://www.youtube.com/watch?v=kUox2TPnpzo&list=PLabc123&index=4"
            )
        )
    }

    @Test
    fun `two different playlists compare as different media`() {
        assertFalse(
            LinkKey.sameMedia(
                "https://www.youtube.com/playlist?list=PL1111111111111111",
                "https://www.youtube.com/playlist?list=PL2222222222222222"
            )
        )
        assertEquals("youtube/playlist/PL111", LinkKey.canonical("https://www.youtube.com/playlist?list=PL111"))
    }

    @Test
    fun `a timestamp does not make it a different video`() {
        assertTrue(
            LinkKey.sameMedia(
                "https://youtu.be/kUox2TPnpzo",
                "https://youtu.be/kUox2TPnpzo?t=142"
            )
        )
    }

    @Test
    fun `shorts, embed and live forms all reduce to the id`() {
        val watch = LinkKey.canonical("https://www.youtube.com/watch?v=kUox2TPnpzo")
        assertEquals(watch, LinkKey.canonical("https://www.youtube.com/shorts/kUox2TPnpzo"))
        assertEquals(watch, LinkKey.canonical("https://www.youtube.com/embed/kUox2TPnpzo"))
        assertEquals(watch, LinkKey.canonical("https://www.youtube.com/live/kUox2TPnpzo"))
    }



    @Test
    fun `two different videos never collapse onto one key`() {
        assertNotEquals(
            LinkKey.canonical("https://youtu.be/kUox2TPnpzo"),
            LinkKey.canonical("https://youtu.be/dQw4w9WgXcQ")
        )
        assertFalse(
            LinkKey.sameMedia(
                "https://www.youtube.com/watch?v=kUox2TPnpzo",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
            )
        )
    }





    @Test
    fun `the order parameters were written in does not matter`() {
        assertEquals(
            LinkKey.canonical("https://example.com/w?a=1&b=2"),
            LinkKey.canonical("https://example.com/w?b=2&a=1")
        )
    }



    @Test
    fun `two blank links are not treated as the same media`() {
        assertFalse(LinkKey.sameMedia("", ""))
    }








}
