package com.hazel.android.util

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FolderLinkTest {

    @Test
    fun `a picked folder on the built-in storage opens by its path, a card folder does not`() {
        val folder = File(Environment.getExternalStorageDirectory(), "Download/testing new dl").apply { mkdirs() }
        val tree = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:Download/testing new dl")
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:Download/testing new dl")
        assertEquals(folder.absolutePath, FolderUtil.localFolderOf(document)?.absolutePath)

        val card = DocumentsContract.buildDocumentUriUsingTree(
            DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "1234-ABCD:Hazel"), "1234-ABCD:Hazel"
        )
        assertNull(FolderUtil.localFolderOf(card))
        assertNull(FolderUtil.localFolderOf(Uri.parse("content://other.provider/document/primary%3ADownload")))
    }
}
