package com.hazel.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CookieSummaryTest {

    private val lines = listOf(
        "# Netscape HTTP Cookie File",
        ".youtube.com\tTRUE\t/\tTRUE\t1791023917\tGPS\t1",
        "#HttpOnly_.youtube.com\tTRUE\t/\tTRUE\t1825582117\tSID\tabc",
        ".youtube.com\tTRUE\t/\tTRUE\t0\tYSC\txyz"
    ).joinToString("\n")

    @Test
    fun `counts cookies, HttpOnly ones included, and takes the latest expiry`() {
        val summary = CookieRepository.summaryOf(CookieEntry(1, "https://www.youtube.com", "", lines))
        assertEquals("youtube.com", summary.site)
        assertEquals(3, summary.count)
        assertEquals(1825582117L, summary.expiresAt)
    }

    @Test
    fun `an imported set without an address is named after its cookies`() {
        val summary = CookieRepository.summaryOf(CookieEntry(2, "", "cookies.txt", lines))
        assertEquals("youtube.com", summary.site)
    }

    @Test
    fun `session cookies have no expiry`() {
        val session = ".example.com\tTRUE\t/\tFALSE\t0\tid\t1"
        val summary = CookieRepository.summaryOf(CookieEntry(3, "https://example.com", "", session))
        assertEquals(1, summary.count)
        assertNull(summary.expiresAt)
    }

    @Test
    fun `space separated lines are read too`() {
        val spaced = ".example.com TRUE / TRUE 1900000000 id 1"
        val summary = CookieRepository.summaryOf(CookieEntry(4, "https://example.com", "", spaced))
        assertEquals(1900000000L, summary.expiresAt)
    }
}
