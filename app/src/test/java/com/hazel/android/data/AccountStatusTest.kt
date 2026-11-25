package com.hazel.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountStatusTest {

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
}
