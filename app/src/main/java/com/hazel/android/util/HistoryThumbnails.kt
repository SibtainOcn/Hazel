package com.hazel.android.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.util.Size
import coil3.SingletonImageLoader
import com.hazel.android.download.NetworkProxy.throughProxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Artwork for the downloads list that survives the source link expiring.
 *
 * Order: saved copy in the cache directory, then the recorded link, then Android's own
 * thumbnail for the file. Whatever the last step finds is saved as the copy. Nothing here is
 * the only copy of anything, so clearing it only sends rows back down the list.
 */
object HistoryThumbnails {

    const val DIRECTORY_NAME = "history-thumbs"

    private const val MAX_REMOTE_BYTES = 6L * 1024 * 1024
    private const val TARGET_WIDTH = 640
    private const val TARGET_HEIGHT = 360

    internal const val NOTHING_FOUND_TTL_MS = 3L * 24 * 60 * 60 * 1000

    /** A row stops waiting after this; the read carries on and is kept. */
    private const val READ_WAIT_MS = 10_000L

    private val readPermits = Semaphore(3)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val inFlight = ConcurrentHashMap<Long, Deferred<File?>>()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .throughProxy()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun root(context: Context): File = File(context.applicationContext.cacheDir, DIRECTORY_NAME)

    private fun rootForWrite(context: Context): File = root(context).also { it.mkdirs() }

    internal fun copyFile(context: Context, id: Long): File = File(root(context), "$id.jpg")

    private fun markerFile(context: Context, id: Long): File = File(root(context), "$id.none")

    /** One per write, so the finish-time copy and a row's copy of the same entry never share a file. */
    private fun tempFile(context: Context, id: Long): File =
        File.createTempFile("$id.", ".part", rootForWrite(context))

    suspend fun cached(context: Context, id: Long): File? = withContext(Dispatchers.IO) {
        copyFile(context, id).takeIf { it.isFile && it.length() > 0 }
    }

    suspend fun store(context: Context, id: Long, link: String?, fileUri: String, isVideo: Boolean): File? =
        cacheRemote(context, id, link) ?: fromSystem(context, id, fileUri, isVideo)

