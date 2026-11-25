package com.hazel.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CaptionMetadataTest {

    private val caption = "Calico cats 🎀🧡 & <friends>\nsecond line"

    @Test
    fun `a caption written into a JPEG and a PNG reads back the same, twice over`() {
        val jpeg = byteArrayOf(-1, -40, -1, -32, 0, 16, 74, 70, 73, 70, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0, -1, -38, 0, 2, -1, -39)
        val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10) +
            chunk("IHDR", ByteArray(13)) + chunk("IDAT", ByteArray(0)) + chunk("IEND", ByteArray(0))
        listOf("a.jpg" to jpeg, "a.png" to png).forEach { (name, bytes) ->
            val file = File.createTempFile("cap", name).apply { writeBytes(bytes) }
            try {
                assertTrue(CaptionMetadata.embed(file, "old"))
                assertTrue(CaptionMetadata.embed(file, caption))
                assertEquals(caption, file.inputStream().use(CaptionMetadata::read))
            } finally {
                file.delete()
            }
        }
    }

    @Test
    fun `other formats are left alone`() {
        val file = File.createTempFile("cap", ".webp").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try {
            assertFalse(CaptionMetadata.embed(file, caption))
        } finally {
            file.delete()
        }
    }

    private fun chunk(type: String, data: ByteArray): ByteArray =
        byteArrayOf(0, 0, 0, data.size.toByte()) + type.toByteArray() + data + ByteArray(4)
}
