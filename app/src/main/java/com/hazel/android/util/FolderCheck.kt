package com.hazel.android.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract.Document

/** Whether a folder picked earlier can still be saved into. */
object FolderCheck {

    /**
     * True when the folder behind [treeUri] is still there, is a folder, and takes new files
     * through the grant Hazel holds. A folder that was deleted, a card that was taken out and
     * a grant that was revoked all answer false rather than throwing. Blocking: call it off
     * the main thread.
     */
    fun usable(context: Context, treeUri: String): Boolean = runCatching {
        val document = MediaStoreHelper.folderDocument(Uri.parse(treeUri))
        context.contentResolver.query(
            document,
            arrayOf(Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS),
            null, null, null
        )?.use { row ->
            row.moveToFirst() &&
                row.getString(0) == Document.MIME_TYPE_DIR &&
                (row.getInt(1) and Document.FLAG_DIR_SUPPORTS_CREATE) != 0
        } ?: false
    }.getOrDefault(false)
}
