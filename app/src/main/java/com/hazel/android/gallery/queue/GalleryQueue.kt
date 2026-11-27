package com.hazel.android.gallery.queue

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hazel.android.HazelApp
import com.hazel.android.data.DownloadHistoryRepository
import com.hazel.android.data.FailedDownload
import com.hazel.android.data.FailedDownloadRepository
import com.hazel.android.data.HistoryEntry
import com.hazel.android.data.SaveKind
import com.hazel.android.data.SettingsRepository
import com.hazel.android.data.dataStore
import com.hazel.android.download.DownloadNotificationHelper
import com.hazel.android.download.DownloadService
import com.hazel.android.download.DownloadTurn
import com.hazel.android.gallery.GalleryDlEngine
import com.hazel.android.gallery.GalleryDlException
import com.hazel.android.gallery.GalleryItem
import com.hazel.android.gallery.GalleryPost
import com.hazel.android.util.siteRootOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Gallery downloads waiting their turn, run one at a time on the app's scope and taking the
 * shared [DownloadTurn], so they never overlap a yt-dlp run. Each file saved goes to History,
 * a failure to the Failed tab with its log, and progress to the usual download notification.
 * The list is kept on disk, except in incognito, so a killed app picks it up again.
 */
object GalleryQueue {

    enum class State { WAITING, RUNNING }

    data class Job(val request: GalleryDownloadRequest, val state: State = State.WAITING, val fetched: Int = 0)

    /** Where a file shown on the Gallery tab stands, for its card. */
    sealed interface ItemStatus {
        data object Waiting : ItemStatus
        /** [progress] is null while the total is unknown. */
        data class Running(val progress: Float?) : ItemStatus
        data object Saved : ItemStatus
        data object Failed : ItemStatus
    }

    private const val FINISHED_KEPT = 100
    private val _finished = MutableStateFlow<List<Pair<GalleryDownloadRequest, Boolean>>>(emptyList())
    /** Jobs that ended this session, newest last, with whether they saved; bounded. */
    val finished: StateFlow<List<Pair<GalleryDownloadRequest, Boolean>>> = _finished.asStateFlow()

    private fun finish(request: GalleryDownloadRequest, saved: Boolean) {
        _finished.update { (it + (request to saved)).takeLast(FINISHED_KEPT) }
    }

    /** The newest state of [item] of [post] across [jobs] and [finished]; null when never queued. */
    fun statusOf(
        jobs: List<Job>,
        finished: List<Pair<GalleryDownloadRequest, Boolean>>,
        post: GalleryPost,
        item: GalleryItem
    ): ItemStatus? {
        jobs.lastOrNull { it.request.covers(post, item) }?.let { job ->
            if (job.state == State.WAITING) return ItemStatus.Waiting
            val total = job.request.files?.size ?: job.request.indexes?.size ?: 0
            return ItemStatus.Running(if (total > 0) (job.fetched.toFloat() / total).coerceAtMost(1f) else null)
        }
        val (_, saved) = finished.lastOrNull { it.first.covers(post, item) } ?: return null
        return if (saved) ItemStatus.Saved else ItemStatus.Failed
    }

    private const val TAG = "HazelGallery"
    private val QUEUE_KEY = stringPreferencesKey("gallery_queue")
    private const val PROGRESS_INTERVAL_MS = 500L

    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    private val lock = Any()
    private var runner: kotlinx.coroutines.Job? = null
    private var running: kotlinx.coroutines.Job? = null
    private var runningId: Long? = null
    @Volatile private var restored = false

    /** Adds [request] to the end of the queue and starts the queue if it is idle. */
    suspend fun add(context: Context, request: GalleryDownloadRequest) {
        val app = context.applicationContext
        restore(app)
        _jobs.update { list -> if (list.any { it.request.id == request.id }) list else list + Job(request) }
        persist(app)
        start(app)
    }

