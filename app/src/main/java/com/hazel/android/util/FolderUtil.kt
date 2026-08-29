package com.hazel.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Handing a folder to whatever app the device uses to browse files.
 *
 * There is no single intent every OEM's file manager understands, so each strategy below is
 * tried in turn and the first one that resolves wins. The last resort simply tells the user
 * the path, which is still better than a silent no-op.
 */
object FolderUtil {

    /** Opens a folder that lives on the filesystem, creating it first if it is missing. */
    fun open(context: Context, folder: File) {
        if (!folder.exists()) folder.mkdirs()

        // Documents UI content URI, which most stock file managers accept.
        val relativePath = folder.absolutePath.substringAfter("/storage/emulated/0/")
        val encodedPath = relativePath.replace("/", "%2F")
        val documentsUri = Uri.parse(
            "content://com.android.externalstorage.documents/document/primary:$encodedPath"
        )
        if (startView(context, documentsUri, "vnd.android.document/directory")) return

        // FileProvider URI with the folder MIME type some managers expect.
        val providerUri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", folder)
        } catch (_: Exception) {
            null
        }
        if (providerUri != null &&
            startView(context, providerUri, "resource/folder", grantRead = true)
        ) return

        // Let the user pick a handler themselves.
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("file://${folder.absolutePath}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open folder"))
        } catch (_: Exception) {
            Toast.makeText(context, "Path: ${folder.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Opens a folder the user picked through the document picker. The tree URI is converted
     * to a document URI first, since a tree URI on its own is not viewable.
     */
    fun openTree(context: Context, treeUri: Uri) {
        val documentUri = try {
            android.provider.DocumentsContract.buildDocumentUriUsingTree(
                treeUri, android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            )
        } catch (_: Exception) {
            treeUri
        }
        if (startView(context, documentUri, "vnd.android.document/directory")) return
        Toast.makeText(context, MediaStoreHelper.describeTree(treeUri), Toast.LENGTH_LONG).show()
    }

    private fun startView(
        context: Context,
        uri: Uri,
        mimeType: String,
        grantRead: Boolean = false
    ): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (grantRead) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
