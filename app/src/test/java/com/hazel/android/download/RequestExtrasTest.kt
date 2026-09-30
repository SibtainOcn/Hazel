package com.hazel.android.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Titles, impersonation targets and made PO tokens become what yt-dlp takes. */
class RequestExtrasTest {

    @Test
    fun captionBecomesAReadableTitle() {
        assertEquals(
            "Loving this sunset with friends",
            readableTitle("Loving this sunset\nwith friends #travel #sunset #fyp")
        )
        assertEquals("#fyp #viral", readableTitle("#fyp #viral"))
        assertEquals(
            "Great day with @john at the #beach today",
            readableTitle("Great day with @john at the #beach today")
        )
        val long = readableTitle("word ".repeat(60))
        assertTrue(long.length <= 100)
        assertFalse(long.endsWith(" "))
    }

    @Test
    fun cutNeverSplitsAnEmoji() {
        val title = readableTitle("a".repeat(99) + "🌅" + "b".repeat(20))
        assertFalse(title.last().isHighSurrogate())
    }

    @Test
    fun onlyUsableImpersonateTargetsAreOffered() {
        val listing = """
            [info] Available impersonate targets
            Client          OS           Source
            --------------------------------------
            Chrome-131      Android-14   curl_cffi
            Safari-18.0     Ios-18.0     curl_cffi
            Edge-101        Windows-10   curl_cffi
            Chrome          -            curl_cffi (unavailable)
            Firefox         -            curl_cffi (unavailable)
        """.trimIndent()
        assertEquals(listOf("chrome", "safari", "edge"), ImpersonateTargets.parse(listing))
        assertEquals(emptyList(), ImpersonateTargets.parse("Chrome    -    curl_cffi (unavailable)"))
    }

    @Test
    fun impersonateIsPassedOnlyWhenAvailable() {
        assertEquals("chrome", AdvancedSettings(impersonate = "chrome", impersonateAvailable = listOf("chrome")).impersonateTarget())
        assertNull(AdvancedSettings(impersonate = "chrome").impersonateTarget())
        assertNull(AdvancedSettings(impersonateAvailable = listOf("chrome")).impersonateTarget())
    }

    private val minted = PoTokenGenerator.Minted(
        visitorData = "Cgt%3D%3D",
        sessionToken = "session",
        videoTokens = mapOf("jNQXAC9IVRw" to "videotoken")
    )

    @Test
    fun madeTokensAreBoundToTheVideoWithTheirVisitor() {
        val args = AdvancedSettings(autoPoTokens = true).youtubeExtractorArgs("en", minted, signedIn = false)!!
        assertTrue(args.startsWith("youtube:player_client=default,mweb;"))
        assertTrue("mweb.gvs+videotoken" in args)
        assertTrue("mweb.player+videotoken" in args)
        assertTrue("visitor_data=Cgt%3D%3D" in args)
        assertTrue("player_skip=webpage,configs" in args)
    }

    @Test
    fun signedInKeepsTheAccountsOwnVisitor() {
        val args = AdvancedSettings(autoPoTokens = true).youtubeExtractorArgs("en", minted, signedIn = true)!!
        assertTrue("mweb.gvs+videotoken" in args)
        assertFalse("visitor_data" in args)
        assertFalse("player_skip" in args)
    }

    @Test
    fun pastedTokensWinOverMadeOnes() {
        val settings = AdvancedSettings(poTokens = "web.gvs+pasted", visitorData = "V")
        val args = settings.youtubeExtractorArgs("en", minted)!!
        assertEquals("youtube:po_token=web.gvs+pasted;visitor_data=V", args)
    }

    @Test
    fun nothingSetMeansNoArguments() {
        assertNull(AdvancedSettings().youtubeExtractorArgs("en"))
    }
}
