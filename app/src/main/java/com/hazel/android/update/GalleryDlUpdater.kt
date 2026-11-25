package com.hazel.android.update

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.NetworkProxy.throughProxy
import com.hazel.android.download.YtDlpEngine
import com.hazel.android.gallery.GalleryDlEngine
import com.hazel.android.gallery.GalleryDlPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps gallery-dl current from its PyPI releases, apart from the app's own releases.
 *
 * The release is gallery-dl's pure-Python wheel, checked against the sha256 PyPI lists for it,
 * and put ahead of the bundled zip on the import path; the libraries it needs stay the bundled
 * ones. A new wheel goes live only while no run holds the engine, and only once it has started
 * on this device; otherwise the previous one stays. The bundled copy is never touched, so
 * removing the wheel always brings back a working version.
 */
object GalleryDlUpdater {

    data class Release(val version: String, val url: String, val sha256: String, val size: Long)

    enum class Problem(@param:StringRes val message: Int) {
        UNREACHABLE(R.string.upd_gallerydl_failure_unreachable),
        CONNECTION_LOST(R.string.upd_failure_connection_lost),
        INCOMPLETE(R.string.upd_failure_incomplete),
        NO_SPACE(R.string.upd_failure_no_space),
        WOULD_NOT_START(R.string.upd_gallerydl_failure_would_not_start)
    }

    class UpdateException(val problem: Problem, message: String, cause: Throwable? = null) :
        Exception(message, cause)

    const val PROJECT_URL = "https://pypi.org/project/gallery-dl/"
    const val RELEASES_URL = "https://github.com/mikf/gallery-dl/releases"
    private const val FEED_URL = "https://pypi.org/pypi/gallery-dl/json"
    private const val FILE_HOST = "files.pythonhosted.org"
    private const val MAX_WHEEL_BYTES = 20L * 1024 * 1024
    private const val DIRECTORY = "gallery-dl-update"
    private const val APPLY_RETRY_MS = 1_000L
    private const val TAG = "HazelGallery"

    private val VERSION = Regex("""\d+(\.\d+){1,3}""")
    private val SHA256 = Regex("""[0-9a-fA-F]{64}""")

    private val isUpdating = AtomicBoolean(false)

    private val client = OkHttpClient.Builder()
        .throughProxy()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Kept apart from the bundled zip's folder, which is emptied on every app update. */
    private fun dir(context: Context) = File(context.noBackupFilesDir, DIRECTORY)
    private fun liveFile(context: Context) = File(dir(context), "gallery_dl.whl")
    private fun liveTag(context: Context) = File(dir(context), "gallery_dl.whl.version")
    private fun stagedFile(context: Context) = File(dir(context), "gallery_dl.staged")
    private fun stagedTag(context: Context) = File(dir(context), "gallery_dl.staged.version")
    private fun rejectedTag(context: Context) = File(dir(context), "gallery_dl.rejected.version")

    /** Why the last staged wheel was refused, for a manual install to report. */
    @Volatile
    private var rejection: Problem? = null

