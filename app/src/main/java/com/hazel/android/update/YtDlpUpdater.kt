package com.hazel.android.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.storage.StorageManager
import android.util.Log
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.YtDlpEngine
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.hazel.android.download.NetworkProxy.throughProxy
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Updates the yt-dlp binary from the yt-dlp GitHub releases, independently of the
 * app's own release cycle.
 *
 * A new binary is never written over the live one. It is downloaded next to it, checked,
 * and then renamed into place in a single step, and only while no yt-dlp run is in flight
 * (see [YtDlpEngine]). A binary fetched while something is downloading waits on disk and
 * goes live the moment the engine is free, or at the next launch.
 */
object YtDlpUpdater {

    /** Release channels published by the yt-dlp project. */
    enum class Channel(
        val label: String,
        val ytdlChannel: YoutubeDL.UpdateChannel,
        val repo: String
    ) {
        STABLE("Stable", YoutubeDL.UpdateChannel.STABLE, "yt-dlp/yt-dlp"),
        NIGHTLY("Nightly", YoutubeDL.UpdateChannel.NIGHTLY, "yt-dlp/yt-dlp-nightly-builds"),
        MASTER("Master", YoutubeDL.UpdateChannel.MASTER, "yt-dlp/yt-dlp-master-builds");

        val releasesUrl: String get() = "https://github.com/$repo/releases"

        companion object {
            fun fromLabel(label: String?): Channel =
                entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: STABLE
        }
    }

    /** Latest release of a [Channel], as reported by the GitHub API. */
    data class ReleaseInfo(
        val version: String,
        val channel: Channel,
        val binarySize: Long
    )

    private const val BINARY_ASSET = "yt-dlp"

    /** A real yt-dlp build is a few megabytes; anything far smaller is an error page. */
    private const val MIN_BINARY_BYTES = 500_000L

    /** Keys youtubedl-android records the installed release under. */
    private const val LIBRARY_PREFS = "youtubedl-android"
    private const val VERSION_KEY = "dlpVersion"
    private const val VERSION_NAME_KEY = "dlpVersionName"

    private val isUpdating = AtomicBoolean(false)

    /** Last `--version` answer, keyed to the size and time of the file it came from. */
    @Volatile
    private var knownVersion: Pair<String, String>? = null

    private val client = OkHttpClient.Builder()
        .throughProxy()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * For the binary itself. The checks above fail fast so the screen answers quickly; the
     * download is allowed to be slow, because the phones that update this way are often on a
     * weak mobile connection, where ten silent seconds is routine rather than a failure.
     */
    private val downloadClient = client.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun engineDir(context: Context) = File(context.noBackupFilesDir, YoutubeDL.baseName)
    private fun liveDir(context: Context) = File(engineDir(context), YoutubeDL.ytdlpDirName)
    private fun liveBinary(context: Context) = File(liveDir(context), YoutubeDL.ytdlpBin)

    /** A downloaded build waiting to go live, and the release tag it came from. */
    private fun stagedBinary(context: Context) = File(engineDir(context), "yt-dlp.staged")
    private fun stagedTag(context: Context) = File(engineDir(context), "yt-dlp.staged.tag")

    /** The build a swap replaced, kept until the new one has started. */
    private fun previousBinary(context: Context) = File(engineDir(context), "yt-dlp.previous")

    /** Tag of a build that would not start here, so auto-update skips it. */
    private fun rejectedTag(context: Context) = File(engineDir(context), "yt-dlp.rejected.tag")

