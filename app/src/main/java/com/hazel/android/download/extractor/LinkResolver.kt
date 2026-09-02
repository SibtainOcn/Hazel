package com.hazel.android.download.extractor

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
enum class ListingSource(val label: String, val description: String) {

    YT_DLP(
        label = "yt-dlp",
        description = "The same engine that does the downloading. Works on every site it " +
                "supports, and updates itself, so it keeps working as sites change."
    ),

    NEWPIPE(
        label = "Prefer the built-in reader",
        description = "Lists playlists and channels in one request instead of starting the " +
                "engine, which is quicker on the sites it knows. Falls back to yt-dlp for " +
                "everything else, and whenever it cannot read a link."
    );

    companion object {
        /**
         * yt-dlp by default. The built-in reader is quicker where it works, but it is fixed
         * at the version the app shipped with, while yt-dlp updates itself, so the default
         * is the one that stays correct without a new release.
         */
        val DEFAULT = YT_DLP

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

        // Only collections are worth routing elsewhere. A single item has to go to yt-dlp
        // regardless, because the next thing wanted from it is its formats, and the read
        // that lists it returns those in the same pass.
        if (source == ListingSource.NEWPIPE && NewPipeLister.handlesCollection(url)) {
            NewPipeLister.list(url)?.let { return it }
        }

        return MediaProbe.listContents(url, cacheDir, access, fetchMode, forceIpv4, processId)
    }
}
