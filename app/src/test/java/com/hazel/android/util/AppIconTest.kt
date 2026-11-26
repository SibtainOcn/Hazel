package com.hazel.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconTest {

    @Test
    fun `every icon's share entry reads back as its target, so shares open the same sheet`() {
        listOf(AppIcon.VIDEO, AppIcon.INSTANT, AppIcon.GALLERY, AppIcon.INSTANT_GALLERY).forEach { target ->
            listOf("", "White", "Glass").forEach { suffix ->
                assertEquals(target, AppIcon.baseName("com.hazel.android.$target$suffix"))
            }
        }
        assertEquals("ShareOverlayActivity", AppIcon.baseName("com.hazel.android.ui.share.ShareOverlayActivity"))
    }
}
