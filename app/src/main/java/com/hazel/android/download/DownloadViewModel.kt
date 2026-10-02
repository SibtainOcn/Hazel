package com.hazel.android.download

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hazel.android.HazelApp
import com.hazel.android.R
import com.hazel.android.data.CookieRepository
import com.hazel.android.data.DownloadHistoryRepository
import com.hazel.android.data.DownloadQueueRepository
import com.hazel.android.data.QueuedDownload
import com.hazel.android.data.toPlan
import com.hazel.android.data.toQueued
import com.hazel.android.data.HistoryEntry
import com.hazel.android.data.HomeResultsRepository
import com.hazel.android.data.SearchHistoryRepository
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.extractor.LinkContents
import com.hazel.android.download.extractor.LinkResolver
import com.hazel.android.download.extractor.ListingSource
import com.hazel.android.download.extractor.MediaSearch
import com.hazel.android.download.extractor.SearchSource
import com.hazel.android.download.extractor.newpipe.NewPipeEngine
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.hazel.android.util.LinkKey
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.flow.update
import java.io.File

/** Where one link has got to while a batch is running. */
enum class BatchState { QUEUED, DOWNLOADING, PAUSED, DONE, FAILED }

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
    val author: String,
    /**
     * The soundtrack to take, for a source that published more than one. Null means the
     * one the source leads with, which is every ordinary link.
     */
    val audioLanguage: String? = null
)

