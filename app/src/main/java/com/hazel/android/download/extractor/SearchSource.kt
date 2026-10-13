package com.hazel.android.download.extractor

import androidx.annotation.StringRes
import com.hazel.android.R

/**
 * A place a search can be sent.
 *
 * [newPipeService] names the NewPipe service that can answer it in-process, with the content
 * filter that keeps the answer to playable items; [ytDlpPrefix] is yt-dlp's search key for
 * the same site. A source may have either or both.
 */
enum class SearchSource(
    @param:StringRes val labelRes: Int,
    val newPipeService: String? = null,
    val newPipeFilter: String? = null,
    val ytDlpPrefix: String? = null,
    /** A search page yt-dlp reads as a list, for a site with no search key. */
    val ytDlpSearchUrl: String? = null
) {
    YOUTUBE(R.string.search_source_youtube, "YouTube", "videos", ytDlpPrefix = "ytsearch"),
    YOUTUBE_MUSIC(
        R.string.search_source_youtube_music, "YouTube", "music_songs",
        ytDlpSearchUrl = "https://music.youtube.com/search?q="
    ),
    SOUNDCLOUD(R.string.search_source_soundcloud, "SoundCloud", "tracks", ytDlpPrefix = "scsearch"),
    BANDCAMP(R.string.search_source_bandcamp, "Bandcamp"),
    BILIBILI(R.string.search_source_bilibili, ytDlpPrefix = "bilisearch"),
    NICONICO(R.string.search_source_niconico, ytDlpPrefix = "nicosearch"),
    PRX(R.string.search_source_prx, ytDlpPrefix = "prxstories"),
    ROKFIN(R.string.search_source_rokfin, ytDlpPrefix = "rkfnsearch");

    val readableByYtDlp: Boolean get() = ytDlpPrefix != null || ytDlpSearchUrl != null

    /** What yt-dlp is handed to run this search, or null when it cannot. */
    fun ytDlpTarget(query: String, count: Int): String? = when {
        ytDlpPrefix != null -> "$ytDlpPrefix$count:$query"
        ytDlpSearchUrl != null -> ytDlpSearchUrl + java.net.URLEncoder.encode(query, "UTF-8")
        else -> null
    }

    companion object {
        val DEFAULT = YOUTUBE

        fun fromName(name: String?): SearchSource =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
