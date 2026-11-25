package com.hazel.android.gallery

import android.content.Context
import android.net.Uri
import android.util.Log
import com.hazel.android.data.CookieRepository
import com.hazel.android.data.SaveKind
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.NetworkProxy
import com.hazel.android.download.YtDlpEngine
import com.hazel.android.update.GalleryDlUpdater
import com.hazel.android.util.MediaStoreHelper
import com.hazel.android.util.StoragePaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The one door every gallery-dl run goes through.
 *
 * Runs hold the yt-dlp engine's shared lock: gallery-dl imports yt-dlp from the live zip
 * for the videos it hands over, and that zip is only replaced while nothing reads it.
 * Cancelling the calling coroutine ends the process and any ffmpeg it started.
 */
object GalleryDlEngine {

    private const val TAG = "HazelGallery"
    private const val LOG_TAIL_CHARS = 16_000
    private const val LISTING_MAX_CHARS = 16 * 1024 * 1024
    private const val SKIPPED_PREFIX = "# "
    private const val ARCHIVE_FILE = "gallery-dl-archive.sqlite3"

    /** The record of posts already saved, used by Skip already downloaded. */
    fun archiveFile(context: Context): File = File(context.filesDir, ARCHIVE_FILE)

    /** Forgets every saved post. Blocking; true when nothing is left. */
    fun clearArchive(context: Context): Boolean {
        val main = archiveFile(context)
        return listOf(main, File(main.path + "-journal"), File(main.path + "-wal"), File(main.path + "-shm"))
            .all { !it.exists() || it.delete() }
    }

    /**
     * Every file of the post at [url]. With [postLimit], the link is a search, profile or tag
     * page, and the files of its first [postLimit] posts are read.
     */
    suspend fun list(context: Context, url: String, postLimit: Int? = null): GalleryListing {
        val output = StringBuilder()
        val args = if (postLimit == null) listOf("--dump-json")
        else listOf("--resolve-json", "--post-range", "1-$postLimit", "--child-range", "1-$postLimit")
        run(context, listOf(url), args) { line ->
            if (output.length + line.length > LISTING_MAX_CHARS) {
                throw GalleryDlException(GalleryDlException.Kind.OTHER, "listing too large")
            }
            output.append(line).append('\n')
        }
        // A download usually follows a read: have gallery-dl loaded for it.
        GallerySpare.warm(context)
        val listing = GalleryListing.parse(output.toString())
        val error = listing.error
        if (error != null && listing.isEmpty) throw readError(context, url, error)
        return listing
    }

    /**
     * What a read that stopped on [error] means. Sites that turn away anonymous readers say so
     * in many ways (X's "Unavailable", Reddit's "blocked by network security"): without a
     * sign-in for the site, anything that is not plainly gone or unsupported asks for one.
     */
    private suspend fun readError(context: Context, url: String, error: Pair<String, String>): GalleryDlException {
        val (name, message) = error
        val detail = "$name: $message"
        var kind = GalleryDlException.classifyError(name, message)
        if (kind == GalleryDlException.Kind.OTHER && !CookieRepository.signsIn(context.applicationContext, listOf(url)).first()) {
            kind = GalleryDlException.Kind.LOGIN_REQUIRED
        }
        return GalleryDlException(kind, detail, log = detail)
    }

