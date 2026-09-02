package com.hazel.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.hazel.android.R

/**
 * Handing a finished download to whatever the device plays that kind of media with.
 *
 * The app deliberately does not carry a player. A downloaded file is an ordinary file, and
 * the device already has an app the user has chosen for it; opening it there is both less
 * code and the behaviour people expect from a downloader.
 */
object MediaOpener {

    /**
     * Opens the file itself.
     *
     * The type is stated as a video or audio wildcard rather than the exact container, so
     * the choice of player is not narrowed by a codec detail the user does not care about.
     * When nothing on the device claims the type, the chooser is offered before giving up.
     */
    fun play(context: Context, fileUri: String, isVideo: Boolean) {
        if (fileUri.isBlank()) {
            Toast.makeText(
                context,
                context.getString(R.string.media_open_unknown_location),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(fileUri), if (isVideo) "video/*" else "audio/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (runCatching { context.startActivity(intent) }.isSuccess) return

        // Nothing claimed it by default, so let the user say what should.
        val chooser = Intent.createChooser(intent, context.getString(R.string.media_open_chooser))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(chooser) }.isSuccess) return

        Toast.makeText(
            context,
            context.getString(R.string.media_open_no_app),
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Opens the folder a download was saved into.
     *
     * A download either went to the folder the app owns or to one the user picked through
     * the document picker, and those are opened in different ways. [treeUri] is the picked
     * folder if there is one; without it the app's own folder is what there is to show.
     */
    fun openLocation(context: Context, treeUri: String) {
        if (treeUri.isNotBlank()) {
            FolderUtil.openTree(context, Uri.parse(treeUri))
        } else {
            FolderUtil.open(context, StoragePaths.finalDownloads)
        }
    }
}
