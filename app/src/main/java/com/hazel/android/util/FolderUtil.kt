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

        // Most stock file managers open a documents URI directly, on every version from
        // Android 7 onwards.
        if (documentUri != null &&
            startView(context, documentUri, "vnd.android.document/directory")
        ) return

        // Some builders' own managers answer to this and nothing else.
        val providerUri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", folder)
        }.getOrNull()
        if (providerUri != null &&
            startView(context, providerUri, "resource/folder", grantRead = true)
        ) return

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
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri)
            )
        }.getOrDefault(treeUri)

        if (startView(context, documentUri, "vnd.android.document/directory")) return

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

    private fun startView(
        context: Context,
        uri: Uri,
        mimeType: String,
        grantRead: Boolean = false
    ): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (grantRead) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
