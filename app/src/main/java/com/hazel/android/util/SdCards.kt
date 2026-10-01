package com.hazel.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import java.io.File

/**
 * A removable card (or other removable volume) that is mounted right now.
 *
 * [appDir] is the app's own folder on it, Android/data/<package>/files, which the app can
 * write to with plain file paths on every supported version and without any permission.
 * It is null when the system has not handed one out for this card.
 */
data class SdCard(val uuid: String, val name: String, val appDir: File?) {
    /** Space left on the card, or null when there is no folder on it to measure from. */
    val freeBytes: Long? get() = appDir?.let { runCatching { it.usableSpace }.getOrNull() }
    val totalBytes: Long? get() = appDir?.let { runCatching { it.totalSpace }.getOrNull() }
}

/**
 * Everything the app knows about removable storage.
 *
 * A download chosen to go to a card is saved through the document picker's grant, like any
 * picked folder, since that is the only way to write to a card's shared folders from
 * Android 7 to the newest versions. What this adds is a way to start the picker on the card
 * itself, a tidy Hazel folder when the card's root is picked, and a working folder on the
 * same card so a large download does not have to fit in internal storage first.
 */
object SdCards {

    private const val EXTERNAL_STORAGE_PROVIDER = "com.android.externalstorage.documents"

    /** Lets the picker show removable volumes on versions that hide them by default. */
    private const val EXTRA_SHOW_ADVANCED = "android.content.extra.SHOW_ADVANCED"

    /** The folder the app works in on a card, beside the internal one of the same name. */
    private const val WORK_DIR = "Hazel"

    private lateinit var appContext: Context

    /** Must be called once from HazelApp.onCreate() */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** The removable volumes mounted right now; empty on a device without a card. */
    fun list(context: Context = appContext): List<SdCard> = runCatching {
        val manager = context.getSystemService(StorageManager::class.java)
            ?: return emptyList()
        val appDirs = context.getExternalFilesDirs(null).filterNotNull()
        manager.storageVolumes
            .filter { it.isRemovable && !it.isPrimary && it.uuid != null && it.state == Environment.MEDIA_MOUNTED }
            .map { volume ->
                val uuid = volume.uuid!!
                SdCard(
                    uuid = uuid,
                    name = volume.getDescription(context).ifBlank { uuid },
                    appDir = appDirs.firstOrNull { it.absolutePath.contains("/$uuid/") }
                )
            }
    }.getOrDefault(emptyList())

    /** How the picker is pointed at a card, by what the running version offers. */
    enum class PickerRoute {
        /** Android 10+: the volume builds its own picker intent. */
        VOLUME_INTENT,
        /** Android 8 and 9: the card's root is handed over as the place to start. */
        INITIAL_URI,
        /** Android 7: neither exists; the card is among the picker's roots to tap. */
        PLAIN
    }

    fun pickerRoute(sdk: Int): PickerRoute = when {
        sdk >= Build.VERSION_CODES.Q -> PickerRoute.VOLUME_INTENT
        sdk >= Build.VERSION_CODES.O -> PickerRoute.INITIAL_URI
        else -> PickerRoute.PLAIN
    }

