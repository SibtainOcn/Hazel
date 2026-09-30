package com.hazel.android.download.extractor.newpipe

import com.hazel.android.download.extractor.LinkEntry
import com.hazel.android.util.UrlExtractor
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Searches one NewPipe service, keeping only playable items. */
internal object NewPipeSearch {

    fun search(serviceName: String, filter: String?, query: String, count: Int): List<LinkEntry> {
        val service = NewPipeLister.serviceNamed(serviceName) ?: return emptyList()
        val factory = service.searchQHFactory
        val filters = filter
            ?.takeIf { it in factory.availableContentFilter.orEmpty() }
            ?.let { listOf(it) }
            ?: emptyList()
        val handler = factory.fromQuery(query, filters, "")

        val found = mutableListOf<LinkEntry>()
        val first = SearchInfo.getInfo(service, handler)
        collect(first.relatedItems, found, count)

        var page: Page? = first.nextPage
        var pages = 0
        while (found.size < count && page != null && pages < MAX_PAGES) {
            val more = SearchInfo.getMoreItems(service, handler, page)
            collect(more.items, found, count)
            page = if (more.hasNextPage()) more.nextPage else null
            pages++
        }
        return found
    }

    private fun collect(items: List<InfoItem>, into: MutableList<LinkEntry>, count: Int) {
        for (item in items) {
            if (into.size >= count) return
            val stream = item as? StreamInfoItem ?: continue
            val address = stream.url?.takeIf { it.isNotBlank() } ?: continue
            if (into.any { it.url == address }) continue
            into += LinkEntry(
                url = address,
                title = stream.name.orEmpty(),
                uploader = stream.uploaderName.orEmpty().removeSuffix(" - Topic"),
                thumbnail = UrlExtractor.extractYouTubeId(address)
                    ?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
                    ?: stream.thumbnails.maxByOrNull { it.height }?.url?.takeIf { it.isNotBlank() },
                durationSeconds = stream.duration.toInt().coerceAtLeast(0)
            )
        }
    }

    private const val MAX_PAGES = 5
}
