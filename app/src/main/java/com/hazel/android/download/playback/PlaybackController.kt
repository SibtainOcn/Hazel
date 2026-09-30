package com.hazel.android.download.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One player for one link: finds the stream, plays it, and moves to the next way of finding
 * it when a stream fails to play.
 *
 * State is held in Compose state so the controls redraw on their own. Create it with
 * `rememberPlaybackController` so it is released with the screen that shows it.
 */
@OptIn(UnstableApi::class)
class PlaybackController(
    context: Context,
    private val scope: CoroutineScope,
    val url: String
) {
    enum class Phase { LOADING, READY, FAILED }

    val player: ExoPlayer = PlayerFactory.create(context)

    var phase by mutableStateOf(Phase.LOADING)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    /** How far ahead of the start the player has loaded. */
    var bufferedMs by mutableLongStateOf(0L)
        private set
    var hasVideo by mutableStateOf(true)
        private set
    /** Width over height of the picture, or 0 before the first frame. */
    var aspectRatio by mutableFloatStateOf(0f)
        private set
    /** The qualities this media can be played at, tallest first; empty until it is found. */
    var heights by mutableStateOf(emptyList<Int>())
        private set
    /** Lines in the picture now playing, or 0 before the first frame. */
    var videoHeight by mutableIntStateOf(0)
        private set
    var isMuted by mutableStateOf(false)
        private set

    /** Called each time the position moves, for a caller that bounds playback to a range. */
    var onProgress: ((Long) -> Unit)? = null

    private var attempt = 0
    private var loadJob: Job? = null
    private var ticker: Job? = null
    private var released = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onPlaybackStateChanged(state: Int) {
            isBuffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) {
                phase = Phase.READY
                durationMs = player.duration.coerceAtLeast(0L)
            }
        }

        override fun onVideoSizeChanged(size: VideoSize) {
            if (size.width > 0 && size.height > 0) {
                aspectRatio = size.width * size.pixelWidthHeightRatio / size.height
                videoHeight = minOf(size.width, size.height)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (attempt < StreamResolver.LAST_ATTEMPT) {
                attempt++
                load(resumeAt = player.currentPosition)
            } else {
                phase = Phase.FAILED
            }
        }
    }

    init {
        player.addListener(listener)
        ticker = scope.launch {
            while (isActive) {
                if (!released) {
                    positionMs = player.currentPosition.coerceAtLeast(0L)
                    bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
                    if (player.duration > 0) durationMs = player.duration
                    onProgress?.invoke(positionMs)
                }
                delay(TICK_MS)
            }
        }
    }

    /** Finds the stream and starts playing, from [startAtMs] when given. */
    fun start(startAtMs: Long = 0L, playWhenReady: Boolean = true) {
        attempt = 0
        player.playWhenReady = playWhenReady
        load(startAtMs)
    }

    private fun load(resumeAt: Long) {
        loadJob?.cancel()
        phase = Phase.LOADING
        loadJob = scope.launch {
            try {
                val stream = StreamResolver.resolve(url, attempt)
                if (released) return@launch
                hasVideo = stream.hasVideo
                heights = stream.heights
                player.setMediaSource(PlayerFactory.sourceFor(stream), resumeAt)
                player.prepare()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (attempt < StreamResolver.LAST_ATTEMPT) {
                    attempt++
                    load(resumeAt)
                } else {
                    phase = Phase.FAILED
                }
            }
        }
    }

    fun retry() = start(positionMs, playWhenReady = true)

    /** Finds the stream again, for a changed quality, and carries on from the same moment. */
    fun reload() = start(player.currentPosition.coerceAtLeast(0L), playWhenReady = player.playWhenReady)

    fun play() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
        player.play()
    }

    fun pause() = player.pause()

    fun toggle() = if (player.isPlaying) pause() else play()

    fun seekTo(ms: Long) {
        player.seekTo(ms.coerceAtLeast(0L))
        positionMs = ms.coerceAtLeast(0L)
    }

    /** Moves by [deltaMs], forwards or back, without leaving the media. */
    fun seekBy(deltaMs: Long) {
        val end = durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        seekTo((player.currentPosition + deltaMs).coerceIn(0L, end))
    }

    fun mute(muted: Boolean) {
        player.volume = if (muted) 0f else 1f
        isMuted = muted
    }

    fun release() {
        if (released) return
        released = true
        loadJob?.cancel()
        ticker?.cancel()
        player.removeListener(listener)
        player.release()
    }

    private companion object {
        const val TICK_MS = 200L
    }
}
