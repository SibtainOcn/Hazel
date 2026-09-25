package com.hazel.android.download.extractor

import com.hazel.android.download.MediaFormat
import com.hazel.android.download.MediaInfo
import com.hazel.android.download.MediaProbe
import com.hazel.android.util.UrlExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * Lists what a link holds, on the sites this extractor knows.
 *
 * It is tried first because it answers in one in-process network request without Python
 * startup overhead, returning metadata and formats in ~200ms. yt-dlp binary stays the
 * universal fallback for non-supported sites or any parsing errors.
 */
object NewPipeLister {

    private val started = AtomicBoolean(false)

    private fun service(url: String): StreamingService? = runCatching {
        if (started.compareAndSet(false, true)) {
            NewPipe.init(NewPipeDownloader())
        }
        NewPipe.getServiceByUrl(url)
    }.getOrNull()

    /**
     * Whether this link is a collection the extractor recognises, decided without a single
     * request: the services match the address against the shapes they own.
     */
    fun handlesCollection(url: String): Boolean {
        val service = service(url) ?: return false
        return runCatching { service.playlistLHFactory.acceptUrl(url) }.getOrDefault(false) ||
                runCatching { service.channelLHFactory.acceptUrl(url) }.getOrDefault(false)
    }

    /**
     * Whether this address is recognized by an extractor service as an individual media stream,
     * decided instantly by matching address patterns without network overhead.
     */
    fun handlesStream(url: String): Boolean {
        val service = service(url) ?: return false
        return runCatching { service.streamLHFactory.acceptUrl(url) }.getOrDefault(false)
    }

    /**
     * Lists a collection, paging until the source runs out.
     */
    suspend fun list(url: String): LinkContents.Many? = withContext(Dispatchers.IO) {
        val service = service(url) ?: return@withContext null

        val isPlaylist = runCatching { service.playlistLHFactory.acceptUrl(url) }
            .getOrDefault(false)

        val result = runCatching {
            if (isPlaylist) listPlaylist(service, url) else listChannel(service, url)
        }.getOrNull()

        result?.takeIf { it.entries.isNotEmpty() }
    }

    /**
     * Reads one item's descriptive metadata AND concrete stream formats directly from the in-process
     * extractor in ~200ms, mapping YouTube itag streams to concrete [MediaFormat] entries.
     */
    suspend fun single(url: String): MediaInfo? = withContext(Dispatchers.IO) {
        val service = service(url) ?: return@withContext null
        val accepted = runCatching { service.streamLHFactory.acceptUrl(url) }.getOrDefault(false)
        if (!accepted) return@withContext null

        runCatching {
            val info = StreamInfo.getInfo(service, url)
            val duration = info.duration.toInt().coerceAtLeast(0)
            val rawThumb = info.thumbnails.maxByOrNull { it.height }?.url?.takeIf { it.isNotBlank() }
            val thumb = UrlExtractor.extractYouTubeId(url)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" } ?: rawThumb

            val videoList = mutableListOf<MediaFormat>()
            val audioList = mutableListOf<MediaFormat>()

            // 1. Process video streams (both video-only and muxed video+audio)
            val allVideoStreams = (info.videoOnlyStreams.orEmpty() + info.videoStreams.orEmpty())
            for (stream in allVideoStreams) {
                if (stream.bitrate == 0) continue
                val itagStr = stream.itag.toString()
                val height = stream.height
                val container = stream.format?.name?.lowercase() ?: "mp4"
                val codec = stream.codec?.takeIf { it.isNotBlank() }
                val isMuxed = stream in info.videoStreams.orEmpty()
                val bitrateKbps = if (stream.bitrate > 0) stream.bitrate / 1000.0 else 0.0
                val exactSize = stream.itagItem?.contentLength?.takeIf { it > 0 }
                val estimatedSize = if (bitrateKbps > 0 && duration > 0) (bitrateKbps * 1000.0 / 8.0 * duration).toLong() else 0L

                val resolutionNote = stream.itagItem?.getResolutionString() ?: stream.quality ?: "${height}p"
                val label = resolutionNote

                videoList.add(
                    MediaFormat(
                        formatId = itagStr,
                        selector = itagStr,
                        label = label,
                        ext = container,
                        vcodec = codec,
                        acodec = if (isMuxed) "aac" else null,
                        height = height,
                        fps = 0,
                        bitrateKbps = bitrateKbps,
                        fileSizeBytes = exactSize ?: estimatedSize,
                        isEstimatedSize = exactSize == null,
                        hasVideo = true,
                        hasAudio = isMuxed
                    )
                )
            }

            // 2. Process audio streams
            for (stream in info.audioStreams.orEmpty()) {
                if (stream.bitrate == 0 || stream.itag in listOf(599, 600)) continue
                val itagStr = stream.itag.toString()
                val container = stream.format?.name?.lowercase() ?: "m4a"
                val codec = stream.codec?.takeIf { it.isNotBlank() }
                val bitrateKbps = if (stream.bitrate > 0) stream.bitrate / 1000.0 else 0.0
                val exactSize = stream.itagItem?.contentLength?.takeIf { it > 0 }
                val estimatedSize = if (bitrateKbps > 0 && duration > 0) (bitrateKbps * 1000.0 / 8.0 * duration).toLong() else 0L
                val trackName = stream.audioTrackName ?: "${bitrateKbps.toInt()} kbps"
                val label = "$trackName (${container.uppercase()})"
                val lang = stream.audioLocale?.language?.takeIf { it.isNotBlank() }

                audioList.add(
                    MediaFormat(
                        formatId = itagStr,
                        selector = itagStr,
                        label = label,
                        language = lang,
                        ext = container,
                        vcodec = null,
                        acodec = codec,
                        height = 0,
                        fps = 0,
                        bitrateKbps = bitrateKbps,
                        fileSizeBytes = exactSize ?: estimatedSize,
                        isEstimatedSize = exactSize == null,
                        hasVideo = false,
                        hasAudio = true
                    )
                )
            }

            // De-duplicate formats by formatId (keeping best bitrate if duplicate)
            val uniqueVideo = videoList.groupBy { it.formatId }
                .map { (_, list) -> list.maxByOrNull { it.bitrateKbps }!! }
                .sortedWith(compareByDescending<MediaFormat> { it.height }.thenByDescending { it.bitrateKbps })

            val uniqueAudio = audioList.groupBy { it.formatId }
                .map { (_, list) -> list.maxByOrNull { it.bitrateKbps }!! }
                .sortedWith(compareByDescending<MediaFormat> { it.bitrateKbps }.thenByDescending { it.fileSizeBytes })

            val finalVideo = (listOf(MediaProbe.BEST_VIDEO) + uniqueVideo).distinctBy { it.formatId }
            val finalAudio = (listOf(MediaProbe.BEST_AUDIO) + uniqueAudio).distinctBy { it.formatId }

            MediaInfo(
                url = info.url?.takeIf { it.isNotBlank() } ?: url,
                title = info.name.orEmpty(),
                uploader = info.uploaderName.orEmpty().removeSuffix(" - Topic"),
                thumbnail = thumb,
                durationSeconds = duration,
                videoFormats = finalVideo,
                audioFormats = finalAudio
            )
        }.getOrNull()?.takeIf { it.title.isNotBlank() }
    }

