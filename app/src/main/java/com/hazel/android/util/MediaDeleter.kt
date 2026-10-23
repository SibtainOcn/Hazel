package com.hazel.android.util

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.system.Os
import java.io.File
import java.io.FileNotFoundException

/** Deletes a saved download and its media index row, whichever kind of address it was saved under. */
object MediaDeleter {

    enum class Result {
        /** The file was deleted. */
        DELETED,
        /** There was nothing left to delete. */
        GONE,
        /** The system has to ask the user first (a media row this install does not own). */
        NEEDS_CONSENT,
        FAILED
    }

    fun delete(context: Context, fileUri: String): Result {
        if (fileUri.isBlank()) return Result.GONE
        val uri = runCatching { Uri.parse(fileUri) }.getOrNull() ?: return Result.FAILED
        val resolver = context.contentResolver
        return try {
            val removed = when {
                uri.scheme == ContentResolver.SCHEME_FILE -> deletePath(context, uri.path)
                uri.authority == MediaStore.AUTHORITY -> resolver.delete(uri, null, null) > 0
                DocumentsContract.isDocumentUri(context, uri) -> DocumentsContract.deleteDocument(resolver, uri)
                else -> {
                    val path = pathOf(context, uri)
                    val ok = resolver.delete(uri, null, null) > 0 || (path != null && !File(path).exists())
                    if (ok && path != null) forgetIndex(context, path)
                    ok
                }
            }
            if (removed) Result.DELETED else afterFailure(context, uri, null)
        } catch (e: SecurityException) {
            afterFailure(context, uri, e)
        } catch (e: Exception) {
            afterFailure(context, uri, null)
        }
    }

    private fun afterFailure(context: Context, uri: Uri, error: SecurityException?): Result {
        if (error != null) {
            return if (Build.VERSION.SDK_INT >= 30 && uri.authority == MediaStore.AUTHORITY) Result.NEEDS_CONSENT
            else Result.FAILED
        }
        return if (stillThere(context, uri)) Result.FAILED else Result.GONE
    }

    /** Only a definite "not found" counts as gone; anything unclear keeps the file's record. */
    private fun stillThere(context: Context, uri: Uri): Boolean = try {
        if (uri.scheme == ContentResolver.SCHEME_FILE) uri.path?.let { File(it).exists() } != false
        else context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (_: FileNotFoundException) {
        false
    } catch (_: Exception) {
        true
    }

    /** One system prompt for media rows this install cannot delete on its own (Android 11+). */
    fun consentRequest(context: Context, uris: List<Uri>): PendingIntent? {
        if (Build.VERSION.SDK_INT < 30 || uris.isEmpty()) return null
        return runCatching { MediaStore.createDeleteRequest(context.contentResolver, uris) }.getOrNull()
    }

    private fun deletePath(context: Context, path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val file = File(path)
        val ok = !file.exists() || file.delete()
        if (ok) forgetIndex(context, path)
        return ok
    }

    /** The real path behind one of the app's own provider addresses, read before the file goes. */
    private fun pathOf(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            Os.readlink("/proc/self/fd/${pfd.fd}")
        }
    }.getOrNull()?.takeIf { it.startsWith("/") }

    /** Drops the media index row so galleries and players stop listing the file. */
    private fun forgetIndex(context: Context, path: String) {
        runCatching {
            context.contentResolver.delete(
                MediaStore.Files.getContentUri("external"),
                "${MediaStore.MediaColumns.DATA}=?",
                arrayOf(path)
            )
        }
        runCatching { MediaScannerConnection.scanFile(context.applicationContext, arrayOf(path), null, null) }
    }
}