    private fun read(file: File): String? = runCatching { file.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    /** The downloaded wheel, while it is newer than the gallery-dl the app ships. */
    internal fun liveWheel(context: Context): File? {
        val file = liveFile(context)
        val version = read(liveTag(context)) ?: return null
        return file.takeIf { it.isFile && isNewer(version, GalleryDlPayload.BUNDLED_VERSION) }
    }

    /** The gallery-dl version runs use now. */
    fun installedVersion(context: Context): String =
        if (liveWheel(context) != null) read(liveTag(context)) ?: GalleryDlPayload.BUNDLED_VERSION
        else GalleryDlPayload.BUNDLED_VERSION

    fun usesBundled(context: Context): Boolean = liveWheel(context) == null

    /** Dotted numeric comparison; anything unreadable is never newer. */
    fun isNewer(remote: String, installed: String): Boolean {
        val a = parts(remote) ?: return false
        val b = parts(installed) ?: return true
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(version: String): List<Int>? =
        version.trim().takeIf { VERSION.matches(it) }?.split('.')?.map { it.toInt() }

    /** The wheel of PyPI's latest stable release, or null when the answer offers none Hazel can use. */
    internal fun parseRelease(json: String): Release? = runCatching {
        val root = JSONObject(json)
        val version = root.getJSONObject("info").optString("version").trim()
        if (!VERSION.matches(version)) return null
        val files = root.optJSONArray("urls") ?: return null
        for (i in 0 until files.length()) {
            val file = files.getJSONObject(i)
            if (file.optString("packagetype") != "bdist_wheel") continue
            if (!file.optString("filename").endsWith("-py3-none-any.whl")) continue
            if (file.optBoolean("yanked")) continue
            val url = file.optString("url")
            val sha = file.optJSONObject("digests")?.optString("sha256").orEmpty()
            val size = file.optLong("size")
            val host = runCatching { URI(url).host }.getOrNull()
            if (!url.startsWith("https://") || host != FILE_HOST) continue
            if (!SHA256.matches(sha) || size <= 0 || size > MAX_WHEEL_BYTES) continue
            return Release(version, url, sha.lowercase(), size)
        }
        null
    }.getOrNull()

    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(FEED_URL).header("Accept", "application/json").build()
        val body = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.body.string()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw UpdateException(Problem.UNREACHABLE, e.message ?: "feed failed", e)
        }
        parseRelease(body) ?: throw UpdateException(Problem.UNREACHABLE, "no usable wheel in the feed")
    }