data class DownloadState(
    val url: String = "",
    val isFetching: Boolean = false,
    /** How far through a multi-link read the app is, for the progress line. */
    val fetchProgress: String = "",
    /** How many links the current read covers, so the screen can stand in for each one. */
    val fetchCount: Int = 0,
    /** The words the results on screen were searched for, or blank when they came from links. */
    val searchQuery: String = "",
    /** Every link that resolved from the last search, in the order they were entered. */
    val results: List<MediaInfo> = emptyList(),
    /** The result the open sheet is editing, and the one the engine is working on. */
    val info: MediaInfo? = null,
    val isDownloading: Boolean = false,
    /**
     * The link being downloaded, or paused mid-download. Kept apart from [info], which is
     * whatever result the sheet is open on: reading a new link or opening another result
     * must not rename, retitle or re-file a download that is already running.
     */
    val active: MediaInfo? = null,
    val progress: Float = 0f,
    /** Total transfer size reported by yt-dlp, 0 until its first progress line. */
    val totalBytes: Long = 0L,
    /**
     * Time remaining as yt-dlp last reported it (`00:14`), blank before its first progress
     * line and once the transfer is over.
     */
    val eta: String = "",
    val status: String = "",
    /**
     * True once the transfer is done and yt-dlp has moved on to merging, converting or
     * tagging. There is nothing left to cancel at that point, so the screen swaps the
     * cancel control for a processing treatment.
     */
    val isProcessing: Boolean = false,
    /**
     * The stages the download in hand goes through, worked out from what its request asks
     * for, and how far along them it is. The card shows them as it processes.
     */
    val processingSteps: List<ProcessingStep> = emptyList(),
    val processingStep: Int = 0,
    val fileName: String = "",
    val savedPath: String = "",
    val error: String? = null,
    /**
     * True when a run was held back because the phone is not on Wi-Fi and downloads are
     * set to Wi-Fi only.
     *
     * Separate from [error] because it is not one. Nothing failed and nothing was lost: the
     * queue is intact and the run starts the moment the phone is on Wi-Fi again. It reads
     * on the card, over the artwork, rather than as a line of red text above the list,
     * because it is a fact about the item and not about the screen.
     */
    val waitingForWifi: Boolean = false,
    /**
     * Untouched text from a failed metadata read, shown in the failure dialog. The
     * sanitized [error] is for inline messages; this is what the user can copy or act on.
     */
    val errorLog: String? = null,
    /**
     * Set when a download could not be written to the folder it was meant for, usually an
     * SD card that was taken out, filled up or stopped granting access, and was saved
     * somewhere else instead. Nothing is lost, but the user has to be told where it went.
     */
    val saveFallback: SaveFallback? = null,
    val isComplete: Boolean = false,
    /** Per-link state while several links download one after another. */
    val batch: List<BatchItem> = emptyList(),
    /**
     * True once a read has taken a finished download off the list.
     *
     * It is not lost, and the line at the foot of the results says where it went. Kept for
     * the rest of the session rather than only for the read that did it, since the cards
     * stay absent for that long; clearing the results is what puts it back to false.
     */
    val savedAside: Boolean = false
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
        readShownElsewhere = false
    }

    /**
     * True while the results in hand came from a read the share overlay started. Its sheet
     * opened over the app it was shared from, and the choice was made or declined there;
     * opening the app afterwards is not a request to be asked again, so the app's own sheet
     * does not open for these results. A read started in the app clears it.
     */
    var readShownElsewhere: Boolean = false
        private set

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

    /**
     * Set while the engine is being stopped for a pause rather than for good.
     *
     * The two look identical from inside the run: the process is destroyed and the call
     * throws. What follows is the opposite in each case, so the difference has to be stated
     * rather than inferred. A cancel throws the part file away and records a failure; a
     * pause leaves the part file exactly where it is, which is what lets the download pick
     * up from there instead of starting again.
     */
    @Volatile private var isPaused = false

    /** When the progress notification was last redrawn, so updates stay evenly paced. */
    @Volatile private var lastNotifiedAt = 0L

    /** Entries whose formats are being read, so opening a sheet twice reads once. */
    /**
     * Links whose formats are being read right now. The format list shows its skeleton
     * for exactly these, so a link whose read failed stops showing one instead of
     * waiting forever.
     */
    private val _formatsReading = MutableStateFlow<Set<String>>(emptySet())
    val formatsReading: StateFlow<Set<String>> = _formatsReading.asStateFlow()

    /** Caps how many links have their formats read at the same time. */
    private val formatReads = Semaphore(FORMAT_READS_AT_ONCE)

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
     * How far along the download actually is, judged by what is on disk.
     *
     * A download picked up from a part file reports its own progress from nothing: the
     * engine counts what this run has fetched, not what is already written. Shown as it
     * comes, that reads as the download having started again, which is the one thing a
     * resume must not look like. The same gap opens up without any resume at all, because a
     * video and its audio are fetched as two runs of the transfer and the second one starts
     * its count at zero with most of the file already down.
     *
     * So the temp directory is measured instead, before the engine starts and again as it
     * runs, and the readout never falls below what those bytes are worth.
     */
    @Volatile private var progressFloor = 0f

    /** When the temp directory was last measured, so the floor is not re-read every line. */
    @Volatile private var lastFloorCheckAt = 0L

    /**
     * Links waiting their turn, and the run that is draining them.
     *
     * Sharing three links in a row used to drop the second and third: a run was already
     * going, and starting one was the only way in. They are appended instead, and the run
     * keeps going until the queue is empty, so a set collected over several shares behaves
     * the same as a set collected in one.
     *
     * The same list is written to disk as it changes. A queue held only here is one that a
     * swipe off the recents list throws away without a word, leaving the user to work out
     * for themselves which seven of their ten downloads never happened.
     */
    private val queue = ArrayDeque<QueuedDownload>()

    /**
     * The run working through [queue], or null when none is. Claimed and released only under
     * the queue's lock, so a link added at any moment either joins the run in flight or
     * starts one of its own, never both: two runs at once share the engine's one process id,
     * and the second fails with "Process ID already exists".
     */
    private var runOwner: Any? = null

    init {
        // Offered to the notification's buttons, so a pause from the shade and a pause from
        // the card are the same call rather than two ideas of what a pause is.
        DownloadCommands.register(this)
        restoreQueue()
    }

    /** Puts back the cards left on the home screen last time, then keeps the file current. */
    private suspend fun restoreHomeResults(app: HazelApp) {
        val incognito = SettingsRepository.getIncognito(app).first()
        val saved = if (incognito) emptyList() else HomeResultsRepository.load(app)
        // No sheet is selected, so none opens on its own.
        _state.update { if (it.results.isEmpty() && !it.isFetching && saved.isNotEmpty()) it.copy(results = saved) else it }
        viewModelScope.launch(Dispatchers.IO) {
            _state.map { it.results }.distinctUntilChanged().drop(1).collect { results ->
                if (SettingsRepository.getIncognito(app).first()) HomeResultsRepository.clear(app)
                else HomeResultsRepository.save(app, results)
            }
        }
    }

    override fun onCleared() {
        DownloadCommands.unregister(this)
        super.onCleared()
    }

    /**
     * Picks up a queue left behind by a run that did not finish.
     *
     * The links are put back in the list so the screen shows what is still owed, and the
     * run starts itself: they were asked for already, and asking again for permission to
     * carry on would make the writing-down pointless.
     */
    private fun restoreQueue() {
        downloadScope.launch {
            val app = HazelApp.instance
            restoreHomeResults(app)
            val pending = runCatching { DownloadQueueRepository.load(app) }
                .getOrDefault(emptyList())
            if (pending.isEmpty()) return@launch

            val plans = pending.map { it.toPlan() }

            // A link the user paused is shown as paused and left alone. Only what was
            // still owed when the app went away is picked up, which is the difference
            // between carrying on and overriding a decision already made.
            _state.value = _state.value.copy(
                results = plans.map { it.info } + _state.value.results.filterNot { existing ->
                    plans.any { it.info.url == existing.url }
                },
                batch = pending.map {
                    BatchItem(
                        url = it.url,
                        title = it.title,
                        state = if (it.paused) BatchState.PAUSED else BatchState.QUEUED
                    )
                }
            )

            val owed = pending.filterNot { it.paused }
            if (owed.isEmpty()) return@launch

            synchronized(queue) {
                // Straight into the queue rather than back through the front door: they are
                // already written down, and enqueuing them again would only rewrite them.
                // A resume from the notification can reach the queue first, as this starts.
                owed.filter { saved -> queue.none { it.url == saved.url } }
                    .forEach { queue.addLast(it) }
            }
            runQueue(app, resumed = true)
        }
    }

    /** Destination picked through the document picker, or blank for Download/Hazel. */
    private var downloadTreeUri: String = ""

    /** Set while a batch runs, so a cancel stops the whole run rather than one item. */
    @Volatile private var isBatchCancelled = false

    /**
     * The folder the download in hand works in: one per link, under the temporary downloads
     * folder, so nothing done to one download touches another's files.
     *
     * They used to share one folder, and everything that tidies up after a download works
     * on a whole folder: the partial files of a paused download were deleted when the next
     * download failed or was cancelled, so it started again from nothing; and a finished
     * download published every file in the folder, so a paused download's finished video
     * stream, still waiting for its audio, landed in Downloads as a video without sound.
     */
    @Volatile private var downloadDir: File = StoragePaths.tempDownloads

    /** The stages the request built last will go through, handed to the state as it runs. */
    @Volatile private var plannedSteps: List<ProcessingStep> = emptyList()

    /** Throws away everything a download had on disk: it was cancelled or it failed. */
    private fun discardWorkDir() {
        runCatching { downloadDir.deleteRecursively() }
    }

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

    /** Clears the resolved links and the URL field, for the Clear results menu action. */
    fun clearResults() {
        fetchJob?.cancel()
        MediaProbe.cancel()
        _state.value = DownloadState()
    }

    /**
     * Resolves a shared URL in complete isolation from previous search/download queries.
     *
     * Clears any existing search results so single videos present [FormatSheet] directly,
     * while collections and playlists present [BatchDownloadSheet].
     */
    fun fetchShare(url: String) {
        clearResults()
        onUrlChange(url)
        fetchAll(listOf(url))
        readShownElsewhere = true
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

    /**
     * Starts a download chosen on a sheet that opened before its link had been read.
     *
     * A shared link opens its sheet at once, and the choice can be made while the read is
     * still running. The choice waits for that read here, where it survives the sheet
     * closing, and is then applied to what the link turned out to be: the chosen kind at its
     * best for a single item, or for every item of a collection. A read that fails is
     * reported the way any failed read is, with a notification that opens its log.
     */
    fun downloadOnceRead(
        context: Context,
        url: String,
        format: MediaFormat,
        options: DownloadOptions,
        title: String,
        author: String,
        audioLanguage: String?,
        treeUri: String
    ) {
        val app = context.applicationContext
        viewModelScope.launch {
            fetchJob?.join()
            val current = _state.value
            if (current.url != url) {
                Log.w("Hazel", "Share choice dropped: another link was read meanwhile")
                return@launch
            }
            val results = current.results
            Log.i("Hazel", "Share choice starting after the read: ${results.size} item(s)")

            when {
                results.size > 1 -> {
                    val plans = results.mapNotNull { info ->
                        GenericFormats.applyTo(info, format, audioLanguage)?.let {
                            DownloadPlan(info, it, info.title, info.uploader, audioLanguage)
                        }
                    }
                    if (plans.isNotEmpty()) startBatch(app, plans, options, treeUri)
                }

                results.size == 1 -> {
                    val info = results.first()
                    val chosen = GenericFormats.applyTo(info, format, audioLanguage) ?: format
                    startDownload(
                        context = app,
                        format = chosen,
                        options = options,
                        title = title.ifBlank { info.title },
                        author = author.ifBlank { info.uploader },
                        audioLanguage = audioLanguage,
                        treeUri = treeUri,
                        info = info
                    )
                }

                else -> {
                    val log = current.errorLog ?: app.getString(R.string.no_results_error_title)
                    DownloadNotificationHelper.showError(
                        app, sanitizeError(log), signInUrl = signInTargetFor(log, url)
                    )
                }
            }
        }
    }

    /** Links Hazel Instant is reading right now, so the same share twice is one download. */
    private val instantReads = mutableSetOf<String>()

    /**
     * Hazel Instant: reads [url] and downloads it with the saved settings, without a sheet.
     *
     * The read is its own and leaves the home screen alone, so a link being read there, or a
     * second Instant share a moment later, neither drops this one nor is dropped by it.
     *
     * The download service is started here, while the share overlay is still in front:
     * Android 12 and later refuse a foreground service started from the background, and the
     * read can easily outlast the overlay. It is let go again if the read fails and nothing
     * else needs it. A failure is reported by notification, with a sign-in button where the
     * site asked for one.
     */
    fun instantDownload(context: Context, url: String) {
        val app = context.applicationContext
        val first = synchronized(instantReads) { instantReads.add(url) }
        if (!first) return
        DownloadService.start(app, url)

        // On the queue as waiting while it is read, so the queue screen shows it from the
        // moment it was shared. Replaced by its real entries once the download starts.
        val waiting = BatchItem(url = url, title = url)
        _state.update { it.copy(batch = it.batch + waiting) }
        val dropWaiting = { _state.update { s -> s.copy(batch = s.batch - waiting) } }

        viewModelScope.launch {
            var failure: String? = null
            try {
                if (!SettingsRepository.getIncognito(app).first()) {
                    withContext(Dispatchers.IO) { SearchHistoryRepository.record(app, url) }
                }
                val options = SettingsRepository.getDownloadOptions(app).first()
                val dirs = SettingsRepository.getSaveDirs(app).first()
                val step = options.videoQuality.takeIf { it > 0 }
                    ?.let { GenericFormats.heightCeiling(it) }
                    ?: MediaProbe.BEST_VIDEO

                val items = withContext(Dispatchers.IO) {
                    expand(
                        url,
                        CookieRepository.accessFor(app, url),
                        SettingsRepository.getFetchMode(app).first(),
                        SettingsRepository.getForceIpv4(app).first(),
                        SettingsRepository.getListingSource(app).first(),
                        "${MediaProbe.PROBE_PROCESS_ID}_instant_${LinkKey.digest(url)}"
                    )
                }
                val plans = items.map { info ->
                    val format = GenericFormats.applyTo(info, step, null) ?: step
                    DownloadPlan(info, format, info.title, info.uploader, null)
                }
                dropWaiting()
                if (plans.isEmpty()) failure = app.getString(R.string.no_results_error_title)
                else startBatch(app, plans, options, saveDirs = dirs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e.message?.trim().orEmpty().ifBlank { app.getString(R.string.no_results_error_title) }
            } finally {
                if (failure != null) dropWaiting()
                val idle = synchronized(instantReads) {
                    instantReads.remove(url)
                    instantReads.isEmpty()
                }
                // Let go of the service only when nothing else is holding it: no other
                // Instant read waiting and no download running or queued.
                if (failure != null && idle && synchronized(queue) { runOwner == null && queue.isEmpty() }) {
                    DownloadService.stop(app)
                }
            }
            failure?.let { log ->
                Log.w("Hazel", "Instant download failed: $log")
                // Under Failed on the queue screen with its log. With no payload to replay,
                // Retry there reads the link again.
                downloadScope.launch {
                    com.hazel.android.data.FailedDownloadRepository.record(
                        app,
                        com.hazel.android.data.FailedDownload(
                            url = url,
                            title = url,
                            author = "",
                            thumbnail = null,
                            isVideo = true,
                            errorLog = log
                        )
                    )
                }
                DownloadNotificationHelper.showError(
                    app, sanitizeError(log), signInUrl = signInTargetFor(log, url)
                )
            }
        }
    }

    /**
     * Starts a download chosen on a card's sheet before that card's formats were read.
     *
     * Waits for the read already running, then applies the choice to what it found: the
     * chosen kind at its best, within the quality and language preferences. If the read
     * fails the choice still goes ahead as it stands, which yt-dlp resolves itself.
     */
    fun downloadOnceFormatsRead(
        context: Context,
        info: MediaInfo,
        format: MediaFormat,
        options: DownloadOptions,
        title: String,
        author: String,
        audioLanguage: String?,
        saveDirs: com.hazel.android.data.SaveDirs
    ) {
        val app = context.applicationContext
        viewModelScope.launch {
            withTimeoutOrNull(FORMAT_WAIT_MS) { _formatsReading.first { info.url !in it } }
            val read = _state.value.results.firstOrNull { it.url == info.url }
                ?.takeIf { it.hasResolvedFormats }
                ?: InfoCache.metadataFor(info.url)?.takeIf { it.hasResolvedFormats }
                ?: info
            val chosen = GenericFormats.applyTo(read, format, audioLanguage) ?: format
            startDownload(
                context = app,
                format = chosen,
                options = options,
                title = title.ifBlank { read.title },
                author = author.ifBlank { read.uploader },
                audioLanguage = audioLanguage,
                info = read,
                saveDirs = saveDirs
            )
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
        val targets = urls.map { it.trim() }
            .map { raw ->
                if (!raw.startsWith("http://", ignoreCase = true) &&
                    !raw.startsWith("https://", ignoreCase = true) &&
                    (raw.startsWith("www.", ignoreCase = true) || (raw.contains(".") && !raw.contains(" ")))
                ) {
                    "https://$raw"
                } else raw
            }
            .filter { it.isNotBlank() }
            .distinct()
        if (targets.isEmpty() || _state.value.isFetching) return

        val valid = targets.filter { URL_PATTERN.matches(it) }
        if (valid.isEmpty()) {
            _state.value = _state.value.copy(
                error = HazelApp.instance.getString(R.string.fetch_invalid_url)
            )
            return
        }

        // Record search history for all valid queried URLs if not incognito
        val app = HazelApp.instance
        viewModelScope.launch(Dispatchers.IO) {
            if (!SettingsRepository.getIncognito(app).first()) {
                valid.forEach { SearchHistoryRepository.record(app, it) }
            }
        }

        // A fresh read is a fresh answer, so the sheet is allowed to open on its own again.
        clearAutoOpened()

        // A running download keeps its record of the run: the queue screen and the
        // notification read it, and reading a new link has nothing to do with either.
        val runInHand = _state.value.isDownloading || _state.value.batch.any {
            it.state == BatchState.DOWNLOADING || it.state == BatchState.PAUSED ||
                it.state == BatchState.QUEUED
        }
        _state.value = _state.value.copy(
            url = valid.first(),
            isFetching = true,
            error = null,
            errorLog = null,
            // A new read is a new question, so the last answer goes and the skeleton takes
            // the whole screen, the way a search replaces its results. Downloads started
            // from the old results carry on in the queue.
            results = emptyList(),
            info = null,
            batch = if (runInHand) _state.value.batch else emptyList(),
            isComplete = false,
            fetchProgress = "",
            fetchCount = valid.size,
            searchQuery = ""
        )

        fetchJob = viewModelScope.launch {
            val app = HazelApp.instance
            val fetchMode = SettingsRepository.getFetchMode(app).first()
            val forceIpv4 = SettingsRepository.getForceIpv4(app).first()

            val listingSource = SettingsRepository.getListingSource(app).first()

            val resolved: List<MediaInfo>
            var lastFailure = ""

            try {
                if (valid.size > 1) {
                    _state.value = _state.value.copy(
                        fetchProgress = HazelApp.instance.getString(R.string.fetch_settings_reading_links)
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
                                    link,
                                    // Each link is read with the sign-in for its own
                                    // site, and with none where there is not one.
                                    CookieRepository.accessFor(app, link),
                                    fetchMode, forceIpv4, listingSource,
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
                    lastFailure = HazelApp.instance.getString(R.string.no_results_error_title)
                }
            } catch (_: CancellationException) {
                _state.value = _state.value.copy(isFetching = false, fetchProgress = "")
                return@launch
            } catch (e: Exception) {
                lastFailure = e.message?.trim().orEmpty()
                val message = sanitizeError(lastFailure.ifBlank { HazelApp.instance.getString(R.string.no_results_error_title) })
                _state.value = _state.value.copy(
                    isFetching = false,
                    fetchProgress = "",
                    errorLog = lastFailure.ifBlank { HazelApp.instance.getString(R.string.no_results_error_title) }
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
                    sanitizeError(lastFailure.ifBlank { HazelApp.instance.getString(R.string.no_results_error_title) }),
                    signInUrl = signInTargetFor(lastFailure, valid.first())
                )
            }

            _state.value = if (resolved.isEmpty()) {
                _state.value.copy(
                    isFetching = false,
                    fetchProgress = "",
                    errorLog = lastFailure.ifBlank { HazelApp.instance.getString(R.string.no_results_error_title) }
                )
            } else {
                _state.value.copy(
                    isFetching = false,
                    fetchProgress = "",
                    results = resolved,
                    info = resolved.singleOrNull(),
                    savedAside = false
                )
            }
        }
    }

    /**
     * Searches [source] for [query] and shows what it found as cards.
     *
     * The cards carry no formats yet, like a playlist's: each is read when its sheet opens,
     * and a play or a download of it works the same as for a pasted link.
     */
    fun search(query: String, source: SearchSource) {
        val text = query.trim()
        if (text.isBlank() || _state.value.isFetching) return
        val app = HazelApp.instance

        viewModelScope.launch(Dispatchers.IO) {
            if (!SettingsRepository.getIncognito(app).first()) SearchHistoryRepository.record(app, text)
        }
        clearAutoOpened()

        val runInHand = _state.value.isDownloading || _state.value.batch.any {
            it.state == BatchState.DOWNLOADING || it.state == BatchState.PAUSED ||
                it.state == BatchState.QUEUED
        }
        _state.value = _state.value.copy(
            isFetching = true,
            error = null,
            errorLog = null,
            results = emptyList(),
            info = null,
            batch = if (runInHand) _state.value.batch else emptyList(),
            isComplete = false,
            fetchProgress = "",
            fetchCount = SEARCH_SKELETONS,
            searchQuery = text
        )

        fetchJob = viewModelScope.launch {
            val found = try {
                MediaSearch.search(
                    query = text,
                    source = source,
                    engine = SettingsRepository.getSearchEngine(app).first(),
                    count = SettingsRepository.getSearchResults(app).first(),
                    cacheDir = ytDlpCacheDir,
                    fetchMode = SettingsRepository.getFetchMode(app).first(),
                    forceIpv4 = SettingsRepository.getForceIpv4(app).first()
                )
            } catch (_: CancellationException) {
                _state.update { it.copy(isFetching = false) }
                return@launch
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isFetching = false,
                        errorLog = e.message?.trim().orEmpty().ifBlank { app.getString(R.string.no_results_error_title) }
                    )
                }
                return@launch
            }

            _state.update {
                if (found.isEmpty()) {
                    it.copy(isFetching = false, errorLog = app.getString(R.string.search_no_results, text))
                } else {
                    it.copy(
                        isFetching = false,
                        results = found.map { entry -> InfoCache.metadataFor(entry.url) ?: MediaProbe.pendingFor(entry) },
                        savedAside = false
                    )
                }
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
        access: SiteAccess,
        fetchMode: FetchMode,
        forceIpv4: Boolean,
        source: ListingSource,
        processKey: String
    ): List<MediaInfo> {
        // A link read recently is not read again. The engine costs seconds to start before
        // it does any work, so the cheapest read is the one that does not happen. This
        // holds across restarts as well: what the last read wrote is still on disk.
        // The chosen reader's own read first, then whichever reader read it last.
        (InfoCache.metadataFor(url, source) ?: InfoCache.metadataFor(url))?.let { return listOf(it) }
        InfoCache.listingFor(url)?.let { listing ->
            return listing.entries.map(MediaProbe::pendingFor)
        }

        val contents = LinkResolver.resolve(
            url, ytDlpCacheDir, access, fetchMode, forceIpv4, source, processKey
        )

        return when (contents) {
            is LinkContents.Single -> listOf(contents.info)
            is LinkContents.Many -> contents.entries.map(MediaProbe::pendingFor)
        }
    }

    /**
     * Reads one entry's formats, for a card that came from a listing.
     *
     * Called when the sheet opens on such a card, and for every link of a set when its
     * audio formats are asked for. A card that already has formats, or one whose formats
     * are still being read, is left alone.
     */
    fun resolveFormats(info: MediaInfo) {
        if (info.hasResolvedFormats) return
        readFormatsOf(info, fresh = false, source = null)
    }

    /**
     * Reads the formats of [infos] with [source] when one is given and the reader setting
     * otherwise.
     *
     * [fresh] is the format list's update action: the reader is asked again and its last
     * read replaced. Without it, as when the list is switched to another reader, that
     * reader's last read of the link is used where it has one, so switching back and forth
     * costs a read only the first time.
     */
    fun refreshFormats(infos: List<MediaInfo>, source: ListingSource? = null, fresh: Boolean = true) {
        infos.forEach { info ->
            // Nothing to do for a link already showing that reader's read, unless asked again.
            if (!fresh && source != null && info.hasResolvedFormats && info.readBy == source) return@forEach
            readFormatsOf(info, fresh = fresh, source = source)
        }
    }

    /**
     * Starts one link's read unless one is already running for it.
     *
     * Reads beyond [FORMAT_READS_AT_ONCE] wait their turn: each is a Python process of its
     * own, and a playlist of eighty started at once would starve the device rather than
     * finish any sooner.
     */
    private fun readFormatsOf(info: MediaInfo, fresh: Boolean, source: ListingSource?) {
        if (!claimFormatRead(info.url)) return

        viewModelScope.launch(Dispatchers.IO) {
            val resolved = try {
                formatReads.withPermit { readFormats(info.url, fresh, source) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Hazel", "Format read failed for ${info.url}: ${e.message}")
                null
            } finally {
                _formatsReading.update { it - info.url }
            }
            if (resolved == null) return@launch

            // The artwork the full read found is preferred, since a listing often carries
            // only a small preview; the listing's title is kept, as the card already shows it.
            val merged = resolved.copy(
                title = info.title.ifBlank { resolved.title },
                thumbnail = resolved.thumbnail ?: info.thumbnail,
                // A running time the listing gave is kept when the full read has none,
                // rather than the card losing the length it was already showing.
                durationSeconds = resolved.durationSeconds.takeIf { it > 0 } ?: info.durationSeconds
            )

            // Several reads finish at once when a set is being resolved, so the change is
            // applied atomically rather than as a read and a separate write that could
            // drop another link's answer landing in between.
            _state.update { current ->
                current.copy(
                    results = current.results.map { if (it.url == info.url) merged else it },
                    info = if (current.info?.url == info.url) merged else current.info
                )
            }
        }
    }

    /** Marks [url] as being read, or returns false when it already is. */
    private fun claimFormatRead(url: String): Boolean {
        while (true) {
            val current = _formatsReading.value
            if (url in current) return false
            if (_formatsReading.compareAndSet(current, current + url)) return true
        }
    }

    /**
     * One link's formats. A [fresh] read skips the cache and replaces what the reader held
     * there, leaving the other reader's read alone; a [source] overrides the reader setting
     * for this read, and names the slot of the cache it is answered from.
     *
     * A reader that cannot answer (NewPipe on a site it does not know, or on one with a saved
     * sign-in it cannot send) falls back to yt-dlp, whose own last read is used first when
     * the read is not a fresh one.
     */
    private suspend fun readFormats(url: String, fresh: Boolean, source: ListingSource?): MediaInfo? {
        val app = HazelApp.instance
        val reader = source ?: SettingsRepository.getListingSource(app).first()
        val access = CookieRepository.accessFor(app, url)
        val newPipeCan = reader == ListingSource.NEWPIPE && !access.hasCookies && NewPipeEngine.handlesStream(url)

        if (fresh) {
            InfoCache.invalidate(url, if (newPipeCan) ListingSource.NEWPIPE else ListingSource.YT_DLP)
        } else {
            val cached = when {
                // A reader asked for by name is answered from its own reads, or, when it
                // cannot read this link at all, from the reader it falls back to.
                source != null -> InfoCache.metadataFor(url, if (newPipeCan) source else ListingSource.YT_DLP)
                else -> InfoCache.metadataFor(url)
            }
            cached?.takeIf { it.hasResolvedFormats }?.let { return it }
        }

        if (newPipeCan) {
            runCatching { NewPipeEngine.single(url) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
                ?.takeIf { it.hasResolvedFormats }
                ?.let {
                    InfoCache.put(url, it, rawJson = null)
                    return it
                }
            // NewPipe could not answer; yt-dlp's own last read stands in before a new one.
            if (!fresh) {
                InfoCache.metadataFor(url, ListingSource.YT_DLP)?.takeIf { it.hasResolvedFormats }?.let { return it }
            }
        }

        return probeWithRetry(
            url,
            access,
            SettingsRepository.getFetchMode(app).first(),
            SettingsRepository.getForceIpv4(app).first(),
            // Each read has an id of its own: the engine refuses a second process under an
            // id already running, which failed every read after the first in a set.
            "${MediaProbe.PROBE_PROCESS_ID}_formats_${LinkKey.digest(url)}"
        )
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
        access: SiteAccess,
        fetchMode: FetchMode,
        forceIpv4: Boolean,
        processKey: String = MediaProbe.PROBE_PROCESS_ID
    ): MediaInfo = try {
        MediaProbe.probe(url, ytDlpCacheDir, access, fetchMode, forceIpv4, processKey)
    } catch (e: Exception) {
        if (isTerminal(e.message?.trim().orEmpty())) throw e
        MediaProbe.probe(
            url, ytDlpCacheDir, access, FetchMode.THOROUGH, forceIpv4, processKey
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

    /** Dismisses the saved-elsewhere dialog. */
    fun clearSaveFallback() {
        _state.value = _state.value.copy(saveFallback = null)
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
            results = listOf(fallback) + _state.value.results.filterNot { it.url == fallback.url },
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
        audioLanguage: String? = null,
        treeUri: String = "",
        info: MediaInfo? = null,
        /** Where each kind is saved; when given, it decides over [treeUri]. */
        saveDirs: com.hazel.android.data.SaveDirs? = null
    ) {
        val targetInfo = info ?: _state.value.info ?: _state.value.results.firstOrNull() ?: return
        if (_state.value.info == null) {
            _state.value = _state.value.copy(info = targetInfo)
        }
        startBatch(
            context = context,
            plans = listOf(DownloadPlan(targetInfo, format, title, author, audioLanguage)),
            options = options,
            treeUri = treeUri,
            saveDirs = saveDirs
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
        treeUri: String = "",
        /**
         * Where each kind is saved. When given, every link goes to the folder for its own
         * kind, so a set mixing audio and video sends each to its place.
         */
        saveDirs: com.hazel.android.data.SaveDirs? = null
    ) {
        if (plans.isEmpty()) return

        com.hazel.android.util.PermissionHelper.ensureNotificationPermission(context)

        // Asked here, on the versions that still need it, because this is where a finished
        // file is about to be published into the user's own Download folder. Up to Android
        // 10 that is a direct file write and needs the permission; without it the move
        // failed and the file stayed in the app's folder without anything saying so.
        com.hazel.android.util.PermissionHelper.ensureSharedStorageWrite(context)

        val queued = plans.map {
            it.toQueued(options, saveDirs?.of(it.format.hasVideo)?.uri ?: treeUri)
        }
        val alreadyRunning = synchronized(queue) {
            queue.addAll(queued)
            runOwner != null
        }

        // Written down before anything starts, so a queue interrupted a second later is
        // still a queue that can be picked up.
        downloadScope.launch { DownloadQueueRepository.add(HazelApp.instance, queued) }

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

        // Start foreground service synchronously immediately while calling activity is in foreground.
        // This keeps the process alive when the share overlay finishes.
        val firstTitle = plans.firstOrNull()?.title.orEmpty()
        DownloadService.start(context.applicationContext, firstTitle)

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
            waitingForWifi = false,
            // What is still owed from before (a paused download, links waiting behind it)
            // stays on the list next to the new links rather than vanishing from it.
            batch = _state.value.batch.filter {
                (it.state == BatchState.PAUSED || it.state == BatchState.QUEUED) &&
                    plans.none { plan -> plan.info.url == it.url }
            } + plans.map { BatchItem(url = it.info.url, title = it.title) }
        )

        runQueue(context.applicationContext, resumed = false)
    }

    /**
     * Works through the queue, one link at a time, until there is nothing left in it.
     *
     * One at a time is deliberate: yt-dlp already saturates the connection for a single
     * download, and every download shares one temporary directory, where the sweep that
     * publishes finished files cannot tell one run's output from another's.
     *
     * [resumed] marks a queue picked up from disk rather than one just asked for, which is
     * the only case where the run has to announce itself.
     */
    private fun runQueue(app: Context, resumed: Boolean) {
        val token = Any()
        val claimed = synchronized(queue) {
            if (runOwner != null) false else {
                runOwner = token
                true
            }
        }
        // A run is already working through the queue, and it takes what was just added.
        if (!claimed) return

        if (resumed) {
            isBatchCancelled = false
            downloadContext = app
            _state.value = _state.value.copy(
                isDownloading = true,
                isComplete = false,
                progress = 0f,
                totalBytes = 0L,
                status = "Resuming downloads",
                isProcessing = false,
                error = null,
                waitingForWifi = false
            )
        }

        val firstTitle = synchronized(queue) { queue.firstOrNull() }?.title.orEmpty()

        // Run on a scope tied to the process rather than to the screen. A download the user
        // has walked away from should not end because the screen that started it did.
        val job = downloadScope.launch {
            val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
            val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            if (caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) {
                // The queue is left where it is, on disk and in memory. There is nothing
                // wrong with what was asked for, only with the connection, so it waits.
                DownloadService.stop(app)
                fail(app, "No internet connection")
                return@launch
            }

            // Checked once, as the run starts, rather than throughout. A transfer already
            // going when the phone drops off Wi-Fi is left alone: stopping it partway
            // wastes the data it has already spent, which is what the setting is for.
            //
            // The question asked is which transport the connection is on, not whether it
            // happens to be the mobile one. A network can be neither: a phone tethered over
            // USB or Bluetooth, or a link the system describes some other way again. Asking
            // whether it was cellular let all of those through a setting whose entire point
            // is that they should not be.
            if (SettingsRepository.getWifiOnly(app).first() && !onWifiOrEthernet(caps)) {
                DownloadService.stop(app)
                holdForWifi(app)
                return@launch
            }

            // Asks the system to leave the process alone for the length of the run. Started
            // before the first link rather than per link, so a set of ten is one service for
            // the whole set instead of ten in a row.
            DownloadService.start(app, firstTitle)

            val limits = TransferLimits(
                speedLimit = SettingsRepository.getSpeedLimit(app).first(),
                concurrentFragments = SettingsRepository.getConcurrentFragments(app).first(),
                throttledRate = SettingsRepository.getThrottledRate(app).first()
            )

            while (true) {
                if (isBatchCancelled) break
                // Released in the same step that finds the queue empty, so a link added a
                // moment later starts a run of its own instead of waiting on this one.
                //
                // A paused link stays where it is and is stepped over: the user stopped it,
                // and starting something else is not a reason to start it again.
                val next = synchronized(queue) {
                    val index = queue.indexOfFirst { !it.paused }
                    (if (index >= 0) queue.removeAt(index) else null)
                        .also { if (it == null && runOwner === token) runOwner = null }
                } ?: break
                val plan = next.toPlan()
                val options = next.options
                downloadTreeUri = next.treeUri
                downloadDir = workDirFor(plan.info.url, next.treeUri)

                // The download uses the site credentials configured for this URL so that
                // both the metadata probe and the download execute with matching access.
                val access = CookieRepository.accessFor(app, plan.info.url)
                val planAccess = when {
                    !access.hasCookies -> SiteAccess.NONE
                    else -> access
                }

                isCancelled = false
                isPaused = false
                downloadIsVideo = plan.format.hasVideo
                markBatch(plan.info.url, BatchState.DOWNLOADING)

                expectedTotalBytes = expectedTotalFor(plan)
                lastFloorCheckAt = 0L
                progressFloor = fractionOnDisk()

                val opening = if (progressFloor > 0f) "Resuming" else "Starting download"
                _state.value = _state.value.copy(
                    info = plan.info,
                    active = plan.info,
                    progress = progressFloor,
                    totalBytes = expectedTotalBytes,
                    eta = "",
                    status = opening,
                    isProcessing = false
                )
                DownloadNotificationHelper.showProgress(
                    app, (progressFloor * 100f).toInt(), opening, plan.title
                )

                try {
                    if (!downloadDir.exists()) downloadDir.mkdirs()

                    try {
                        executeYtDlp(
                            buildRequest(
                                plan.info.url, plan.format, options, plan.title, plan.author,
                                planAccess, limits,
                                plan.info.mergeAudioFor(plan.audioLanguage)?.selector,
                                plan.audioLanguage
                            )
                        )
                    } catch (e: Exception) {
                        // A replayed payload whose addresses have expired fails here. That
                        // is not the link failing, so it is read again and tried once more
                        // before the item is recorded as a failure.
                        // A pause counts as a reason not to retry, the same as a cancel.
                        // Without that the retry treated the stopped process as a stale
                        // payload, threw away the part file and started the download again,
                        // which is the one thing a pause must not do.
                        val replayed = InfoCache.infoJsonFor(plan.info.url) != null
                        if (!replayed || isCancelled || isBatchCancelled || isPaused) throw e

                        InfoCache.invalidate(plan.info.url)
                        purgeFragments()
                        // The fragments that earned the floor have just been thrown away,
                        // so the floor goes with them rather than holding the bar up at a
                        // figure nothing on disk backs any more.
                        progressFloor = 0f
                        lastFloorCheckAt = 0L
                        executeYtDlp(
                            buildRequest(
                                plan.info.url, plan.format, options, plan.title, plan.author,
                                planAccess, limits,
                                plan.info.mergeAudioFor(plan.audioLanguage)?.selector,
                                plan.audioLanguage
                            )
                        )
                    }

                    if (isPaused) {
                        holdForResume(next)
                        break
                    }

                    if (isBatchCancelled) {
                        // Cancelled, so nothing it left is kept. Publishing it put a half-made
                        // file in Downloads and a history entry for a cancelled download.
                        discardWorkDir()
                        markBatch(plan.info.url, BatchState.FAILED, "Cancelled")
                        break
                    }

                    if (isCancelled) {
                        discardWorkDir()
                        markBatch(plan.info.url, BatchState.FAILED, "Cancelled")
                        isCancelled = false
                        continue
                    }

                    finishDownload(app, plan, options)
                    markBatch(plan.info.url, BatchState.DONE)
                    downloadScope.launch {
                        com.hazel.android.data.FailedDownloadRepository.removeByUrl(app, plan.info.url)
                    }
                } catch (_: CancellationException) {
                    if (isPaused) {
                        holdForResume(next)
                        break
                    }
                    if (isBatchCancelled) {
                        // Cancelled, so nothing it left is kept. Publishing it put a half-made
                        // file in Downloads and a history entry for a cancelled download.
                        discardWorkDir()
                        markBatch(plan.info.url, BatchState.FAILED, "Cancelled")
                        break
                    }
                    if (isCancelled) {
                        discardWorkDir()
                        markBatch(plan.info.url, BatchState.FAILED, "Cancelled")
                        isCancelled = false
                        continue
                    }
                    discardWorkDir()
                    markBatch(plan.info.url, BatchState.FAILED, "Cancelled")
                    break
                } catch (e: Exception) {
                    if (isPaused) {
                        holdForResume(next)
                        break
                    }

                    if (isBatchCancelled) {
                        // Cancelled, so nothing it left is kept. Publishing it put a half-made
                        // file in Downloads and a history entry for a cancelled download.
                        discardWorkDir()
                        markBatch(plan.info.url, BatchState.FAILED, "Cancelled")
                        break
                    }

                    if (isCancelled) {
                        discardWorkDir()
                        markBatch(plan.info.url, BatchState.FAILED, "Cancelled")
                        isCancelled = false
                        continue
                    }

                    com.hazel.android.utils.CrashLogger.logDownloadError(
                        url = plan.info.url,
                        platform = detectPlatform(plan.info.url),
                        error = e.message ?: "Download failed"
                    )
                    val queuedPayload = DownloadQueueRepository.encodeItem(next)
                    downloadScope.launch {
                        com.hazel.android.data.FailedDownloadRepository.record(
                            app,
                            com.hazel.android.data.FailedDownload(
                                url = plan.info.url,
                                title = plan.info.title.ifBlank { plan.title },
                                author = plan.author.ifBlank { plan.info.uploader },
                                thumbnail = plan.info.thumbnail,
                                isVideo = plan.format.hasVideo,
                                errorLog = e.message ?: "Download failed",
                                queuedPayload = queuedPayload
                            )
                        )
                    }
                    // One bad link does not stop the rest: the failure is recorded against
                    // that item and the batch carries on. A retry starts clean.
                    discardWorkDir()
                    markBatch(
                        plan.info.url,
                        BatchState.FAILED,
                        sanitizeError(e.message ?: "Download failed")
                    )
                }
            }

            // A run stopped part way leaves whatever it did not reach written down, so the
            // next launch carries on. A run the user cancelled leaves nothing: they stopped
            // it on purpose, and a queue that came back on the next launch would be the app
            // overruling that.
            val remaining =
                if (isBatchCancelled) emptyList()
                else synchronized(queue) { queue.toList() }
            DownloadQueueRepository.save(app, remaining)
            if (isBatchCancelled) {
                synchronized(queue) { queue.clear() }
                // Paused downloads are given up with the rest, their partial files and their
                // notification with them. Nothing is running any more to be caught by this.
                runCatching { com.hazel.android.util.SdCards.workRoots().forEach { root -> root.listFiles()?.forEach { it.deleteRecursively() } } }
                DownloadNotificationHelper.cancelPaused(app)
                _state.value = _state.value.copy(
                    batch = _state.value.batch.map {
                        if (it.state == BatchState.PAUSED || it.state == BatchState.QUEUED || it.state == BatchState.DOWNLOADING) {
                            it.copy(state = BatchState.FAILED, error = "Cancelled")
                        } else it
                    }
                )
            }

            DownloadService.stop(app)
            finishBatch(app)
        }
        job.invokeOnCompletion {
            synchronized(queue) { if (runOwner === token) runOwner = null }
        }
        downloadJob = job
    }

    /**
     * Puts a paused link back at the head of the queue, with its part file untouched.
     *
     * Nothing is swept and nothing is published: the temporary directory still holds a
     * half-finished file, and handing that to the sweep would put half a video in the user's
     * Downloads folder and write it into the history as though it had finished.
     */
    private fun holdForResume(item: QueuedDownload) {
        synchronized(queue) { queue.addFirst(item.copy(paused = true)) }
        markBatch(item.url, BatchState.PAUSED)
        _state.value = _state.value.copy(status = "Paused", isProcessing = false, eta = "")

        // The shade is told the same thing the card is, off the same figures. A download
        // held while the app is off screen otherwise loses its notification along with the
        // service, leaving nothing anywhere to say it is waiting or to start it again.
        val app = HazelApp.instance
        val fraction = _state.value.progress.coerceIn(0f, 1f)
        val total = _state.value.totalBytes
        DownloadNotificationHelper.showPaused(
            context = app,
            progress = (fraction * 100f).toInt(),
            mediaTitle = _state.value.active?.title.orEmpty().ifBlank { item.title },
            doneBytes = (total * fraction).toLong(),
            totalBytes = total
        )

        downloadScope.launch {
            DownloadQueueRepository.setPaused(app, item.url, true)
        }
    }

    /**
     * Throws away a download that was sitting paused.
     *
     * Everything a finished run would have done is done here instead: the half-finished
     * files go, the record goes, and the shade is cleared. What is deliberately not done is
     * publishing anything, because a cancelled download has nothing worth keeping.
     */
    private fun discardHeldDownload() {
        val app = HazelApp.instance
        // Nothing is running, so every partial download on disk is one being given up.
        runCatching { com.hazel.android.util.SdCards.workRoots().forEach { root -> root.listFiles()?.forEach { it.deleteRecursively() } } }
        DownloadNotificationHelper.cancelProgress(app)
        DownloadNotificationHelper.showCancelled(app)

        synchronized(queue) { queue.clear() }
        _state.value = _state.value.copy(
            batch = _state.value.batch.map {
                if (it.state == BatchState.PAUSED || it.state == BatchState.QUEUED) {
                    it.copy(state = BatchState.FAILED, error = "Cancelled")
                } else it
            },
            isDownloading = false,
            active = null,
            isProcessing = false,
            progress = 0f,
            status = ""
        )

        downloadScope.launch {
            DownloadQueueRepository.save(app, emptyList())
        }
    }

    private fun markBatch(url: String, state: BatchState, error: String? = null) {
        _state.value = _state.value.copy(
            batch = _state.value.batch.map {
                if (it.url == url) it.copy(state = state, error = error) else it
            }
        )

        // Taken off the written-down queue once it is settled either way. A link kept there
        // after it finished would download itself again on the next launch. A paused one is
        // not settled: it is still owed, and staying on the record is the point of it.
        if (state == BatchState.DONE || state == BatchState.FAILED) {
            downloadScope.launch {
                DownloadQueueRepository.remove(HazelApp.instance, url)
            }
        }
    }

    /** Reports the run as a whole once every item has been attempted. */
    private fun finishBatch(context: Context) {
        val current = _state.value
        val done = current.batchDone
        // Only count actual failures, NOT user-cancelled items!
        val trueFailed = current.batch.count { it.state == BatchState.FAILED && it.error != "Cancelled" }

        _state.value = current.copy(
            isDownloading = false,
            active = null,
            isComplete = done > 0,
            status = "",
            isProcessing = false,
            error = when {
                done == 0 && trueFailed > 0 ->
                    current.batch.firstOrNull { it.error != null && it.error != "Cancelled" }?.error ?: "Download failed"
                trueFailed > 0 -> "$trueFailed of ${current.batch.size} failed"
                else -> null
            }
        )

        when {
            isBatchCancelled && done == 0 -> DownloadNotificationHelper.showCancelled(context)

            trueFailed > 0 && done == 0 -> {
                val item = current.batch.firstOrNull { it.state == BatchState.FAILED && it.error != "Cancelled" }
                DownloadNotificationHelper.showError(
                    context,
                    item?.error ?: "Download failed",
                    item?.title.orEmpty()
                )
            }

            trueFailed > 0 -> DownloadNotificationHelper.showError(
                context,
                "$trueFailed of ${current.batch.size} could not be downloaded",
                ""
            )
        }
    }

    /**
     * Cancels or removes a single item from the batch/results.
     *
     * If the item is currently downloading, it stops ONLY this item's process,
     * marks it cancelled, and the batch runner automatically proceeds to the next
     * item in the queue without killing the whole batch.
     *
     * If the item is still waiting in the queue, it removes it from the queue
     * and disk queue record, without disturbing the active download.
     */
    fun cancelItem(url: String) {
        val activeInfo = _state.value.active
        if (_state.value.isDownloading && activeInfo?.url == url) {
            // Cancel ONLY this active download item (do NOT set isBatchCancelled)
            isCancelled = true
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().destroyProcessById(processId)
                } catch (_: Exception) { /* process may already be done */ }
            }
        } else {
            // It is waiting in the queue or is paused
            val app = HazelApp.instance
            // Its own folder only: another download may be running beside it, and its
            // partial files are in a folder of their own.
            runCatching { workDirsFor(url).forEach { it.deleteRecursively() } }
            synchronized(queue) {
                queue.removeAll { it.url == url }
            }
            downloadScope.launch {
                DownloadQueueRepository.remove(app, url)
            }
            markBatch(url, BatchState.FAILED, "Cancelled")
            val remainingPaused = synchronized(queue) { queue.any { it.paused } } ||
                    _state.value.batch.any { it.url != url && it.state == BatchState.PAUSED }
            if (!remainingPaused) {
                DownloadNotificationHelper.cancelPaused(app)
                downloadScope.launch {
                    DownloadQueueRepository.clearPaused(app)
                }
            }
            val hasActiveOrQueued = synchronized(queue) { queue.isNotEmpty() } ||
                    _state.value.batch.any { it.url != url && (it.state == BatchState.DOWNLOADING || it.state == BatchState.PAUSED || it.state == BatchState.QUEUED) }
            if (!hasActiveOrQueued && !_state.value.isDownloading) {
                _state.value = _state.value.copy(
                    isDownloading = false,
                    isProcessing = false,
                    waitingForWifi = false,
                    status = ""
                )
                DownloadNotificationHelper.cancelProgress(app)
            }
        }
    }

    /**
     * Cancels the running download or entire batch. yt-dlp is killed, and whatever finished
     * downloading is still moved into public storage by the coroutine's cleanup path.
     */
    fun cancelAllDownloads() {
        isCancelled = true
        isBatchCancelled = true

        _state.value = _state.value.copy(
            batch = _state.value.batch.map {
                if (it.state == BatchState.PAUSED || it.state == BatchState.QUEUED || it.state == BatchState.DOWNLOADING) {
                    it.copy(state = BatchState.FAILED, error = "Cancelled")
                } else it
            }
        )

        // A paused download has no process left to kill and no run left to tidy up after
        // it, so giving it up has to be done here. Without this the record survived the
        // cancel and the download let itself back in on the next launch.
        if (!_state.value.isDownloading) {
            discardHeldDownload()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().destroyProcessById(processId)
            } catch (_: Exception) { /* process may already be done */ }
        }
    }

    fun cancelDownload() = cancelAllDownloads()

    /**
     * Stops the download in hand and keeps everything it has fetched so far.
     *
     * The engine has no pause of its own, so the process is stopped the same way a cancel
     * stops it. What makes it a pause is what does not happen afterwards: the part file is
     * left alone, nothing is published, and the link goes back to the head of the queue,
     * still written down. Starting again hands yt-dlp the same part file, which it carries
     * on from rather than fetching a second time.
     */
    fun pauseDownload() {
        if (!_state.value.isDownloading || _state.value.isProcessing) return

        isPaused = true
        downloadScope.launch {
            try {
                YoutubeDL.getInstance().destroyProcessById(processId)
            } catch (_: Exception) { /* process may already be done */ }
        }
    }

    /**
     * Starts every paused download again, and the queue with them.
     *
     * Works whether or not something is running: a paused link waits in the queue, stepped
     * over, and resuming it only has to say it is no longer paused. A run in flight takes it
     * up next; otherwise a run starts for it. A pause written down by an earlier session is
     * only on disk, so it is brought back into the queue here.
     */
    fun resumeDownload() {
        val app = HazelApp.instance
        downloadScope.launch {
            val pending = runCatching { DownloadQueueRepository.load(app) }
                .getOrDefault(emptyList())

            // Links already settled, or the one in hand, are not taken from the record
            // again, even if it has not caught up with them yet.
            val inHand = _state.value.batch
                .filter { it.state != BatchState.PAUSED && it.state != BatchState.QUEUED }
                .mapTo(mutableSetOf()) { it.url }
            _state.value.active?.url?.takeIf { _state.value.isDownloading }?.let { inHand += it }

            val anything = synchronized(queue) {
                val held = queue.map { it.copy(paused = false) }
                queue.clear()
                queue.addAll(held)
                pending
                    .filter { saved -> saved.url !in inHand && queue.none { it.url == saved.url } }
                    .forEach { queue.addLast(it.copy(paused = false)) }
                queue.isNotEmpty()
            }
            if (!anything) return@launch

            DownloadNotificationHelper.cancelPaused(app)
            DownloadQueueRepository.clearPaused(app)
            _state.value = _state.value.copy(
                batch = _state.value.batch.map {
                    if (it.state == BatchState.PAUSED) it.copy(state = BatchState.QUEUED) else it
                }
            )
            // Joins the run in flight if there is one, and starts one if there is not.
            runQueue(app, resumed = !_state.value.isDownloading)
        }
    }

    fun resetState() {
        _state.value = DownloadState()
    }

    /** Removes an item from the waiting queue. */
    fun removeQueued(context: Context, url: String) {
        synchronized(queue) {
            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().url == url) {
                    iterator.remove()
                }
            }
        }
        _state.value = _state.value.copy(
            batch = _state.value.batch.filterNot { it.url == url }
        )
        downloadScope.launch {
            DownloadQueueRepository.remove(context, url)
        }
    }

    /**
     * Clears the links waiting their turn. The download running and any paused download are
     * not waiting, and stay: they are on the Running tab, and clearing the waiting list is
     * not a way of cancelling them. Clearing the whole record used to take their entries with
     * it, so a paused download could no longer be resumed and a running one was not picked
     * up again after the app was closed.
     */
    fun clearQueue(context: Context) {
        val cleared = synchronized(queue) {
            val waiting = queue.filter { !it.paused }
            queue.removeAll { !it.paused }
            waiting.map { it.url }
        }.toMutableSet()
        // A waiting link written down by an earlier session is only on disk.
        val activeUrl = _state.value.active?.url?.takeIf { _state.value.isDownloading }
        _state.value = _state.value.copy(
            batch = _state.value.batch.filterNot {
                it.state == BatchState.QUEUED || it.url in cleared
            }
        )
        downloadScope.launch {
            DownloadQueueRepository.load(context)
                .filter { !it.paused && it.url != activeUrl }
                .forEach { cleared += it.url }
            cleared.forEach { DownloadQueueRepository.remove(context, it) }
        }
    }

    /** Retries a failed download, either directly from its queued payload or by fetching anew. */
    fun retryFailed(context: Context, failed: com.hazel.android.data.FailedDownload) {
        downloadScope.launch {
            com.hazel.android.data.FailedDownloadRepository.remove(context, failed.id)
        }
        val queued = DownloadQueueRepository.decodeItem(failed.queuedPayload)
        if (queued != null) {
            startBatch(context, listOf(queued.toPlan()), queued.options, queued.treeUri)
        } else {
            fetchAll(listOf(failed.url))
        }
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
        access: SiteAccess,
        limits: TransferLimits,
        /**
         * The audio stream to mux into a video-only format. Worked out by the caller from
         * the plan, so a download of a source with several soundtracks takes the one the
         * sheet was showing rather than whichever the source listed first.
         */
        audioSelector: String?,
        /**
         * The soundtrack the sheet was set to, as a language tag. Named as well as
         * resolved to an id, so the request can still ask for the right language if the id
         * it was resolved to is not in the list the engine ends up with.
         */
        audioLanguage: String?
    ): YoutubeDLRequest = buildDownloadRequest(url).apply {
        val isVideo = format.hasVideo
        val container = (if (isVideo) options.videoContainer else options.audioContainer).trim()

        addOption("-o", "${downloadDir.absolutePath}/${outputTemplate(options, title, author)}")
        addOption("--no-playlist")
        addOption("--no-mtime")
        // A card is usually FAT or exFAT, which refuses names with characters such as : or ?
        // in them, so a title carrying one would fail only at the very end of the download.
        if (com.hazel.android.util.SdCards.isRemovable(downloadDir)) addOption("--windows-filenames")

        // A ceiling on transfer speed, when one was asked for. Left off entirely otherwise,
        // rather than passed as some very large number, so nothing stands between yt-dlp
        // and the connection in the ordinary case.
        if (limits.speedLimit.isNotBlank()) addOption("--limit-rate", limits.speedLimit)
        // Fragments fetched side by side. Only streams that come in pieces are affected, so
        // it is safe to pass for every source.
        if (limits.concurrentFragments > 1) {
            addOption("--concurrent-fragments", limits.concurrentFragments.toString())
        }
        // Below this speed the links are taken to be throttled and fetched again.
        if (limits.throttledRate.isNotBlank()) addOption("--throttled-rate", limits.throttledRate)
        addOption("--no-check-certificates")
        addOption("--cache-dir", ytDlpCacheDir.absolutePath)

        // Saved sign-ins, which are what make age-restricted and members-only media
        // reachable, under the identity they were collected with. The read that filled the
        // sheet used the same ones, so the format ids it showed are the ids this asks for.
        applySiteAccess(access, url)
        applyAdvanced(url, signedIn = access.cookieFile != null)

        // Whether the two streams have to be muxed back together after the download. The
        // container option decides the result when one was chosen, otherwise mp4 is used
        // because it is the container both streams are most likely to fit.
        var needsMerge = false

        val languageFilter = audioLanguage
            ?.takeIf { it.isNotBlank() }
            ?.let { "ba[language^=$it]" }

        when {
            // Generic rows carry a complete yt-dlp expression already.
            format.isGeneric -> {
                addOption("-f", format.selector)
                // A preferred codec is sorted after what the row itself asks for, so it
                // decides between streams of that quality and never costs resolution or
                // bitrate. yt-dlp reads "vcodec:h264" as h264 first, then anything plainer.
                val codecSort = if (isVideo) {
                    options.videoCodecPreference?.let { "vcodec:${it.sortKey}" }
                } else {
                    options.audioCodecPreference?.let { "acodec:${it.sortKey}" }
                }
                val sort = when {
                    codecSort == null -> format.sort
                    isVideo -> "${format.sort ?: "res"},$codecSort"
                    else -> "${format.sort ?: "abr"},$codecSort"
                }
                sort?.let { addOption("-S", it) }
                needsMerge = isVideo
            }
            // Video-only stream: pair it with the audio track the sheet named, so the
            // file matches what the row advertised. The expression falls back to the best
            // available audio, then to the bare video, so a track that has since gone
            // missing cannot fail the whole download.
            isVideo && !format.hasAudio -> {
                val audioId = audioSelector
                val selector = buildString {
                    if (audioId != null) append("${format.selector}+$audioId/")
                    // The engine's own way of asking for a soundtrack by name, which is
                    // what still gets the right language when the id above has gone.
                    languageFilter?.let { append("${format.selector}+$it/") }
                    append("${format.selector}+bestaudio/${format.selector}")
                }
                // The one line worth having in the log: what the download actually asked
                // for. A soundtrack chosen in the sheet only means anything if it reaches
                // this expression, and this is where that can be seen.
                Log.i("Hazel", "format expression: $selector")
                addOption("-f", selector)
                needsMerge = true
            }
            // An audio download is already the soundtrack that was chosen, so the filter
            // only stands in for it if that stream is no longer offered.
            !isVideo && languageFilter != null ->
                addOption("-f", "${format.selector}/$languageFilter/ba")

            else -> addOption("-f", format.selector)
        }

        if (isVideo) {
            if (container.isNotBlank()) {
                addOption("--merge-output-format", container.lowercase())
            } else if (needsMerge) {
                addOption("--merge-output-format", "mp4")
            }
        } else {
            // Always extracted, even with no conversion asked for: an audio stream usually
            // arrives inside a video container (Opus in WebM), and extracting is what hands
            // it back as the audio file it is (.opus) rather than as that container.
            addOption("-x")
            val targetFormat = container.lowercase()
            if (targetFormat.isNotBlank() && targetFormat !in setOf("default", "webm")) {
                addOption("--audio-format", targetFormat)
            }
            if (options.audioQuality.isNotBlank()) {
                addOption("--audio-quality", options.audioQuality)
            }
        }

        // Where the cover goes. Asking yt-dlp to embed one into a file that cannot hold it
        // fails the whole download, and what the file ends up as is not always what was
        // asked for: a site serving one ready-made file (archive.org's .ogv, a single .flv)
        // is not merged, so the chosen container never applies to it. Rather than guess,
        // the file is steered into a container that can hold the cover.
        val chosen = container.lowercase().takeUnless { it == "default" }.orEmpty()
        val wantsCover = options.embedThumbnail && if (isVideo) {
            // A choice of WebM, AVI or FLV is kept, and goes without a cover.
            chosen !in NO_ARTWORK_CONTAINERS
        } else {
            chosen != "wav"
        }
        if (wantsCover) {
            if (isVideo) {
                // Files already in a container that holds a cover stay as they are; any
                // other container is remuxed into MKV, which takes any codec, so this can
                // never fail on the codecs a site happens to use. A merged download is
                // already in its chosen container, so this changes nothing there.
                addOption("--remux-video", if (chosen == "mkv") "mkv" else COVER_SAFE_VIDEO)
            } else if (chosen.isBlank() || chosen == "webm") {
                // Extraction as it would be without a cover, except for a source file the
                // cover cannot go into (WAV, AIFF, WMA), which is converted instead.
                addOption("--audio-format", COVER_SAFE_AUDIO)
            }
            addOption("--embed-thumbnail")
            if (options.cropThumbnail) {
                // The crop is a filter on the thumbnail's conversion, and yt-dlp skips the
                // conversion of a thumbnail already in the target format: a JPEG cover (most
                // music sites) would come through uncropped. Converting JPEG to PNG and
                // everything else to JPEG sends every cover through the filter.
                addOption("--convert-thumbnails", "jpg>png/jpg")
                addOption("--ppa", "ThumbnailsConvertor:-vf $CROP_TO_SQUARE")
            } else {
                addOption("--convert-thumbnails", "jpg")
            }
        }

        applyChapters(options, isVideo)
        applySponsorBlock(options, isVideo)
        applyOneOff(options)
        if (isVideo) applySubtitles(options)
        applyMetadata(title, author)
        // Music players group cover art by album, and a file with no album tag is filed
        // under its folder's name. Every song saved to the same folder then shows one
        // cover. A source's own album is kept, so an album's tracks still group together;
        // anything else is its own album, named after the track.
        if (!isVideo) addOption("--parse-metadata", "%(album,title)s:%(meta_album)s")

        // The stages this request sets in motion, in the order yt-dlp runs them.
        plannedSteps = buildList {
            add(ProcessingStep.FETCH)
            if (isVideo && needsMerge) add(ProcessingStep.MERGE)
            if (!isVideo) add(ProcessingStep.EXTRACT)
            if (isVideo && wantsCover) add(ProcessingStep.REMUX)
            if (isVideo && options.embedSubs) add(ProcessingStep.SUBTITLES)
            if (options.useSponsorBlock && options.sponsorBlockFilters.isNotEmpty()) add(ProcessingStep.CUT)
            if (title.isNotBlank() || author.isNotBlank() || (isVideo && options.addChapters)) {
                add(ProcessingStep.TAGS)
            }
            if (wantsCover) add(ProcessingStep.COVER)
            if (options.splitByChapters) add(ProcessingStep.SPLIT)
            add(ProcessingStep.SAVE)
        }

        // Last, so an extra argument from the advanced settings can change what was set above.
        applyExtraDownloadArguments()
    }

    /**
     * Chapter handling. Marking with SponsorBlock is paired with embedding, since the marks
     * are written as chapters and are invisible without it.
     */
    private fun YoutubeDLRequest.applyChapters(options: DownloadOptions, isVideo: Boolean) {
        if (isVideo && options.addChapters) {
            // The source's own chapters are embedded either way. SponsorBlock's segments
            // are added as chapters of their own only while SponsorBlock is in use, since
            // marking them means asking its server about every video.
            if (options.useSponsorBlock) addOption("--sponsorblock-mark", "all")
            addOption("--embed-chapters")
        }
        if (options.splitByChapters) {
            addOption("--split-chapters")
            // In the download's own folder, like the file itself. A bare template is taken
            // relative to the process's working directory, which on Android is the root of
            // the system and cannot be written, so splitting failed; and the title keeps two
            // videos' "01 Intro" from overwriting each other.
            addOption(
                "-o",
                "chapter:${downloadDir.absolutePath}/" +
                    limitNameFields("%(title)s - %(section_number)02d %(section_title)s.%(ext)s")
            )
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
    private fun YoutubeDLRequest.applySponsorBlock(options: DownloadOptions, isVideo: Boolean) {
        if (!options.useSponsorBlock) return
        val filters = options.sponsorBlockFilters.filter { it.isNotBlank() }
        if (filters.isNotEmpty()) {
            addOption("--sponsorblock-remove", filters.joinToString(","))
        }
        if (filters.isNotEmpty() || (isVideo && options.addChapters)) {
            addOption("--sponsorblock-api", options.sponsorBlockServer)
        }
    }

    /**
     * What the sheet set for this download alone: a part of it to keep, and how to take a
     * live stream.
     *
     * The part is passed as yt-dlp's `--download-sections "*START-END"`, in seconds. Without
     * `--force-keyframes-at-cuts` the cut snaps to the nearest keyframe (a few tenths of a
     * second out, checked: a 4.5 s cut came out 4.72 s); with it the cut is exact (4.52 s)
     * and the parts around the cut points are encoded again, which takes longer.
     */
    private fun YoutubeDLRequest.applyOneOff(options: DownloadOptions) {
        if (options.hasSection) {
            addOption(
                "--download-sections",
                "*%.3f-%.3f".format(java.util.Locale.ROOT, options.sectionStart, options.sectionEnd)
            )
            if (options.preciseCuts) addOption("--force-keyframes-at-cuts")
        }
        if (options.liveFromStart) addOption("--live-from-start")
        // Checked again every minute at first and then less often, up to every ten, so a
        // premiere hours away is not asked about every few seconds.
        if (options.waitForVideo) addOption("--wait-for-video", "60-600")
    }

    /** Subtitle downloading and embedding, both driven by the same language selector. */
    private fun YoutubeDLRequest.applySubtitles(options: DownloadOptions) {
        // yt-dlp keeps the subtitle files it embedded only when they were also asked for as
        // files, so keeping them after embedding is asking for them as files as well.
        if (options.writeSubs || (options.embedSubs && !options.deleteSubsAfterEmbed)) {
            addOption("--write-subs")
        }
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
     * yt-dlp reads `--parse-metadata` as `FROM:TO`, splitting on the first unescaped colon.
     * Colons inside the user's value are escaped as `\:` so they pass through the split
     * as literal characters. This fixes the previous approach of skipping values that
     * contained colons altogether.
     *
     * The author is also copied into the `artist` metadata slot, which is what
     * `FFmpegMetadataPP` writes as the ID3 / Vorbis / MP4 artist tag. Without this,
     * music platforms that populate `artist` instead of `uploader` (JioSaavn, SoundCloud,
     * Bandcamp) would leave audio files with no artist tag at all.
     */
    private fun YoutubeDLRequest.applyMetadata(title: String, author: String) {
        if (title.isBlank() && author.isBlank()) return

        addOption("--embed-metadata")

        if (title.isNotBlank()) {
            val escaped = title.replace(":", """\:""")
            addOption("--parse-metadata", "$escaped:%(title)s")
        }

        if (author.isNotBlank()) {
            val escaped = author.replace(":", """\:""")
            addOption("--parse-metadata", "$escaped:%(uploader)s")
            // Map uploader → artist tag so audio files get a proper artist ID3/Vorbis tag
            addOption("--parse-metadata", "%(uploader)s:%(artist)s")
        }
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
        val info = _state.value.active ?: _state.value.info

        if (title.isNotBlank() && title != info?.title) {
            template = template.replace("%(title)s", sanitizeForFilename(title))
        }
        if (author.isNotBlank() && author != info?.uploader) {
            val safe = sanitizeForFilename(author, NAME_FIELD_BYTES.getValue("uploader"))
            template = template
                .replace("%(uploader)s", safe)
                .replace("%(channel)s", safe)
        }
        // yt-dlp fills these with the text as the site gave it and does not shorten it, so a
        // post whose caption is its title failed with "File name too long". Its byte limit
        // on a field keeps the name inside the 255 bytes a file name may have, whatever the
        // script.
        return limitNameFields(template)
    }

    /**
     * Gives the free-text fields of an output template yt-dlp's byte limit (`%(title).120B`),
     * leaving any field that already has a format of its own as it was written.
     */
    private fun limitNameFields(template: String): String =
        NAME_FIELD_BYTES.entries.fold(template) { acc, (field, bytes) ->
            acc.replace("%($field)s", "%($field).${bytes}B")
        }

    /**
     * Strips path separators, characters Android's filesystems reject, and the percent sign
     * that would otherwise be read back as another template field, and cuts the rest to
     * [maxBytes].
     *
     * The cut is by bytes, not characters: a file name may be 255 bytes, and a character
     * of Japanese or an emoji takes three or four, so 120 characters of either was already
     * too long. What is left of the 255 is for the extension, the ".part" and format
     * suffixes yt-dlp adds while it works, and a chapter's name when chapters are split.
     */
    private fun sanitizeForFilename(value: String, maxBytes: Int = MAX_NAME_BYTES): String {
        val clean = value.replace(Regex("""[\\/:*?"<>|%]"""), "_").replace(Regex("\\s+"), " ").trim()
        val out = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < clean.length) {
            val cp = clean.codePointAt(i)
            val size = String(Character.toChars(cp)).toByteArray().size
            if (bytes + size > maxBytes) break
            out.appendCodePoint(cp)
            bytes += size
            i += Character.charCount(cp)
        }
        return out.toString().trim().ifBlank { "download" }
    }

    private fun executeYtDlp(request: YoutubeDLRequest) {
        lastNotifiedAt = 0L
        val steps = plannedSteps
        // Whether this run has moved on from transferring to working on the file. Held here
        // and written whole on every line, rather than OR-ed into what the state already
        // said: a flag carried over from the item before, or put back by a write racing
        // this one, otherwise stuck the card on the stage track while bytes were still
        // arriving.
        var postProcessing = false
        _state.value = _state.value.copy(
            processingSteps = steps,
            processingStep = 0,
            isProcessing = false,
            eta = ""
        )
        YtDlpEngine.execute(request, processId) { progress, _, line ->
            refreshFloorFromDisk()
            val percent = progress.coerceIn(0f, 100f).coerceAtLeast(progressFloor * 100f)
            val status = cleanProgressLine(line) ?: _state.value.status
            when {
                isPostProcessing(line) -> postProcessing = true
                // A transfer line means bytes are moving, whatever came before it.
                ProgressText.isTransferLine(line) -> postProcessing = false
            }
            // Only ever forward: a stage the engine announces moves the card to it, and a
            // line for one already passed (a second [Metadata], say) changes nothing. Once
            // the file is being worked on, fetching it is behind it.
            val announced = ProcessingStep.announcedBy(line)?.let { steps.indexOf(it) } ?: -1
            val fetched = if (postProcessing && steps.size > 1) 1 else 0
            val current = _state.value
            _state.value = current.copy(
                progress = percent / 100f,
                totalBytes = if (expectedTotalBytes > 0) expectedTotalBytes
                else parseTotalBytes(line) ?: current.totalBytes,
                eta = if (postProcessing) "" else ProgressText.eta(line) ?: current.eta,
                status = status,
                isProcessing = postProcessing,
                processingStep = maxOf(current.processingStep, announced, fetched)
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
                        mediaTitle = (_state.value.active ?: _state.value.info)?.title.orEmpty(),
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
    private fun finishDownload(
        context: Context,
        plan: DownloadPlan,
        options: DownloadOptions
    ) {
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

        _state.value = _state.value.copy(
            status = "Saving",
            isProcessing = true,
            eta = "",
            processingStep = _state.value.processingSteps.indexOf(ProcessingStep.SAVE)
                .takeIf { it >= 0 } ?: _state.value.processingStep
        )

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

        val isMusic = !plan.format.hasVideo
        val savedPath: String
        val finalDir: File

        if (tree != null && movedToTree) {
            savedPath = com.hazel.android.util.MediaStoreHelper.describeTree(tree)
            finalDir = downloadDir
        } else {
            finalDir = try {
                com.hazel.android.util.MediaStoreHelper.moveToPublicStorage(
                    context, downloadDir, StoragePaths.downloadRelativePath(isAudio = isMusic),
                    isMusic = isMusic
                ) { savedUri = it }
            } catch (_: Exception) {
                // MediaStore move failed; the files stay in app storage and remain accessible.
                downloadDir
            }
            savedPath = StoragePaths.downloadsDisplay(isAudio = isMusic)

            // The picked folder refused the file. It was kept rather than lost, and the
            // user is told where, once per run however many downloads it happened to.
            if (tree != null) {
                val wanted = com.hazel.android.util.MediaStoreHelper.describeTree(tree)
                val savedTo = if (finalDir == downloadDir) finalDir.absolutePath else savedPath
                val title = plan.title.ifBlank { fileName }
                _state.value = _state.value.copy(
                    saveFallback = SaveFallback.adding(_state.value.saveFallback, wanted, savedTo, title)
                )
            }
        }

        com.hazel.android.util.MediaStoreHelper.scanFiles(context, finalDir)
        // The download's own folder is empty once its file has moved, and goes. A file the
        // move could not publish stays in it, and a folder with anything in it is not deleted.
        runCatching { downloadDir.delete() }

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
            title = (_state.value.active ?: _state.value.info)?.title.orEmpty().ifBlank { fileName },
            isVideo = plan.format.hasVideo,
            fileUri = savedUri
        )

        recordHistory(context, fileName, savedPath, savedUri, finalSizeBytes, plan, options)
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
        sizeBytes: Long,
        plan: DownloadPlan,
        options: DownloadOptions
    ) {
        val info = _state.value.active ?: _state.value.info ?: return

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
                    completedAt = System.currentTimeMillis(),
                    // Taken from what was asked for rather than from the file. A container
                    // reports the streams it ended up holding, which after a merge and a
                    // conversion is not the same thing as the quality that was chosen.
                    formatLabel = plan.format.shortLabel,
                    codec = plan.format.codecLabel,
                    bitrateKbps = plan.format.bitrateKbps,
                    embedded = embeddedExtras(options, plan.format.hasVideo)
                )
            )
        }
    }

    /**
     * What was written into the file alongside the media.
     *
     * None of this can be recovered from the file afterwards without opening it and reading
     * its streams, so it is written down at the moment it is true. Chapters are only asked
     * for on a video download, which is why the kind is part of the question.
     */
    private fun embeddedExtras(options: DownloadOptions, isVideo: Boolean): String =
        buildList {
            if (options.embedSubs) add("Subtitles")
            if (options.addChapters && isVideo) add("Chapters")
            if (options.embedThumbnail) add("Cover art")
            if (options.sponsorBlockFilters.isNotEmpty()) add("SponsorBlock")
        }.joinToString(", ")

    /**
     * What fraction of the expected file is sitting in the temp directory right now.
     *
     * Read off the files rather than remembered, because the files are the thing that
     * actually survives: a download interrupted by the app being killed leaves them behind
     * with nobody left to have remembered anything.
     *
     * Everything in the directory counts, not only what is still a part file. A video whose
     * transfer finished is renamed off .part while its audio is still being fetched, and
     * counting part files alone would call that download nearly empty at the moment it is
     * nearly done. The directory holds one download's working files and nothing else, so
     * its whole weight is the honest answer.
     */
    private fun fractionOnDisk(): Float {
        if (expectedTotalBytes <= 0L) return 0f
        val onDisk = try {
            downloadDir.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".ytdl") }
                ?.sumOf { it.length() }
                ?: 0L
        } catch (_: Exception) {
            0L
        }
        if (onDisk <= 0L) return 0f

        // Capped short of the end: a floor of one would leave the bar full for the whole of
        // whatever is left to do, muxing included.
        return (onDisk.toFloat() / expectedTotalBytes).coerceIn(0f, 0.99f)
    }

    /**
     * Re-measures the temp directory, at most once a second.
     *
     * The floor is what keeps the readout honest through a resume and through the switch
     * from the video stream to the audio one, and it can only do that if it keeps up with
     * the bytes landing. A progress line arrives many times a second, though, and listing a
     * directory that often for a number that moves slowly is not worth the reads.
     */
    private fun refreshFloorFromDisk() {
        val now = System.currentTimeMillis()
        if (now - lastFloorCheckAt < FLOOR_INTERVAL_MS) return
        lastFloorCheckAt = now
        progressFloor = maxOf(progressFloor, fractionOnDisk())
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

    /**
     * Whether a connection is one the Wi-Fi only setting permits.
     *
     * Ethernet counts. A device on a dock or an adapter is not on the mobile network and
     * not spending anybody's allowance, which is the whole of what the setting is protecting.
     * A VPN reports the transport it is carried over alongside its own, so a VPN over Wi-Fi
     * is still Wi-Fi here.
     */
    private fun onWifiOrEthernet(caps: android.net.NetworkCapabilities): Boolean =
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)

    /**
     * Holds the run back for want of Wi-Fi, without calling it a failure.
     *
     * Nothing is purged and nothing is dropped: the queue is exactly as it was, and the
     * links keep their queued state, so starting again once the phone is on Wi-Fi picks up
     * where this left off. That is the whole difference from [fail], which exists for a run
     * that got somewhere and stopped.
     */
    private fun holdForWifi(context: Context) {
        _state.value = _state.value.copy(
            isDownloading = false,
            progress = 0f,
            status = "",
            isProcessing = false,
            error = null,
            waitingForWifi = true
        )
        DownloadNotificationHelper.showWaitingForWifi(context)
    }

    private fun fail(context: Context, message: String) {
        // Nothing on disk is touched: this is a run that could not start, and the folder
        // last worked in may be a paused download's, whose partial files are its progress.
        _state.value = _state.value.copy(
            isDownloading = false,
            active = null,
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
        /** Cards stood in while a search runs. */
        const val SEARCH_SKELETONS = 4

        /** The longest a choice waits for its card's formats before going ahead anyway. */
        const val FORMAT_WAIT_MS = 60_000L

        /** The most a title may take of a file name, in UTF-8 bytes; 255 is the limit. */
        const val MAX_NAME_BYTES = 120

        /**
         * Template fields that carry whatever text the site gave, with the bytes each may
         * take. A title and a name together, plus the extension and the suffixes yt-dlp adds
         * while it works, stay inside 255.
         */
        val NAME_FIELD_BYTES = mapOf(
            "title" to MAX_NAME_BYTES,
            "album" to MAX_NAME_BYTES,
            "uploader" to 60,
            "channel" to 60,
            "artist" to 60,
            "section_title" to 60
        )

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

        /** How often the temp directory is weighed while a transfer runs. */
        const val FLOOR_INTERVAL_MS = 1000L

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

        val URL_PATTERN = Regex("^https?://.+", RegexOption.IGNORE_CASE)

        /** Format reads allowed to run together; see [resolveFormats]. */
        private const val FORMAT_READS_AT_ONCE = 3

        /** Containers with no tag atom that can hold cover art. */
        val NO_ARTWORK_CONTAINERS = setOf("webm", "avi", "flv")

        /**
         * yt-dlp remux rules, matched on the file's extension: containers that hold a cover
         * are left alone (a rule whose target is its source is a no-op), anything else is
         * remuxed to MKV, which takes any codec.
         */
        const val COVER_SAFE_VIDEO = "mp4>mp4/mov>mov/mkv>mkv/mkv"

        /**
         * yt-dlp extraction rules, matched on the file's extension. Everything extracts as
         * it would by default ("best"), except the three kinds of file a cover cannot go
         * into: uncompressed WAV and AIFF become lossless FLAC, WMA becomes M4A.
         */
        const val COVER_SAFE_AUDIO = "aiff>flac/wav>flac/wma>m4a/best"

        /**
         * Centre crop to the shorter side, whichever way round the artwork is. yt-dlp splits
         * post-processor arguments the way a shell would, so the double quotes are what keep
         * the single quotes for ffmpeg; without them the commas split the filter and the
         * whole download fails with "Filter not found".
         */
        const val CROP_TO_SQUARE = "crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\""
    }
}

/** Downloads that were saved somewhere other than the folder they were meant for. */
data class SaveFallback(val wanted: String, val savedTo: String, val titles: List<String>) {
    companion object {
        /**
         * [previous] with one more download, so a batch that hit the same problem is told
         * once with every title rather than with a dialog per file. A different folder or
         * destination starts a fresh notice.
         */
        fun adding(previous: SaveFallback?, wanted: String, savedTo: String, title: String): SaveFallback =
            if (previous != null && previous.wanted == wanted && previous.savedTo == savedTo) {
                previous.copy(titles = previous.titles + title)
            } else {
                SaveFallback(wanted = wanted, savedTo = savedTo, titles = listOf(title))
            }
    }
}

/**
 * The working folder of the download for [url]: the same one every time that link is
 * resumed, and never shared with another link. It sits on the SD card when [treeUri] saves
 * to one, so the download's working space is taken from the card, and internally otherwise.
 */
internal fun workDirFor(url: String, treeUri: String = ""): File =
    File(com.hazel.android.util.SdCards.workRootFor(treeUri), workDirName(url))

/** The working folder [url] may have on each storage, for throwing all of them away. */
internal fun workDirsFor(url: String): List<File> =
    com.hazel.android.util.SdCards.workRoots().map { File(it, workDirName(url)) }

internal fun workDirName(url: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
    val key = digest.take(8).joinToString("") { "%02x".format(it) }
    return "dl_$key"
}

/**
 * The transfer settings from More > Downloads, read once when a run starts so every link in
 * it is fetched the same way, as yt-dlp spells each one. A blank rate is left off.
 */
internal data class TransferLimits(
    val speedLimit: String = "",
    val concurrentFragments: Int = 8,
    val throttledRate: String = ""
)