    /**
     * Downloads the post at [url] into the gallery save folder: only the files at [indexes]
     * (counted from 1) when given, else all of them; with [files], those file addresses instead. [onFetched] gets the count of files fetched
     * so far, [onSaved] each file once it is in the save folder. Returns how many were saved.
     */
    suspend fun download(
        context: Context,
        url: String,
        indexes: List<Int>? = null,
        options: GalleryOptions? = null,
        files: List<String>? = null,
        onFetched: (Int) -> Unit = {},
        onSaved: (Uri) -> Unit = {}
    ): Int {
        val chosen = options ?: SettingsRepository.getGalleryOptions(context).first()
        val incognito = SettingsRepository.getIncognito(context).first()
        val archive = archiveFile(context).takeUnless { incognito }
        val work = File(StoragePaths.tempDownloads, "gallery-${UUID.randomUUID()}")
        try {
            if (!work.mkdirs()) throw GalleryDlException(GalleryDlException.Kind.SETUP, "no work folder")
            val args = buildList {
                add("--directory"); add(work.absolutePath)
                add("--no-part")
                if (files.isNullOrEmpty() && !indexes.isNullOrEmpty()) { add("--range"); add(indexes.sorted().joinToString(",")) }
                addAll(chosen.toArgs(archive))
                if (!files.isNullOrEmpty() && chosen.safeTemplate == null) {
                    add("--option"); add("extractor.directlink.filename={filename}.{extension}")
                }
            }
            var fetched = 0
            run(context, files?.takeIf { it.isNotEmpty() } ?: listOf(url), args) { onFetched(++fetched) }
            val target = SettingsRepository.getSaveDirs(context).first().of(SaveKind.GALLERY)
            return withContext(Dispatchers.IO) {
                var saved = 0
                val count: (Uri) -> Unit = { saved++; onSaved(it) }
                val inTree = target.isCustom && runCatching {
                    MediaStoreHelper.moveToTree(context, work, Uri.parse(target.uri), onMoved = count)
                }.onFailure { Log.w(TAG, "Picked gallery folder failed, using the built-in one", it) }
                    .getOrDefault(false)
                if (!inTree) {
                    MediaStoreHelper.moveToPublicStorage(context, work, StoragePaths.DOWNLOAD_GALLERY_RELATIVE_PATH, onMoved = count)
                }
                saved
            }
        } finally {
            withContext(Dispatchers.IO) { work.deleteRecursively() }
        }
    }

    private suspend fun run(context: Context, urls: List<String>, extra: List<String>, onLine: (String) -> Unit) {
        val app = context.applicationContext
        val access = CookieRepository.accessFor(app, urls.first())
        val preferences = preferenceOptions(app)
        coroutineScope {
            val running = AtomicReference<Process?>()
            val job = coroutineContext[Job]
            val work = async(Dispatchers.IO) {
                val runtime = PythonRuntime.ready(app)
                val command = buildList {
                    add(runtime.python.absolutePath)
                    add("-m"); add("gallery_dl")
                    addAll(fixedOptions(app, runtime))
                    access.cookieFile?.let { add("--cookies"); add(it.absolutePath) }
                    access.userAgent.takeIf { access.hasCookies && it.isNotBlank() }?.let { add("--user-agent"); add(it) }
                    NetworkProxy.current()?.let { add("--proxy"); add(it.url) }
                    addAll(preferences)
                    addAll(extra)
                    add("--")
                    addAll(urls)
                }
                val secrets = listOfNotNull(access.cookieFile?.absolutePath)
                try {
                    YtDlpEngine.shared { execute(app, runtime, command, running, job, secrets, onLine) }
                } finally {
                    // A gallery-dl update fetched during this run goes live once nothing runs.
                    runCatching { GalleryDlUpdater.applyStagedUpdate(app) }
                }
            }
            try {
                work.await()
            } catch (e: CancellationException) {
                running.get()?.destroyForcibly()
                YtDlpEngine.killStrayFfmpeg()
                throw e
            }
        }
    }

    private suspend fun preferenceOptions(context: Context): List<String> = buildList {
        val videos = GalleryItem.VIDEO_EXTENSIONS.joinToString(",") { "'$it'" }
        when (SettingsRepository.getGalleryShow(context).first()) {
            GalleryShow.ALL -> Unit
            GalleryShow.IMAGES -> { add("--filter"); add("extension not in ($videos)") }
            GalleryShow.VIDEOS -> { add("--filter"); add("extension in ($videos)") }
        }
        val reposts = !SettingsRepository.getGallerySkipReposts(context).first()
        add("--option"); add("extractor.twitter.retweets=$reposts")
        add("--option"); add("extractor.bluesky.reposts=$reposts")
    }

    /** The downloaded gallery-dl update first, then the bundled one, then yt-dlp for post videos. */
    private fun importPath(context: Context, runtime: PythonRuntime, wheel: File? = GalleryDlUpdater.liveWheel(context)): List<File> =
        listOfNotNull(wheel, GalleryDlPayload.file(context), runtime.ytDlp.takeIf { it.isFile })

