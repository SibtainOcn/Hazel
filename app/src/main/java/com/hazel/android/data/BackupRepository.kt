package com.hazel.android.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.hazel.android.BuildConfig
import com.hazel.android.R
import com.hazel.android.util.AppLocale
import com.hazel.android.util.MediaStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** What a backup can hold, each a set of stored keys. */
enum class BackupCategory(val id: String, @param:StringRes val label: Int, val keys: Set<String>) {
    SETTINGS("settings", R.string.backup_category_settings, emptySet()),
    HISTORY("history", R.string.backup_category_history, setOf("download_history")),
    QUEUE("queue", R.string.backup_category_queue, setOf("download_queue", "download_queue_held")),
    FAILED("failed", R.string.backup_category_failed, setOf("failed_downloads")),
    COOKIES("cookies", R.string.backup_category_cookies, setOf("cookie_entries", "use_cookies", "cookie_user_agent", "adv_po_tokens", "adv_visitor_data")),
    SEARCH("search", R.string.backup_category_search, setOf("search_history"));

    companion object {
        fun of(key: String): BackupCategory =
            entries.firstOrNull { key in it.keys } ?: SETTINGS

        fun byId(id: String): BackupCategory? = entries.firstOrNull { it.id == id }
    }
}

/** How often an automatic backup is made besides the one before each update. */
enum class BackupSchedule(val id: String, @param:StringRes val label: Int, val days: Long) {
    OFF("off", R.string.backup_schedule_off, 0),
    DAILY("daily", R.string.backup_schedule_daily, 1),
    WEEKLY("weekly", R.string.backup_schedule_weekly, 7);

    companion object {
        fun byId(id: String?): BackupSchedule = entries.firstOrNull { it.id == id } ?: OFF
    }
}

/** A backup file found in the backup folder. */
data class BackupFile(val uri: Uri, val name: String, val size: Long, val modified: Long) {
    val automatic: Boolean get() = name.startsWith(BackupRepository.AUTO_PREFIX)

    /**
     * When it was made: the stamp in its name, which survives copies and providers that
     * report no modified time, else what the folder says.
     */
    val madeAt: Long by lazy {
        NAME_STAMP.find(name)?.groupValues?.get(1)?.let { stamp ->
            runCatching { SimpleDateFormat(STAMP_PATTERN, Locale.ROOT).parse(stamp)?.time }.getOrNull()
        } ?: modified
    }

    private companion object {
        val NAME_STAMP = Regex("""(\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2})(?:\s*\(\d+\))?\.json$""")
    }
}

internal const val STAMP_PATTERN = "yyyy-MM-dd_HH-mm-ss"

/** The backup screen's settings, read together. */
data class BackupSettings(
    val beforeUpdates: Boolean,
    val schedule: BackupSchedule,
    val keep: Int,
    val include: Set<BackupCategory>,
    val folderLabel: String,
    val folderUri: String,
    val lastAt: Long,
    val lastSize: Long
)

/**
 * Backs up and restores the app's own data as one JSON file.
 *
 * Settings are written with their types. Lists are merged into what the device already has
 * on restore, so restoring an old backup never drops anything newer.
 */
object BackupRepository {

    private const val FORMAT = "hazel-backup"
    internal const val FORMAT_VERSION = 1
    private const val DEFAULT_FOLDER = "Download/Hazel/Backups"
    internal const val AUTO_PREFIX = "hazel_auto_backup_"
    private const val MANUAL_PREFIX = "hazel_backup_"
    const val DEFAULT_FOLDER_LABEL = DEFAULT_FOLDER

    /** The default folder on the built-in storage. */
    @Suppress("DEPRECATION")
    fun defaultFolder(): File = File(android.os.Environment.getExternalStorageDirectory(), DEFAULT_FOLDER)

    /** Choices for how many automatic backups are kept; 0 keeps them all. */
    val KEEP_CHOICES = listOf(3, 5, 10, 0)
    private const val DEFAULT_KEEP = 5

