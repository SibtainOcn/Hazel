package com.hazel.android.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** A remembered read is served back only while the settings it depended on still hold. */
class ReadProfileTest {

    private val youtube = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    private val other = "https://vimeo.com/76979871"

    @Test
    fun `the shipped settings stamp nothing`() {
        assertEquals("", ReadProfile.stampFor(youtube, AdvancedSettings()))
        assertEquals("", ReadProfile.stampFor(other, AdvancedSettings()))
    }

    @Test
    fun `YouTube settings change YouTube reads and no other site's`() {
        val tokens = AdvancedSettings(poTokens = "web.gvs+abc123", playerClients = listOf("mweb"))
        assertNotEquals("", ReadProfile.stampFor(youtube, tokens))
        assertEquals("", ReadProfile.stampFor(other, tokens))
    }

    @Test
    fun `another token is another stamp, and the same settings the same one`() {
        val first = ReadProfile.stampFor(youtube, AdvancedSettings(poTokens = "web.gvs+abc123"))
        val again = ReadProfile.stampFor(youtube, AdvancedSettings(poTokens = "web.gvs+abc123"))
        val second = ReadProfile.stampFor(youtube, AdvancedSettings(poTokens = "web.gvs+xyz789"))
        assertEquals(first, again)
        assertNotEquals(first, second)
    }

    @Test
    fun `settings that only change how long a read takes leave the stamp alone`() {
        val paced = AdvancedSettings(sleepRequestsSeconds = 3, noCheckCertificates = true, downloadExtraArgs = "--no-mtime")
        assertEquals("", ReadProfile.stampFor(youtube, paced))
    }

    @Test
    fun `a browser imitation the engine can do changes every site's stamp`() {
        val imitating = AdvancedSettings(impersonate = "chrome", impersonateAvailable = listOf("chrome"))
        assertNotEquals("", ReadProfile.stampFor(other, imitating))
        // One the engine cannot do is never passed, so it changes nothing either.
        val unavailable = AdvancedSettings(impersonate = "chrome", impersonateAvailable = emptyList())
        assertEquals("", ReadProfile.stampFor(other, unavailable))
    }
}
