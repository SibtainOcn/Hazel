package com.hazel.android.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the saved file itself says it is.
 *
 * The download record holds what was asked for. This holds what arrived, read out of the
 * file's own header: the two differ whenever a merge, a conversion or a container change
 * sat between them, and for anything downloaded before the record started carrying those
 * details it is the only account there is.
 *
 * Everything is optional, because a file can be gone, unreadable, or of a kind the platform
 * extractor has nothing to say about. A blank field is left out of the sheet rather than
 * shown as zero.
 */
data class MediaFacts(
    val bitrateKbps: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "",
    val durationSeconds: Int = 0
) {
    val resolution: String
        get() = if (width > 0 && height > 0) "${width}x$height" else ""

    val hasAnything: Boolean
        get() = bitrateKbps > 0 || resolution.isNotBlank() || mimeType.isNotBlank()
}

object MediaProbeFacts {

    /**
     * Reads [fileUri]'s header. Returns an empty answer for anything that cannot be opened,
     * which is the ordinary case for a download the user has since deleted.
     */
    suspend fun read(context: Context, fileUri: String): MediaFacts =
        withContext(Dispatchers.IO) {
            if (fileUri.isBlank()) return@withContext MediaFacts()

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(fileUri))

                MediaFacts(
                    // Reported in bits per second, and shown in the thousands the rest of
                    // the app talks in.
                    bitrateKbps = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                        ?.toIntOrNull()
                        ?.let { it / 1000 }
                        ?: 0,
                    width = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull() ?: 0,
                    height = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull() ?: 0,
                    mimeType = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                        .orEmpty(),
                    durationSeconds = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?.let { (it / 1000).toInt() }
                        ?: 0
                )
            } catch (_: Exception) {
                MediaFacts()
            } finally {
                runCatching { retriever.release() }
            }
        }
}
