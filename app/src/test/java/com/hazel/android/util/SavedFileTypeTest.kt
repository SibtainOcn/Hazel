package com.hazel.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedFileTypeTest {

    @Test
    fun `images and text are saved under their own type, so a repeat name keeps its extension`() {
        assertEquals("image/png", MediaStoreHelper.mimeTypeOf("pin.png"))
        assertEquals("image/jpeg", MediaStoreHelper.mimeTypeOf("photo.JPG"))
        assertEquals("image/webp", MediaStoreHelper.mimeTypeOf("a.b.webp"))
        assertEquals("text/plain", MediaStoreHelper.mimeTypeOf("caption.txt"))
        assertEquals("video/mp4", MediaStoreHelper.mimeTypeOf("clip.mp4"))
    }
}