    /** Downloads [release]'s wheel next to the live one and keeps it only when its sha256 matches. */
    private suspend fun stage(context: Context, release: Release, onProgress: (Long, Long) -> Unit) =
        withContext(Dispatchers.IO) {
            val dir = dir(context).apply { mkdirs() }
            val partial = File(dir, "gallery_dl.part")
            partial.delete()
            try {
                val request = Request.Builder().url(release.url).build()
                val digest = MessageDigest.getInstance("SHA-256")
                var done = 0L
                try {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw UpdateException(Problem.UNREACHABLE, "HTTP ${response.code}")
                        partial.outputStream().use { output ->
                            response.body.byteStream().use { input ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    done += read
                                    if (done > release.size) throw UpdateException(Problem.INCOMPLETE, "larger than listed")
                                    output.write(buffer, 0, read)
                                    digest.update(buffer, 0, read)
                                    onProgress(done, release.size)
                                }
                            }
                        }
                    }
                } catch (e: IOException) {
                    val noSpace = e.message.orEmpty().let { "ENOSPC" in it || "No space" in it }
                    throw UpdateException(if (noSpace) Problem.NO_SPACE else Problem.CONNECTION_LOST, e.message ?: "download failed", e)
                }
                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                if (done != release.size || sha != release.sha256) {
                    throw UpdateException(Problem.INCOMPLETE, "size or sha256 does not match the release")
                }
                synchronized(this@GalleryDlUpdater) {
                    stagedFile(context).delete()
                    if (!partial.renameTo(stagedFile(context))) throw UpdateException(Problem.NO_SPACE, "could not stage")
                    stagedTag(context).writeText(release.version)
                }
            } finally {
                partial.delete()
            }
        }

    /**
     * Puts a staged wheel live while nothing runs. It must start and report its version on this
     * device first; a wheel that does not is removed, the previous one kept, and its version
     * remembered so auto-update skips it. True when a staged wheel went live.
     */
    fun applyStagedUpdate(context: Context): Boolean {
        val staged = stagedFile(context)
        if (!staged.exists()) return false
        return YtDlpEngine.whenIdle {
            synchronized(this) {
                if (!staged.exists()) return@synchronized false
                rejection = null
                val tag = read(stagedTag(context)).orEmpty()
                val live = liveFile(context)
                val previous = File(dir(context), "gallery_dl.previous")
                val previousTag = read(liveTag(context))
                previous.delete()
                val keptPrevious = live.isFile && live.renameTo(previous)
                val works = VERSION.matches(tag) && staged.renameTo(live) &&
                    GalleryDlEngine.probe(context, live, tag)
                if (!works) {
                    Log.w(TAG, "gallery-dl $tag did not start, keeping the current version")
                    rejection = Problem.WOULD_NOT_START
                    live.delete()
                    if (keptPrevious && previous.renameTo(live) && previousTag != null) {
                        liveTag(context).writeText(previousTag)
                    } else {
                        liveTag(context).delete()
                    }
                    staged.delete()
                    stagedTag(context).delete()
                    if (tag.isNotBlank()) {
                        runCatching { rejectedTag(context).writeText(tag) }
                        EngineUpdateNotifier.post(context, EngineUpdateNotifier.Engine.GALLERY_DL, EngineUpdateNotifier.Event.FAILED, tag)
                    }
                    return@synchronized false
                }
                liveTag(context).writeText(tag)
                previous.delete()
                stagedTag(context).delete()
                rejectedTag(context).delete()
                Log.i(TAG, "gallery-dl $tag is now live")
                EngineUpdateNotifier.post(context, EngineUpdateNotifier.Engine.GALLERY_DL, EngineUpdateNotifier.Event.INSTALLED, tag)
                true
            }
        } ?: false
    }

    /** Drops the downloaded wheel so runs use the bundled gallery-dl. False while a run holds the engine. */
    fun useBundled(context: Context): Boolean = YtDlpEngine.whenIdle {
        synchronized(this) {
            liveFile(context).delete()
            liveTag(context).delete()
            stagedFile(context).delete()
            stagedTag(context).delete()
            true
        }
    } ?: false

    /**
     * Downloads and installs [release]. When a run is in progress the wheel waits for it to end
     * before going live, so this can take as long as that run.
     */
    suspend fun install(context: Context, release: Release, onProgress: (Long, Long) -> Unit = { _, _ -> }) =
        withContext(Dispatchers.IO) {
            while (!isUpdating.compareAndSet(false, true)) delay(APPLY_RETRY_MS)
            try {
                if (installedVersion(context) == release.version) return@withContext
                stage(context, release, onProgress)
                while (!applyStagedUpdate(context)) {
                    if (!stagedFile(context).exists()) {
                        synchronized(this@GalleryDlUpdater) {}
                        if (rejection == null && installedVersion(context) == release.version) break
                        throw UpdateException(rejection ?: Problem.WOULD_NOT_START, "update was rejected")
                    }
                    delay(APPLY_RETRY_MS)
                }
                SettingsRepository.setGalleryDlUpdateAvailable(context, false)
            } finally {
                isUpdating.set(false)
            }
        }

    /**
     * Keeps gallery-dl current without opening its screen, following that screen's settings:
     * with automatic download off, or on mobile data with Wi-Fi only on, a newer release is
     * only announced.
     */
    suspend fun autoUpdateIfAvailable(context: Context) = withContext(Dispatchers.IO) {
        if (!isUpdating.compareAndSet(false, true)) return@withContext
        var target: String? = null
        try {
            val release = latest()
            val newer = isNewer(release.version, installedVersion(context))
            SettingsRepository.setGalleryDlUpdateAvailable(context, newer)
            if (!newer) return@withContext
            val autoDownload = SettingsRepository.getGalleryDlAutoUpdate(context).first()
            val wifiOnly = SettingsRepository.getGalleryDlWifiOnly(context).first()
            if (!autoDownload || (wifiOnly && !onUnmeteredNetwork(context))) {
                EngineUpdateNotifier.post(context, EngineUpdateNotifier.Engine.GALLERY_DL, EngineUpdateNotifier.Event.AVAILABLE, release.version)
                return@withContext
            }
            if (read(rejectedTag(context)) == release.version) return@withContext
            target = release.version
            if (read(stagedTag(context)) != release.version || !stagedFile(context).exists()) {
                stage(context, release) { _, _ -> }
            }
            if (applyStagedUpdate(context)) SettingsRepository.setGalleryDlUpdateAvailable(context, false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "gallery-dl auto-update failed: ${e.message}")
            target?.let {
                EngineUpdateNotifier.post(context, EngineUpdateNotifier.Engine.GALLERY_DL, EngineUpdateNotifier.Event.FAILED, it)
            }
        } finally {
            isUpdating.set(false)
        }
    }
}
