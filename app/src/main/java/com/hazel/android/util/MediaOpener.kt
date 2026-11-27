package com.hazel.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.hazel.android.R

/**
 * Handing a finished download to another app: the device's default for its kind, a chosen
 * one, a folder view or a share. Playing in Hazel itself goes through OfflinePlayback.
 */
object MediaOpener {

    /**
     * Opens the file itself.
     *
     * The type is stated as a video or audio wildcard rather than the exact container, so
     * the choice of player is not narrowed by a codec detail the user does not care about.
     * When nothing on the device claims the type, the chooser is offered before giving up.
     */
    fun play(context: Context, fileUri: String, isVideo: Boolean) = play(context, fileUri, mimeOf(isVideo))

    fun play(context: Context, fileUri: String, mimeType: String) {
        if (fileUri.isBlank()) {
            Toast.makeText(
                context,
                context.getString(R.string.media_open_unknown_location),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(fileUri), mimeType)
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
    /** Opens a picked folder, or the built-in one: for [isVideo]'s kind when it is given. */
    fun openLocation(context: Context, treeUri: String, isVideo: Boolean? = null) {
        if (treeUri.isNotBlank()) {
            FolderUtil.openTree(context, Uri.parse(treeUri))
        } else {
            FolderUtil.open(
                context,
                isVideo?.let { StoragePaths.finalDownloads(isAudio = !it) } ?: StoragePaths.finalDownloads
            )
        }
    }

    /** Always asks which app opens the file, rather than going to the default player. */
    fun openWith(context: Context, fileUri: String, isVideo: Boolean) = openWith(context, fileUri, mimeOf(isVideo))

    fun openWith(context: Context, fileUri: String, mimeType: String) {
        if (fileUri.isBlank()) return toast(context, R.string.media_open_unknown_location)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(fileUri), mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Asked from inside Hazel, so Hazel's own player is left off the list.
        val chooser = Intent.createChooser(view, context.getString(R.string.media_open_chooser)).putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(android.content.ComponentName(context, com.hazel.android.ui.player.PlayerActivity::class.java))
        )
        start(context, chooser)
    }

    /** Sends the file itself to another app. */
    fun share(context: Context, fileUri: String, isVideo: Boolean) = share(context, fileUri, mimeOf(isVideo))

    fun share(context: Context, fileUri: String, mimeType: String) {
        if (fileUri.isBlank()) return toast(context, R.string.media_open_unknown_location)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, Uri.parse(fileUri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        start(context, Intent.createChooser(send, null))
    }

    /**
     * Opens the folder holding the file: the folder inside a picked tree it was saved to, or
     * the app's own folder for its kind.
     */
    fun showFile(context: Context, fileUri: String, isVideo: Boolean, isImage: Boolean = false) {
        val uri = runCatching { Uri.parse(fileUri) }.getOrNull()
        val folder = uri?.takeIf { MediaStoreHelper.isDocumentInTree(it.pathSegments) }?.let {
            runCatching {
                val parent = android.provider.DocumentsContract.getDocumentId(it).substringBeforeLast('/', "")
                parent.takeIf { id -> id.isNotEmpty() }
                    ?.let { id -> android.provider.DocumentsContract.buildDocumentUriUsingTree(it, id) }
            }.getOrNull()
        }
        when {
            folder != null -> FolderUtil.openTree(context, folder)
            isImage -> FolderUtil.open(context, java.io.File(StoragePaths.finalDownloads, "Gallery"))
            else -> openLocation(context, "", isVideo)
        }
    }

    private fun start(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isFailure) toast(context, R.string.media_open_no_app)
    }

    private fun toast(context: Context, id: Int) {
        Toast.makeText(context, context.getString(id), Toast.LENGTH_SHORT).show()
    }
    private fun mimeOf(isVideo: Boolean) = if (isVideo) "video/*" else "audio/*"
}
