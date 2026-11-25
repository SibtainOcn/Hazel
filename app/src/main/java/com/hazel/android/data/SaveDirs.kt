package com.hazel.android.data

import com.hazel.android.util.StoragePaths
import java.io.File

/**
 * Where one kind of download is saved: a folder the user picked, or blank for the built-in
 * one. The label is the picked folder's readable form, kept so nothing has to ask the
 * document provider for it on every frame.
 */
data class SaveDir(val uri: String = "", val label: String = "") {
    val isCustom: Boolean get() = uri.isNotBlank()
}

/** The kinds of download that each keep their own destination. */
enum class SaveKind(val folderName: String, val relativePath: String, val display: String) {
    AUDIO("Audio", StoragePaths.DOWNLOAD_AUDIO_RELATIVE_PATH, StoragePaths.DOWNLOADS_AUDIO_DISPLAY),
    VIDEO("Video", StoragePaths.DOWNLOAD_VIDEO_RELATIVE_PATH, StoragePaths.DOWNLOADS_VIDEO_DISPLAY),
    GALLERY("Gallery", StoragePaths.DOWNLOAD_GALLERY_RELATIVE_PATH, StoragePaths.DOWNLOADS_GALLERY_DISPLAY);

    fun builtInFolder(): File = File(StoragePaths.finalDownloads, folderName)

    companion object {
        fun of(isVideo: Boolean): SaveKind = if (isVideo) VIDEO else AUDIO
    }
}

/** The destinations of audio, video and gallery downloads, set and kept apart. */
data class SaveDirs(
    val audio: SaveDir = SaveDir(),
    val video: SaveDir = SaveDir(),
    val gallery: SaveDir = SaveDir()
) {
    fun of(isVideo: Boolean): SaveDir = of(SaveKind.of(isVideo))

    fun of(kind: SaveKind): SaveDir = when (kind) {
        SaveKind.AUDIO -> audio
        SaveKind.VIDEO -> video
        SaveKind.GALLERY -> gallery
    }

    /** What the screens show for a kind: the picked folder, or the built-in one. */
    fun labelOf(isVideo: Boolean): String = labelOf(SaveKind.of(isVideo))

    fun labelOf(kind: SaveKind): String {
        val dir = of(kind)
        return if (dir.isCustom) dir.label.ifBlank { dir.uri } else kind.display
    }
}
