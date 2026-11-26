package com.hazel.android.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
    fun `nothing is signed in with Use cookies off`() {
        assertFalse(CookieRepository.signsIn(false, listOf(youtube), video))
    }

    @Test
    fun `a sign-in for another site does not count`() {
        assertFalse(CookieRepository.signsIn(true, listOf(youtube), listOf("https://vimeo.com/76979871")))
    }









    private fun entry(vararg lines: String, refused: Boolean = false) =
        CookieEntry(1, "https://www.instagram.com", "", lines.joinToString("\n"), refused = refused)

    @Test
    fun `state follows the site's login cookie, not the longest-lived cookie`() {
        val now = 1_800_000_000L
        val longLived = ".instagram.com\tTRUE\t/\tTRUE\t1900000000\tcsrftoken\tx"
        val loginOld = "#HttpOnly_.instagram.com\tTRUE\t/\tTRUE\t1700000000\tsessionid\ty"
        val loginNew = "#HttpOnly_.instagram.com\tTRUE\t/\tTRUE\t1850000000\tsessionid\ty"

        assertEquals(AccountState.SIGNED_OUT, CookieRepository.statusOf(entry(longLived), now).state)
        assertEquals(AccountState.EXPIRED, CookieRepository.statusOf(entry(longLived, loginOld), now).state)
        val active = CookieRepository.statusOf(entry(longLived, loginNew), now)
        assertEquals(AccountState.ACTIVE, active.state)
        assertEquals(1850000000L, active.expiresAt)
        assertEquals(AccountState.EXPIRED, CookieRepository.statusOf(entry(loginNew, refused = true), now).state)
    }

    @Test
    fun jsonExportNetscapeWithSpacesAndJunk() {
        val json = """[{"domain":".x.com","hostOnly":false,"httpOnly":true,"name":"auth_token","path":"/",
            "secure":true,"expirationDate":1790000000.5,"value":"abc"}]"""
        assertEquals(
            listOf("#HttpOnly_.x.com\tTRUE\t/\tTRUE\t1790000000\tauth_token\tabc"),
            CookieImport.toNetscapeLines(json)
        )
        assertEquals(
            listOf(".instagram.com\tTRUE\t/\tTRUE\t0\tsessionid\tv 1"),
            CookieImport.toNetscapeLines("# Netscape HTTP Cookie File\n.instagram.com TRUE / TRUE 0 sessionid v 1")
        )
        assertTrue(CookieImport.toNetscapeLines("hello world\nnot a cookie").isEmpty())
    }
}