    private val AUTO_KEY = booleanPreferencesKey("backup_auto")
    private val TREE_URI_KEY = stringPreferencesKey("backup_tree_uri")
    private val TREE_LABEL_KEY = stringPreferencesKey("backup_tree_label")
    private val SCHEDULE_KEY = stringPreferencesKey("backup_schedule")
    private val KEEP_KEY = intPreferencesKey("backup_keep")
    private val INCLUDE_KEY = stringSetPreferencesKey("backup_include")
    private val LAST_AT_KEY = longPreferencesKey("backup_last_at")
    private val LAST_SIZE_KEY = longPreferencesKey("backup_last_size")
    private val LAST_AUTO_AT_KEY = longPreferencesKey("backup_last_auto_at")
    private val LAST_VERSION_KEY = stringPreferencesKey("backup_last_version")
    private val OFFER_RESTORE_KEY = booleanPreferencesKey("backup_offer_restore")
    private val AUTO_DONE_FOR_KEY = stringPreferencesKey("backup_auto_done_for")

    /** Keys that describe this device or this install, not the user's choices. */
    private const val PROXY_KEY = "adv_proxy"

    private fun isDeviceOnly(key: String) =
        key.startsWith("backup_") || key.startsWith("download_tree_") ||
            key.endsWith("_update_available") ||
            key in DEVICE_ONLY

    /** Update checks, one-time marks and this install's own state. */
    private val DEVICE_ONLY = setOf(
        "adv_impersonate_available", "whats_new_seen", "has_update_available", "hazel_update_version",
        "update_notify_off_applied", GlassDefaultMigration.SETTLED_NAME
    )

    /** Which list entries are the same entry, per key; null compares whole values. */
    private val IDENTITY = mapOf(
        "download_history" to "id",
        "download_queue" to "url",
        "failed_downloads" to "url",
        "cookie_entries" to "url",
        "search_history" to null
    )

    // Settings for the screen.

    fun getSettings(context: Context): Flow<BackupSettings> = context.dataStore.data.map { prefs ->
        BackupSettings(
            beforeUpdates = prefs[AUTO_KEY] ?: true,
            schedule = BackupSchedule.byId(prefs[SCHEDULE_KEY]),
            keep = prefs[KEEP_KEY] ?: DEFAULT_KEEP,
            include = includeOf(prefs),
            folderLabel = if (prefs[TREE_URI_KEY].isNullOrBlank()) "" else prefs[TREE_LABEL_KEY].orEmpty(),
            folderUri = prefs[TREE_URI_KEY].orEmpty(),
            lastAt = prefs[LAST_AT_KEY] ?: 0L,
            lastSize = prefs[LAST_SIZE_KEY] ?: 0L
        )
    }

    private fun includeOf(prefs: Preferences): Set<BackupCategory> =
        prefs[INCLUDE_KEY]?.mapNotNull(BackupCategory::byId)?.toSet()?.takeIf { it.isNotEmpty() }
            ?: BackupCategory.entries.toSet()

