package com.hazel.android.ui.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hazel.android.download.MediaInfo
import com.hazel.android.download.playback.PlaybackController

/**
 * The one playing link, held above the screens so the video keeps playing in the mini
 * player while another tab or screen is open.
 */
@Stable
class PlayerSession {
    var playingUrl by mutableStateOf<String?>(null)
        private set
    var fullscreen by mutableStateOf(false)
    val dock = PlayerDock()

    /** Set when the large or full screen view asks for the download sheet; Home opens it. */
    var sheetRequested by mutableStateOf(false)

    /** A card starts playing in place. */
    fun play(url: String) {
        playingUrl = url
        dock.start(url)
    }

    /** A link starts straight in the large view. */
    fun playLarge(url: String) {
        fullscreen = false
        playingUrl = url
        dock.startLarge(url)
    }

    fun stop() {
        playingUrl = null
        fullscreen = false
        dock.start(null)
    }

    /** True while the video is out of its card, in the mini or large view. */
    val outOfCard: Boolean get() = playingUrl != null && (dock.place != PlayerPlace.CARD || dock.docking)
}

/**
 * The mini, large and full screen views of [session], drawn over every screen. [results]
 * are the links read on Home; the player stops once its link is no longer among them.
 */
@Composable
fun PlayerOverlay(
    session: PlayerSession,
    playback: PlaybackController?,
    results: List<MediaInfo>,
    onDownload: (MediaInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val url = session.playingUrl
    LaunchedEffect(results, url) {
        if (url != null && results.none { it.url == url }) session.stop()
    }
    if (playback == null) return
    val info = results.firstOrNull { it.url == url }
    val dock = session.dock

    MiniPlayer(
        dock = dock,
        controller = playback,
        thumbnail = info?.thumbnail,
        fullscreen = session.fullscreen,
        onClose = session::stop,
        modifier = modifier
    )

    if (dock.place == PlayerPlace.LARGE) {
        LargePlayer(
            dock = dock,
            controller = playback,
            title = info?.title,
            author = info?.uploader,
            thumbnail = info?.thumbnail,
            fullscreen = session.fullscreen,
            onFullscreen = { session.fullscreen = true },
            onClose = session::stop,
            onDownload = info?.let { { dock.collapse(); onDownload(it) } }
        )
    }

    if (session.fullscreen) {
        FullscreenPlayer(
            controller = playback,
            thumbnail = info?.thumbnail,
            onExit = { session.fullscreen = false },
            onDownload = info?.let { { session.fullscreen = false; onDownload(it) } }
        )
    }
}
