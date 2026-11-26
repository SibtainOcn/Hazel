package com.hazel.android.ui.screens.cookies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageZoomTest {

    @Test
    fun zoomIsCountedFromTheScaleThePageOpenedAt() {
        val zoom = PageZoom()
        assertNull(zoom.factorFor(200))
        zoom.startAt(2.625f)
        zoom.scaled(5.25f)
        assertEquals(200, zoom.percent)
        assertEquals(0.5f, zoom.factorFor(100)!!, 0.001f)
        assertEquals(100f, zoom.factorFor(1_000_000)!!, 0f)
    }
}
