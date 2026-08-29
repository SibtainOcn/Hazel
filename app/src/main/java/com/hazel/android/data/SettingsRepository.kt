package com.hazel.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.FetchMode
import kotlinx.coroutines.flow.Flow
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hazel_settings")

object SettingsRepository {

    private val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")
    private val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
    private val FILENAME_TEMPLATE_KEY = stringPreferencesKey("filename_template")
    private val EMBED_THUMBNAIL_KEY = booleanPreferencesKey("embed_thumbnail")

    // Theme
    fun isDarkTheme(context: Context): Flow<Boolean?> {
        return context.dataStore.data.map { prefs -> prefs[DARK_THEME_KEY] }
    }

    suspend fun setDarkTheme(context: Context, isDark: Boolean) {
        context.dataStore.edit { prefs -> prefs[DARK_THEME_KEY] = isDark }
    }

    // Accent color
    fun getAccentColor(context: Context): Flow<String> {
        return context.dataStore.data.map { prefs -> prefs[ACCENT_COLOR_KEY] ?: "White" }
    }

    suspend fun setAccentColor(context: Context, name: String) {
        context.dataStore.edit { prefs -> prefs[ACCENT_COLOR_KEY] = name }
    }

    // Output filename template passed to yt-dlp as -o
    fun getFilenameTemplate(context: Context): Flow<String> {
        return context.dataStore.data.map { prefs ->
            prefs[FILENAME_TEMPLATE_KEY] ?: DEFAULT_FILENAME_TEMPLATE
        }
    }
    suspend fun setFilenameTemplate(context: Context, template: String) {
        context.dataStore.edit { prefs -> prefs[FILENAME_TEMPLATE_KEY] = template }
    }

