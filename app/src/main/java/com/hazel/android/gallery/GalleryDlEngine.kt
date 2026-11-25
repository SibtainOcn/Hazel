package com.hazel.android.gallery

import android.content.Context
import android.net.Uri
import android.util.Log
import com.hazel.android.data.CookieRepository
import com.hazel.android.data.SaveKind
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.NetworkProxy
import com.hazel.android.download.YtDlpEngine
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
    private const val ERROR_TAIL_BYTES = 8_192L
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
        run(context, url, args) { line ->
            if (output.length + line.length > LISTING_MAX_CHARS) {
                throw GalleryDlException(GalleryDlException.Kind.OTHER, "listing too large")
            }
            output.append(line).append('\n')
        }
        return GalleryListing.parse(output.toString())
    }

    /**
     * Downloads the post at [url] into the gallery save folder: only the files at [indexes]
     * (counted from 1) when given, else all of them. Returns how many were saved.
     */
    suspend fun download(
        context: Context,
        url: String,
        indexes: List<Int>? = null,
        options: GalleryOptions? = null,
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
                if (!indexes.isNullOrEmpty()) { add("--range"); add(indexes.sorted().joinToString(",")) }
                addAll(chosen.toArgs(archive))
            }
            run(context, url, args) { }
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

    private suspend fun run(context: Context, url: String, extra: List<String>, onLine: (String) -> Unit) {
        val app = context.applicationContext
        val access = CookieRepository.accessFor(app, url)
        val preferences = preferenceOptions(app)
        coroutineScope {
            val running = AtomicReference<Process?>()
            val job = coroutineContext[Job]
            val work = async(Dispatchers.IO) {
                val runtime = PythonRuntime.ready(app)
                val command = buildList {
                    add(runtime.python.absolutePath)
                    add(GalleryDlPayload.file(app).absolutePath)
                    addAll(fixedOptions(app, runtime))
                    access.cookieFile?.let { add("--cookies"); add(it.absolutePath) }
                    access.userAgent.takeIf { access.hasCookies && it.isNotBlank() }?.let { add("--user-agent"); add(it) }
                    NetworkProxy.current()?.let { add("--proxy"); add(it.url) }
                    addAll(preferences)
                    addAll(extra)
                    add("--")
                    add(url)
                }
                YtDlpEngine.shared { execute(app, runtime, command, running, job, onLine) }
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
        onLine: (String) -> Unit
    ) {
        val errors = File.createTempFile("gallery-dl", ".log", context.cacheDir)
        try {
            val builder = ProcessBuilder(command).redirectError(errors)
            runtime.applyTo(builder.environment(), importPath = listOf(runtime.ytDlp).filter { it.isFile })
            val process = builder.start()
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
                    val last = lastError(errors)
                    Log.w(TAG, "gallery-dl exited with $code: $last")
                    throw GalleryDlException(GalleryDlException.classify(last), last)
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
            errors.delete()
        }
    }

    /** gallery-dl's last error line, without its `[site][error]` prefix. */
    private fun lastError(log: File): String {
        val text = runCatching {
            log.inputStream().use { input ->
                input.skip((log.length() - ERROR_TAIL_BYTES).coerceAtLeast(0))
                input.readBytes().decodeToString()
            }
        }.getOrDefault("")
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val line = lines.lastOrNull { "error" in it.lowercase() } ?: lines.lastOrNull().orEmpty()
        return line.replace(ERROR_PREFIX, "").trim()
    }

    private val ERROR_PREFIX = Regex("""^(\[[^\]]+\])+\s*""")
}
