package com.hazel.android.gallery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Progress of several pasted links being read on the Gallery tab, for the header; null when idle. */
object GalleryReading {

    data class Progress(val read: Int, val total: Int)

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress

    internal fun set(progress: Progress?) {
        _progress.value = progress
    }
}
