package com.hazel.android.ui.screens.download.batch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hazel.android.download.DownloadPlan
import com.hazel.android.download.MediaFormat
import com.hazel.android.download.MediaInfo

/**
 * Everything the batch sheet decides before the download starts.
 *
 * The sheet, its rows, its action bar and its sub sheets all read and write the same few
 * facts, so those live here rather than being threaded through as a growing list of
 * parameters. Keeping them in one place is also what makes the two scopes legible: a change
 * from the action bar lands on [targets], while a change from a row lands on that row alone.
 *
 * A link's format is held per url in [formatFor]. A link with no entry there follows the
 * batch default, which is [videoTab] paired with [maxHeight]; a link the user has adjusted
 * has an entry and stops following it. This is why picking 1080p for one link in a set left
 * on best quality does not disturb the others, and why the reverse holds too.
 */
@Stable
class BatchDownloadState {

    private val resultsState = mutableStateOf<List<MediaInfo>>(emptyList())

    /**
     * The links the sheet is showing.
     *
     * This is written on every composition rather than fixed at construction, because a
     * link that came from a listing is read in the background and arrives as a new list.
     * Rebuilding the state on that would throw away every choice made so far, so the list
     * is replaced and the choices are kept; only the ones whose link has gone are dropped.
     */
    var results: List<MediaInfo>
        get() = resultsState.value
        set(value) {
            if (value == resultsState.value) return
            resultsState.value = value
            val urls = value.mapTo(mutableSetOf()) { it.url }
            if (formatFor.keys.any { it !in urls }) {
                formatFor = formatFor.filterKeys { it in urls }
            }
            if (titleFor.keys.any { it !in urls }) {
                titleFor = titleFor.filterKeys { it in urls }
            }
            if (authorFor.keys.any { it !in urls }) {
                authorFor = authorFor.filterKeys { it in urls }
            }
            if (languageFor.keys.any { it !in urls }) {
                languageFor = languageFor.filterKeys { it in urls }
            }
            if (selected.any { it !in urls }) {
                selected = selected.filterTo(mutableSetOf()) { it in urls }
            }
        }

    /** The batch default: video when true, audio when false. */
    var videoTab by mutableStateOf(true)
        private set

    /** Height ceiling for the batch default. 0 means best available. */
    var maxHeight by mutableStateOf(0)
        private set

    /** Links the user has adjusted on their own, keyed by url. */
    var formatFor by mutableStateOf(emptyMap<String, MediaFormat>())
        private set

    /**
     * The soundtrack a link takes, keyed by url, for the sources that publish several.
     * A link with no entry follows [audioLanguage], and null there means the one the
     * source itself leads with.
     */
    var languageFor by mutableStateOf(emptyMap<String, String?>())
        private set

    /** The batch default soundtrack. Null is whatever each source leads with. */
    var audioLanguage by mutableStateOf<String?>(null)
        private set

    /** Titles and authors edited on one link, which name the file it is saved as. */
    var titleFor by mutableStateOf(emptyMap<String, String>())
        private set

    var authorFor by mutableStateOf(emptyMap<String, String>())
        private set

    /** True while the ticks are showing. Reached by long press or from the list menu. */
    var selectionMode by mutableStateOf(false)
        private set

    var selected by mutableStateOf(emptySet<String>())
        private set

    /**
     * What an action bar change applies to: the ticked links, or every link when nothing
     * is ticked. The download itself always covers the whole list, so ticking is a way of
     * narrowing an edit rather than of choosing what gets downloaded.
     */
    val targets: List<MediaInfo>
        get() = if (selectionMode && selected.isNotEmpty()) results.filter { it.url in selected }
        else results

    /** The format a link will actually download with. */
    fun formatOf(info: MediaInfo): MediaFormat? =
        formatFor[info.url] ?: info.autoPick(videoTab, maxHeight, languageOf(info))

    /** The soundtrack a link will actually download with. */
    fun languageOf(info: MediaInfo): String? =
        if (info.url in languageFor) languageFor[info.url] else audioLanguage

    /** Every soundtrack any link in the set offers, which is what there is to choose from. */
    val audioLanguages: List<String>
        get() = results.flatMap { it.audioLanguages }.distinct()

    /** What the file will be called, which the link's own sheet can change. */
    fun titleOf(info: MediaInfo): String = titleFor[info.url] ?: info.title

    fun authorOf(info: MediaInfo): String = authorFor[info.url] ?: info.uploader

    /** True when the link carries a choice of its own rather than following the batch. */
    fun isAdjusted(info: MediaInfo): Boolean = info.url in formatFor