    /**
     * Whether gallery-dl from [wheel] starts here and reports [version]. Runs outside the engine
     * lock, so only call it while that lock is held exclusively, as an update swap does.
     */
    internal fun probe(context: Context, wheel: File, version: String): Boolean = runCatching {
        val runtime = PythonRuntime.ready(context)
        val builder = ProcessBuilder(runtime.python.absolutePath, "-c", PROBE).redirectErrorStream(true)
        runtime.applyTo(builder.environment(), importPath = importPath(context, runtime, wheel))
        val process = builder.start()
        try {
            process.outputStream.close()
            if (!process.waitFor(PROBE_TIMEOUT_S, TimeUnit.SECONDS)) return@runCatching false
            val output = process.inputStream.bufferedReader().readText()
            val reported = output.lines().map { it.trim() }.lastOrNull { it.isNotEmpty() }
            if (reported != version) Log.w(TAG, "gallery-dl probe: ${output.takeLast(400)}")
            process.exitValue() == 0 && reported == version
        } finally {
            process.destroyForcibly()
        }
    }.getOrDefault(false)

    /** What a spare gallery-dl must have been started with to serve a run now. */
    internal fun importKey(context: Context): String =
        importPath(context, PythonRuntime.ready(context)).joinToString(":") { "${it.absolutePath}@${it.lastModified()}" }

    /** A Python process running [boot] with the environment a run gets, its stderr in [stderr]. */
    internal fun spareProcess(context: Context, boot: String, stderr: File): Process {
        val runtime = PythonRuntime.ready(context)
        val builder = ProcessBuilder(runtime.python.absolutePath, "-c", boot).redirectError(stderr)
        runtime.applyTo(builder.environment(), importPath = importPath(context, runtime))
        return builder.start()
    }

    private const val PROBE ="import gallery_dl, gallery_dl.extractor, gallery_dl.job, gallery_dl.version as v; print(v.__version__)"
    private const val PROBE_TIMEOUT_S = 60L

    /** `python -m gallery_dl` at the head of every run's command; a spare gets what follows. */
    private const val COMMAND_PREFIX = 3

    private fun fixedOptions(context: Context, runtime: PythonRuntime): List<String> = listOf(
        "--config-ignore",
        "--cache-file", File(context.cacheDir, "gallery-dl.sqlite3").absolutePath,
        "--option", "downloader.ytdl.module=yt_dlp",
        "--option", "downloader.ytdl.raw-options={\"ffmpeg_location\": \"${runtime.ffmpeg.absolutePath}\"}"
    )

    private fun execute(
        context: Context,
        runtime: PythonRuntime,
        command: List<String>,
        running: AtomicReference<Process?>,
        job: Job?,
        secrets: List<String>,
        onLine: (String) -> Unit
    ) {
        val logs = GalleryLogs.dir(context)
        var raw: File? = null
        try {
            // A warmed-up gallery-dl takes the run when it fits; anything else starts one cold.
            val spare = GallerySpare.take(importKey(context), command.drop(COMMAND_PREFIX))
            val process = if (spare != null) {
                raw = spare.second
                spare.first
            } else {
                raw = GalleryLogs.rawFile(logs)
                val builder = ProcessBuilder(command).redirectError(raw)
                runtime.applyTo(builder.environment(), importPath = importPath(context, runtime))
                builder.start()
            }
            running.set(process)
            try {
                if (job?.isActive == false) throw CancellationException()
                process.outputStream.close()
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> if (!line.startsWith(SKIPPED_PREFIX)) onLine(line) }
                }
                val code = process.waitFor()
                if (job?.isActive == false) throw CancellationException()
                if (code != 0) {
                    val log = GalleryLogs.keep(logs, raw, secrets)
                    val last = lastError(log)
                    Log.w(TAG, "gallery-dl exited with $code: $last")
                    throw GalleryDlException(GalleryDlException.classify(last), last, log = log.takeLast(LOG_TAIL_CHARS))
                }
            } finally {
                running.set(null)
                process.destroy()
            }
        } catch (e: GalleryDlException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw GalleryDlException(GalleryDlException.Kind.SETUP, e.message ?: e.javaClass.simpleName, e)
        } finally {
            raw?.takeIf { it.exists() }?.let { runCatching { GalleryLogs.keep(logs, it, secrets) } }
        }
    }

    /** gallery-dl's last error line, without its `[site][error]` prefix. */
    private fun lastError(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val line = lines.lastOrNull { "error" in it.lowercase() } ?: lines.lastOrNull().orEmpty()
        return line.replace(ERROR_PREFIX, "").trim()
    }

    private val ERROR_PREFIX = Regex("""^(\[[^\]]+\])+\s*""")
}
