package com.hazel.android.gallery

/** Largest image the site is asked for. Only X serves smaller sizes; other sites keep the original. */
enum class GalleryImageSize(val xSizes: String) {
    ORIGINAL("orig,4096x4096,large,medium,small"),
    LARGE("large,medium,small"),
    MEDIUM("medium,small");

    companion object {
        fun fromName(name: String?): GalleryImageSize = entries.firstOrNull { it.name == name } ?: ORIGINAL
    }
}

/** How a post's video is saved. */
enum class GalleryVideoMode {
    /** Best video and audio, merged by yt-dlp with ffmpeg. */
    BEST,
    /** The site's ready-made single file, without merging. */
    SINGLE;

    companion object {
        fun fromName(name: String?): GalleryVideoMode = entries.firstOrNull { it.name == name } ?: BEST
    }
}

/** What a download saves: the files, the caption as a text file, or both. */
enum class GallerySave {
    MEDIA, CAPTION, BOTH;

    val media: Boolean get() = this != CAPTION
    val caption: Boolean get() = this != MEDIA

    companion object {
        fun fromName(name: String?): GallerySave = entries.firstOrNull { it.name == name } ?: MEDIA
    }
}

/** Choices for one gallery download; the saved defaults come from settings. */
data class GalleryOptions(
    val imageSize: GalleryImageSize = GalleryImageSize.ORIGINAL,
    val videoMode: GalleryVideoMode = GalleryVideoMode.BEST,
    /** gallery-dl filename format such as `{username}_{num}.{extension}`; blank keeps the site's default. */
    val filenameTemplate: String = "",
    val writeMetadata: Boolean = false,
    val keepPostDate: Boolean = true,
    val packZip: Boolean = false,
    val skipDownloaded: Boolean = false,
    /** The post's caption is written into each JPEG and PNG saved. */
    val embedCaption: Boolean = false
) {
    /** The template is a name, never a path, so files cannot land outside the work folder. */
    val safeTemplate: String?
        get() = filenameTemplate.trim().takeIf { it.isNotEmpty() && '/' !in it && '\\' !in it && ".." !in it }

    internal fun toArgs(archive: java.io.File?): List<String> = buildList {
        add("--option"); add("extractor.twitter.size=${imageSize.xSizes}")
        if (videoMode == GalleryVideoMode.SINGLE) { add("--option"); add("extractor.instagram.videos=merged") }
        safeTemplate?.let { add("--filename"); add(it) }
        if (writeMetadata) add("--write-metadata")
        if (!keepPostDate) add("--no-mtime")
        if (packZip) add("--zip")
        if (skipDownloaded && archive != null) { add("--download-archive"); add(archive.absolutePath) }
    }
}
