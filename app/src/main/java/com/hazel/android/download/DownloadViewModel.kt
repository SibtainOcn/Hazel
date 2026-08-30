package com.hazel.android.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hazel.android.HazelApp
import com.hazel.android.data.CookieRepository
import com.hazel.android.data.DownloadHistoryRepository
import com.hazel.android.data.HistoryEntry
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.extractor.LinkContents
import com.hazel.android.download.extractor.LinkResolver
import com.hazel.android.download.extractor.ListingSource
import com.hazel.android.util.StoragePaths
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/** Where one link has got to while a batch is running. */
enum class BatchState { QUEUED, DOWNLOADING, DONE, FAILED }

/** One link's outcome inside a batch, so the screen can report progress per item. */
data class BatchItem(
    val url: String,
    val title: String,
    val state: BatchState = BatchState.QUEUED,
    val error: String? = null
)

/**
 * What the user chose for one link before a batch starts. The sheet builds one of these
 * per item so every link keeps its own format and naming.
 */
data class DownloadPlan(
    val info: MediaInfo,
    val format: MediaFormat,
    val title: String,
    val author: String
)

/**
 * One link waiting its turn, with the settings it was asked for under.
 *
 * The options travel with the link rather than being read when its turn comes, because
 * links can be added while a run is going: three shared in a row are three separate asks,
 * and the second one should not quietly pick up a setting changed after it was made.
 */
private data class QueuedDownload(
    val plan: DownloadPlan,
    val options: DownloadOptions,
    val treeUri: String
)

data class DownloadState(
    val url: String = "",
    val isFetching: Boolean = false,
    /** How far through a multi-link read the app is, for the progress line. */
    val fetchProgress: String = "",
    /** How many links the current read covers, so the screen can stand in for each one. */
    val fetchCount: Int = 0,
    /** Every link that resolved from the last search, in the order they were entered. */
    val results: List<MediaInfo> = emptyList(),
    /** The result the open sheet is editing, and the one the engine is working on. */
    val info: MediaInfo? = null,
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    /** Total transfer size reported by yt-dlp, 0 until its first progress line. */
    val totalBytes: Long = 0L,
    val status: String = "",
    /**
     * True once the transfer is done and yt-dlp has moved on to merging, converting or
     * tagging. There is nothing left to cancel at that point, so the screen swaps the
     * cancel control for a processing treatment.
     */
    val isProcessing: Boolean = false,
    val fileName: String = "",
    val savedPath: String = "",
    val error: String? = null,
    /**
     * Untouched text from a failed metadata read, shown in the failure dialog. The
     * sanitized [error] is for inline messages; this is what the user can copy or act on.
     */
    val errorLog: String? = null,
    val isComplete: Boolean = false,
    /** Per-link state while several links download one after another. */
    val batch: List<BatchItem> = emptyList()
) {
    /** True once more than one link resolved, which is what turns the screen into a list. */
    val isMultiple: Boolean get() = results.size > 1

    val batchDone: Int get() = batch.count { it.state == BatchState.DONE }
    val batchFailed: Int get() = batch.count { it.state == BatchState.FAILED }
}

class DownloadViewModel : ViewModel() {

    private val _state = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    /**
     * The link whose sheet has already opened on its own.
     *
     * Held here rather than on the screen because the screen is rebuilt every time the user
     * comes back to it, and a memory that lives there is blank on every return: the sheet
     * for a link read long ago would open again each time the user left the downloads list
     * or the settings. A new read clears it, which is what lets the next one open.
     */
    var autoOpenedUrl: String? = null
        private set

    fun markAutoOpened(url: String) {
        autoOpenedUrl = url
    }

    fun clearAutoOpened() {
        autoOpenedUrl = null
    }

    private var fetchJob: Job? = null
    private var downloadJob: Job? = null

    /**
     * Where a download runs.
     *
     * Not the view model's own scope: that is cancelled when the screen that owns it goes
     * away, which is exactly what happens when the task is swiped off the recents list
     * mid-download. This one lives as long as the process, which the foreground service
     * keeps alive for as long as there is something to download.
     */
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processId = "hazel_download"
    @Volatile private var isCancelled = false

    /** When the progress notification was last redrawn, so updates stay evenly paced. */
    @Volatile private var lastNotifiedAt = 0L

    /** Entries whose formats are being read, so opening a sheet twice reads once. */
    private val formatsInFlight = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private var downloadContext: Context? = null
    private var downloadIsVideo: Boolean = true

    /**
     * What the link being downloaded was advertised as costing, or 0 when nothing was.
     *
     * yt-dlp reports a total on every progress line, and on a fragmented transfer that
     * total is an estimate it refines upward as it goes, so a figure read off the output
     * climbs for the whole download and ends nowhere near where it started. The sheet
     * already showed the user a size, so that size is what the progress is measured
     * against and it does not move.
     */
    @Volatile private var expectedTotalBytes: Long = 0L

    /**
     * Links waiting their turn, and the run that is draining them.
     *
     * Sharing three links in a row used to drop the second and third: a run was already
     * going, and starting one was the only way in. They are appended instead, and the run
     * keeps going until the queue is empty, so a set collected over several shares behaves
     * the same as a set collected in one.
     */
    private val queue = ArrayDeque<QueuedDownload>()

    /** Destination picked through the document picker, or blank for Download/Hazel. */
    private var downloadTreeUri: String = ""

    /** Set while a batch runs, so a cancel stops the whole run rather than one item. */
    @Volatile private var isBatchCancelled = false

    private val downloadDir: File
        get() = StoragePaths.tempDownloads

