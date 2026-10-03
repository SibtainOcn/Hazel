package com.hazel.android.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which links the format list treats as signed in, where picking NewPipe explains that it
 * cannot send the sign-in rather than reading. Only a link to a site with a saved, enabled
 * set, with Use cookies on, counts.
 */
class CookieSignInTest {

    private val youtube = CookieEntry(
        1, "https://www.youtube.com", "YouTube",
        ".youtube.com\tTRUE\t/\tTRUE\t1825582117\tSID\tabc"
    )
    private val video = listOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ")

    @Test
    fun `a link to a site with a saved sign-in is signed in`() {
        assertTrue(CookieRepository.signsIn(true, listOf(youtube), video))
    }

    @Test
    fun `short links count as the site they belong to`() {
        assertTrue(CookieRepository.signsIn(true, listOf(youtube), listOf("https://youtu.be/dQw4w9WgXcQ")))
    }

    @Test
    fun `nothing is signed in with Use cookies off`() {
        assertFalse(CookieRepository.signsIn(false, listOf(youtube), video))
    }

    @Test
    fun `a sign-in for another site does not count`() {
        assertFalse(CookieRepository.signsIn(true, listOf(youtube), listOf("https://vimeo.com/76979871")))
    }

    @Test
    fun `a switched off or empty set does not count`() {
        assertFalse(CookieRepository.signsIn(true, listOf(youtube.copy(enabled = false)), video))
        assertFalse(CookieRepository.signsIn(true, listOf(youtube.copy(content = " ")), video))
    }

    @Test
    fun `an imported set is matched by its cookies`() {
        assertTrue(CookieRepository.signsIn(true, listOf(youtube.copy(url = "")), video))
    }

    @Test
    fun `a set of links is signed in when any of them is`() {
        val links = listOf("https://vimeo.com/76979871") + video
        assertTrue(CookieRepository.signsIn(true, listOf(youtube), links))
    }

    @Test
    fun `a link with no address is never signed in`() {
        assertFalse(CookieRepository.signsIn(true, listOf(youtube), listOf("")))
    }
}