    /** Puts back what an earlier session left queued and runs it. Once per process. */
    suspend fun restore(context: Context) {
        if (restored) return
        val app = context.applicationContext
        val saved = withContext(kotlinx.coroutines.Dispatchers.IO) { app.dataStore.data.first()[QUEUE_KEY] }
        synchronized(lock) {
            if (restored) return
            restored = true
        }
        val earlier = decode(saved)
        if (earlier.isEmpty()) return
        _jobs.update { list -> earlier.filter { e -> list.none { it.request.id == e.id } }.map { Job(it) } + list }
        start(app)
    }

    /** Takes a waiting job off the queue, or stops the running one. */
    fun cancel(context: Context, id: Long) {
        val stop = synchronized(lock) { running?.takeIf { runningId == id } }
        if (stop != null) {
            stop.cancel()
            return
        }
        _jobs.update { list -> list.filterNot { it.request.id == id } }
        HazelApp.instance.applicationScope.launch { persist(context.applicationContext) }
    }

    /** Stops the running job, from the notification's Cancel. False when none runs. */
    fun cancelRunning(): Boolean {
        val stop = synchronized(lock) { running } ?: return false
        stop.cancel()
        return true
    }

    val isRunning: Boolean get() = synchronized(lock) { running != null }

    private fun start(app: Context) {
        synchronized(lock) {
            if (runner != null) return
            runner = HazelApp.instance.applicationScope.launch { drain(app) }
        }
    }

    private suspend fun drain(app: Context) {
        while (true) {
            val next = synchronized(lock) {
                _jobs.value.firstOrNull { it.state == State.WAITING }.also { if (it == null) runner = null }
            } ?: break
            val turn = Any()
            try {
                DownloadTurn.acquire(turn)
                runOne(app, next.request)
            } finally {
                DownloadTurn.release(turn)
            }
        }
    }

    private suspend fun runOne(app: Context, request: GalleryDownloadRequest) {
        val title = request.title.ifBlank { hostOf(request.url) }
        val incognito = SettingsRepository.getIncognito(app).first()
        val savedPath = SettingsRepository.getSaveDirs(app).first().of(SaveKind.GALLERY)
            .let { if (it.isCustom) it.label else SaveKind.GALLERY.display }
        setState(request.id, State.RUNNING)
        DownloadService.start(app, title)
        DownloadNotificationHelper.showProgress(app, -1, mediaTitle = title, canPause = false)

        val entries = mutableListOf<HistoryEntry>()
        var firstFile: Pair<Uri, String>? = null
        var lastShown = 0L
        try {
            coroutineScope {
                val work = async {
                    GalleryDlEngine.download(
                        app, request.url, request.indexes, request.options, request.files,
                        caption = request.caption,
                        captionName = title.takeIf { request.save.caption },
                        media = request.save.media,
                        onFetched = { count ->
                            setFetched(request.id, count)
                            val now = System.currentTimeMillis()
                            if (now - lastShown >= PROGRESS_INTERVAL_MS) {
                                lastShown = now
                                val total = request.files?.size ?: request.indexes?.size ?: 0
                                val percent = if (total > 0) (count * 100 / total).coerceAtMost(100) else -1
                                DownloadNotificationHelper.showProgress(
                                    app, percent, statusLine = app.getString(com.hazel.android.R.string.gallery_fetched, count),
                                    mediaTitle = title, canPause = false
                                )
                            }
                        },
                        onSaved = { uri ->
                            // Sidecar text and metadata files are saved but are not downloads of their own.
                            historyEntry(app, request, uri, savedPath)?.let { entry ->
                                if (firstFile == null) firstFile = uri to entry.mimeType
                                entries += entry
                            }
                        }
                    )
                }
                synchronized(lock) { running = work; runningId = request.id }
                work.await()
            }
            withContext(NonCancellable) {
                if (!incognito) DownloadHistoryRepository.recordAll(app, entries.asReversed())
                FailedDownloadRepository.removeByUrl(app, request.url)
                request.retryOf?.let { FailedDownloadRepository.removeByUrl(app, it) }
                finish(request, saved = true)
                DownloadNotificationHelper.showComplete(
                    app, title, isVideo = false, fileUri = firstFile?.first, mimeType = firstFile?.second
                )
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                if (!incognito) DownloadHistoryRepository.recordAll(app, entries.asReversed())
                DownloadNotificationHelper.showCancelled(app)
            }
            // Only this job was stopped; the queue carries on unless the app scope itself ended.
            if (!currentCoroutineContext().isActive) throw e
        } catch (e: Exception) {
            val failure = e as? GalleryDlException
            val reason = app.getString(failure?.kind?.message ?: com.hazel.android.R.string.gallery_failed)
            Log.w(TAG, "Gallery download failed: ${failure?.kind ?: e.javaClass.simpleName}")
            finish(request, saved = false)
            withContext(NonCancellable) {
                if (!incognito) DownloadHistoryRepository.recordAll(app, entries.asReversed())
                // This failure takes the place of the one it retried.
                request.retryOf?.let { FailedDownloadRepository.removeByUrl(app, it) }
                FailedDownloadRepository.record(
                    app,
                    FailedDownload(
                        url = request.url,
                        title = title,
                        author = request.author,
                        thumbnail = request.thumbnail,
                        isVideo = false,
                        errorLog = failure?.log?.ifBlank { failure.detail } ?: (e.message ?: reason),
                        queuedPayload = request.toPayload(),
                        stoppedAt = if (entries.isNotEmpty()) app.getString(com.hazel.android.R.string.gallery_saved_before_failure, entries.size) else null
                    )
                )
                val signIn = when (failure?.kind) {
                    GalleryDlException.Kind.LOGIN_REQUIRED, GalleryDlException.Kind.SIGN_IN_REFUSED -> siteRootOf(request.url)
                    else -> null
                }
                DownloadNotificationHelper.showError(app, reason, title, signInUrl = signIn, recorded = true)
            }
        } finally {
            synchronized(lock) { running = null; runningId = null }
            _jobs.update { list -> list.filterNot { it.request.id == request.id } }
            withContext(NonCancellable) {
                persist(app)
                DownloadService.stop(app)
            }
        }
    }

