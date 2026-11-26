package com.hazel.android.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Titles, impersonation targets and made PO tokens become what yt-dlp takes. */
class RequestExtrasTest {



    @Test
    fun cutNeverSplitsAnEmoji() {
        val title = readableTitle("a".repeat(99) + "🌅" + "b".repeat(20))
        assertFalse(title.last().isHighSurrogate())
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