    suspend fun setAutoBackup(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[AUTO_KEY] = enabled }
    }

    suspend fun setSchedule(context: Context, schedule: BackupSchedule) {
        context.dataStore.edit { it[SCHEDULE_KEY] = schedule.id }
    }

    suspend fun setKeep(context: Context, keep: Int) {
        context.dataStore.edit { it[KEEP_KEY] = keep.coerceAtLeast(0) }
        autoLock.withLock { prune(context) }
    }

    suspend fun setInclude(context: Context, categories: Set<BackupCategory>) {
        if (categories.isEmpty()) return
        context.dataStore.edit { prefs -> prefs[INCLUDE_KEY] = categories.map { it.id }.toSet() }
    }

    /** A blank [treeUri] goes back to the default folder. */
    suspend fun setFolder(context: Context, treeUri: String, label: String) {
        context.dataStore.edit {
            it[TREE_URI_KEY] = treeUri
            it[TREE_LABEL_KEY] = label
        }
    }

    // Backup.

    /** Writes a backup of [categories] and returns its file name, or null if it failed. */
    suspend fun backup(
        context: Context,
        categories: Set<BackupCategory>,
        automatic: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        if (categories.isEmpty()) return@withContext null
        // Its own folder per backup, so one made at launch and one asked for at the same
        // moment never clear each other's file.
        val staging = File(context.cacheDir, "backup-staging-${System.nanoTime()}")
        runCatching {
            val prefs = context.dataStore.data.first()
            val text = export(prefs, categories, AppLocale.tag(context)).toString(2)
            val stamp = SimpleDateFormat(STAMP_PATTERN, Locale.ROOT).format(Date())
            val name = if (automatic) "$AUTO_PREFIX${BuildConfig.VERSION_NAME}_$stamp.json"
            else "$MANUAL_PREFIX$stamp.json"

            if (!staging.mkdirs()) return@runCatching null
            File(staging, name).writeText(text)

            val tree = prefs[TREE_URI_KEY].orEmpty()
            val saved = if (tree.isNotBlank()) {
                MediaStoreHelper.moveToTree(context, staging, Uri.parse(tree))
            } else {
                MediaStoreHelper.moveToPublicStorage(context, staging, DEFAULT_FOLDER) != staging
            }
            if (!saved) return@runCatching null
            context.dataStore.edit {
                it[LAST_AT_KEY] = System.currentTimeMillis()
                it[LAST_SIZE_KEY] = text.toByteArray().size.toLong()
            }
            name
        }.getOrNull().also { staging.deleteRecursively() }
    }

    /** An automatic backup of the chosen categories, then the oldest automatic ones go. */
    private suspend fun autoBackup(context: Context): Boolean = autoLock.withLock {
        val prefs = context.dataStore.data.first()
        val name = backup(context, includeOf(prefs), automatic = true) ?: return@withLock false
        context.dataStore.edit { it[LAST_AUTO_AT_KEY] = System.currentTimeMillis() }
        prune(context, justMade = name)
        true
    }

    /** One automatic backup or prune at a time, so two never race over the same files. */
    private val autoLock = Mutex()

    /**
     * Called when an update to the app is found, so the data from before it can come back.
     * Once per installed version, however often the check finds the update again.
     */
    suspend fun autoBackupBeforeUpdate(context: Context) {
        val prefs = context.dataStore.data.first()
        if (prefs[AUTO_KEY] == false || prefs[AUTO_DONE_FOR_KEY] == BuildConfig.VERSION_NAME) return
        if (runCatching { autoBackup(context) }.getOrDefault(false)) {
            context.dataStore.edit { it[AUTO_DONE_FOR_KEY] = BuildConfig.VERSION_NAME }
        }
    }

    /**
     * Run once per launch. Backs up when the app was updated since the last launch, however
     * the update arrived, or when the schedule is due. On the first launch of an install with
     * nothing in it, marks that a restore should be offered.
     */
    suspend fun onLaunch(context: Context) = runCatching {
        val prefs = context.dataStore.data.first()
        val last = prefs[LAST_VERSION_KEY]
        val current = BuildConfig.VERSION_NAME
        if (last != current) context.dataStore.edit { it[LAST_VERSION_KEY] = current }
        if (last == null) {
            if (looksFresh(prefs)) context.dataStore.edit { it[OFFER_RESTORE_KEY] = true }
            return@runCatching
        }
        val updated = last != current && prefs[AUTO_KEY] != false
        if (updated || isDue(prefs, System.currentTimeMillis())) autoBackup(context)
    }

    internal fun isDue(prefs: Preferences, now: Long): Boolean {
        val days = BackupSchedule.byId(prefs[SCHEDULE_KEY]).days
        if (days == 0L) return false
        return now - (prefs[LAST_AUTO_AT_KEY] ?: 0L) >= TimeUnit.DAYS.toMillis(days)
    }

    /** No downloads and next to no choices made: a new or reinstalled app. */
    internal fun looksFresh(prefs: Preferences): Boolean {
        val names = prefs.asMap().keys.map { it.name }.filterNot(::isDeviceOnly)
        return "download_history" !in names && names.size < FRESH_KEY_LIMIT
    }

    private const val FRESH_KEY_LIMIT = 3

    fun offerRestore(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[OFFER_RESTORE_KEY] == true }

    suspend fun dismissRestoreOffer(context: Context) {
        context.dataStore.edit { it.remove(OFFER_RESTORE_KEY) }
    }

    internal fun export(prefs: Preferences, categories: Set<BackupCategory>, language: String? = null): JSONObject {
        val values = JSONObject()
        prefs.asMap().forEach { (key, value) ->
            val name = key.name
            if (isDeviceOnly(name) || BackupCategory.of(name) !in categories) return@forEach
            // A proxy's password stays on the device; the address and user name are kept.
            val kept = if (name == PROXY_KEY && value is String) {
                com.hazel.android.download.NetworkProxy.withoutPassword(value)
            } else value
            typed(kept)?.let { values.put(name, it) }
        }
        return JSONObject().apply {
            put("format", FORMAT)
            put("formatVersion", FORMAT_VERSION)
            put("appVersion", BuildConfig.VERSION_NAME)
            put("createdAt", System.currentTimeMillis())
            put("categories", JSONArray(categories.map { it.id }))
            if (BackupCategory.SETTINGS in categories && !language.isNullOrBlank()) put("language", language)
            put("values", values)
        }
    }

    private fun typed(value: Any): JSONObject? {
        val (type, stored) = when (value) {
            is Boolean -> "b" to value
            is Int -> "i" to value
            is Long -> "l" to value
            is Float -> "f" to value.toDouble()
            is Double -> "d" to value
            is String -> "s" to value
            is Set<*> -> "S" to JSONArray(value.filterIsInstance<String>())
            else -> return null
        }
        return JSONObject().put("t", type).put("v", stored)
    }

    // Saved backups.

    /** Every backup in the backup folder, newest first. Empty when the folder cannot be read. */
    suspend fun list(context: Context): List<BackupFile> = withContext(Dispatchers.IO) {
        runCatching {
            val tree = context.dataStore.data.first()[TREE_URI_KEY].orEmpty()
            if (tree.isNotBlank()) listTree(context, Uri.parse(tree)) else listDefault(context)
        }.getOrDefault(emptyList())
            .filter { it.name.endsWith(".json") && (it.automatic || it.name.startsWith(MANUAL_PREFIX)) }
            .sortedByDescending { it.madeAt }
    }

    private fun listTree(context: Context, tree: Uri): List<BackupFile> {
        val parent = MediaStoreHelper.folderDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        return context.contentResolver.query(children, columns, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    add(BackupFile(DocumentsContract.buildDocumentUriUsingTree(tree, id), c.getString(1).orEmpty(), c.getLong(2), c.getLong(3)))
                }
            }
        }.orEmpty()
    }

    /**
     * Backups in the default folder, through the media store. From Android 11 the store only
     * shows files this install wrote, so a reinstall picks older ones with the file picker.
     */
    private fun listDefault(context: Context): List<BackupFile> {
        val (collection, selection, arg) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Triple(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", "$DEFAULT_FOLDER/")
        } else {
            @Suppress("DEPRECATION")
            Triple(MediaStore.Files.getContentUri("external"), "${MediaStore.MediaColumns.DATA} LIKE ?", "%/$DEFAULT_FOLDER/%")
        }
        val columns = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        return context.contentResolver.query(collection, columns, selection, arrayOf(arg), null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val uri = ContentUris.withAppendedId(collection, c.getLong(0))
                    add(BackupFile(uri, c.getString(1).orEmpty(), c.getLong(2), TimeUnit.SECONDS.toMillis(c.getLong(3))))
                }
            }
        }.orEmpty()
    }

    suspend fun delete(context: Context, file: BackupFile): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (DocumentsContract.isDocumentUri(context, file.uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, file.uri)
            } else {
                context.contentResolver.delete(file.uri, null, null) > 0
            }
        }.getOrDefault(false)
    }

    /**
     * Deletes the oldest automatic backups past the number kept. Manual ones are never
     * touched, nor is [justMade], and nothing goes when the folder could not be read.
     */
    suspend fun prune(context: Context, justMade: String? = null) {
        val keep = context.dataStore.data.first()[KEEP_KEY] ?: DEFAULT_KEEP
        toPrune(list(context), keep, justMade).forEach { delete(context, it) }
    }

    internal fun toPrune(files: List<BackupFile>, keep: Int, justMade: String? = null): List<BackupFile> =
        if (keep <= 0) emptyList()
        else files.filter { it.automatic && it.name != justMade }
            .sortedByDescending { it.madeAt }
            .drop(if (justMade != null && files.any { it.name == justMade }) keep - 1 else keep)

    // Restore.

    /** A backup read from a file, before anything is written. */
    class Contents internal constructor(
        val categories: Set<BackupCategory>,
        val appVersion: String,
        val createdAt: Long,
        val language: String?,
        internal val values: JSONObject
    ) {
        /** How many entries each category holds, for the preview; settings count their keys. */
        val counts: Map<BackupCategory, Int> by lazy {
            val counts = mutableMapOf<BackupCategory, Int>()
            values.keys().forEach { name ->
                val category = BackupCategory.of(name)
                val entry = values.optJSONObject(name)
                val size = if (name in IDENTITY && entry?.optString("t") == "s") {
                    runCatching { JSONArray(entry.optString("v")).length() }.getOrDefault(0)
                } else if (category == BackupCategory.SETTINGS) 1 else 0
                counts[category] = (counts[category] ?: 0) + size
            }
            counts
        }
    }

    class NotABackup : Exception()

    /** Made by a newer version of the app, whose format this one may misread. */
    class NewerBackup(val appVersion: String) : Exception()

    suspend fun read(context: Context, uri: Uri): Contents = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        }.getOrNull() ?: throw NotABackup()
        parse(text)
    }

    internal fun parse(text: String): Contents {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: throw NotABackup()
        if (json.optString("format") != FORMAT) throw NotABackup()
        val appVersion = json.optString("appVersion")
        if (json.optInt("formatVersion", FORMAT_VERSION) > FORMAT_VERSION) throw NewerBackup(appVersion)
        val values = json.optJSONObject("values") ?: JSONObject()
        val listed = json.optJSONArray("categories") ?: JSONArray()
        val categories = (0 until listed.length()).mapNotNull { BackupCategory.byId(listed.optString(it)) }.toSet()
        return Contents(
            categories = categories,
            appVersion = appVersion,
            createdAt = json.optLong("createdAt"),
            language = json.optString("language").takeIf { it.isNotBlank() },
            values = values
        )
    }

    /** Writes the chosen [categories] of [contents] into the app. */
    suspend fun restore(context: Context, contents: Contents, categories: Set<BackupCategory>) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                apply(prefs, contents, categories)
                prefs.remove(OFFER_RESTORE_KEY)
            }
        }
        if (BackupCategory.COOKIES in categories) CookieRepository.writeCookieFile(context)
        val language = contents.language
        if (BackupCategory.SETTINGS in categories && language != null && language != AppLocale.tag(context)) {
            withContext(Dispatchers.Main) { runCatching { AppLocale.set(context, language) } }
        }
    }

    internal fun apply(prefs: MutablePreferences, contents: Contents, categories: Set<BackupCategory>) {
        contents.values.keys().forEach { name ->
            if (isDeviceOnly(name) || BackupCategory.of(name) !in categories) return@forEach
            val entry = contents.values.optJSONObject(name) ?: return@forEach
            when (entry.optString("t")) {
                "b" -> prefs[booleanPreferencesKey(name)] = entry.optBoolean("v")
                "i" -> prefs[intPreferencesKey(name)] = entry.optInt("v")
                "l" -> prefs[longPreferencesKey(name)] = entry.optLong("v")
                "f" -> prefs[floatPreferencesKey(name)] = entry.optDouble("v").toFloat()
                "d" -> prefs[doublePreferencesKey(name)] = entry.optDouble("v")
                "S" -> entry.optJSONArray("v")?.let { array ->
                    prefs[stringSetPreferencesKey(name)] = (0 until array.length()).map { array.optString(it) }.toSet()
                }
                "s" -> {
                    val key = stringPreferencesKey(name)
                    val incoming = if (name == "download_queue") waitingForResume(entry.optString("v"))
                    else entry.optString("v")
                    prefs[key] = if (name in IDENTITY) mergeLists(prefs[key], incoming, IDENTITY[name]) else incoming
                }
            }
        }
    }

    /**
     * Restored downloads wait to be resumed rather than starting on the next launch, and
     * drop the folder they were picked into, whose grant belonged to the other install.
     */
    private fun waitingForResume(queue: String): String = runCatching {
        val array = JSONArray(queue)
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.apply {
                put("paused", true)
                put("treeUri", "")
            }
        }
        array.toString()
    }.getOrDefault(queue)

    /** What the device has, then each entry of the backup it does not have yet. */
    internal fun mergeLists(current: String?, incoming: String, identity: String?): String {
        val mine = runCatching { JSONArray(current ?: "[]") }.getOrElse { JSONArray() }
        val theirs = runCatching { JSONArray(incoming) }.getOrElse { return current ?: incoming }
        fun idOf(item: Any?): String = when {
            identity != null && item is JSONObject -> "${item.opt(identity)}"
            else -> item.toString()
        }
        val seen = (0 until mine.length()).map { idOf(mine.opt(it)) }.toMutableSet()
        for (i in 0 until theirs.length()) {
            val item = theirs.opt(i)
            if (seen.add(idOf(item))) mine.put(item)
        }
        return mine.toString()
    }
}
