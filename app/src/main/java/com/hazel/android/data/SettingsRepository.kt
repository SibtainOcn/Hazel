package com.hazel.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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

    /**
     * One set of option keys.
     *
     * There are two sets, under different names in the same store. The sheet writes one and
     * the instant share reads the other, so a decision made for a download being watched
     * cannot silently change what an unattended share does with the next link, which is the
     * one download nobody is there to correct.
     *
     * The unprefixed set keeps the names it has always had, so settings made before the
     * split are still the sheet's settings.
     */
    private class OptionKeys(prefix: String) {
        val videoContainer = stringPreferencesKey("${prefix}video_container")
        val audioContainer = stringPreferencesKey("${prefix}audio_container")
        val embedThumbnail = booleanPreferencesKey("${prefix}embed_thumbnail")
        val filenameTemplate = stringPreferencesKey("${prefix}filename_template")
        val sponsorBlock = stringSetPreferencesKey("${prefix}sponsorblock_filters")
        val addChapters = booleanPreferencesKey("${prefix}add_chapters")
        val splitChapters = booleanPreferencesKey("${prefix}split_by_chapters")
        val embedSubs = booleanPreferencesKey("${prefix}embed_subs")
        val writeSubs = booleanPreferencesKey("${prefix}write_subs")
        val writeAutoSubs = booleanPreferencesKey("${prefix}write_auto_subs")
        val subLanguages = stringPreferencesKey("${prefix}sub_languages")
    }

    private val SHEET_OPTIONS = OptionKeys("")
    private val INSTANT_OPTIONS = OptionKeys("instant_")

    private fun Preferences.readOptions(keys: OptionKeys): DownloadOptions {
        val defaults = DownloadOptions()
        return DownloadOptions(
            videoContainer = this[keys.videoContainer] ?: defaults.videoContainer,
            audioContainer = this[keys.audioContainer] ?: defaults.audioContainer,
            embedThumbnail = this[keys.embedThumbnail] ?: defaults.embedThumbnail,
            filenameTemplate = this[keys.filenameTemplate] ?: defaults.filenameTemplate,
            sponsorBlockFilters = this[keys.sponsorBlock] ?: defaults.sponsorBlockFilters,
            addChapters = this[keys.addChapters] ?: defaults.addChapters,
            splitByChapters = this[keys.splitChapters] ?: defaults.splitByChapters,
            embedSubs = this[keys.embedSubs] ?: defaults.embedSubs,
            writeSubs = this[keys.writeSubs] ?: defaults.writeSubs,
            writeAutoSubs = this[keys.writeAutoSubs] ?: defaults.writeAutoSubs,
            subLanguages = this[keys.subLanguages] ?: defaults.subLanguages
        )
    }

    private fun MutablePreferences.writeOptions(keys: OptionKeys, options: DownloadOptions) {
        this[keys.videoContainer] = options.videoContainer
        this[keys.audioContainer] = options.audioContainer
        this[keys.embedThumbnail] = options.embedThumbnail
        this[keys.filenameTemplate] = options.filenameTemplate
        this[keys.sponsorBlock] = options.sponsorBlockFilters
        this[keys.addChapters] = options.addChapters
        this[keys.splitChapters] = options.splitByChapters
        this[keys.embedSubs] = options.embedSubs
        this[keys.writeSubs] = options.writeSubs
        this[keys.writeAutoSubs] = options.writeAutoSubs
        this[keys.subLanguages] = options.subLanguages
    }

    fun getDownloadOptions(context: Context): Flow<DownloadOptions> =
        context.dataStore.data.map { prefs -> prefs.readOptions(SHEET_OPTIONS) }

    suspend fun setDownloadOptions(context: Context, options: DownloadOptions) {
        context.dataStore.edit { prefs -> prefs.writeOptions(SHEET_OPTIONS, options) }
    }

    private val INSTANT_AUDIO_LANGUAGE_KEY = stringPreferencesKey("instant_audio_language")

    /**
     * The soundtrack an instant share prefers, as a language tag, or blank for whichever
     * the source leads with.
     *
     * Nothing is asked at share time, so this is a standing preference rather than a
     * choice: a source that has the language gets it, and one that does not is downloaded
     * with what it has.
     */
    fun getInstantAudioLanguage(context: Context): Flow<String> =
        context.dataStore.data.map { prefs -> prefs[INSTANT_AUDIO_LANGUAGE_KEY].orEmpty() }

    suspend fun setInstantAudioLanguage(context: Context, language: String) {
        context.dataStore.edit { prefs -> prefs[INSTANT_AUDIO_LANGUAGE_KEY] = language }
    }

    /** The same knobs, kept separately for the share target that asks nothing. */
    fun getInstantOptions(context: Context): Flow<DownloadOptions> =
        context.dataStore.data.map { prefs -> prefs.readOptions(INSTANT_OPTIONS) }

    suspend fun setInstantOptions(context: Context, options: DownloadOptions) {
        context.dataStore.edit { prefs -> prefs.writeOptions(INSTANT_OPTIONS, options) }
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

    /**
     * The ceilings offered, paired with what each is called.
     *
     * A list rather than a field to type in: the value has a shape yt-dlp expects, a typo
     * in it is only discovered when a download runs slower than a modem, and nobody has a
     * particular number in mind anyway. These cover the reasons for setting one at all,
     * which are sparing a metered connection and leaving room for everything else on the
     * network.
     */
    val SPEED_LIMITS: List<Pair<String, String>> = listOf(
        "" to "No limit",
        "256K" to "256 KB/s",
        "512K" to "512 KB/s",
        "1M" to "1 MB/s",
        "2M" to "2 MB/s",
        "5M" to "5 MB/s",
        "10M" to "10 MB/s"
    )

    /** What to call the stored ceiling, falling back to its own text if it is not a preset. */
    fun speedLimitLabel(limit: String): String =
        SPEED_LIMITS.firstOrNull { it.first == limit }?.second
            ?: limit.ifBlank { "No limit" }

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