    suspend fun cacheRemote(context: Context, id: Long, url: String?): File? {
        if (url.isNullOrBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) return null
        cached(context, id)?.let { return it }
        return withContext(Dispatchers.IO) {
            attempt {
                val temp = tempFile(context, id)
                try {
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        val body = response.body
                        if (!response.isSuccessful || body == null) return@attempt null
                        if (body.contentLength() > MAX_REMOTE_BYTES) return@attempt null
                        var total = 0L
                        temp.outputStream().use { out ->
                            val buffer = ByteArray(8 * 1024)
                            body.byteStream().use { input ->
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    total += read
                                    // The declared length can be missing or wrong.
                                    if (total > MAX_REMOTE_BYTES) return@attempt null
                                    out.write(buffer, 0, read)
                                }
                            }
                        }
                    }
                    adopt(context, id, temp)
                } finally {
                    temp.delete()
                }
            }
        }
    }

    /** Keeps what Coil already downloaded for [url]. Never fetches. */
    suspend fun keepFromImageCache(context: Context, id: Long, url: String?): File? {
        if (url.isNullOrBlank()) return null
        cached(context, id)?.let { return it }
        return withContext(Dispatchers.IO) {
            attempt {
                val diskCache = SingletonImageLoader.get(context.applicationContext).diskCache
                    ?: return@attempt null
                val snapshot = diskCache.openSnapshot(url) ?: return@attempt null
                val temp = tempFile(context, id)
                try {
                    snapshot.data.toFile().copyTo(temp, overwrite = true)
                    adopt(context, id, temp)
                } finally {
                    snapshot.close()
                    temp.delete()
                }
            }
        }
    }

    private fun adopt(context: Context, id: Long, temp: File): File? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(temp.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val target = copyFile(context, id)
        if (!temp.renameTo(target)) return null
        markerFile(context, id).delete()
        return target
    }

    /**
     * Artwork for a saved file the way a gallery shows it. Reads are shared per entry, limited
     * to three at once, and a file that gives nothing is skipped for [NOTHING_FOUND_TTL_MS] so
     * scrolling does not re-read it.
     */
    suspend fun fromSystem(context: Context, id: Long, fileUri: String, isVideo: Boolean): File? {
        cached(context, id)?.let { return it }
        if (fileUri.isBlank()) return null
        if (recentlyFoundNothing(context, id)) return null

        val appContext = context.applicationContext
        val read = inFlight.computeIfAbsent(id) {
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    readPermits.withPermit { readFile(appContext, id, fileUri, isVideo) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }.also { started -> started.invokeOnCompletion { inFlight.remove(id, started) } }
        }
        read.start()
        return withTimeoutOrNull(READ_WAIT_MS) { read.await() }
    }

    private fun readFile(context: Context, id: Long, fileUri: String, isVideo: Boolean): File? {
        val uri = runCatching { Uri.parse(fileUri) }.getOrNull() ?: return null
        val bitmap = if (isImage(context, uri)) {
            attempt { platformThumbnail(context, uri, isVideo = false).takeIf { uri.scheme == ContentResolver.SCHEME_CONTENT } }
                ?: attempt { decodeImage(context, uri) }
        } else {
            attempt { platformThumbnail(context, uri, isVideo) }
                ?: attempt { readOffFile(context, uri, isVideo) }
        }

        if (bitmap == null) {
            markNothingFound(context, id)
            return null
        }
        return attempt { save(context, id, bitmap) }
    }

    private fun platformThumbnail(context: Context, uri: Uri, isVideo: Boolean): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val size = Size(TARGET_WIDTH, TARGET_HEIGHT)
        return when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> context.contentResolver.loadThumbnail(uri, size, null)
            ContentResolver.SCHEME_FILE -> uri.path?.let { path ->
                if (isVideo) ThumbnailUtils.createVideoThumbnail(File(path), size, null)
                else ThumbnailUtils.createAudioThumbnail(File(path), size, null)
            }
            else -> null
        }
    }

    /** Needed below Android 10 and for files the media index does not know, such as an unscanned SD card. */
    private fun readOffFile(context: Context, uri: Uri, isVideo: Boolean): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            if (uri.scheme == ContentResolver.SCHEME_FILE) {
                retriever.setDataSource(uri.path ?: return null)
            } else {
                // A plain path is not readable on an SD card; the descriptor is.
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
                pfd.use { retriever.setDataSource(it.fileDescriptor) }
            }

            fun embedded(): Bitmap? = retriever.embeddedPicture?.let { decodeSampled(it) }

            return if (isVideo) {
                val durationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                // Not the first frame, which is often black.
                val atUs = (durationMs * 1000L / 10L).coerceAtLeast(0L)
                frameAt(retriever, atUs) ?: frameAt(retriever, 0L) ?: embedded()
            } else {
                embedded()
            }
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun isImage(context: Context, uri: Uri): Boolean {
        val type = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            ?: android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(uri.lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase())
        return type?.startsWith("image/") == true
    }

    /** A saved picture, read at a fraction of its size: a phone photo decoded whole is tens of MB. */
    private fun decodeImage(context: Context, uri: Uri): Bitmap? {
        fun open(): java.io.InputStream? = if (uri.scheme == ContentResolver.SCHEME_FILE) File(uri.path ?: return null).inputStream()
        else context.contentResolver.openInputStream(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_WIDTH) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return open()?.use { BitmapFactory.decodeStream(it, null, options) }?.let(::scaleDown)
    }

    /** Scaled while decoding where possible: a full 4K frame alone is over 30 MB. */
    private fun frameAt(retriever: MediaMetadataRetriever, atUs: Long): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            attempt {
                retriever.getScaledFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, TARGET_WIDTH, TARGET_HEIGHT)
            }?.let { return it }
        }
        // Some vendor decoders give nothing scaled but do give the full frame.
        return retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }

    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_WIDTH) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun scaleDown(source: Bitmap): Bitmap {
        if (source.width <= TARGET_WIDTH) return source
        val height = (source.height * TARGET_WIDTH.toFloat() / source.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, TARGET_WIDTH, height, true)
    }

    /** JPEG has no alpha, so a transparent cover would be saved as black. */
    private fun flatten(source: Bitmap): Bitmap {
        // A hardware bitmap cannot be drawn on a software canvas.
        if (!source.hasAlpha() || isHardware(source)) return source
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.BLACK)
            drawBitmap(source, 0f, 0f, null)
        }
        return out
    }

    private fun isHardware(bitmap: Bitmap): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE

    private fun save(context: Context, id: Long, bitmap: Bitmap): File? {
        val temp = tempFile(context, id)
        var scaled: Bitmap? = null
        var ready: Bitmap? = null
        return try {
            val small = scaleDown(bitmap).also { scaled = it }
            val flat = flatten(small).also { ready = it }
            val written = temp.outputStream().use { flat.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            val target = copyFile(context, id)
            if (written && temp.length() > 0 && temp.renameTo(target)) {
                markerFile(context, id).delete()
                target
            } else null
        } finally {
            temp.delete()
            ready?.takeIf { it !== scaled }?.recycle()
            scaled?.takeIf { it !== bitmap }?.recycle()
            bitmap.recycle()
        }
    }

    internal fun markNothingFound(context: Context, id: Long) {
        attempt {
            val marker = markerFile(context, id)
            rootForWrite(context)
            marker.writeBytes(ByteArray(0))
            marker.setLastModified(System.currentTimeMillis())
        }
    }

    internal fun recentlyFoundNothing(context: Context, id: Long, now: Long = System.currentTimeMillis()): Boolean {
        val marker = markerFile(context, id)
        if (!marker.isFile) return false
        if (now - marker.lastModified() < NOTHING_FOUND_TTL_MS) return true
        marker.delete()
        return false
    }

    suspend fun discard(context: Context, id: Long) = withContext(Dispatchers.IO) {
        attempt { copyFile(context, id).delete() }
        Unit
    }

    fun delete(context: Context, ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val gone = ids.toHashSet()
        attempt {
            root(context).listFiles()?.forEach { file ->
                if (file.name.substringBefore('.').toLongOrNull() in gone) file.delete()
            }
        }
    }

    /** Removes files for entries not in [keep]: removed rows, restored backups, interrupted writes. */
    fun prune(context: Context, keep: Set<Long>) {
        attempt {
            root(context).listFiles()?.forEach { file ->
                val id = file.name.substringBefore('.').toLongOrNull()
                if (id == null || id !in keep) file.delete()
            }
        }
    }

    fun sizeBytes(context: Context): Long = attempt {
        root(context).takeIf { it.isDirectory }?.walkBottomUp()?.filter { it.isFile }?.sumOf { it.length() }
    } ?: 0L

    fun clearAll(context: Context) {
        attempt { root(context).deleteRecursively() }
        inFlight.clear()
    }

    /** Null instead of failing: full or unplugged storage, undecodable files, oversized covers. Never wraps suspension. */
    private inline fun <T> attempt(block: () -> T?): T? = try {
        block()
    } catch (ignored: Exception) {
        null
    } catch (ignored: OutOfMemoryError) {
        null
    }
}