    private fun historyEntry(app: Context, request: GalleryDownloadRequest, uri: Uri, savedPath: String): HistoryEntry? {
        var name = ""
        var size = 0L
        runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0).orEmpty()
                    size = c.getLong(1)
                }
            }
        }
        val extension = name.substringAfterLast('.', "").lowercase()
        val isVideo = extension in GalleryItem.VIDEO_EXTENSIONS
        if (!isVideo && extension != "zip" && extension !in GalleryItem.IMAGE_EXTENSIONS) return null
        return HistoryEntry(
            id = System.currentTimeMillis(),
            url = request.url,
            title = request.title.ifBlank { name.substringBeforeLast('.') },
            author = request.author,
            thumbnail = null,
            durationSeconds = 0,
            fileName = name,
            fileUri = uri.toString(),
            savedPath = savedPath,
            isVideo = isVideo,
            sizeBytes = size,
            completedAt = System.currentTimeMillis(),
            formatLabel = extension.uppercase(),
            isImage = !isVideo && extension != "zip",
            isGallery = true
        )
    }

    private fun setState(id: Long, state: State) =
        _jobs.update { list -> list.map { if (it.request.id == id) it.copy(state = state) else it } }

    private fun setFetched(id: Long, count: Int) =
        _jobs.update { list -> list.map { if (it.request.id == id) it.copy(fetched = count) else it } }

    private suspend fun persist(app: Context) {
        val waiting = _jobs.value.map { it.request }
        val incognito = SettingsRepository.getIncognito(app).first()
        app.dataStore.edit { prefs ->
            if (waiting.isEmpty() || incognito) prefs.remove(QUEUE_KEY)
            else prefs[QUEUE_KEY] = JSONArray(waiting.map { it.toJson() }).toString()
        }
    }

    private fun decode(raw: String?): List<GalleryDownloadRequest> = runCatching {
        val array = JSONArray(raw ?: return emptyList())
        (0 until array.length()).mapNotNull { GalleryDownloadRequest.fromJson(array.getJSONObject(it).toString()) }
    }.getOrDefault(emptyList())

    private fun hostOf(url: String): String = runCatching { java.net.URI(url).host.removePrefix("www.") }.getOrNull() ?: url
}
