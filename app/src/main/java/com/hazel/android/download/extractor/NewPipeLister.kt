package com.hazel.android.download.extractor

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
 * Its whole job is the question "one item or several, and if several, which ones". It never
 * reads formats and never downloads: the entries it returns are ordinary page addresses,
 * handed to yt-dlp exactly as a pasted link would be, so nothing about the download path
 * changes for a link that came from here. That boundary is what makes falling back safe,
 * since [MediaProbe] can answer the same question from the same input.
 *
 * It is tried first because it answers in one request where yt-dlp answers in a process, and
 * it pages properly through long playlists. It is pinned at build time, though, while the
 * yt-dlp binary updates itself in the field, so it is treated as an accelerator rather than
 * as the source of truth: every failure here is silent and falls through.
 *
 * Which sites it covers is never hardcoded. [handles] asks the library whether any of its
 * services claims the address, so the sites it supports today and the ones it gains later
 * are picked up without naming any of them.
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
     *
     * A plain single item answers false here and never reaches the network path below,
     * which is what keeps the common case exactly as fast as it was.
     */
    fun handlesCollection(url: String): Boolean {
        val service = service(url) ?: return false
        return runCatching { service.playlistLHFactory.acceptUrl(url) }.getOrDefault(false) ||
                runCatching { service.channelLHFactory.acceptUrl(url) }.getOrDefault(false)
    }

    /**
     * Lists a collection, paging until the source runs out.
     *
     * Returns null on any failure, which the caller reads as "ask yt-dlp instead". Nothing
     * here reports an error to the user: a link the extractor cannot read is still a link
     * yt-dlp very likely can.
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
     * Reads one item's descriptive metadata: what a card needs, and nothing else.
     *
     * Formats are pointedly not read here even though the extractor reports them. Its stream
     * ids are itags, and yt-dlp names some of the same streams differently once a video
     * carries several audio tracks, so a format chosen from these could fail at download
     * time, after the user picked it. yt-dlp stays the only source of formats.
     */
    suspend fun single(url: String): LinkEntry? = withContext(Dispatchers.IO) {
        val service = service(url) ?: return@withContext null
        val accepted = runCatching { service.streamLHFactory.acceptUrl(url) }.getOrDefault(false)
        if (!accepted) return@withContext null

        runCatching {
            val info = StreamInfo.getInfo(service, url)
            LinkEntry(
                url = info.url?.takeIf { it.isNotBlank() } ?: url,
                title = info.name.orEmpty(),
                uploader = info.uploaderName.orEmpty().removeSuffix(" - Topic"),
                thumbnail = info.thumbnails?.maxByOrNull { it.height }?.url
                    ?.takeIf { it.isNotBlank() },
                durationSeconds = info.duration.toInt().coerceAtLeast(0)
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
                // Artwork comes from whatever the source offered, largest first, rather
                // than from an address built for one particular site.
                thumbnail = stream.thumbnails
                    ?.maxByOrNull { it.height }
                    ?.url
                    ?.takeIf { it.isNotBlank() },
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
