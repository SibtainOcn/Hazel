package com.hazel.android.ui.screens.download

import com.hazel.android.download.BatchAudioFormats
import com.hazel.android.download.GenericFormats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatBadgeTest {

    @Test
    fun `the video ladder names each rung instead of DEFAULT`() {
        val ladder = GenericFormats.videoLadder("Best quality", "Worst quality")
        val badges = ladder.map(::badgeTextOf)
        assertEquals("BEST", badges.first())
        assertEquals("WORST", badges.last())
        assertTrue(badges.contains("1080"))
        assertTrue(badges.contains("720"))
        assertTrue(badges.none { it == "DEFAULT" })
    }

    @Test
    fun `the audio ladder names best, worst and bitrates`() {
        val badges = BatchAudioFormats.ladder("Best", "Worst").map(::badgeTextOf)
        assertTrue(badges.none { it == "DEFAULT" })
        assertTrue(badges.contains("WORST"))
    }

    @Test
    fun `a real stream still shows its container`() {
        val real = GenericFormats.heightCeiling(1080).copy(isGeneric = false, ext = "mp4")
        assertEquals("MP4", badgeTextOf(real))
    }
}
