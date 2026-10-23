package com.hazel.android.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Adds up the files inside a folder picked through the document picker. */
object FolderSize {
    /** Past this many entries the walk stops; the total is then a lower bound. */
    private const val MAX_ENTRIES = 20_000

    /** Bytes held under [treeUri], or null when the folder cannot be read. Call off the main thread. */
    suspend fun of(context: Context, treeUri: String): Long? {
        val tree = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return null
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
        val resolver = context.contentResolver
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE)
        val pending = ArrayDeque<String>().apply { add(rootId) }
        var total = 0L
        var seen = 0
        var readAny = false
        while (pending.isNotEmpty() && seen < MAX_ENTRIES) {
            currentCoroutineContext().ensureActive()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, pending.removeFirst())
            runCatching {
                resolver.query(children, columns, null, null, null)?.use { c ->
                    readAny = true
                    while (c.moveToNext() && seen < MAX_ENTRIES) {
                        seen++
                        if (c.getString(1) == Document.MIME_TYPE_DIR) {
                            c.getString(0)?.let(pending::addLast)
                        } else if (!c.isNull(2)) {
                            total += c.getLong(2).coerceAtLeast(0L)
                        }
                    }
                }
            }
        }
        return if (readAny) total else null
    }
}
