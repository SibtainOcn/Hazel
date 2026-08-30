package com.hazel.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.FetchMode
import com.hazel.android.download.extractor.ListingSource
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
    private val LISTING_SOURCE_KEY = stringPreferencesKey("listing_source")

    /**
     * Which extractor is asked what a link holds. Defaults to yt-dlp, which is the engine
     * that updates itself in the field and does the downloading either way.
     */
    fun getListingSource(context: Context): Flow<ListingSource> =
        context.dataStore.data.map { prefs -> ListingSource.fromName(prefs[LISTING_SOURCE_KEY]) }

    suspend fun setListingSource(context: Context, source: ListingSource) {
        context.dataStore.edit { prefs -> prefs[LISTING_SOURCE_KEY] = source.name }
    }

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

    // ── First run ──

    private val GUIDE_SEEN_KEY = booleanPreferencesKey("guide_seen")

    /**
     * Whether the getting-started dialog has been shown. It appears once and never again,
     * because everything in it is either discoverable afterwards or repeated in More.
     *
     * Null while the stored value is still being read, so the caller can tell "not seen"
     * apart from "not known yet" and avoid flashing the dialog up on every launch.
     */
    fun getGuideSeen(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[GUIDE_SEEN_KEY] ?: false }

    suspend fun setGuideSeen(context: Context) {
        context.dataStore.edit { prefs -> prefs[GUIDE_SEEN_KEY] = true }
    }

    // ── Incognito ──

    private val INCOGNITO_KEY = booleanPreferencesKey("incognito")

    /**
     * While on, a download leaves no record: nothing is written to the downloads list and
     * no link is remembered for the search suggestions. The file itself still arrives, in
     * the same place it always would. This is about what the app keeps, not about hiding
     * anything from the device or the network.
     *
     * Deliberately not persisted across launches would be the wrong call either way, so it
     * is persisted: a mode you have to remember to re-enable is one that fails quietly.
     */
    fun getIncognito(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[INCOGNITO_KEY] ?: false }

    suspend fun setIncognito(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[INCOGNITO_KEY] = enabled }
    }

    // ── Network ──

    private val WIFI_ONLY_KEY = booleanPreferencesKey("wifi_only")
    private val SPEED_LIMIT_KEY = stringPreferencesKey("speed_limit")

    /**
     * Refuses to start a download while the phone is on mobile data.
     *
     * Checked when a download starts rather than throughout: a transfer already running
     * when the connection changes is left alone, because killing it partway to save data
     * wastes what it has already spent.
     */
    fun getWifiOnly(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[WIFI_ONLY_KEY] ?: false }

    suspend fun setWifiOnly(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[WIFI_ONLY_KEY] = enabled }
    }

    /**
     * Ceiling on transfer speed, as yt-dlp spells it: a number with an optional K or M, so
     * "500K" or "1.5M". Blank means no ceiling, which is the default.
     */
    fun getSpeedLimit(context: Context): Flow<String> =
        context.dataStore.data.map { prefs -> prefs[SPEED_LIMIT_KEY].orEmpty() }

    suspend fun setSpeedLimit(context: Context, limit: String) {
        context.dataStore.edit { prefs -> prefs[SPEED_LIMIT_KEY] = limit.trim() }
    }

    /** True when [limit] is something yt-dlp will accept, or blank. */
    fun isValidSpeedLimit(limit: String): Boolean =
        limit.isBlank() || Regex("""^\d+(\.\d+)?[KMkm]?$""").matches(limit.trim())

    // ── List layout ──
    //
    // Big artwork or a tight list. Two settings rather than one: the results list is read
    // while deciding what to download, where the artwork is what identifies a link, and the
    // downloads list is read while looking for a file that is already there, where a name
    // finds it faster. Somebody who wants one of those compact does not necessarily want
    // the other, so each screen remembers its own answer.

    private val RESULTS_COMPACT_KEY = booleanPreferencesKey("results_compact")
    private val HISTORY_COMPACT_KEY = booleanPreferencesKey("history_compact")

    fun getResultsCompact(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[RESULTS_COMPACT_KEY] ?: false }

    suspend fun setResultsCompact(context: Context, compact: Boolean) {
        context.dataStore.edit { prefs -> prefs[RESULTS_COMPACT_KEY] = compact }
    }

    fun getHistoryCompact(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[HISTORY_COMPACT_KEY] ?: false }

    suspend fun setHistoryCompact(context: Context, compact: Boolean) {
        context.dataStore.edit { prefs -> prefs[HISTORY_COMPACT_KEY] = compact }
    }

    // ── Direct share ──
    //
    // Sharing to the direct target skips the sheet entirely, so the choices the sheet would
    // have asked for have to be answered in advance. These are those answers.

    private val QUICK_IS_VIDEO_KEY = booleanPreferencesKey("quick_is_video")
    private val QUICK_MAX_HEIGHT_KEY = intPreferencesKey("quick_max_height")

    /** Whether a direct share saves video or audio. */
    fun getQuickIsVideo(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[QUICK_IS_VIDEO_KEY] ?: true }

    suspend fun setQuickIsVideo(context: Context, isVideo: Boolean) {
        context.dataStore.edit { prefs -> prefs[QUICK_IS_VIDEO_KEY] = isVideo }
    }

    /**
     * Tallest video a direct share will take, or 0 for whatever the source calls best.
     *
     * A cap rather than an exact height: sources do not all offer the same ladder, and a
     * request for a height that is missing would have nothing to fall back to.
     */
    fun getQuickMaxHeight(context: Context): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[QUICK_MAX_HEIGHT_KEY] ?: 0 }

    suspend fun setQuickMaxHeight(context: Context, height: Int) {
        context.dataStore.edit { prefs -> prefs[QUICK_MAX_HEIGHT_KEY] = height }
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
