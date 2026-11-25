package com.hazel.android.gallery

import android.content.Context
import java.io.File

/**
 * gallery-dl as the app ships it: a zip in the APK's assets, built by
 * `tools/gallery-dl/build_payload.py`, copied out once per install or update of the app.
 */
internal object GalleryDlPayload {

    private const val ASSET = "gallery-dl.zip"
    private const val DIRECTORY = "gallery-dl"

    @Synchronized
    fun file(context: Context): File {
        val dir = File(context.noBackupFilesDir, DIRECTORY).apply { mkdirs() }
        val target = File(dir, "gallery-dl-${installStamp(context)}.zip")
        if (target.isFile && target.length() > 0) return target

        dir.listFiles()?.forEach { it.delete() }
        val partial = File(dir, "$ASSET.part")
        try {
            context.assets.open(ASSET).use { input -> partial.outputStream().use { input.copyTo(it) } }
            if (!partial.renameTo(target)) throw GalleryDlException(GalleryDlException.Kind.SETUP, "rename failed")
        } catch (e: GalleryDlException) {
            partial.delete()
            throw e
        } catch (e: Exception) {
            partial.delete()
            throw GalleryDlException(GalleryDlException.Kind.SETUP, e.message ?: "copy failed", e)
        }
        return target
    }

    /** Changes with every install or update, so a new build's zip replaces the old one. */
    private fun installStamp(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
}