    /**
     * Whether the live binary starts on this device's Python; null when the library itself
     * won't start, or when the check ran out of time. Bypasses [YtDlpEngine] so it can run
     * while a swap holds the engine.
     *
     * A start that fails with no error text, or with the system's words for lacking memory,
     * is tried once more after a pause: on a phone with little free memory the process can
     * be stopped by the system while Python is still reading yt-dlp's thousands of modules,
     * which says nothing about whether the build itself is sound. [onFailure] receives what
     * the last failed start printed, for telling the person why a build was refused.
     */
    private fun runsOnDevice(context: Context, onFailure: (String?) -> Unit = {}): Boolean? {
        try {
            YoutubeDL.getInstance().init(context)
        } catch (e: Exception) {
            Log.w("Hazel", "yt-dlp health check skipped, the engine did not start: ${e.message}")
            return null
        }
        for (attempt in 1..START_ATTEMPTS) {
            val processId = "ytdlp-health-${System.nanoTime()}"
            val run = healthExecutor.submit<Boolean> {
                val request = YoutubeDLRequest(emptyList<String>()).addOption("--version")
                YoutubeDL.getInstance().execute(request, processId).out.isNotBlank()
            }
            try {
                return run.get(HEALTH_CHECK_TIMEOUT_S, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                // Undecided rather than broken: a slow device must not lose a good build.
                Log.w("Hazel", "yt-dlp health check timed out")
                runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
                return null
            } catch (e: ExecutionException) {
                if (e.cause is YoutubeDL.CanceledException) return null
                val message = e.cause?.message
                Log.w("Hazel", "yt-dlp health check failed (attempt $attempt): $message")
                if (attempt < START_ATTEMPTS && UpdateFailure.isTransientStartFailure(message)) {
                    try {
                        Thread.sleep(START_RETRY_MS)
                    } catch (_: InterruptedException) {
                        return null
                    }
                    continue
                }
                // Never null: a null here would read as "the start never ran" to the caller.
                onFailure(message.orEmpty())
                return false
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }

    private val healthExecutor = Executors.newCachedThreadPool()
    private const val HEALTH_CHECK_TIMEOUT_S = 60L
    private const val START_ATTEMPTS = 2
    private const val START_RETRY_MS = 3_000L

    /**
     * Whether [file] is a complete yt-dlp zip rather than a truncated or foreign file.
     *
     * Not [java.util.zip.ZipFile]: yt-dlp is a zip behind a shebang line, which Android's
     * ZipFile rejects outright. The zip is read from its end record, as Python does.
     */
    internal fun isValidBinary(file: File): Boolean {
        if (!file.isFile || file.length() < MIN_BINARY_BYTES) return false
        return try {
            RandomAccessFile(file, "r").use { zip -> zipEntryNames(zip)?.any { it in MAIN_ENTRIES } == true }
        } catch (_: Exception) {
            false
        }
    }

    private val MAIN_ENTRIES = setOf("__main__.py", "yt_dlp/__init__.py")

    private const val EOCD_SIG = 0x06054b50
    private const val CEN_SIG = 0x02014b50
    private const val LOC_SIG = 0x04034b50
    private const val EOCD_SIZE = 22
    private const val CEN_HEADER_SIZE = 46

    /**
     * Entry names of [zip], or null when it is not a whole zip. Leading bytes are allowed.
     * Only the local headers of [MAIN_ENTRIES] are read, to keep the check to a few reads.
     */
    private fun zipEntryNames(zip: RandomAccessFile): List<String>? {
        val length = zip.length()
        val tailSize = minOf(length, (EOCD_SIZE + 0xFFFF).toLong()).toInt()
        val tail = ByteArray(tailSize)
        zip.seek(length - tailSize)
        zip.readFully(tail)
        val tailBuf = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN)

        // The comment length must land on the end, so a signature inside the comment is skipped.
        val eocd = (tailSize - EOCD_SIZE downTo 0).firstOrNull {
            tailBuf.getInt(it) == EOCD_SIG &&
                it + EOCD_SIZE + (tailBuf.getShort(it + 20).toInt() and 0xFFFF) == tailSize
        } ?: return null
        val entryCount = tailBuf.getShort(eocd + 10).toInt() and 0xFFFF
        val cenSize = tailBuf.getInt(eocd + 12).toLong() and 0xFFFFFFFFL
        val cenOffset = tailBuf.getInt(eocd + 16).toLong() and 0xFFFFFFFFL

        // Gap between where the directory is and where it says it is = bytes ahead of the zip.
        val cenPos = length - tailSize + eocd - cenSize
        val shift = cenPos - cenOffset
        if (cenPos < 0 || shift < 0) return null

        val cen = ByteArray(cenSize.toInt())
        zip.seek(cenPos)
        zip.readFully(cen)
        val cenBuf = ByteBuffer.wrap(cen).order(ByteOrder.LITTLE_ENDIAN)
        val local = ByteArray(4)
        val localBuf = ByteBuffer.wrap(local).order(ByteOrder.LITTLE_ENDIAN)

        val names = ArrayList<String>(entryCount)
        var pos = 0
        repeat(entryCount) {
            if (pos + CEN_HEADER_SIZE > cen.size || cenBuf.getInt(pos) != CEN_SIG) return null
            val nameLen = cenBuf.getShort(pos + 28).toInt() and 0xFFFF
            val extraLen = cenBuf.getShort(pos + 30).toInt() and 0xFFFF
            val commentLen = cenBuf.getShort(pos + 32).toInt() and 0xFFFF
            val localOffset = (cenBuf.getInt(pos + 42).toLong() and 0xFFFFFFFFL) + shift
            if (pos + CEN_HEADER_SIZE + nameLen > cen.size) return null
            if (localOffset + 4 > cenPos) return null
            val name = String(cen, pos + CEN_HEADER_SIZE, nameLen, Charsets.UTF_8)
            if (name in MAIN_ENTRIES) {
                zip.seek(localOffset)
                zip.readFully(local)
                if (localBuf.getInt(0) != LOC_SIG) return null
            }
            names += name
            pos += CEN_HEADER_SIZE + nameLen + extraLen + commentLen
        }
        return names
    }

    /**
     * Makes sure the live yt-dlp binary is a complete, readable zip.
     *
     * A binary cut short by an older build's in-place update fails every run with a zip
     * import error. When that is what is on disk it is replaced with the copy bundled in
     * the app, so the engine works again even if it is a release behind.
     *
     * A binary that fails the check but still starts is kept, so the check alone never
     * undoes an update. Pass [confirmedBroken] when a real run has already failed on it.
     *
     * Returns true when a valid binary is in place.
     */
    @Synchronized
    fun ensureValidBinary(context: Context, confirmedBroken: Boolean = false): Boolean {
        val binary = liveBinary(context)
        if (isValidBinary(binary)) return true

        // A swap interrupted between its two renames.
        val previous = previousBinary(context)
        if (!binary.exists() && isValidBinary(previous) && previous.renameTo(binary)) {
            binary.setExecutable(true, false)
            Log.w("Hazel", "yt-dlp swap was interrupted, put the previous build back")
            return true
        }

        if (!confirmedBroken && binary.isFile && runsOnDevice(context) != false) {
            Log.w("Hazel", "yt-dlp binary failed the zip check but starts, keeping it")
            return true
        }

        Log.w("Hazel", "yt-dlp binary is missing or damaged, restoring the bundled copy")
        return try {
            binary.delete()
            YoutubeDL.getInstance().init_ytdlp(context, liveDir(context))
            // The recorded release no longer describes the file on disk.
            context.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE).edit()
                .remove(VERSION_KEY)
                .remove(VERSION_NAME_KEY)
                .apply()
            isValidBinary(binary)
        } catch (e: Exception) {
            Log.e("Hazel", "Restoring the bundled yt-dlp failed", e)
            false
        }
    }

    /**
     * Replaces the live binary with the copy bundled in the app, whatever is on disk.
     *
     * For a binary that is intact but too old to run with the options the engine library
     * passes: every run fails on it, so there is nothing to wait for. The copy is written
     * beside the live one and renamed over it, so the swap itself is atomic.
     */
    @Synchronized
    fun restoreBundledBinary(context: Context): Boolean {
        val partial = File(engineDir(context), "yt-dlp.bundled")
        return try {
            context.resources.openRawResource(com.yausername.youtubedl_android.R.raw.ytdlp).use { input ->
                partial.outputStream().use { input.copyTo(it) }
            }
            if (!isValidBinary(partial) || !partial.renameTo(liveBinary(context))) return false
            liveBinary(context).setExecutable(true, false)
            context.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE).edit()
                .remove(VERSION_KEY)
                .remove(VERSION_NAME_KEY)
                .apply()
            true
        } catch (e: Exception) {
            Log.e("Hazel", "Restoring the bundled yt-dlp failed", e)
            false
        } finally {
            partial.delete()
        }
    }

