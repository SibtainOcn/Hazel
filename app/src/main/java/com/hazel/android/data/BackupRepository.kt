package com.hazel.android.data

import android.content.Context
import android.net.Uri
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
import com.hazel.android.util.MediaStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What a backup can hold, each a set of stored keys. */
enum class BackupCategory(val id: String, @StringRes val label: Int, val keys: Set<String>) {
    SETTINGS("settings", R.string.backup_category_settings, emptySet()),
    HISTORY("history", R.string.backup_category_history, setOf("download_history")),
    QUEUE("queue", R.string.backup_category_queue, setOf("download_queue")),
    FAILED("failed", R.string.backup_category_failed, setOf("failed_downloads")),
    COOKIES("cookies", R.string.backup_category_cookies, setOf("cookie_entries", "use_cookies", "cookie_user_agent")),
    SEARCH("search", R.string.backup_category_search, setOf("search_history"));

    companion object {
        fun of(key: String): BackupCategory =
            entries.firstOrNull { key in it.keys } ?: SETTINGS

        fun byId(id: String): BackupCategory? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Backs up and restores the app's own data as one JSON file.
 *
 * Settings are written with their types. Lists are merged into what the device already has
 * on restore, so restoring an old backup never drops anything newer.
 */
object BackupRepository {

    private const val FORMAT = "hazel-backup"
    private const val FORMAT_VERSION = 1
    private const val DEFAULT_FOLDER = "Download/Hazel/Backups"

    private val AUTO_KEY = booleanPreferencesKey("backup_auto")
    private val TREE_URI_KEY = stringPreferencesKey("backup_tree_uri")
    private val TREE_LABEL_KEY = stringPreferencesKey("backup_tree_label")
    private val AUTO_DONE_FOR_KEY = stringPreferencesKey("backup_auto_done_for")

    /** Keys that describe this device or this install, not the user's choices. */
    private fun isDeviceOnly(key: String) =
        key.startsWith("backup_") || key.startsWith("download_tree_") ||
            key.endsWith("_update_available") || key == "adv_impersonate_available"

    /** Which list entries are the same entry, per key; null compares whole values. */
    private val IDENTITY = mapOf(
        "download_history" to "id",
        "download_queue" to "url",
        "failed_downloads" to "url",
        "cookie_entries" to "url",
        "search_history" to null
    )

    // Settings for the screen.

    fun getAutoBackup(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[AUTO_KEY] ?: true }

    suspend fun setAutoBackup(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[AUTO_KEY] = enabled }
    }

    /** The picked folder's label, or blank for the default one. */
    fun getFolderLabel(context: Context): Flow<String> =
        context.dataStore.data.map { if (it[TREE_URI_KEY].isNullOrBlank()) "" else it[TREE_LABEL_KEY].orEmpty() }

    suspend fun setFolder(context: Context, treeUri: String, label: String) {
        context.dataStore.edit {
            it[TREE_URI_KEY] = treeUri
            it[TREE_LABEL_KEY] = label
        }
    }

    const val DEFAULT_FOLDER_LABEL = DEFAULT_FOLDER

    // Backup.

    /** Writes a backup of [categories] and returns its file name, or null if it failed. */
    suspend fun backup(
        context: Context,
        categories: Set<BackupCategory>,
        automatic: Boolean = false
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val json = export(context.dataStore.data.first(), categories)
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
            val name = if (automatic) "hazel_auto_backup_${BuildConfig.VERSION_NAME}_$stamp.json"
            else "hazel_backup_$stamp.json"

            val staging = File(context.cacheDir, "backup-staging").apply {
                deleteRecursively()
                mkdirs()
            }
            File(staging, name).writeText(json.toString(2))

            val tree = context.dataStore.data.first()[TREE_URI_KEY].orEmpty()
            val saved = if (tree.isNotBlank()) {
                MediaStoreHelper.moveToTree(context, staging, Uri.parse(tree))
            } else {
                MediaStoreHelper.moveToPublicStorage(context, staging, DEFAULT_FOLDER) != staging
            }
            staging.deleteRecursively()
            name.takeIf { saved }
        }.getOrNull()
    }

    /**
     * One backup of everything for the version installed, made when an update to the app is
     * found, so the data from before the update can always be brought back.
     */
    suspend fun autoBackupBeforeUpdate(context: Context) {
        val prefs = context.dataStore.data.first()
        if (prefs[AUTO_KEY] == false) return
        if (prefs[AUTO_DONE_FOR_KEY] == BuildConfig.VERSION_NAME) return
        if (backup(context, BackupCategory.entries.toSet(), automatic = true) != null) {
            context.dataStore.edit { it[AUTO_DONE_FOR_KEY] = BuildConfig.VERSION_NAME }
        }
    }

    internal fun export(prefs: Preferences, categories: Set<BackupCategory>): JSONObject {
        val values = JSONObject()
        prefs.asMap().forEach { (key, value) ->
            val name = key.name
            if (isDeviceOnly(name) || BackupCategory.of(name) !in categories) return@forEach
            typed(value)?.let { values.put(name, it) }
        }
        return JSONObject().apply {
            put("format", FORMAT)
            put("formatVersion", FORMAT_VERSION)
            put("appVersion", BuildConfig.VERSION_NAME)
            put("createdAt", System.currentTimeMillis())
            put("categories", JSONArray(categories.map { it.id }))
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

    // Restore.

    /** A backup read from a file, before anything is written. */
    class Contents internal constructor(val categories: Set<BackupCategory>, internal val values: JSONObject)

    class NotABackup : Exception()

    suspend fun read(context: Context, uri: Uri): Contents = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            ?: throw NotABackup()
        parse(text)
    }

    internal fun parse(text: String): Contents {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: throw NotABackup()
        if (json.optString("format") != FORMAT) throw NotABackup()
        val values = json.optJSONObject("values") ?: JSONObject()
        val listed = json.optJSONArray("categories")
        val categories = (0 until (listed?.length() ?: 0))
            .mapNotNull { BackupCategory.byId(listed!!.optString(it)) }
            .toSet()
        return Contents(categories, values)
    }

    /** Writes the chosen [categories] of [contents] into the app. */
    suspend fun restore(context: Context, contents: Contents, categories: Set<BackupCategory>) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs -> apply(prefs, contents, categories) }
        }
        if (BackupCategory.COOKIES in categories) CookieRepository.writeCookieFile(context)
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
            identity != null && item is JSONObject -> item.opt(identity).toString()
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