    /**
     * The picker, opened on [card]. From Android 10 the volume itself builds the intent,
     * which is the one way to land on the card reliably. On 8 and 9 the card's root is passed
     * as a starting point, which the picker honours on most devices. On 7 the picker opens
     * where it likes, with the card listed among the roots for the user to tap.
     */
    fun pickerIntent(context: Context, card: SdCard): Intent {
        val route = pickerRoute(Build.VERSION.SDK_INT)
        if (route == PickerRoute.VOLUME_INTENT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            volumeFor(context, card.uuid)?.createOpenDocumentTreeIntent()?.let {
                return it.putExtra(EXTRA_SHOW_ADVANCED, true)
            }
        }
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            if (route != PickerRoute.PLAIN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_PROVIDER, "${card.uuid}:")
                )
            }
            putExtra(EXTRA_SHOW_ADVANCED, true)
        }
    }

    private fun volumeFor(context: Context, uuid: String): StorageVolume? = runCatching {
        context.getSystemService(StorageManager::class.java)
            ?.storageVolumes?.firstOrNull { it.uuid == uuid }
    }.getOrNull()

    /** The volume of a storage document id: "1A2B-3C4D" for "1A2B-3C4D:Music". */
    fun volumeOfDocumentId(id: String): String? =
        id.substringBefore(':', "").takeIf { it.isNotBlank() }

    /** True for the id of a volume's root, such as "1A2B-3C4D:". */
    fun isVolumeRoot(id: String): Boolean =
        id.contains(':') && id.substringAfter(':').isBlank()

    /** The card volume of a document id, or null for internal storage and anything else. */
    fun cardVolumeOfDocumentId(id: String): String? =
        volumeOfDocumentId(id)?.takeUnless { it.equals("primary", ignoreCase = true) }

    /** The card a picked folder lives on, when it lives on one that is mounted. */
    fun cardOf(uri: String, context: Context = appContext): SdCard? {
        if (uri.isBlank()) return null
        val parsed = Uri.parse(uri)
        if (parsed.authority != EXTERNAL_STORAGE_PROVIDER) return null
        val id = runCatching { MediaStoreHelper.folderDocumentId(parsed) }.getOrNull() ?: return null
        val volume = cardVolumeOfDocumentId(id) ?: return null
        return list(context).firstOrNull { it.uuid.equals(volume, ignoreCase = true) }
    }

    /** The readable name of a volume id, for labels: the card's own name when it is known. */
    fun nameOf(volume: String): String? = runCatching {
        list().firstOrNull { it.uuid.equals(volume, ignoreCase = true) }?.name
    }.getOrNull()

    /**
     * Where a download saved to [treeUri] works: on the same card when it is going to one,
     * so the card's space is spent rather than the phone's, and the internal working folder
     * otherwise. Falls back to internal whenever the card has no app folder to offer.
     */
    fun workRootFor(treeUri: String): File =
        chooseWorkRoot(cardOf(treeUri)?.appDir, StoragePaths.tempDownloads)

    /**
     * The card's working folder when it can be made and written, [fallback] otherwise. A
     * card pulled out, mounted read-only or refusing the folder must never stop a download.
     */
    fun chooseWorkRoot(cardAppDir: File?, fallback: File): File {
        val dir = cardAppDir?.let { File(it, WORK_DIR) } ?: return fallback
        runCatching { if (!dir.exists()) dir.mkdirs() }
        return if (dir.isDirectory && dir.canWrite()) dir else fallback
    }

    /**
     * Every working folder the app may have written downloads into: the internal one and
     * the one on each card that is mounted. Cleanup and cancelling go through all of them,
     * so nothing left on a card is missed.
     */
    fun workRoots(context: Context = appContext): List<File> = workRootsAmong(
        StoragePaths.tempDownloads,
        runCatching { context.getExternalFilesDirs(null).filterNotNull().filter { isRemovable(it) } }
            .getOrDefault(emptyList())
    )

    fun workRootsAmong(internal: File, cardAppDirs: List<File>): List<File> =
        (listOf(internal) + cardAppDirs.map { File(it, WORK_DIR) }.filter { it.isDirectory })
            .distinctBy { it.absolutePath }

    /** True for a file on removable storage; false when it cannot be told. */
    fun isRemovable(file: File): Boolean =
        runCatching { Environment.isExternalStorageRemovable(file) }.getOrDefault(false)

    /**
     * The folder downloads of one kind go to inside a picked folder on a card.
     *
     * Picking a card usually means picking its root, and a root filling up with downloads
     * is not what anyone wants, so the root gets Hazel/Audio or Hazel/Video made inside it,
     * the same layout the built-in folder uses. A folder below the root was chosen on
     * purpose and is used as it is, and comes back unchanged. Null when the folders were
     * needed but the card would not make them, so the caller can say so.
     */
    fun kindFolderIn(context: Context, treeUri: Uri, isVideo: Boolean): Uri? = runCatching {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        if (!isVolumeRoot(rootId)) return treeUri
        val hazel = childFolder(context, treeUri, rootId, "Hazel") ?: return null
        childFolder(context, treeUri, DocumentsContract.getDocumentId(hazel), kindFolderName(isVideo))
    }.getOrNull()

    fun kindFolderName(isVideo: Boolean): String = if (isVideo) "Video" else "Audio"

    /** A folder named [name] under [parentId], found if it is there and made if it is not. */
    private fun childFolder(context: Context, treeUri: Uri, parentId: String, name: String): Uri? {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name &&
                    cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                ) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
                }
            }
        }
        return DocumentsContract.createDocument(
            resolver,
            DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId),
            DocumentsContract.Document.MIME_TYPE_DIR,
            name
        )
    }
}
