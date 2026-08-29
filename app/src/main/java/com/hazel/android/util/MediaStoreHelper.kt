package com.hazel.android.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * MediaStoreHelper: moves files from app-specific temp dir to public shared storage
 * via MediaStore API. Works on ALL API levels without MANAGE_EXTERNAL_STORAGE.
 *
 * API ≤ 29: Direct file move (WRITE_EXTERNAL_STORAGE grants access)
 * API 30+ : MediaStore insert (ownership-based, no special permission needed)
 *
 * Uses FileChannel.transferTo() for zero-copy kernel-level file transfer.
 */
object MediaStoreHelper {

    private const val TAG = "MediaStoreHelper"

    /**
     * Move all files from a temp directory to their public final location.
     * Handles single files, playlist subfolders, and batch folders.
     *
     * @param context    Application context
     * @param tempDir    The temp directory (e.g., StoragePaths.tempDownloads or a subfolder)
     * @param relativePath  MediaStore relative path (e.g., "Download/Hazel" or "Download/Hazel/PlaylistName")
     * @param isMusic    true for audio files (uses MediaStore.Audio), false for downloads
     * @return the final public directory where files were moved
     */
    fun moveToPublicStorage(
        context: Context,
        tempDir: File,
        relativePath: String,
        isMusic: Boolean = false,
        onMoved: (Uri) -> Unit = {}
    ): File {
        val files = tempDir.listFiles()?.filter { it.isFile } ?: return tempDir

        for (file in files) {
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    moveViaMediaStore(context, file, relativePath, isMusic)?.let(onMoved)
                } else {
                    moveViaDirect(file, relativePath).let(onMoved)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to move ${file.name}: ${e.message}")
                // File stays in temp dir, still accessible, just not in public storage
            }
        }

        // Return the final public directory
        return if (Build.VERSION.SDK_INT >= 30) {
            val basePath = if (isMusic) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
            // Extract subdirectory from relativePath (e.g., "Download/Hazel/Playlist" → "Hazel/Playlist")
            val subPath = relativePath.substringAfter("/")
            File(basePath, subPath.substringAfter("/").ifEmpty { subPath })
        } else {
            File(Environment.getExternalStorageDirectory(), relativePath)
        }
    }

    /**
     * API 30+: Insert file via MediaStore (no MANAGE_EXTERNAL_STORAGE needed).
     * App owns the MediaStore entry → full write access.
     */
    private fun moveViaMediaStore(
        context: Context,
        sourceFile: File,
        relativePath: String,
        isMusic: Boolean
    ): Uri? {
        val resolver = context.contentResolver

        val collection = if (isMusic && Build.VERSION.SDK_INT >= 30) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(sourceFile))
            put(MediaStore.MediaColumns.SIZE, sourceFile.length())
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(collection, values) ?: run {
            Log.w(TAG, "MediaStore insert returned null for ${sourceFile.name}")
            return null
        }

        // Stream copy (ContentResolver URIs don't support FileChannel output)
        try {
            resolver.openOutputStream(uri)?.use { os ->
                sourceFile.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        os.write(buffer, 0, bytesRead)
                    }
                }
            } ?: throw Exception("Could not open output stream")

            // Mark as complete
            if (Build.VERSION.SDK_INT >= 29) {
                val updateValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, updateValues, null, null)
            }

            // Delete source after successful move
            sourceFile.delete()
            Log.d(TAG, "Moved ${sourceFile.name} to $relativePath via MediaStore")
            return uri

        } catch (e: Exception) {
            // If transfer fails, clean up the MediaStore entry
            resolver.delete(uri, null, null)
            throw e
        }
    }

    /**
     * API ≤ 29: Direct file move using WRITE_EXTERNAL_STORAGE.
     */
    private fun moveViaDirect(sourceFile: File, relativePath: String): Uri {
        val destDir = File(Environment.getExternalStorageDirectory(), relativePath)
        if (!destDir.exists()) destDir.mkdirs()

        val destFile = File(destDir, sourceFile.name)

        // Try rename first (instant if same partition)
        if (sourceFile.renameTo(destFile)) {
            Log.d(TAG, "Moved ${sourceFile.name} to $relativePath via rename")
            return Uri.fromFile(destFile)
        }

        // Fallback: zero-copy channel transfer
        FileInputStream(sourceFile).channel.use { src ->
            FileOutputStream(destFile).channel.use { dst ->
                src.transferTo(0, src.size(), dst)
            }
        }
        sourceFile.delete()
        Log.d(TAG, "Moved ${sourceFile.name} to $relativePath via copy")
        return Uri.fromFile(destFile)
    }

    /**
     * Moves everything in [tempDir] into a folder the user picked through the system
     * document picker.
     *
     * The picked folder is held as a persisted tree URI rather than a path, because a
     * user-chosen destination can sit on an SD card or a provider that has no filesystem
     * path at all. Writing goes through DocumentsContract for the same reason.
     *
     * A file that cannot be written is left in [tempDir] rather than being lost, matching
     * how [moveToPublicStorage] handles a failed move.
     *
     * @return true when every file was moved, false when at least one was left behind.
     */
    fun moveToTree(
        context: Context,
        tempDir: File,
        treeUri: Uri,
        onMoved: (Uri) -> Unit = {}
    ): Boolean {
        val files = tempDir.listFiles()?.filter { it.isFile } ?: return true
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )

        var allMoved = true
        for (file in files) {
            try {
                val target = DocumentsContract.createDocument(
                    resolver, parent, getMimeType(file), file.name
                ) ?: throw Exception("Provider refused to create ${file.name}")

                resolver.openOutputStream(target)?.use { os ->
                    file.inputStream().use { it.copyTo(os, DEFAULT_BUFFER_SIZE) }
                } ?: throw Exception("Could not open output stream")

                file.delete()
                onMoved(target)
                Log.d(TAG, "Moved ${file.name} to $treeUri")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to move ${file.name} to picked folder: ${e.message}")
                allMoved = false
            }
        }
        return allMoved
    }

    /**
     * Human-readable name for a picked tree, e.g. "Movies/Clips" on primary storage.
     * Falls back to the raw document id when the provider is not the storage framework.
     */
    fun describeTree(treeUri: Uri): String {
        val id = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            return treeUri.toString()
        }
        val volume = id.substringBefore(':', "")
        val path = id.substringAfter(':', "")
        return when {
            path.isBlank() && volume.isBlank() -> id
            volume == "primary" -> path.ifBlank { "Internal storage" }
            path.isBlank() -> volume
            else -> "$volume/$path"
        }
    }

    /**
     * Scan files with MediaScanner so they appear in gallery/music apps.
     */
    fun scanFiles(context: Context, dir: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        val paths = files.map { it.absolutePath }.toTypedArray()
        val mimeTypes = files.map { getMimeType(it) }.toTypedArray()
        MediaScannerConnection.scanFile(context, paths, mimeTypes, null)
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            else -> "application/octet-stream"
        }
    }
}
