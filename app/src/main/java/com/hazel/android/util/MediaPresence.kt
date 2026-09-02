package com.hazel.android.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Whether the file a finished download produced is still on the device.
 *
 * A download record says what happened, not what is there now: the file can be moved or
 * deleted from a gallery or a file manager at any point afterwards. Opening the address is
 * not enough to tell the difference. From Android 11 a gallery delete moves the row to the
 * system bin rather than removing it, and the app that owns the row can still open it from
 * in there, so a file the user deleted read as present right up until they emptied the bin
 * as well. The index is asked whether the row is binned or half written before the file
 * behind it is opened at all.
 *
 * Answers are held, because this touches storage and is asked once per row on a list that
 * can run to hundreds. [forget] drops them, which is what a screen returning to the
 * foreground does: coming back is exactly when a held answer is most likely to be stale.
 */
object MediaPresence {

    private val known = ConcurrentHashMap<String, Boolean>()

    /** Answers for [fileUri], reading storage only when no answer is held for it. */
    suspend fun exists(context: Context, fileUri: String): Boolean =
        known[fileUri] ?: refresh(context, fileUri)

    /** Asks storage again, whatever was held before, and keeps what it finds. */
    suspend fun refresh(context: Context, fileUri: String): Boolean =
        withContext(Dispatchers.IO) {
            val present = fileUri.isNotBlank() &&
                    runCatching { check(context, Uri.parse(fileUri)) }.getOrDefault(false)
            known[fileUri] = present
            present
        }

    /** Throws away every held answer, so the next ask reads storage. */
    fun forget() {
        known.clear()
    }

    private fun check(context: Context, uri: Uri): Boolean {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.let { File(it).exists() } == true
        }

        // A row the index reports as binned or still being written opens exactly as a
        // finished one does, so that has to be settled before the address is tried.
        if (indexSaysGone(context, uri) == true) return false

        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    /**
     * What the media index says about the row, or null where it has nothing to say: an
     * address that is not its own, or a version without the columns to answer with.
     *
     * True means binned or half written, both of which are the file not being there as far
     * as anything that wants to play it is concerned.
     */
    private fun indexSaysGone(context: Context, uri: Uri): Boolean? {
        if (Build.VERSION.SDK_INT < 30) return null
        if (uri.authority != MediaStore.AUTHORITY) return null

        // Binned rows are left out of an ordinary query, so a missing row would be
        // indistinguishable from a binned one. Asking for them explicitly is what makes an
        // empty answer mean the row is genuinely gone.
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
        }

        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.IS_TRASHED, MediaStore.MediaColumns.IS_PENDING),
                args,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use true
                cursor.getInt(0) != 0 || cursor.getInt(1) != 0
            }
        }.getOrNull()
    }
}
