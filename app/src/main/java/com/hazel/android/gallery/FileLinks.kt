package com.hazel.android.gallery

/** Facts about a site's file links that decide whether one can still be used. */
internal object FileLinks {

    private val EXPIRY = Regex("[?&]oe=([0-9A-Fa-f]{6,10})(?:&|$)")

    /** A link expiring within this many seconds is treated as expired. */
    private const val MARGIN_SECONDS = 600L

    /**
     * True when [url] carries its expiry (Instagram and Facebook files: `oe=` in hex Unix
     * seconds) and that time has passed or is minutes away. A link without one is never stale.
     */
    fun isStale(url: String, nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
        val at = EXPIRY.find(url)?.groupValues?.get(1)?.toLongOrNull(16) ?: return false
        return at - MARGIN_SECONDS <= nowSeconds
    }

    /** The file's own name, which stays the same between reads while the signed query changes. */
    fun fileKey(url: String?): String? =
        url?.substringBefore('?')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }

    /**
     * Fresh play links for [old] from a new read of the same post ([listed]), by each file's
     * preview name, or by position when nothing else in the post moved. Keyed by the old [GalleryItem.url].
     */
    fun freshLinks(old: List<GalleryItem>, listed: List<GalleryItem>): Map<String, String> {
        val byFile = listed.mapNotNull { item ->
            val key = fileKey(item.thumbnail) ?: return@mapNotNull null
            item.playUrl?.let { key to it }
        }.toMap()
        val sameShape = old.size == listed.size
        return old.mapIndexedNotNull { position, item ->
            if (!item.isVideo) return@mapIndexedNotNull null
            val url = fileKey(item.thumbnail)?.let(byFile::get)
                ?: listed.getOrNull(position)?.takeIf { sameShape && it.isVideo }?.playUrl
            url?.let { item.url to it }
        }.toMap()
    }
}
