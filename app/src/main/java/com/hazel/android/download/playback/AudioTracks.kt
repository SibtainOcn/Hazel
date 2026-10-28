package com.hazel.android.download.playback

/**
 * One sound track a video offers, such as its original language or a dub.
 *
 * [url] is empty for a track inside a DASH or HLS manifest, which the player switches itself.
 */
data class AudioTrack(
    val id: String,
    val language: String?,
    val original: Boolean = false,
    val descriptive: Boolean = false,
    val url: String = "",
    val headers: Map<String, String> = emptyMap()
)

/** What the listener asked to hear: a track they picked for this video, else a language. */
data class AudioWish(val trackId: String? = null, val language: String? = null)

object AudioTracks {

    /** Stored preference: the track the video was made in. */
    const val ORIGINAL = ""

    /** Stored preference: the language the app is shown in. */
    const val APP = "app"

    /**
     * The track to play: the one picked for this video, then one in the wished language, then
     * the original. Audio description is taken only when it was asked for by name.
     */
    fun choose(tracks: List<AudioTrack>, wish: AudioWish): AudioTrack? {
        if (tracks.isEmpty()) return null
        wish.trackId?.let { id -> tracks.firstOrNull { it.id == id }?.let { return it } }
        val plain = tracks.filterNot { it.descriptive }.ifEmpty { tracks }
        wish.language?.takeIf { it.isNotBlank() }?.let { wanted ->
            matching(plain, wanted)?.let { return it }
        }
        return plain.firstOrNull { it.original } ?: plain.first()
    }

    /** Same tag first, then the same base language, so "en" finds "en-US". */
    private fun matching(tracks: List<AudioTrack>, wanted: String): AudioTrack? {
        val base = wanted.substringBefore('-').lowercase()
        return tracks.firstOrNull { it.language.equals(wanted, ignoreCase = true) }
            ?: tracks.firstOrNull { it.language?.substringBefore('-')?.lowercase() == base }
    }
}
