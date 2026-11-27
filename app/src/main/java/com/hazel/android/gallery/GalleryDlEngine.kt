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
import com.hazel.android.util.CaptionMetadata
import com.hazel.android.util.MediaStoreHelper
import com.hazel.android.util.StoragePaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
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
        val output = GalleryLinkOutput(listOf(url), LISTING_MAX_CHARS)
        run(context, listOf(url), listArgs(postLimit)) { line -> output.feed(line) }
        // A download usually follows a read: have gallery-dl loaded for it.
        GallerySpare.warm(context)
        return read(context, url, output.part(0)).getOrThrow()
    }

    /**
     * Reads several links, one gallery-dl run per site (each with that site's sign-in), and
     * hands [onLink] each link's listing or failure in turn, as soon as its part is read.
     */
    suspend fun listEach(
        context: Context,
        urls: List<String>,
        postLimit: Int? = null,
        onLink: suspend (url: String, result: Result<GalleryListing>) -> Unit
    ) {
        urls.distinct().groupBy { Uri.parse(it).host.orEmpty().lowercase().removePrefix("www.") }.values.forEach { group ->
            coroutineScope {
                val output = GalleryLinkOutput(group, LISTING_MAX_CHARS)
                val closed = Channel<Int>(Channel.UNLIMITED)
                val reading = async {
                    try {
                        run(context, group, listArgs(postLimit)) { line -> output.feed(line)?.let(closed::trySend) }
                        null
                    } catch (e: GalleryDlException) {
                        e
                    } finally {
                        closed.close()
                    }
                }
                val handed = BooleanArray(group.size)
                for (index in closed) {
                    handed[index] = true
                    onLink(group[index], read(context, group[index], output.part(index)))
                }
                val failure = reading.await()
                group.indices.filterNot { handed[it] }.forEach { index ->
                    onLink(group[index], read(context, group[index], output.part(index), failure))
                }
            }
        }
        GallerySpare.warm(context)
    }

    private fun listArgs(postLimit: Int?): List<String> =
        if (postLimit == null) listOf("--dump-json")
        else listOf("--resolve-json", "--post-range", "1-$postLimit", "--child-range", "1-$postLimit")

    /**
     * The listing in [text], read off the main thread. Without one, the run's [failure] (an
     * unreadable link makes the whole run exit with an error) or else an unsupported link.
     */
    private suspend fun read(
        context: Context,
        url: String,
        text: String?,
        failure: GalleryDlException? = null
    ): Result<GalleryListing> = try {
        if (text == null) throw failure ?: GalleryDlException(GalleryDlException.Kind.UNSUPPORTED, "no listing")
        val listing = withContext(Dispatchers.Default) { GalleryListing.parse(text) }
        val error = listing.error
        if (error != null && listing.isEmpty) throw readError(context, url, error)
        Result.success(listing)
    } catch (e: GalleryDlException) {
        Result.failure(e)
    }

    /**
     * What a read that stopped on [error] means. Sites that turn away anonymous readers say so
     * in many ways (X's "Unavailable", Reddit's "blocked by network security"): without a
     * sign-in for the site, anything that is not plainly gone or unsupported asks for one. With
     * one, a refusal asks to sign in again.
     */
    private suspend fun readError(context: Context, url: String, error: Pair<String, String>): GalleryDlException {
        val (name, message) = error
        val detail = "$name: $message"
        val signedIn = CookieRepository.signsIn(context.applicationContext, listOf(url)).first()
        val kind = GalleryDlException.withSignIn(GalleryDlException.classifyError(name, message), detail, signedIn)
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
        /** The post's caption: written into the images when the options ask, and saved as [captionName].txt when that is given. */
        caption: String = "",
        captionName: String? = null,
        /** False when only the caption is saved. */
        media: Boolean = true,
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
            if (media) run(context, files?.takeIf { it.isNotEmpty() } ?: listOf(url), args) { onFetched(++fetched) }
            withContext(Dispatchers.IO) {
                if (chosen.embedCaption && caption.isNotBlank()) {
                    work.walkTopDown().filter { it.isFile && CaptionMetadata.canEmbed(it) }.forEach { file ->
                        runCatching { CaptionMetadata.embed(file, caption) }
                            .onFailure { Log.w(TAG, "Caption not written into ${file.extension}", it) }
                    }
                }
                if (captionName != null && caption.isNotBlank()) File(work, captionFileName(captionName)).writeText(caption)
            }
            return moveToSaveFolder(context, work, onSaved)
        } finally {
            withContext(Dispatchers.IO) { work.deleteRecursively() }
        }
    }

    /** A text file name for [title]: no path characters, a short readable stem. */
    internal fun captionFileName(title: String): String =
        title.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").trim().take(80).ifBlank { "caption" } + ".txt"

    /** Saves [text] as [name].txt in the gallery save folder, as a download would. True when it landed. */
    suspend fun saveCaptionText(context: Context, name: String, text: String): Boolean {
        val work = File(StoragePaths.tempDownloads, "caption-${UUID.randomUUID()}")
        return try {
            withContext(Dispatchers.IO) {
                if (!work.mkdirs()) return@withContext false
                File(work, captionFileName(name)).writeText(text)
                true
            } && moveToSaveFolder(context, work) {} > 0
        } catch (e: Exception) {
            Log.w(TAG, "Caption text not saved", e)
            false
        } finally {
            withContext(Dispatchers.IO) { work.deleteRecursively() }
        }
    }

    /** Moves everything in [work] into the gallery save folder; [onSaved] gets each file. Returns how many. */
    private suspend fun moveToSaveFolder(context: Context, work: File, onSaved: (Uri) -> Unit): Int {
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
                    add("-c"); add(RUN)
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
                } catch (e: GalleryDlException) {
                    if (!access.hasCookies) throw e
                    val kind = GalleryDlException.withSignIn(e.kind, e.detail, signedIn = true)
                    throw if (kind == e.kind) e else GalleryDlException(kind, e.detail, e, e.log)
                } finally {
                    // A gallery-dl update fetched during this run goes live once nothing runs.
                    runCatching { GalleryDlUpdater.applyStagedUpdate(app) }
                }
            }
            try {
                work.await()
            } catch (e: CancellationException) {
                running.get()?.let(com.hazel.android.util.Processes::kill)
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
        add("--option"); add("extractor.twitter.quoted=${SettingsRepository.getGalleryXQuotes(context).first()}")
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
            if (!com.hazel.android.util.Processes.waitFor(process, PROBE_TIMEOUT_S, TimeUnit.SECONDS)) return@runCatching false
            val output = process.inputStream.bufferedReader().readText()
            val reported = output.lines().map { it.trim() }.lastOrNull { it.isNotEmpty() }
            if (reported != version) Log.w(TAG, "gallery-dl probe: ${output.takeLast(400)}")
            process.exitValue() == 0 && reported == version
        } finally {
            com.hazel.android.util.Processes.kill(process)
        }
    }.getOrDefault(false)

    /** What a spare gallery-dl must have been started with to serve a run now. */
    internal fun importKey(context: Context): String =
        importPath(context, PythonRuntime.ready(context)).joinToString(":") { "${it.absolutePath}@${it.lastModified()}" }

    /** A Python process running [boot] with the environment a run gets, its stderr in [stderr]. */
    internal fun spareProcess(context: Context, boot: String, stderr: File): Process {
        val runtime = PythonRuntime.ready(context)
        val builder = ProcessBuilder(runtime.python.absolutePath, "-c", boot)
        runtime.applyTo(builder.environment(), importPath = importPath(context, runtime))
        return com.hazel.android.util.Processes.startWithErrorTo(builder, stderr)
    }

    private const val PROBE ="import gallery_dl, gallery_dl.extractor, gallery_dl.job, gallery_dl.version as v; print(v.__version__)"
    private const val PROBE_TIMEOUT_S = 60L

    /** `python -c RUN` at the head of every run's command; a spare gets what follows. */
    private const val COMMAND_PREFIX = 3
    private const val RUN = GalleryLinkOutput.RUN_PREFIX + "sys.exit(gallery_dl.main())"

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
                val builder = ProcessBuilder(command)
                runtime.applyTo(builder.environment(), importPath = importPath(context, runtime))
                com.hazel.android.util.Processes.startWithErrorTo(builder, raw)
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
