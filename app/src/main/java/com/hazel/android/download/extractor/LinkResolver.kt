package com.hazel.android.download.extractor

import androidx.annotation.StringRes
import com.hazel.android.R
import com.hazel.android.download.FetchMode
import com.hazel.android.download.MediaProbe
import com.hazel.android.download.SiteAccess
import java.io.File

/**
 * Which extractor is asked what a link holds.
 *
 * Only the listing is in question. Formats and the download itself are always yt-dlp's,
 * whichever of these is chosen, because its format ids are what a download is expressed in.
 */
enum class ListingSource(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int
) {

    YT_DLP(
        labelRes = R.string.listing_source_ytdlp_label,
        descriptionRes = R.string.listing_source_ytdlp_description
    ),

    NEWPIPE(
        labelRes = R.string.listing_source_newpipe_label,
        descriptionRes = R.string.listing_source_newpipe_description
    );

    companion object {
        /**
         * Built-in Java reader (NewPipe) by default. It executes in-process on Android's ART
         * runtime without Python process startup overhead, returning metadata in ~200ms.
         * Falls back silently to yt-dlp on any parsing or extraction failure.
         */
        val DEFAULT = NEWPIPE

        fun fromName(name: String?): ListingSource =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Answers what a link holds, from whichever extractor can say soonest.
 *
 * The fallback is the point of this class. Anything the preferred reader cannot answer is
 * put to yt-dlp instead, silently, because a link it cannot read is still a link yt-dlp very
 * likely can. Nothing here reports a failure to the user: the only observable difference
 * between the two paths is how long the answer took.
 */
object LinkResolver {

    suspend fun resolve(
        url: String,
        cacheDir: File,
        access: SiteAccess,
        fetchMode: FetchMode,
        forceIpv4: Boolean,
        source: ListingSource,
        processId: String = MediaProbe.PROBE_PROCESS_ID
    ): LinkContents {

        if (source == ListingSource.NEWPIPE) {
            // First check collections (playlists & channel tabs) for low-latency listing
            if (NewPipeLister.handlesCollection(url)) {
                NewPipeLister.list(url)?.let { return it }
            } else if (NewPipeLister.handlesStream(url)) {
                // Single media stream: Extract metadata and resolved formats instantly in ~200ms
                NewPipeLister.single(url)?.let { info ->
                    return LinkContents.Single(info)
                }
            }
        }

        // Silent universal fallback to yt-dlp binary engine
        return MediaProbe.listContents(url, cacheDir, access, fetchMode, forceIpv4, processId)
    }
}