    /**
     * Puts a downloaded build live if one is waiting and nothing is running.
     *
     * The rename is atomic, so a run sees the old file or the new one, never half of each.
     * The new build must start on this device's Python before any run reaches it; otherwise
     * the previous build is put back and the tag is remembered.
     *
     * Returns true when a staged build went live. A refused build is deleted, so a caller
     * can tell a refusal from a busy engine.
     */
    fun applyStagedUpdate(context: Context): Boolean {
        val staged = stagedBinary(context)
        if (!staged.exists()) return false

        return YtDlpEngine.whenIdle {
            synchronized(this) {
                if (!staged.exists()) return@synchronized false
                rejection = null
                val tag = runCatching { stagedTag(context).readText().trim() }.getOrDefault("")
                // Before the swap, so the library never finds the slot empty and unpacks its own copy.
                runCatching { YoutubeDL.getInstance().init(context) }
                liveDir(context).mkdirs()
                val live = liveBinary(context)
                val previous = previousBinary(context)
                previous.delete()
                val keptPrevious = live.isFile && live.renameTo(previous)
                if (!staged.renameTo(live)) {
                    if (keptPrevious) previous.renameTo(live)
                    Log.w("Hazel", "yt-dlp ${tag.ifBlank { "update" }} could not be moved into place")
                    refuse(context, UpdateFailure.of(UpdateFailure.Kind.COULD_NOT_INSTALL, "rename failed"))
                    discardStaged(context)
                    notifyFailed(context, tag)
                    return@synchronized false
                }
                live.setExecutable(true, false)

                // Starting is the test; the zip check only decides when the engine can't run one.
                var startError: String? = null
                val runs = runsOnDevice(context) { startError = it } ?: isValidBinary(live)
                if (!runs) {
                    Log.w("Hazel", "yt-dlp ${tag.ifBlank { "update" }} does not start here, keeping the previous build")
                    // A null here means the start never ran (the health check was undecided),
                    // so the file itself failed its zip check. An empty string is a start that
                    // ran and printed nothing, which is what a process killed for memory looks like.
                    val failure = startError?.let { UpdateFailure.forStartFailure(it) }
                        ?: UpdateFailure.of(UpdateFailure.Kind.INCOMPLETE, "installed file failed its zip check")
                    refuse(context, failure)
                    live.delete()
                    if (!(keptPrevious && previous.renameTo(live))) {
                        ensureValidBinary(context, confirmedBroken = true)
                    }
                    live.setExecutable(true, false)
                    // A build the system stopped for lack of memory is not known to be bad, and
                    // auto-update runs while the app is still starting, which is when memory is
                    // tightest. Its tag is left unmarked so the next launch tries it again.
                    if (tag.isNotBlank() && failure.kind != UpdateFailure.Kind.STOPPED_WHILE_STARTING) {
                        runCatching { rejectedTag(context).writeText(tag) }
                    }
                    stagedTag(context).delete()
                    notifyFailed(context, tag)
                    return@synchronized false
                }

                previous.delete()
                rejectedTag(context).delete()
                stagedTag(context).delete()
                context.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE).edit().apply {
                    if (tag.isNotBlank()) putString(VERSION_KEY, tag).putString(VERSION_NAME_KEY, tag)
                    else remove(VERSION_KEY).remove(VERSION_NAME_KEY)
                }.apply()
                clearFailure(context)
                Log.i("Hazel", "yt-dlp ${tag.ifBlank { "update" }} is now live")
                if (tag.isNotBlank()) YtDlpUpdateNotifier.post(context, YtDlpUpdateNotifier.Event.INSTALLED, tag)
                true
            }
        } ?: run {
            Log.i("Hazel", "yt-dlp update staged; it goes live once the running jobs finish")
            false
        }
    }

    private fun discardStaged(context: Context) {
        stagedBinary(context).delete()
        stagedTag(context).delete()
    }

    /** Why the last staged build was refused, so a manual install can say so. */
    @Volatile
    private var rejection: UpdateFailure? = null

    private fun refuse(context: Context, failure: UpdateFailure) {
        rejection = failure
        recordFailure(context, failure)
    }

    private const val STATE_PREFS = "ytdlp_update_state"
    private const val FAILURE_KEY = "lastFailure"

    /** Keeps the last failure where a bug report can find it, whichever path hit it. */
    private fun recordFailure(context: Context, failure: UpdateFailure) {
        Log.w("Hazel", "yt-dlp update failed: ${failure.summary}")
        runCatching {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit()
                .putString(FAILURE_KEY, "${failure.summary} at $stamp").apply()
        }
    }

    private fun clearFailure(context: Context) {
        runCatching {
            context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit().remove(FAILURE_KEY).apply()
        }
    }

    /** The last update failure, as one line for a bug report; null when the last update worked. */
    fun lastFailure(context: Context): String? = runCatching {
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).getString(FAILURE_KEY, null)
    }.getOrNull()

    private fun notifyFailed(context: Context, tag: String) {
        if (tag.isNotBlank()) YtDlpUpdateNotifier.post(context, YtDlpUpdateNotifier.Event.FAILED, tag)
    }

    /**
     * Version of the yt-dlp binary currently installed on the device.
     *
     * Queries the binary directly (`yt-dlp --version`) so the bundled binary also
     * reports a version; [YoutubeDL.version] is only populated after the first
     * successful in-app update. Returns null when yt-dlp is not initialized.
     */
    suspend fun installedVersion(context: Context): String? = withContext(Dispatchers.IO) {
        val binary = liveBinary(context)
        val stamp = "${binary.length()}:${binary.lastModified()}"
        knownVersion?.let { (forStamp, version) -> if (forStamp == stamp) return@withContext version }
        try {
            val request = YoutubeDLRequest(emptyList<String>()).addOption("--version")
            YtDlpEngine.execute(request).out.trim().ifBlank { null }
                ?.also { knownVersion = stamp to it }
        } catch (_: Exception) {
            // Library not initialized, or the binary is unusable: fall back to the
            // version recorded by the last successful update.
            cachedVersion(context)
        }
    }

    /**
     * Version recorded by the last successful in-app update, read from shared prefs.
     * Synchronous and allocation-free; null when yt-dlp was never updated in-app.
     */
    fun cachedVersion(context: Context): String? = try {
        YoutubeDL.getInstance().version(context)
    } catch (_: Exception) {
        null
    }

    sealed interface CheckResult {
        data class Success(val info: ReleaseInfo) : CheckResult
        data object NoReleaseFound : CheckResult
        data class NetworkError(val message: String) : CheckResult
    }

    /**
     * Extracts the first release tag from a GitHub Atom feed.
     */
    fun parseFirstTagFromAtom(xml: String): String? {
        val match = Regex("""href=["'][^"']*/releases/tag/([^"']+)["']""").find(xml)
            ?: Regex("""<id>[^<]*/([^/<]+)</id>""").find(xml)
        return match?.groupValues?.get(1)?.trim()
    }

    /**
     * Fetches the latest release of [channel] from GitHub without installing anything.
     * Uses resilient redirect and Atom feed resolution to be immune to GitHub API 403 rate limits.
     */
    suspend fun latestReleaseResult(channel: Channel): CheckResult = withContext(Dispatchers.IO) {
        // 1. Direct release redirect check (instantaneous, zero API rate limits, 403-proof)
        try {
            val noRedirectClient = client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
            val request = Request.Builder()
                .url("https://github.com/${channel.repo}/releases/latest")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            noRedirectClient.newCall(request).execute().use { response ->
                val location = response.header("Location")
                if (location != null && location.contains("/releases/tag/")) {
                    val tag = location.substringAfterLast("/releases/tag/").trim()
                    if (tag.isNotBlank()) {
                        return@withContext CheckResult.Success(
                            ReleaseInfo(
                                version = tag,
                                channel = channel,
                                binarySize = 0L
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Fall back to Atom feed
        }

        // 2. Releases Atom feed check (CDN cached, zero rate limits)
        try {
            val atomRequest = Request.Builder()
                .url("https://github.com/${channel.repo}/releases.atom")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(atomRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val xml = response.body.string()
                    val tag = parseFirstTagFromAtom(xml)
                    if (tag != null && tag.isNotBlank()) {
                        return@withContext CheckResult.Success(
                            ReleaseInfo(
                                version = tag,
                                channel = channel,
                                binarySize = 0L
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Fall back to API
        }

        // 3. Fallback to API if available (when not rate limited)
        try {
            val request = Request.Builder()
                .url(channel.ytdlChannel.apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Hazel-App-Updater")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body.string())
                    val tag = json.optString("tag_name", "").trim()
                    if (tag.isNotBlank()) {
                        return@withContext CheckResult.Success(
                            ReleaseInfo(
                                version = tag,
                                channel = channel,
                                binarySize = findBinarySize(json)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Network failure
        }

        CheckResult.NetworkError("Couldn't reach GitHub. Check your connection.")
    }

    suspend fun latestRelease(channel: Channel): ReleaseInfo? =
        (latestReleaseResult(channel) as? CheckResult.Success)?.info

    /**
     * Downloads the `yt-dlp` asset of [release] next to the live binary, without touching it.
     *
     * The asset is fetched by its release tag from github.com rather than through the API,
     * which rate limits unauthenticated callers. [onProgress] receives bytes done and the
     * total, the total being 0 when the server does not say.
     *
     * A connection that drops or times out is tried again, picking up where it stopped when
     * GitHub allows it, so one bad moment on a weak connection does not fail the update.
     *
     * A file that arrived in one piece, at the length the server gave, is trusted on its size
     * and left to the swap's start check, as it always was. One that was resumed, or whose
     * length was never stated, is also checked as a zip before it is staged. That check reads
     * the archive's structure, so it catches a cut-off or garbled file but cannot prove the
     * bytes are the release's; the resume only continues from the offset it asked for.
     */
    private suspend fun stage(
        context: Context,
        release: ReleaseInfo,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val dir = engineDir(context)
        dir.mkdirs()
        val staged = stagedBinary(context)
        val partial = File(dir, "yt-dlp.part")
        val url = "https://github.com/${release.channel.repo}/releases/download/${release.version}/$BINARY_ASSET"

        // Leftovers hold space a nearly full phone may not have to spare. An earlier part-file
        // belongs to an earlier try, possibly of another release, so it is never resumed.
        synchronized(this@YtDlpUpdater) {
            staged.delete()
            stagedTag(context).delete()
            if (isValidBinary(liveBinary(context))) previousBinary(context).delete()
        }
        partial.delete()

        try {
            var attempt = 0
            while (true) {
                attempt++
                try {
                    val inOnePiece = download(context, url, partial, onProgress)
                    val sound = if (inOnePiece) partial.length() >= MIN_BINARY_BYTES else isValidBinary(partial)
                    if (!sound) {
                        // Starting over is the only safe way on from a file that is not a whole zip.
                        partial.delete()
                        throw UpdateFailureException(UpdateFailure.Kind.INCOMPLETE, "download failed its zip check")
                    }
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    val failure = UpdateFailure.from(e)
                    if (!failure.retryable || attempt >= DOWNLOAD_ATTEMPTS) throw e
                    Log.w("Hazel", "yt-dlp download attempt $attempt failed (${failure.kind}), trying again: ${e.message}")
                    delay(DOWNLOAD_RETRY_MS * attempt)
                }
            }

            synchronized(this@YtDlpUpdater) {
                staged.delete()
                if (!partial.renameTo(staged)) {
                    throw UpdateFailureException(UpdateFailure.Kind.COULD_NOT_INSTALL, "could not stage the download")
                }
                stagedTag(context).writeText(release.version)
            }
        } finally {
            partial.delete()
        }
    }

    /**
     * One pass at fetching [url] into [partial], continuing the file when it already holds the
     * start of the same download and the server agrees to go on from there.
     *
     * Returns true when the file arrived in one piece at exactly the length the server stated,
     * and false when it was resumed or the server gave no length, which is when it needs the
     * stronger check in [stage].
     */
    private suspend fun download(
        context: Context,
        url: String,
        partial: File,
        onProgress: (Long, Long) -> Unit
    ): Boolean {
        val resumeFrom = if (partial.isFile) partial.length() else 0L
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Hazel-App-Updater")
            .apply { if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-") }
            .build()

        return downloadClient.newCall(request).execute().use { response ->
            if (resumeFrom > 0 && response.code == 416) {
                partial.delete()
                throw IOException("The saved part of the download was not accepted")
            }
            if (!response.isSuccessful) throw UpdateFailureException(UpdateFailure.http(response.code))

            val append = resumeFrom > 0 && response.code == 206
            if (append && !response.header("Content-Range").orEmpty().startsWith("bytes $resumeFrom-")) {
                partial.delete()
                throw IOException("The server went on from the wrong place")
            }
            // A plain 200 answers a ranged request when the server ignores it: start over.
            val offset = if (append) resumeFrom else 0L
            val length = response.body.contentLength()
            val total = if (length > 0) offset + length else 0L

            makeRoom(context, partial, if (length > 0) length else FALLBACK_SIZE_BYTES)

            var done = offset
            FileOutputStream(partial, append).use { output ->
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        onProgress(done, total)
                    }
                }
            }
            if (total > 0 && done != total) {
                throw UpdateFailureException(UpdateFailure.Kind.INCOMPLETE, "$done of $total bytes")
            }
            offset == 0L && total > 0L
        }
    }

    /**
     * Asks the system to clear other apps' cached files when the phone is too full for the
     * download. Phones with little storage left are often the same ones this fails on, and
     * the system can free what the app cannot. Best effort: if it cannot, the write itself
     * reports the lack of space.
     */
    private fun makeRoom(context: Context, file: File, bytes: Long) {
        val dir = file.parentFile ?: return
        val needed = bytes + SPACE_SLACK_BYTES
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (dir.usableSpace >= needed) return
        try {
            val storage = context.getSystemService(StorageManager::class.java) ?: return
            storage.allocateBytes(storage.getUuidForPath(dir), needed)
        } catch (e: Exception) {
            Log.w("Hazel", "Could not free space for the yt-dlp download: ${e.message}")
        }
    }

    /**
     * Downloads and installs the latest yt-dlp binary of [channel].
     *
     * Pass [release] when the caller has just looked it up, as the update screen has: asking
     * GitHub again is a second chance to fail on a weak connection, and a slow one, as the
     * lookup tries three routes in turn.
     *
     * When a download is running the new build waits for it to finish before going live,
     * so this can take as long as that download does. Throws when the download fails; the
     * exception's cause is told apart by [UpdateFailure.from].
     */
    suspend fun install(
        context: Context,
        channel: Channel,
        release: ReleaseInfo? = null,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): YoutubeDL.UpdateStatus = withContext(Dispatchers.IO) {
        try {
            // A refusal from an earlier swap must not be reported as this attempt's.
            rejection = null
            val target = release ?: latestRelease(channel)
                ?: throw UpdateFailureException(UpdateFailure.Kind.UNREACHABLE, "release lookup failed")
            stage(context, target, onProgress)
            while (!applyStagedUpdate(context)) {
                if (!stagedBinary(context).exists()) {
                    throw UpdateFailureException(
                        rejection ?: UpdateFailure.of(UpdateFailure.Kind.COULD_NOT_INSTALL, "update was rejected")
                    )
                }
                delay(APPLY_RETRY_MS)
            }
            YoutubeDL.UpdateStatus.DONE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A refusal was already recorded where it happened; recording it again only
            // refreshes the time, and it keeps the download's own failures from going unseen.
            recordFailure(context, UpdateFailure.from(e))
            throw e
        }
    }

    /**
     * yt-dlp publishes a fresh date-based tag for every build, so tag equality is the
     * only meaningful comparison; there is no numeric ordering to evaluate.
     */
    fun isNewer(remote: String, installed: String?): Boolean {
        if (installed.isNullOrBlank()) return true
        return remote.trimStart('v', 'V') != installed.trimStart('v', 'V')
    }

    /** Size of the `yt-dlp` asset in a release payload, 0 when absent. */
    private fun findBinarySize(releaseJson: JSONObject): Long {
        val assets = releaseJson.optJSONArray("assets") ?: return 0L
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name") == BINARY_ASSET) {
                return asset.optLong("size", 0L)
            }
        }
        return 0L
    }

    /**
     * Keeps yt-dlp current without the user opening the update screen.
     *
     * Follows the update screen's settings: with automatic download off, or on a metered
     * connection with Wi-Fi only on, a newer release is only announced. Returns true when
     * a newer build was fetched.
     */
    suspend fun autoUpdateIfAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isUpdating.compareAndSet(false, true)) return@withContext false
        var target: String? = null
        try {
            val channel = Channel.fromLabel(SettingsRepository.getYtDlpChannel(context).first())
            val remote = latestRelease(channel) ?: return@withContext false
            val current = installedVersion(context)
            if (!isNewer(remote.version, current)) return@withContext false

            val autoDownload = SettingsRepository.getUpdateAutoDownload(context).first()
            val wifiOnly = SettingsRepository.getUpdateWifiOnly(context).first()
            if (!autoDownload || (wifiOnly && !onUnmeteredNetwork(context))) {
                YtDlpUpdateNotifier.post(context, YtDlpUpdateNotifier.Event.AVAILABLE, remote.version)
                return@withContext false
            }
            val rejected = runCatching { rejectedTag(context).readText().trim() }.getOrNull()
            if (rejected == remote.version) return@withContext false

            target = remote.version
            val alreadyStaged = runCatching { stagedTag(context).readText().trim() }.getOrNull()
            if (alreadyStaged != remote.version || !stagedBinary(context).exists()) {
                Log.i("Hazel", "Fetching yt-dlp ${remote.version} (${channel.label})")
                stage(context, remote)
            }
            if (applyStagedUpdate(context)) SettingsRepository.setYtDlpUpdateAvailable(context, false)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Hazel", "yt-dlp auto-update failed: ${e.message}")
            recordFailure(context, UpdateFailure.from(e))
            target?.let { notifyFailed(context, it) }
            false
        } finally {
            isUpdating.set(false)
        }
    }

    private fun onUnmeteredNetwork(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /** How often a manual install checks whether the engine has come free. */
    private const val APPLY_RETRY_MS = 1_000L

    /** Tries at the download, and the pause before the second, which doubles for the third. */
    private const val DOWNLOAD_ATTEMPTS = 3
    private const val DOWNLOAD_RETRY_MS = 2_000L

    /** Room kept beyond the file itself, and the size assumed when the server gives none. */
    private const val SPACE_SLACK_BYTES = 2L * 1024 * 1024
    private const val FALLBACK_SIZE_BYTES = 8L * 1024 * 1024
}
