package com.hazel.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StorageSpaceTest {

    @Test
    fun `used share follows free space and stays in range`() {
        assertEquals(0.75f, StorageSpace(freeBytes = 25, totalBytes = 100).usedFraction, 0.001f)
        assertEquals(0f, StorageSpace(freeBytes = 100, totalBytes = 100).usedFraction, 0.001f)
        assertEquals(1f, StorageSpace(freeBytes = 0, totalBytes = 100).usedFraction, 0.001f)
    }

    @Test
    fun `a missing folder has no space to show`() {
        assertNull(StorageSpace.of(null))
    }
}
