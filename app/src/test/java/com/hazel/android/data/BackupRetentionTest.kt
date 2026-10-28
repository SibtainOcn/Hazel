package com.hazel.android.data

import android.net.Uri
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Only the oldest automatic backups past the limit go; manual ones always stay. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupRetentionTest {

    private fun file(name: String, modified: Long) = BackupFile(Uri.parse("content://b/$name"), name, 10, modified)

    @Test
    fun prunesOnlyOldAutomaticBackups() {
        val files = listOf(
            file("hazel_auto_backup_1_a.json", 1),
            file("hazel_backup_b.json", 2),
            file("hazel_auto_backup_1_c.json", 3),
            file("hazel_auto_backup_1_d.json", 4)
        )
        assertEquals(listOf("hazel_auto_backup_1_a.json"), BackupRepository.toPrune(files, 2).map { it.name })
        assertTrue(BackupRepository.toPrune(files, 0).isEmpty())
        assertTrue(BackupRepository.toPrune(files, 5).isEmpty())
    }
}
