package com.hazel.android.download

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hazel.android.HazelApp
import com.hazel.android.data.CookieRepository
import com.hazel.android.data.DownloadHistoryRepository
import com.hazel.android.data.HistoryEntry
import com.hazel.android.data.SettingsRepository
import com.hazel.android.util.StoragePaths
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private var fetchJob: Job? = null
    private var downloadJob: Job? = null
    private val processId = "hazel_download"
    @Volatile private var isCancelled = false

    private var downloadContext: Context? = null
    private var downloadIsVideo: Boolean = true

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

    // ── Metadata ──

    /** Resolves whatever is in the field. */
    fun fetchInfo() {
        val url = _state.value.url.trim()
        if (url.isBlank()) return
        fetchAll(listOf(url))
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
    fun fetchAll(urls: List<String>) {
        val targets = urls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (targets.isEmpty() || _state.value.isFetching) return

        val valid = targets.filter { URL_PATTERN.matches(it) }
        if (valid.isEmpty()) {
            _state.value = _state.value.copy(
                error = "Invalid URL, must start with http:// or https://"
            )
            return
        }

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

            // Every link goes to one yt-dlp run rather than one run each, which is what
            // makes reading a set take about as long as reading its slowest member.
            val resolved: List<MediaInfo>
            var lastFailure = ""

            try {
                resolved = if (valid.size == 1) {
                    listOf(probeWithRetry(valid.first(), cookies, fetchMode, forceIpv4))
                } else {
                    _state.value = _state.value.copy(
                        fetchProgress = "Reading ${valid.size} links"
                    )
                    MediaProbe.probeAll(
                        valid, ytDlpCacheDir, cookies, fetchMode, forceIpv4
                    )
                }
            } catch (_: CancellationException) {
                _state.value = _state.value.copy(isFetching = false, fetchProgress = "")
                return@launch
            } catch (e: Exception) {
                lastFailure = e.message?.trim().orEmpty()
                _state.value = _state.value.copy(
                    isFetching = false,
                    fetchProgress = "",
                    errorLog = lastFailure.ifBlank { "Could not read this link" }
                )
                return@launch
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
     * Each item keeps its own entry in [DownloadState.batch], so one failure is recorded
     * against that link and the rest of the batch still runs.
     */
    fun startBatch(
        context: Context,
        plans: List<DownloadPlan>,
        options: DownloadOptions,
        treeUri: String = ""
    ) {
        if (_state.value.isDownloading || plans.isEmpty()) return

        com.hazel.android.util.PermissionHelper.ensureNotificationPermission(context)

        isBatchCancelled = false
        downloadContext = context
        downloadTreeUri = treeUri

        _state.value = _state.value.copy(
            isDownloading = true,
            isComplete = false,
            progress = 0f,
            totalBytes = 0L,
            status = "Starting download",
            error = null,
            batch = plans.map { BatchItem(url = it.info.url, title = it.title) }
        )

        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
            val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            if (caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) {
                fail(context, "No internet connection")
                return@launch
            }

            val cookies = CookieRepository.activeCookieFile(context)

            for (plan in plans) {
                if (isBatchCancelled) break

                isCancelled = false
                downloadIsVideo = plan.format.hasVideo
                markBatch(plan.info.url, BatchState.DOWNLOADING)

                _state.value = _state.value.copy(
                    info = plan.info,
                    progress = 0f,
                    totalBytes = 0L,
                    status = "Starting download"
                )
                DownloadNotificationHelper.showProgress(context, 0, "Starting download", plan.title)

                try {
                    if (!downloadDir.exists()) downloadDir.mkdirs()

                    executeYtDlp(
                        buildRequest(
                            plan.info.url, plan.format, options, plan.title, plan.author, cookies
                        )
                    )

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
            error = when {
                done == 0 && failed > 0 ->
                    current.batch.firstOrNull { it.error != null }?.error ?: "Download failed"
                failed > 0 -> "$failed of ${current.batch.size} failed"
                else -> null
            }
        )

        if (isBatchCancelled && done == 0) {
            DownloadNotificationHelper.showCancelled(context)
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

    private fun buildRequest(
        url: String,
        format: MediaFormat,
        options: DownloadOptions,
        title: String,
        author: String,
        cookieFile: File?
    ): YoutubeDLRequest = YoutubeDLRequest(url).apply {
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
        YoutubeDL.getInstance().execute(request, processId) { progress, _, line ->
            val percent = progress.coerceIn(0f, 100f)
            val status = cleanProgressLine(line) ?: _state.value.status
            _state.value = _state.value.copy(
                progress = percent / 100f,
                totalBytes = parseTotalBytes(line) ?: _state.value.totalBytes,
                status = status
            )
            if (percent.toInt() % 2 == 0) {
                downloadContext?.let {
                    DownloadNotificationHelper.showProgress(
                        it, percent.toInt(), status, _state.value.info?.title.orEmpty()
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

        _state.value = _state.value.copy(status = "Saving")

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
            fileName = fileName,
            savedPath = savedPath
        )

        DownloadNotificationHelper.showComplete(
            context, fileName, isVideo = downloadIsVideo, fileUri = savedUri
        )

        recordHistory(context, fileName, savedPath, savedUri)
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
        savedUri: android.net.Uri?
    ) {
        val info = _state.value.info ?: return

        viewModelScope.launch {
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
                    sizeBytes = _state.value.totalBytes,
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
     */
    private fun cleanProgressLine(line: String): String? =
        line.replace(LEADING_TAG_PATTERN, "")
            .trim()
            .takeIf { it.isNotEmpty() }

    /**
     * Reads the transfer size out of a yt-dlp progress line, which looks like
     * `[download]  42.5% of ~  40.20MiB at 2.35MiB/s ETA 00:10`.
     */
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