    // Persistent yt-dlp cache, shared with MediaProbe so the player data resolved while
    // fetching metadata is reused by the download instead of being fetched twice.
    private val ytDlpCacheDir: File
        get() {
            val dir = File(HazelApp.instance.cacheDir, "yt-dlp")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    // ── URL input ──

    fun onUrlChange(url: String) {
        _state.value = _state.value.copy(url = url, error = null)
    }

    fun clearUrl() {
        fetchJob?.cancel()
        MediaProbe.cancel()
        _state.value = DownloadState()
    }

    /** Clears the resolved links but keeps the field, for the Clear results menu action. */
    fun clearResults() {
        fetchJob?.cancel()
        MediaProbe.cancel()
        _state.value = DownloadState(url = _state.value.url)
    }

    /** Points the sheet at one of several resolved links. */
    fun selectResult(info: MediaInfo) {
        _state.value = _state.value.copy(info = info)
    }

    /** Drops one link from the resolved list without touching the others. */
    fun removeResult(info: MediaInfo) {
        val remaining = _state.value.results.filterNot { it.url == info.url }
        _state.value = _state.value.copy(
            results = remaining,
            info = if (_state.value.info?.url == info.url) remaining.firstOrNull()
            else _state.value.info
        )
    }

    // ── Instant share ──

    /** Links shared to the instant target, waiting to be read. */
    private val directQueue = ArrayDeque<String>()

    @Volatile private var directRunning = false

    /**
     * Reads a link shared to the instant target and downloads it without asking anything.
     *
     * Held as a queue of its own, ahead of the download queue, because the two stages fail
     * differently. Sharing three links in a row used to leave one: the second arrived while
     * the first was still being read, and a read already in flight turned it away. They are
     * read one after another now, and each hands its download to the queue behind it, so
     * three shares in three seconds become three downloads.
     *
     * The screen is shown whichever link is being read at the time, so a user who does open
     * the app mid-run sees where it has got to rather than the first link frozen in place.
     */
    fun startDirect(context: Context, url: String) {
        val link = url.trim()
        if (link.isBlank()) return

        synchronized(directQueue) { directQueue.addLast(link) }
        if (directRunning) return
        directRunning = true

        val app = context.applicationContext
        downloadScope.launch {
            while (true) {
                val next = synchronized(directQueue) { directQueue.removeFirstOrNull() } ?: break

                val info = runCatching { readOne(next) }.getOrNull()
                if (info == null) {
                    DownloadNotificationHelper.showError(app, "Could not read this link")
                    continue
                }

                val isVideo = SettingsRepository.getQuickIsVideo(app).first()
                val maxHeight = SettingsRepository.getQuickMaxHeight(app).first()
                val format = info.autoPick(isVideo, maxHeight)
                if (format == null) {
                    DownloadNotificationHelper.showError(app, "Nothing to download from this link")
                    continue
                }

                _state.value = _state.value.copy(
                    url = next,
                    info = info,
                    results = listOf(info),
                    error = null,
                    errorLog = null
                )
                markAutoOpened(info.url)

                startBatch(
                    context = app,
                    plans = listOf(DownloadPlan(info, format, info.title, info.uploader)),
                    options = SettingsRepository.getDownloadOptions(app).first(),
                    treeUri = SettingsRepository.getDownloadTreeUri(app).first()
                )
            }
            directRunning = false
        }
    }

    /**
     * Reads one link's metadata, for the paths with no screen watching. A link that turns
     * out to hold several items contributes its first, since an instant share is one ask
     * and a playlist shared that way is not a request for two hundred files.
     */
    private suspend fun readOne(url: String): MediaInfo? {
        val app = HazelApp.instance
        return expand(
            url,
            CookieRepository.activeCookieFile(app),
            SettingsRepository.getFetchMode(app).first(),
            SettingsRepository.getForceIpv4(app).first(),
            SettingsRepository.getListingSource(app).first(),
            "${MediaProbe.PROBE_PROCESS_ID}_direct"
        ).firstOrNull()?.let { first ->
            // A listing entry carries no formats, so it is read again on its own before a
            // quality can be picked from it.
            if (first.hasResolvedFormats) first
            else runCatching {
                probeWithRetry(
                    first.url,
                    CookieRepository.activeCookieFile(app),
                    SettingsRepository.getFetchMode(app).first(),
                    SettingsRepository.getForceIpv4(app).first(),
                    "${MediaProbe.PROBE_PROCESS_ID}_direct_formats"
                )
            }.getOrNull() ?: first
        }
    }

    // ── Metadata ──

    /**
     * Resolves whatever is in the field.
     *
     * @param notifyFailure posts a notification if the link cannot be read, for the paths
     *   where nobody is watching the screen. A share that goes straight to a download is
     *   started and then left, so a failure reported only on screen is a failure the user
     *   never learns about.
     */
    fun fetchInfo(notifyFailure: Boolean = false) {
        val url = _state.value.url.trim()
        if (url.isBlank()) return
        fetchAll(listOf(url), notifyFailure)
    }

    /**
     * Resolves several links in one pass.
     *
     * Links are read one after another rather than together: each read spawns a yt-dlp
     * process, and running a batch of them at once competes for the same network and CPU
     * without finishing any sooner. Results are published as they arrive, so the first card
     * is on screen while the rest are still being read.
     *
     * A link that cannot be read is skipped rather than failing the batch. Only when none
     * of them resolved is the failure reported, since that is the case where the user has
     * nothing to act on.
     */
    fun fetchAll(urls: List<String>, notifyFailure: Boolean = false) {
        val targets = urls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (targets.isEmpty() || _state.value.isFetching) return

        val valid = targets.filter { URL_PATTERN.matches(it) }
        if (valid.isEmpty()) {
            _state.value = _state.value.copy(
                error = "Invalid URL, must start with http:// or https://"
            )
            return
        }

        // A fresh read is a fresh answer, so the sheet is allowed to open on its own again.
        clearAutoOpened()

        _state.value = _state.value.copy(
            url = valid.first(),
            isFetching = true,
            error = null,
            errorLog = null,
            results = emptyList(),
            info = null,
            batch = emptyList(),
            isComplete = false,
            fetchProgress = "",
            fetchCount = valid.size
        )

        fetchJob = viewModelScope.launch {
            val app = HazelApp.instance
            val cookies = CookieRepository.activeCookieFile(app)
            val fetchMode = SettingsRepository.getFetchMode(app).first()
            val forceIpv4 = SettingsRepository.getForceIpv4(app).first()

            val listingSource = SettingsRepository.getListingSource(app).first()

            val resolved: List<MediaInfo>
            var lastFailure = ""

            try {
                if (valid.size > 1) {
                    _state.value = _state.value.copy(
                        fetchProgress = "Reading ${valid.size} links"
                    )
                }

                // Each pasted link is asked what it holds, and a link holding a collection
                // contributes one card per entry. Reads run together rather than in turn,
                // because each one is almost entirely waiting.
                resolved = coroutineScope {
                    valid.mapIndexed { index, link ->
                        async(Dispatchers.IO) {
                            runCatching {
                                expand(
                                    link, cookies, fetchMode, forceIpv4, listingSource,
                                    "${MediaProbe.PROBE_PROCESS_ID}_$index"
                                )
                            }.onFailure { failure ->
                                if (failure is CancellationException) throw failure
                                lastFailure = failure.message?.trim().orEmpty()
                            }.getOrDefault(emptyList())
                        }
                    }.awaitAll().flatten().distinctBy { it.url }
                }

                // One link that resolved to nothing is a failure worth reporting, since
                // there is no other card to look at.
                if (resolved.isEmpty() && valid.size == 1 && lastFailure.isBlank()) {
                    lastFailure = "Could not read this link"
                }
            } catch (_: CancellationException) {
                _state.value = _state.value.copy(isFetching = false, fetchProgress = "")
                return@launch
            } catch (e: Exception) {
                lastFailure = e.message?.trim().orEmpty()
                val message = sanitizeError(lastFailure.ifBlank { "Could not read this link" })
                _state.value = _state.value.copy(
                    isFetching = false,
                    fetchProgress = "",
                    errorLog = lastFailure.ifBlank { "Could not read this link" }
                )
                if (notifyFailure) {
                    DownloadNotificationHelper.showError(
                        app, message, signInUrl = signInTargetFor(lastFailure, valid.first())
                    )
                }
                return@launch
            }

            if (resolved.isEmpty() && notifyFailure) {
                DownloadNotificationHelper.showError(
                    app,
                    sanitizeError(lastFailure.ifBlank { "Could not read this link" }),
                    signInUrl = signInTargetFor(lastFailure, valid.first())
                )
            }

            _state.value = if (resolved.isEmpty()) {
                _state.value.copy(
                    isFetching = false,
                    fetchProgress = "",
                    errorLog = lastFailure.ifBlank { "Could not read this link" }
                )
            } else {
                _state.value.copy(
                    isFetching = false,
                    fetchProgress = "",
                    results = resolved,
                    info = resolved.singleOrNull()
                )
            }
        }
    }

    /**
     * Turns one pasted link into the cards it stands for.
     *
     * A link holding one item gives one card, fully resolved. A link holding a collection
     * gives a card per entry, each carrying what the listing reported and no formats: those
     * are read per item, when the item is opened, by [resolveFormats]. Reading them here
     * would mean a read per entry before anything appeared, which for a long playlist is
     * minutes of waiting for cards that will mostly never be opened.
     */
    private suspend fun expand(
        url: String,
        cookies: File?,
        fetchMode: FetchMode,
        forceIpv4: Boolean,
        source: ListingSource,
        processKey: String
    ): List<MediaInfo> {
        // A link read a moment ago is not read again. The engine costs seconds to start
        // before it does any work, so the cheapest read is the one that does not happen.
        InfoCache.metadataFor(url)?.let { return listOf(it) }

        val contents = LinkResolver.resolve(
            url, ytDlpCacheDir, cookies, fetchMode, forceIpv4, source, processKey
        )

        return when (contents) {
            is LinkContents.Single -> listOf(contents.info)
            is LinkContents.Many -> contents.entries.map(MediaProbe::pendingFor)
        }
    }

    /**
     * Reads one entry's formats, for a card that came from a listing.
     *
     * Called when the sheet opens on such a card. A card that already has formats, or one
     * whose formats are still being read, is left alone.
     */
    fun resolveFormats(info: MediaInfo) {
        if (info.hasResolvedFormats || info.url in formatsInFlight) return

        formatsInFlight += info.url
        viewModelScope.launch(Dispatchers.IO) {
            val app = HazelApp.instance
            val resolved = runCatching {
                InfoCache.metadataFor(info.url)?.takeIf { it.hasResolvedFormats }
                    ?: probeWithRetry(
                        info.url,
                        CookieRepository.activeCookieFile(app),
                        SettingsRepository.getFetchMode(app).first(),
                        SettingsRepository.getForceIpv4(app).first(),
                        "${MediaProbe.PROBE_PROCESS_ID}_formats"
                    )
            }.getOrNull()

            formatsInFlight -= info.url
            if (resolved == null) return@launch

            // The listing's title and artwork are kept: they are what the card already
            // shows, and replacing them mid-read would make the card flicker.
            val merged = resolved.copy(
                title = info.title.ifBlank { resolved.title },
                thumbnail = info.thumbnail ?: resolved.thumbnail
            )

            _state.value = _state.value.copy(
                results = _state.value.results.map { if (it.url == info.url) merged else it },
                info = if (_state.value.info?.url == info.url) merged else _state.value.info
            )
        }
    }

    /**
     * Reads one link, giving a failed first attempt a second, more patient try.
     *
     * A first read has to fetch player data the cache does not hold yet, so a timeout there
     * often means the attempt was too short rather than that the link is unreadable. By the
     * time the retry runs the cache is warm, so it costs little.
     */
    private suspend fun probeWithRetry(
        url: String,
        cookies: File?,
        fetchMode: FetchMode,
        forceIpv4: Boolean,
        processKey: String = MediaProbe.PROBE_PROCESS_ID
    ): MediaInfo = try {
        MediaProbe.probe(url, ytDlpCacheDir, cookies, fetchMode, forceIpv4, processKey)
    } catch (e: Exception) {
        if (isTerminal(e.message?.trim().orEmpty())) throw e
        MediaProbe.probe(
            url, ytDlpCacheDir, cookies, FetchMode.THOROUGH, forceIpv4, processKey
        )
    }

    /**
     * Reopens the failure dialog on a reason that arrived from outside, which is how a tap
     * on a failure notification gets back to the log and the sign-in offer.
     */
    fun showFailure(message: String) {
        if (message.isBlank()) return
        _state.value = _state.value.copy(isFetching = false, errorLog = message)
    }

    /** Dismisses the failure dialog without changing anything else. */
    fun clearErrorLog() {
        _state.value = _state.value.copy(errorLog = null)
    }

    /**
     * Goes ahead with the link whose metadata could not be read, offering the generic
     * "best" rows so a download is still possible.
     */
    fun continueWithoutMetadata() {
        val url = _state.value.url.trim()
        if (url.isBlank()) return
        val fallback = MediaProbe.fallbackFor(url)
        _state.value = _state.value.copy(
            errorLog = null,
            results = listOf(fallback),
            info = fallback
        )
    }

    // ── Download ──

    /**
     * Downloads [format] exactly as it was advertised in the sheet. A video-only format is
     * merged with the audio track the sheet named; everything else is taken as-is so the
     * source container is preserved.
     */
    fun startDownload(
        context: Context,
        format: MediaFormat,
        options: DownloadOptions,
        title: String,
        author: String,
        treeUri: String = ""
    ) {
        val info = _state.value.info ?: return
        startBatch(
            context = context,
            plans = listOf(DownloadPlan(info, format, title, author)),
            options = options,
            treeUri = treeUri
        )
    }

    /**
     * Downloads several links one after another.
     *
     * They run in sequence rather than together: yt-dlp already saturates the connection
     * for a single download, and parallel runs would share the same temporary directory,
     * where the completed-file sweep cannot tell one download's output from another's.
     *
     * Links asked for while a run is going join the end of the queue instead of being
     * turned away, so sharing three links in a row downloads all three. Each keeps its own
     * entry in [DownloadState.batch], so one failure is recorded against that link and the
     * rest of the queue still runs.
     */
    fun startBatch(
        context: Context,
        plans: List<DownloadPlan>,
        options: DownloadOptions,
        treeUri: String = ""
    ) {
        if (plans.isEmpty()) return

        com.hazel.android.util.PermissionHelper.ensureNotificationPermission(context)

        val queued = plans.map { QueuedDownload(it, options, treeUri) }
        val alreadyRunning = _state.value.isDownloading

        synchronized(queue) { queue.addAll(queued) }

        if (alreadyRunning) {
            // The run in flight picks these up on its own. Only the list the screen shows
            // needs saying, so the new links appear as waiting rather than as nothing.
            _state.value = _state.value.copy(
                batch = _state.value.batch + plans.map {
                    BatchItem(url = it.info.url, title = it.title)
                }
            )
            return
        }

        isBatchCancelled = false
        downloadContext = context.applicationContext
        downloadTreeUri = treeUri

        _state.value = _state.value.copy(
            isDownloading = true,
            isComplete = false,
            progress = 0f,
            totalBytes = 0L,
            status = "Starting download",
            isProcessing = false,
            error = null,
            batch = plans.map { BatchItem(url = it.info.url, title = it.title) }
        )

        // Asks the system to leave the process alone for the length of the run. Started
        // before the first link rather than per link, so a set of ten is one service for
        // the whole set instead of ten in a row.
        DownloadService.start(context, plans.first().title)

        // Run on a scope tied to the process rather than to the screen. A download the user
        // has walked away from should not end because the screen that started it did.
        downloadJob = downloadScope.launch {
            val app = context.applicationContext
            val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
            val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            if (caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) {
                DownloadService.stop(app)
                synchronized(queue) { queue.clear() }
                fail(context, "No internet connection")
                return@launch
            }

            val cookies = CookieRepository.activeCookieFile(context)

            while (true) {
                if (isBatchCancelled) break
                val next = synchronized(queue) { queue.removeFirstOrNull() } ?: break
                val plan = next.plan
                val options = next.options
                downloadTreeUri = next.treeUri

                isCancelled = false
                downloadIsVideo = plan.format.hasVideo
                markBatch(plan.info.url, BatchState.DOWNLOADING)

                expectedTotalBytes = expectedTotalFor(plan)

                _state.value = _state.value.copy(
                    info = plan.info,
                    progress = 0f,
                    totalBytes = expectedTotalBytes,
                    status = "Starting download",
                    isProcessing = false
                )
                DownloadNotificationHelper.showProgress(context, 0, "Starting download", plan.title)

                try {
                    if (!downloadDir.exists()) downloadDir.mkdirs()

                    try {
                        executeYtDlp(
                            buildRequest(
                                plan.info.url, plan.format, options, plan.title, plan.author,
                                cookies
                            )
                        )
                    } catch (e: Exception) {
                        // A replayed payload whose addresses have expired fails here. That
                        // is not the link failing, so it is read again and tried once more
                        // before the item is recorded as a failure.
                        val replayed = InfoCache.infoJsonFor(plan.info.url) != null
                        if (!replayed || isCancelled || isBatchCancelled) throw e

                        InfoCache.invalidate(plan.info.url)
                        purgeFragments()
                        executeYtDlp(
                            buildRequest(
                                plan.info.url, plan.format, options, plan.title, plan.author,
                                cookies
                            )
                        )
                    }

                    if (isCancelled || isBatchCancelled) {
                        finishDownload(context)
                        markBatch(plan.info.url, BatchState.FAILED, "Cancelled")
                        break
                    }

                    finishDownload(context)
                    markBatch(plan.info.url, BatchState.DONE)
                } catch (_: CancellationException) {
                    finishDownload(context)
                    markBatch(plan.info.url, BatchState.FAILED, "Cancelled")
                    break
                } catch (e: Exception) {
                    if (isCancelled || isBatchCancelled) {
                        finishDownload(context)
                        markBatch(plan.info.url, BatchState.FAILED, "Cancelled")
                        break
                    }

                    com.hazel.android.utils.CrashLogger.logDownloadError(
                        url = plan.info.url,
                        platform = detectPlatform(plan.info.url),
                        error = e.message ?: "Download failed"
                    )
                    // One bad link does not stop the rest: the failure is recorded against
                    // that item and the batch carries on.
                    purgeFragments()
                    markBatch(
                        plan.info.url,
                        BatchState.FAILED,
                        sanitizeError(e.message ?: "Download failed")
                    )
                }
            }

            synchronized(queue) { queue.clear() }
            DownloadService.stop(context.applicationContext)
            finishBatch(context)
        }
    }

    private fun markBatch(url: String, state: BatchState, error: String? = null) {
        _state.value = _state.value.copy(
            batch = _state.value.batch.map {
                if (it.url == url) it.copy(state = state, error = error) else it
            }
        )
    }

    /** Reports the run as a whole once every item has been attempted. */
    private fun finishBatch(context: Context) {
        val current = _state.value
        val failed = current.batchFailed
        val done = current.batchDone

        _state.value = current.copy(
            isDownloading = false,
            isComplete = done > 0,
            status = "",
            isProcessing = false,
            error = when {
                done == 0 && failed > 0 ->
                    current.batch.firstOrNull { it.error != null }?.error ?: "Download failed"
                failed > 0 -> "$failed of ${current.batch.size} failed"
                else -> null
            }
        )

        when {
            isBatchCancelled && done == 0 -> DownloadNotificationHelper.showCancelled(context)

            // A failure inside a set went unreported: the run ended, the notification was
            // taken down, and nothing said why the file never arrived.
            failed > 0 && done == 0 -> {
                val item = current.batch.firstOrNull { it.state == BatchState.FAILED }
                DownloadNotificationHelper.showError(
                    context,
                    item?.error ?: "Download failed",
                    item?.title.orEmpty()
                )
            }

            failed > 0 -> DownloadNotificationHelper.showError(
                context,
                "$failed of ${current.batch.size} could not be downloaded",
                ""
            )
        }
    }

    /**
     * Cancels the running download. yt-dlp is killed, and whatever finished downloading is
     * still moved into public storage by the coroutine's cleanup path.
     */
    fun cancelDownload() {
        isCancelled = true
        isBatchCancelled = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().destroyProcessById(processId)
            } catch (_: Exception) { /* process may already be done */ }
        }
    }

    fun resetState() {
        _state.value = DownloadState()
    }

    // ── Internals ──

    /**
     * The request a download starts from, given the metadata already in hand.
     *
     * Resolving a link and downloading it are two separate runs of the engine, and left to
     * itself the second one repeats every bit of the extraction the first one did. Handing
     * back the payload the read produced skips that: measured on a mid-range device, the
     * wait before the first byte moved fell from 5.8 seconds to 3.2, the rest being the
     * engine starting up.
     *
     * The payload holds signed addresses with a limited life, so this is only used while it
     * is recent, and [startBatch] treats a refusal as a signal to discard it and read the
     * link again rather than as a failed download.
     */
    private fun buildDownloadRequest(url: String): YoutubeDLRequest {
        val cached = InfoCache.infoJsonFor(url) ?: return YoutubeDLRequest(url)
        return YoutubeDLRequest(emptyList()).apply {
            addOption("--load-info-json", cached.absolutePath)
        }
    }

    private fun buildRequest(
        url: String,
        format: MediaFormat,
        options: DownloadOptions,
        title: String,
        author: String,
        cookieFile: File?
    ): YoutubeDLRequest = buildDownloadRequest(url).apply {
        val isVideo = format.hasVideo
        val container = (if (isVideo) options.videoContainer else options.audioContainer).trim()

        addOption("-o", "${downloadDir.absolutePath}/${outputTemplate(options, title, author)}")
        addOption("--no-playlist")
        addOption("--no-mtime")
        addOption("--no-check-certificates")
        addOption("--cache-dir", ytDlpCacheDir.absolutePath)

        // Saved sign-ins, which are what make age-restricted and members-only media
        // reachable. The same file serves every download until the user changes it.
        cookieFile?.let { addOption("--cookies", it.absolutePath) }

        // Whether the two streams have to be muxed back together after the download. The
        // container option decides the result when one was chosen, otherwise mp4 is used
        // because it is the container both streams are most likely to fit.
        var needsMerge = false

        when {
            // Generic rows carry a complete yt-dlp expression already.
            format.isGeneric -> {
                addOption("-f", format.selector)
                needsMerge = isVideo
            }
            // Video-only stream: pair it with the audio track the sheet named, so the
            // file matches what the row advertised. The expression falls back to the best
            // available audio, then to the bare video, so a track that has since gone
            // missing cannot fail the whole download.
            isVideo && !format.hasAudio -> {
                val audioId = _state.value.info?.mergeAudio?.selector
                val selector = buildString {
                    if (audioId != null) append("${format.selector}+$audioId/")
                    append("${format.selector}+bestaudio/${format.selector}")
                }
                addOption("-f", selector)
                needsMerge = true
            }
            else -> addOption("-f", format.selector)
        }

        if (isVideo) {
            if (container.isNotBlank()) {
                addOption("--merge-output-format", container.lowercase())
            } else if (needsMerge) {
                addOption("--merge-output-format", "mp4")
            }
        } else if (container.isNotBlank()) {
            // Audio containers are produced by extracting and re-encoding, which is the
            // only way to hand back a format the source did not offer in the first place.
            addOption("-x")
            addOption("--audio-format", container.lowercase())
        }

        // Cover art cannot be written into these containers, so asking for it there makes
        // the whole download fail in post-processing.
        val artworkContainer = container.lowercase() !in NO_ARTWORK_CONTAINERS
        if (options.embedThumbnail && artworkContainer) addOption("--embed-thumbnail")

        applyChapters(options, isVideo)
        applySponsorBlock(options)
        if (isVideo) applySubtitles(options)
        applyMetadata(title, author)
    }

    /**
     * Chapter handling. Marking with SponsorBlock is paired with embedding, since the marks
     * are written as chapters and are invisible without it.
     */
    private fun YoutubeDLRequest.applyChapters(options: DownloadOptions, isVideo: Boolean) {
        if (isVideo && options.addChapters) {
            addOption("--sponsorblock-mark", "all")
            addOption("--embed-chapters")
        }
        if (options.splitByChapters) {
            addOption("--split-chapters")
            addOption("-o", "chapter:%(section_number)d - %(section_title)s.%(ext)s")
        }
    }

    /**
     * SponsorBlock segment removal.
     *
     * yt-dlp is what queries the SponsorBlock service and follows its API, so the only
     * maintenance this needs is keeping yt-dlp current, which the in-app updater handles.
     * The endpoint is passed explicitly so a change of default in yt-dlp cannot silently
     * redirect the lookups.
     */
    private fun YoutubeDLRequest.applySponsorBlock(options: DownloadOptions) {
        val filters = options.sponsorBlockFilters.filter { it.isNotBlank() }
        if (filters.isNotEmpty()) {
            addOption("--sponsorblock-remove", filters.joinToString(","))
        }
        if (filters.isNotEmpty() || options.addChapters) {
            addOption("--sponsorblock-api", SponsorBlock.API_URL)
        }
    }

    /** Subtitle downloading and embedding, both driven by the same language selector. */
    private fun YoutubeDLRequest.applySubtitles(options: DownloadOptions) {
        if (options.writeSubs) addOption("--write-subs")
        if (options.writeAutoSubs) addOption("--write-auto-subs")
        if (options.embedSubs) addOption("--embed-subs")

        if (options.embedSubs || options.writeSubs || options.writeAutoSubs) {
            addOption(
                "--sub-langs",
                options.subLanguages.ifBlank { DownloadOptions.DEFAULT_SUB_LANGUAGES }
            )
        }
    }

    /**
     * Writes an edited title or author into the file's tags.
     *
     * yt-dlp reads `--parse-metadata` as `FROM:TO`, so a value containing a colon would be
     * split in the wrong place. Such a value is left out of the tags; it still reaches the
     * filename through [outputTemplate], which has no such restriction.
     */
    private fun YoutubeDLRequest.applyMetadata(title: String, author: String) {
        val overrides = buildList {
            if (title.isNotBlank() && ':' !in title) add("$title:%(title)s")
            if (author.isNotBlank() && ':' !in author) add("$author:%(uploader)s")
        }
        if (overrides.isEmpty()) return

        addOption("--embed-metadata")
        overrides.forEach { addOption("--parse-metadata", it) }
    }

    /**
     * Resolves the output template against the title and author shown in the sheet.
     *
     * The user can edit both, so the fields they edited are substituted as literal text
     * rather than left for yt-dlp to fill from the source. Characters a filesystem rejects
     * are replaced first, and a substituted value is never allowed to be empty.
     */
    private fun outputTemplate(options: DownloadOptions, title: String, author: String): String {
        var template = options.filenameTemplate.ifBlank {
            SettingsRepository.DEFAULT_FILENAME_TEMPLATE
        }
        val info = _state.value.info

        if (title.isNotBlank() && title != info?.title) {
            template = template.replace("%(title)s", sanitizeForFilename(title))
        }
        if (author.isNotBlank() && author != info?.uploader) {
            val safe = sanitizeForFilename(author)
            template = template
                .replace("%(uploader)s", safe)
                .replace("%(channel)s", safe)
        }
        return template
    }

    /**
     * Strips path separators, characters Android's filesystems reject, and the percent sign
     * that would otherwise be read back as another template field.
     */
    private fun sanitizeForFilename(value: String): String =
        value.replace(Regex("""[\\/:*?"<>|%]"""), "_")
            .trim()
            .take(120)
            .ifBlank { "download" }

    private fun executeYtDlp(request: YoutubeDLRequest) {
        lastNotifiedAt = 0L
        YoutubeDL.getInstance().execute(request, processId) { progress, _, line ->
            val percent = progress.coerceIn(0f, 100f)
            val status = cleanProgressLine(line) ?: _state.value.status
            val processing = _state.value.isProcessing || isPostProcessing(line)
            _state.value = _state.value.copy(
                progress = percent / 100f,
                totalBytes = if (expectedTotalBytes > 0) expectedTotalBytes
                else parseTotalBytes(line) ?: _state.value.totalBytes,
                status = status,
                isProcessing = processing
            )

            // Updates are paced by the clock rather than by the percentage. yt-dlp reports
            // the same percentage for seconds at a time on a large transfer while the speed
            // and ETA behind it keep moving, so pacing on the percentage left the
            // notification showing figures that were already out of date.
            val now = System.currentTimeMillis()
            if (now - lastNotifiedAt >= NOTIFICATION_INTERVAL_MS) {
                lastNotifiedAt = now
                downloadContext?.let {
                    val total = _state.value.totalBytes
                    DownloadNotificationHelper.showProgress(
                        context = it,
                        progress = percent.toInt(),
                        statusLine = status,
                        mediaTitle = _state.value.info?.title.orEmpty(),
                        doneBytes = (total * percent / 100f).toLong(),
                        totalBytes = total
                    )
                }
            }
        }
    }

    /**
     * Moves everything in the temp dir into its destination and records the result.
     *
     * Whether the run as a whole is finished is not decided here, because this is called
     * once per item in a batch. [finishBatch] owns that.
     */
    private fun finishDownload(context: Context) {
        // Purge fragments first: the move publishes every file it finds, so a leftover
        // .part from a cancelled or failed run would otherwise land in Downloads.
        purgeFragments()

        val latestFile = downloadDir.listFiles()?.filter { it.isFile }
            ?.maxByOrNull { it.lastModified() }
        val fileName = latestFile?.name ?: "File saved"

        // Measured off the finished file rather than taken from the transfer figures. Those
        // count one stream at a time, so a video muxed from separate video and audio streams
        // reported whichever of them finished last, which is a fraction of the real file.
        // Read here, before the move, because afterwards it is a content URI rather than a
        // file and its length is no longer a question with a cheap answer.
        val finalSizeBytes = latestFile?.length()?.takeIf { it > 0 } ?: _state.value.totalBytes

        _state.value = _state.value.copy(status = "Saving", isProcessing = true)

        val tree = downloadTreeUri.takeIf { it.isNotBlank() }?.let(android.net.Uri::parse)

        // The saved file's address is kept so the completion notification can open it.
        var savedUri: android.net.Uri? = null

        val movedToTree = tree != null && try {
            com.hazel.android.util.MediaStoreHelper.moveToTree(context, downloadDir, tree) {
                savedUri = it
            }
        } catch (_: Exception) {
            false
        }

        val savedPath: String
        val finalDir: File

        if (tree != null && movedToTree) {
            savedPath = com.hazel.android.util.MediaStoreHelper.describeTree(tree)
            finalDir = downloadDir
        } else {
            finalDir = try {
                com.hazel.android.util.MediaStoreHelper.moveToPublicStorage(
                    context, downloadDir, StoragePaths.DOWNLOAD_RELATIVE_PATH, isMusic = false
                ) { savedUri = it }
            } catch (_: Exception) {
                // MediaStore move failed; the files stay in app storage and remain accessible.
                downloadDir
            }
            savedPath = StoragePaths.DOWNLOADS_DISPLAY
        }

        com.hazel.android.util.MediaStoreHelper.scanFiles(context, finalDir)

        _state.value = _state.value.copy(
            progress = 1f,
            status = "",
            isProcessing = false,
            fileName = fileName,
            savedPath = savedPath
        )

        // The media's own title heads the completion notification rather than the file
        // name, which carries the template's separators and the container extension.
        DownloadNotificationHelper.showComplete(
            context,
            title = _state.value.info?.title.orEmpty().ifBlank { fileName },
            isVideo = downloadIsVideo,
            fileUri = savedUri
        )

        recordHistory(context, fileName, savedPath, savedUri, finalSizeBytes)
    }

    /**
     * Files the finished download into the history.
     *
     * Written as the download completes rather than gathered later by scanning a folder,
     * so the record keeps the title, artwork and duration the source reported. Once the
     * file is in public storage there is nothing left to recover those from.
     */
    private fun recordHistory(
        context: Context,
        fileName: String,
        savedPath: String,
        savedUri: android.net.Uri?,
        sizeBytes: Long
    ) {
        val info = _state.value.info ?: return

        viewModelScope.launch {
            // Incognito is checked here rather than at the call site, so every path that
            // finishes a download passes through the same gate and none can forget to.
            if (SettingsRepository.getIncognito(context).first()) return@launch

            DownloadHistoryRepository.record(
                context,
                HistoryEntry(
                    id = System.currentTimeMillis(),
                    url = info.url,
                    title = info.title,
                    author = info.uploader,
                    thumbnail = info.thumbnail,
                    durationSeconds = info.durationSeconds,
                    fileName = fileName,
                    fileUri = savedUri?.toString().orEmpty(),
                    savedPath = savedPath,
                    isVideo = downloadIsVideo,
                    sizeBytes = sizeBytes,
                    completedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Deletes yt-dlp's in-progress artefacts from the temp directory. */
    private fun purgeFragments() {
        try {
            downloadDir.listFiles()?.forEach { f ->
                if (f.isFile && (f.name.endsWith(".part") || f.name.endsWith(".ytdl")
                            || f.name.endsWith(".temp") || f.name.startsWith("."))
                ) {
                    f.delete()
                }
            }
        } catch (_: Exception) { /* best-effort cleanup */ }
    }

    private fun fail(context: Context, message: String) {
        // A failed run leaves fragments behind; clear them so the next download does not
        // publish them alongside its own output.
        purgeFragments()
        _state.value = _state.value.copy(
            isDownloading = false,
            progress = 0f,
            status = "",
            isProcessing = false,
            error = message
        )
        DownloadNotificationHelper.showError(context, message)
    }

    /**
     * Trims a yt-dlp output line down to the part worth showing.
     *
     * Every line is prefixed with the stage that produced it, such as `[download]` or
     * `[youtube]`. The prefix repeats on every line and says nothing the progress bar does
     * not already show, so it is dropped and the rest of the line is kept as written.
     *
     * Lines whose content is a file path are dropped rather than shown. A destination path
     * fills a notification on its own, pushes the transfer figures out of view, and is not
     * something anyone can act on while the download is still running.
     */
    private fun cleanProgressLine(line: String): String? =
        line.replace(LEADING_TAG_PATTERN, "")
            .trim()
            .takeIf { it.isNotEmpty() && !PATH_LINE_PATTERN.containsMatchIn(it) }

    /**
     * Whether a line comes from a stage that runs after every byte is in: merging the video
     * and audio streams, converting the container, or writing tags and artwork.
     */
    private fun isPostProcessing(line: String): Boolean {
        val lower = line.lowercase()
        return POST_PROCESS_MARKERS.any { it in lower }
    }

    /**
     * Reads the transfer size out of a yt-dlp progress line, which looks like
     * `[download]  42.5% of ~  40.20MiB at 2.35MiB/s ETA 00:10`.
     */
    /**
     * The size the sheet advertised for one link, which is what its progress is measured
     * against.
     *
     * A video-only stream arrives with its audio track alongside it and the two are muxed,
     * so the pair is what actually gets transferred and the pair is what is counted. A
     * format that reported no size at all leaves this at 0, and the progress line's own
     * figure is used instead, which is the best there is in that case.
     */
    private fun expectedTotalFor(plan: DownloadPlan): Long {
        val format = plan.format
        if (format.fileSizeBytes <= 0L) return 0L

        val mergedAudio = if (format.hasVideo && !format.hasAudio) {
            plan.info.mergeAudio?.fileSizeBytes ?: 0L
        } else 0L

        return format.fileSizeBytes + mergedAudio
    }

    private fun parseTotalBytes(line: String): Long? {
        val match = TOTAL_SIZE_PATTERN.find(line) ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].uppercase()) {
            "GIB" -> 1_073_741_824.0
            "MIB" -> 1_048_576.0
            "KIB" -> 1024.0
            else -> return null
        }
        return (amount * multiplier).toLong()
    }

    /**
     * The site to offer a sign-in for, when the failure reads like one an account would
     * settle, or null when signing in would not help.
     *
     * Cookies are stored per site, so the individual media address is trimmed back to the
     * front page, which is where a sign-in actually happens.
     */
    private fun signInTargetFor(failure: String, url: String): String? {
        if (!com.hazel.android.ui.screens.download.isCookieRelated(failure)) return null
        return runCatching {
            val parsed = java.net.URL(url)
            "${parsed.protocol}://${parsed.host}"
        }.getOrNull()
    }

    /**
     * Whether a failure means the link genuinely cannot be downloaded, as opposed to the
     * metadata dump simply not working for this source.
     */
    private fun isTerminal(raw: String): Boolean {
        val lower = raw.lowercase()
        return TERMINAL_MARKERS.any { it in lower }
    }

    private fun detectPlatform(url: String): String = when {
        "youtube.com" in url || "youtu.be" in url -> "YouTube"
        "instagram.com" in url -> "Instagram"
        "twitter.com" in url || "x.com" in url -> "X (Twitter)"
        "tiktok.com" in url -> "TikTok"
        "facebook.com" in url || "fb.watch" in url -> "Facebook"
        "reddit.com" in url -> "Reddit"
        "twitch.tv" in url -> "Twitch"
        "vimeo.com" in url -> "Vimeo"
        "soundcloud.com" in url -> "SoundCloud"
        else -> "source"
    }

    /**
     * Translates raw yt-dlp error strings into clean, user-facing messages.
     * CrashLogger keeps the raw text; only the UI and notification see the sanitized form.
     */
    private fun sanitizeError(raw: String): String {
        val lower = raw.lowercase()
        return when {
            "is not a valid url" in lower || "unsupported url" in lower ||
                    "no suitable infoextractor" in lower
                -> "Invalid or unsupported URL"

            "video unavailable" in lower || "this video is unavailable" in lower
                -> "This video is unavailable"

            "private video" in lower || "sign in" in lower || "login required" in lower
                -> "This content is private or requires login"

            "geo restricted" in lower || "not available in your country" in lower ||
                    "blocked" in lower
                -> "This content is not available in your region"

            "age" in lower && ("restricted" in lower || "gate" in lower || "verify" in lower)
                -> "Age-restricted content, cannot download"

            "copyright" in lower || "taken down" in lower || "dmca" in lower
                -> "Content removed due to copyright"

            "format" in lower && "not available" in lower
                -> "That format is no longer available, refresh and try again"

            "no video formats" in lower || "no audio formats" in lower
                -> "No downloadable format found for this URL"

            "unable to download" in lower && "http" in lower
                -> "Network error, check your connection and try again"

            "connection" in lower || "timed out" in lower || "timeout" in lower ||
                    "network" in lower
                -> "Network error, check your connection"

            "ffmpeg" in lower || "postprocessor" in lower
                -> "Processing failed, try again"

            "live" in lower && ("event" in lower || "stream" in lower)
                -> "Live streams cannot be downloaded"

            else -> raw
                .replace(Regex("^ERROR\\s*[:—-]?\\s*(\\[[^]]*]\\s*)?", RegexOption.IGNORE_CASE), "")
                .trim()
                .take(90)
                .ifBlank { "Download failed" }
        }
    }

    private companion object {
        val TOTAL_SIZE_PATTERN = Regex("""of\s*~?\s*([\d.]+)(KiB|MiB|GiB)""", RegexOption.IGNORE_CASE)

        /** Leading `[stage]` markers yt-dlp puts at the front of every output line. */
        val LEADING_TAG_PATTERN = Regex("""^(\s*\[[^\]]*]\s*)+""")

        /** Lines whose content is a file path, which the notification leaves out. */
        val PATH_LINE_PATTERN = Regex(
            """^(destination|merging formats into|writing)|/storage/|/data/|/files/""",
            RegexOption.IGNORE_CASE
        )

        /** How often the progress notification is redrawn while a transfer runs. */
        const val NOTIFICATION_INTERVAL_MS = 600L

        /** Stages that run once the transfer itself has finished. */
        val POST_PROCESS_MARKERS = listOf(
            "[merger]", "merging formats", "[ffmpeg]", "[extractaudio]",
            "[videoconvertor]", "[videoremuxer]", "[metadata]", "[embedthumbnail]",
            "[fixup", "deleting original file", "correcting container"
        )

        val TERMINAL_MARKERS = listOf(
            "unsupported url", "is not a valid url", "no suitable infoextractor",
            "video unavailable", "this video is unavailable", "private video",
            "login required", "sign in", "requested content is not available",
            "not available in your country", "geo restricted",
            "removed", "deleted", "copyright", "dmca", "404"
        )

        val URL_PATTERN = Regex("^https?://\\S+$")

        /** Containers with no tag atom that can hold cover art. */
        val NO_ARTWORK_CONTAINERS = setOf("webm", "avi", "flv")
    }
}
