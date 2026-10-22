package com.hazel.android.ui.screens.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsNewTest {

    @Test
    fun `nothing is decided while either value loads`() {
        assertNull(WhatsNew.shouldShow(null, ""))
        assertNull(WhatsNew.shouldShow(true, null))
        assertNull(WhatsNew.shouldShow(null, null))
    }

    @Test
    fun `an update that has not seen this release shows it`() {
        assertEquals(true, WhatsNew.shouldShow(guideSeen = true, seenId = ""))
        assertEquals(true, WhatsNew.shouldShow(guideSeen = true, seenId = "1.0.0"))
    }

    @Test
    fun `this release is shown once`() {
        assertEquals(false, WhatsNew.shouldShow(guideSeen = true, seenId = WhatsNew.RELEASE_ID))
    }

    @Test
    fun `a fresh install gets the guide instead`() {
        assertEquals(false, WhatsNew.shouldShow(guideSeen = false, seenId = ""))
    }
}