    // Whether to embed the cover art into the downloaded file
    fun getEmbedThumbnail(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs -> prefs[EMBED_THUMBNAIL_KEY] ?: false }
    }
    suspend fun setEmbedThumbnail(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[EMBED_THUMBNAIL_KEY] = enabled }
    }

    const val DEFAULT_FILENAME_TEMPLATE = DownloadOptions.DEFAULT_FILENAME_TEMPLATE

    // ── Download sheet options ──
    //
    // Everything the sheet can adjust is read back as one DownloadOptions so the screen
    // observes a single flow instead of a dozen, and so a new knob only needs a key here.

    private val VIDEO_CONTAINER_KEY = stringPreferencesKey("video_container")
    private val AUDIO_CONTAINER_KEY = stringPreferencesKey("audio_container")
    private val SPONSORBLOCK_KEY = stringSetPreferencesKey("sponsorblock_filters")
    private val ADD_CHAPTERS_KEY = booleanPreferencesKey("add_chapters")
    private val SPLIT_CHAPTERS_KEY = booleanPreferencesKey("split_by_chapters")
    private val EMBED_SUBS_KEY = booleanPreferencesKey("embed_subs")
    private val WRITE_SUBS_KEY = booleanPreferencesKey("write_subs")
    private val WRITE_AUTO_SUBS_KEY = booleanPreferencesKey("write_auto_subs")
    private val SUB_LANGUAGES_KEY = stringPreferencesKey("sub_languages")

    fun getDownloadOptions(context: Context): Flow<DownloadOptions> =
        context.dataStore.data.map { prefs ->
            val defaults = DownloadOptions()
            DownloadOptions(
                videoContainer = prefs[VIDEO_CONTAINER_KEY] ?: defaults.videoContainer,
                audioContainer = prefs[AUDIO_CONTAINER_KEY] ?: defaults.audioContainer,
                embedThumbnail = prefs[EMBED_THUMBNAIL_KEY] ?: defaults.embedThumbnail,
                filenameTemplate = prefs[FILENAME_TEMPLATE_KEY] ?: defaults.filenameTemplate,
                sponsorBlockFilters = prefs[SPONSORBLOCK_KEY] ?: defaults.sponsorBlockFilters,
                addChapters = prefs[ADD_CHAPTERS_KEY] ?: defaults.addChapters,
                splitByChapters = prefs[SPLIT_CHAPTERS_KEY] ?: defaults.splitByChapters,
                embedSubs = prefs[EMBED_SUBS_KEY] ?: defaults.embedSubs,
                writeSubs = prefs[WRITE_SUBS_KEY] ?: defaults.writeSubs,
                writeAutoSubs = prefs[WRITE_AUTO_SUBS_KEY] ?: defaults.writeAutoSubs,
                subLanguages = prefs[SUB_LANGUAGES_KEY] ?: defaults.subLanguages
            )
        }

    suspend fun setDownloadOptions(context: Context, options: DownloadOptions) {
        context.dataStore.edit { prefs ->
            prefs[VIDEO_CONTAINER_KEY] = options.videoContainer
            prefs[AUDIO_CONTAINER_KEY] = options.audioContainer
            prefs[EMBED_THUMBNAIL_KEY] = options.embedThumbnail
            prefs[FILENAME_TEMPLATE_KEY] = options.filenameTemplate
            prefs[SPONSORBLOCK_KEY] = options.sponsorBlockFilters
            prefs[ADD_CHAPTERS_KEY] = options.addChapters
            prefs[SPLIT_CHAPTERS_KEY] = options.splitByChapters
            prefs[EMBED_SUBS_KEY] = options.embedSubs
            prefs[WRITE_SUBS_KEY] = options.writeSubs
            prefs[WRITE_AUTO_SUBS_KEY] = options.writeAutoSubs
            prefs[SUB_LANGUAGES_KEY] = options.subLanguages
        }
    }

    // ── Link reading ──
    //
    // Applies to every site: these are plain yt-dlp network settings, not per-extractor
    // behaviour, so changing them cannot break one source while helping another.

    private val FETCH_MODE_KEY = stringPreferencesKey("fetch_mode")
    private val FORCE_IPV4_KEY = booleanPreferencesKey("force_ipv4")

    fun getFetchMode(context: Context): Flow<FetchMode> =
        context.dataStore.data.map { prefs -> FetchMode.fromName(prefs[FETCH_MODE_KEY]) }

    suspend fun setFetchMode(context: Context, mode: FetchMode) {
        context.dataStore.edit { prefs -> prefs[FETCH_MODE_KEY] = mode.name }
    }

    /**
     * Forcing IPv4 works around networks whose IPv6 route to a CDN is broken, at the cost
     * of the faster route everywhere else, so it is off unless asked for.
     */
    fun getForceIpv4(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[FORCE_IPV4_KEY] ?: false }

    suspend fun setForceIpv4(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[FORCE_IPV4_KEY] = enabled }
    }

    // ── Download destination ──
    //
    // Blank means the built-in Download/Hazel folder. Otherwise this is a persisted SAF
    // tree URI the user picked, with its human-readable form kept alongside it so the sheet
    // has something to show without having to resolve the document provider on every frame.

    private val DOWNLOAD_TREE_URI_KEY = stringPreferencesKey("download_tree_uri")
    private val DOWNLOAD_TREE_LABEL_KEY = stringPreferencesKey("download_tree_label")

    fun getDownloadTreeUri(context: Context): Flow<String> =
        context.dataStore.data.map { prefs -> prefs[DOWNLOAD_TREE_URI_KEY] ?: "" }

    fun getDownloadTreeLabel(context: Context): Flow<String> =
        context.dataStore.data.map { prefs -> prefs[DOWNLOAD_TREE_LABEL_KEY] ?: "" }

    suspend fun setDownloadTree(context: Context, uri: String, label: String) {
        context.dataStore.edit { prefs ->
            prefs[DOWNLOAD_TREE_URI_KEY] = uri
            prefs[DOWNLOAD_TREE_LABEL_KEY] = label
        }
    }

    suspend fun clearDownloadTree(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.remove(DOWNLOAD_TREE_URI_KEY)
            prefs.remove(DOWNLOAD_TREE_LABEL_KEY)
        }
    }

    // yt-dlp update channel persistence ("Stable" / "Nightly" / "Master")
    private val YTDLP_CHANNEL_KEY = stringPreferencesKey("ytdlp_channel")

    fun getYtDlpChannel(context: Context): Flow<String> {
        return context.dataStore.data.map { prefs -> prefs[YTDLP_CHANNEL_KEY] ?: "Stable" }
    }
    suspend fun setYtDlpChannel(context: Context, channel: String) {
        context.dataStore.edit { prefs -> prefs[YTDLP_CHANNEL_KEY] = channel }
    }
}
