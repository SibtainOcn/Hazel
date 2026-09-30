package com.hazel.android.data

import com.hazel.android.util.StoragePaths

/**
 * Where one kind of download is saved: a folder the user picked, or blank for the built-in
 * one. The label is the picked folder's readable form, kept so nothing has to ask the
 * document provider for it on every frame.
 */
data class SaveDir(val uri: String = "", val label: String = "") {
    val isCustom: Boolean get() = uri.isNotBlank()
}

/** The destinations of audio and video downloads, set and kept apart. */
data class SaveDirs(val audio: SaveDir = SaveDir(), val video: SaveDir = SaveDir()) {
    fun of(isVideo: Boolean): SaveDir = if (isVideo) video else audio

    /** What the screens show for a kind: the picked folder, or the built-in one. */
    fun labelOf(isVideo: Boolean): String {
        val dir = of(isVideo)
        return if (dir.isCustom) dir.label.ifBlank { dir.uri }
        else StoragePaths.downloadsDisplay(isAudio = !isVideo)
    }
}
