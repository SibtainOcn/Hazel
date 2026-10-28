package com.hazel.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Handing a folder to whatever app the device uses to browse files.
 *
 * There is no one intent every builder's file manager answers, so each approach below is
 * tried in turn and the first that resolves wins. What each of them needs differs by
 * version as well as by device, which is why the order matters and why the last resort is
 * simply telling the user the path.
 */
object FolderUtil {

    /** The provider that owns everything on the built-in storage. */
    private const val EXTERNAL_STORAGE_PROVIDER = "com.android.externalstorage.documents"

    /** Opens a folder that lives on the filesystem, creating it first if it is missing. */
    fun open(context: Context, folder: File) {
        if (!folder.exists()) folder.mkdirs()

        val documentUri = documentUriFor(folder)
        val providerUri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", folder)
        }.getOrNull()

        // Every file manager on the device, offered together, rather than whichever one
        // answers the first way of asking.
        if (chooseManager(context, documentUri, providerUri)) return

        // The system's own picker, opened at this folder. Nothing is being picked: it is
        // the one file browser guaranteed to be present, and this is how it is asked to
        // start somewhere in particular. EXTRA_INITIAL_URI only exists from Android 8, and
        // is a hint even there, so a device that ignores it opens the picker at its own
        // idea of the top rather than failing.
        if (documentUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val opened = runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, documentUri)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }.isSuccess
            if (opened) return
        }

        // Deliberately not a file:// intent. Passing one has thrown FileUriExposedException
        // since Android 7, which is every version this app runs on, so the path is put on
        // screen instead where the user can at least go and find it.
        Toast.makeText(context, folder.absolutePath, Toast.LENGTH_LONG).show()
    }

    /**
     * The documents URI for a folder on the built-in storage.
     *
     * Built through DocumentsContract rather than by pasting a path into a string, which
     * left the separators unencoded and quietly produced a URI for the wrong folder. Null
     * for anything that is not on the primary volume, such as a removable card, where the
     * document id is the volume's own and cannot be worked out from the path.
     */
    private fun documentUriFor(folder: File): Uri? {
        val root = Environment.getExternalStorageDirectory().absolutePath
        val path = folder.absolutePath
        if (!path.startsWith(root)) return null

        val relative = path.removePrefix(root).trim('/')
        if (relative.isBlank()) return null

        return runCatching {
            DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_PROVIDER, "primary:$relative")
        }.getOrNull()
    }

    /**
     * Opens a folder the user picked through the document picker. The tree URI is converted
     * to a document URI first, since a tree URI on its own is not viewable.
     */
    fun openTree(context: Context, treeUri: Uri) {
        val documentUri = runCatching {
            MediaStoreHelper.folderDocument(treeUri)
        }.getOrDefault(treeUri)

        if (chooseManager(context, documentUri, null)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val opened = runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, documentUri)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }.isSuccess
            if (opened) return
        }

        Toast.makeText(context, MediaStoreHelper.describeTree(treeUri), Toast.LENGTH_LONG).show()
    }

    /** The folder types file managers register for, most specific first. */
    private val FOLDER_TYPES = listOf("vnd.android.document/directory", "resource/folder", "inode/directory")

    /**
     * Gathers every app that opens a folder in any of the ways managers ask for, one entry per
     * app, and lets the user pick. A single app opens directly. False when nothing answered.
     */
    private fun chooseManager(context: Context, documentUri: Uri?, providerUri: Uri?): Boolean = runCatching {
        val asks = buildList {
            documentUri?.let { uri -> FOLDER_TYPES.forEach { add(viewIntent(uri, it, grantRead = false)) } }
            providerUri?.let { uri -> FOLDER_TYPES.drop(1).forEach { add(viewIntent(uri, it, grantRead = true)) } }
        }
        val pm = context.packageManager
        val seen = HashSet<String>()
        val targets = asks.flatMap { ask ->
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(ask, 0).mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                if (!activity.exported || !seen.add(activity.packageName)) return@mapNotNull null
                Intent(ask).setClassName(activity.packageName, activity.name)
            }
        }
        when (targets.size) {
            0 -> false
            1 -> { context.startActivity(targets[0].addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
            else -> {
                val chooser = Intent.createChooser(targets[0], context.getString(com.hazel.android.R.string.media_open_chooser))
                    .putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.drop(1).toTypedArray())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                true
            }
        }
    }.getOrDefault(false)

    private fun viewIntent(uri: Uri, type: String, grantRead: Boolean) = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, type)
        if (grantRead) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

}