    /** Whether a link is heading for a video file, which its own format decides. */
    fun isVideo(info: MediaInfo): Boolean = formatOf(info)?.hasVideo ?: videoTab

    /**
     * One plan per link, in list order. Links with no usable format are left out.
     *
     * Worked out once per change rather than once per frame: the header, the total and
     * every row read from this, and a hundred link playlist would otherwise walk its whole
     * format list several times over on every recomposition.
     */
    private val plansState = derivedStateOf {
        results.mapNotNull { info ->
            formatOf(info)?.let {
                DownloadPlan(info, it, titleOf(info), authorOf(info), languageOf(info))
            }
        }
    }

    val plans: List<DownloadPlan> get() = plansState.value

    /** Every link's chosen format added up, which is what the whole set will cost. */
    val totalBytes: Long
        get() = plans.sumOf { it.format.fileSizeBytes }

    /**
     * False while some link has not reported a size yet, which is the case for a link from
     * a listing whose formats nobody has opened. The total is then a floor rather than the
     * whole figure, and is marked as one.
     */
    val allSizesKnown: Boolean
        get() = plans.size == results.size && plans.all { it.format.fileSizeBytes > 0L }

    // ── Changes from the action bar, which land on [targets] ──

    fun setDownloadType(isVideo: Boolean) {
        videoTab = isVideo
        applyToTargets { it.autoPick(isVideo, maxHeight) }
    }

    /**
     * Applies one soundtrack to every target.
     *
     * An audio download follows it straight away, since the format itself is the
     * soundtrack; a video download carries it as the track that will be muxed in.
     */
    fun applyAudioLanguage(language: String?) {
        val scope = targets
        if (scope.size == results.size) {
            // The whole set follows the new answer, so the per-link entries that were
            // overriding it have nothing left to override.
            audioLanguage = language
            languageFor = emptyMap()
        } else {
            languageFor = languageFor + scope.associate { it.url to language }
        }
        applyToTargets { it.autoPick(videoTab, maxHeight, language) }
    }

    fun setQualityCeiling(height: Int) {
        maxHeight = height
        applyToTargets { it.autoPick(videoTab, height) }
    }

    /**
     * Writes a fresh choice onto every target.
     *
     * When only part of the list is ticked, the untouched links are first pinned to what
     * they are already set to. Without that they would have no entry of their own and
     * would quietly follow the new batch default, which is the opposite of what ticking a
     * few links asked for.
     */
    private fun applyToTargets(pick: (MediaInfo) -> MediaFormat?) {
        val next = formatFor.toMutableMap()
        val scope = targets
        if (scope.size != results.size) {
            results.forEach { info ->
                if (info.url !in next) next[info.url] = formatOf(info) ?: return@forEach
            }
        }
        scope.forEach { info -> pick(info)?.let { next[info.url] = it } }
        formatFor = next
    }

    // ── Changes from one row ──

    fun setFormat(info: MediaInfo, format: MediaFormat) {
        formatFor = formatFor + (info.url to format)
    }

    /** Everything one link's own sheet decided, applied together when it is confirmed. */
    fun setChoice(
        info: MediaInfo,
        format: MediaFormat,
        title: String,
        author: String,
        audioLanguage: String?
    ) {
        formatFor = formatFor + (info.url to format)
        titleFor = titleFor + (info.url to title.ifBlank { info.title })
        authorFor = authorFor + (info.url to author)
        languageFor = languageFor + (info.url to audioLanguage)
    }

    // ── Selection ──

    fun startSelection(info: MediaInfo? = null) {
        selectionMode = true
        selected = info?.let { setOf(it.url) } ?: emptySet()
    }

    fun endSelection() {
        selectionMode = false
        selected = emptySet()
    }

    fun toggle(info: MediaInfo) {
        selected = if (info.url in selected) selected - info.url else selected + info.url
    }

    fun selectAll() {
        selected = results.map { it.url }.toSet()
    }

    fun invertSelection() {
        selected = results.map { it.url }.toSet() - selected
    }
}

/**
 * Holds the batch choices for as long as the sheet is open.
 *
 * The state outlives a change to the results on purpose: links read in the background come
 * back as a new list, and rebuilding here would drop every choice the user had made a
 * moment earlier and close whatever sheet was open on top.
 */
@Composable
fun rememberBatchDownloadState(results: List<MediaInfo>): BatchDownloadState {
    val state = remember { BatchDownloadState() }
    state.results = results
    return state
}