    private suspend fun listPlaylist(service: StreamingService, url: String): LinkContents.Many {
        val first = PlaylistInfo.getInfo(service, url)
        val entries = mutableListOf<LinkEntry>()

        collect(first.relatedItems, entries)

        var page: Page? = if (first.hasNextPage()) first.nextPage else null
        var pagesRead = 0

        while (page != null && entries.size < MAX_ENTRIES && pagesRead < MAX_PAGES) {
            coroutineContext.ensureActive()
            val more = PlaylistInfo.getMoreItems(service, url, page)
            val before = entries.size
            collect(more.items, entries)
            // A page that adds nothing means the source is repeating itself, and following
            // its next pointer would loop rather than advance.
            if (entries.size == before) break
            page = if (more.hasNextPage()) more.nextPage else null
            pagesRead++
        }

        return LinkContents.Many(first.name.orEmpty(), entries)
    }

    /**
     * Lists a channel by walking the tabs that hold media.
     *
     * A channel is not one list but several, and the tabs that are not media, such as the
     * about page, would contribute nothing but still cost a request each.
     */
    private suspend fun listChannel(service: StreamingService, url: String): LinkContents.Many {
        val channel = ChannelInfo.getInfo(service, url)
        val entries = mutableListOf<LinkEntry>()

        for (tab in channel.tabs) {
            if (entries.size >= MAX_ENTRIES) break
            if (!tab.holdsMedia()) continue
            coroutineContext.ensureActive()
            runCatching { collectTab(service, tab, entries) }
        }

        return LinkContents.Many(channel.name.orEmpty(), entries)
    }

    private suspend fun collectTab(
        service: StreamingService,
        tab: ListLinkHandler,
        entries: MutableList<LinkEntry>
    ) {
        val first = ChannelTabInfo.getInfo(service, tab)
        collect(first.relatedItems, entries)

        var page: Page? = if (first.hasNextPage()) first.nextPage else null
        var pagesRead = 0

        while (page != null && entries.size < MAX_ENTRIES && pagesRead < MAX_PAGES) {
            coroutineContext.ensureActive()
            val more = ChannelTabInfo.getMoreItems(service, tab, page)
            val before = entries.size
            collect(more.items, entries)
            if (entries.size == before) break
            page = if (more.hasNextPage()) more.nextPage else null
            pagesRead++
        }
    }

    /** True for the tabs that list media, rather than the ones describing the channel. */
    private fun ListLinkHandler.holdsMedia(): Boolean =
        contentFilters.any { it in MEDIA_TABS }

    private fun collect(items: List<Any?>, into: MutableList<LinkEntry>) {
        for (item in items) {
            if (into.size >= MAX_ENTRIES) return
            val stream = item as? StreamInfoItem ?: continue
            val address = stream.url?.takeIf { it.isNotBlank() } ?: continue
            into += LinkEntry(
                url = address,
                title = stream.name.orEmpty(),
                uploader = stream.uploaderName.orEmpty().removeSuffix(" - Topic"),
                thumbnail = UrlExtractor.extractYouTubeId(address)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
                    ?: stream.thumbnails.maxByOrNull { it.height }?.url?.takeIf { it.isNotBlank() },
                durationSeconds = stream.duration.toInt().coerceAtLeast(0)
            )
        }
    }

    /**
     * Runaway guards rather than product limits. A playlist of any ordinary length finishes
     * well inside these; they exist so a channel with tens of thousands of uploads, or a
     * source whose paging never terminates, cannot page forever.
     */
    private const val MAX_ENTRIES = 5000
    private const val MAX_PAGES = 200

    private val MEDIA_TABS = setOf(
        ChannelTabs.VIDEOS,
        ChannelTabs.SHORTS,
        ChannelTabs.LIVESTREAMS,
        ChannelTabs.TRACKS,
        ChannelTabs.ALBUMS
    )
}
