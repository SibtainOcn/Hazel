package com.hazel.android.download.playback

import androidx.media3.common.C
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
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.extractor.ListingSource
import com.hazel.android.download.extractor.newpipe.NewPipeEngine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
    val url: String,
    /** Title, author and artwork for the media notification and lock screen; null when none is shown. */
    private val metadata: androidx.media3.common.MediaMetadata? = null
) {
    enum class Phase { LOADING, READY, FAILED }

    val player: ExoPlayer = PlayerFactory.create(context)
    private val app = context.applicationContext

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
    /** The sound tracks this video offers, when it has more than one; empty otherwise. */
    var audioTracks by mutableStateOf(emptyList<AudioTrack>())
        private set
    /** Which of [audioTracks] is playing. */
    var audioTrackId by mutableStateOf<String?>(null)
        private set

    /** Called each time the position moves, for a caller that bounds playback to a range. */
    var onProgress: ((Long) -> Unit)? = null

    private var attempt = 0
    private var loadJob: Job? = null
    private var readId: String? = null
    private var engineWatch: Job? = null
    private var ticker: Job? = null
    private var released = false
    private var quiet = false
    private var current: PlayableStream? = null
    /** The track picked in the player for this video, kept through reloads and retries. */
    private var pickedAudio: AudioTrack? = null

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

        override fun onTracksChanged(tracks: Tracks) {
            if (current?.isAdaptive != true) return
            audioTracks = PlayerFactory.manifestAudioTracks(tracks)
            audioTrackId = PlayerFactory.selectedManifestAudio(tracks)
            val picked = pickedAudio?.id
            if (picked != null && picked != audioTrackId && audioTracks.any { it.id == picked }) {
                PlayerFactory.selectManifestAudio(player, picked)
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
                delay(if (quiet) QUIET_TICK_MS else TICK_MS)
            }
        }
        // A play engine picked while this player is open applies to it straight away.
        engineWatch = scope.launch {
            SettingsRepository.getPlayEngine(app).distinctUntilChanged().drop(1).collect { engine ->
                if (!released && (phase == Phase.FAILED || changesStream(engine))) reload()
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
        stopRead()
        phase = Phase.LOADING
        val id = StreamResolver.newReadId().also { readId = it }
        loadJob = scope.launch {
            try {
                val stream = StreamResolver.resolve(url, attempt, pickedAudio, id)
                if (released) return@launch
                current = stream
                hasVideo = stream.hasVideo
                heights = stream.heights
                audioTracks = stream.audioTracks
                audioTrackId = stream.audioTrackId
                PlayerFactory.applyCap(player, stream)
                PlayerFactory.applyAudio(player, stream)
                player.setMediaSource(PlayerFactory.sourceFor(stream, metadata), resumeAt)
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

    /**
     * Saves [height] as the playback quality, on the settings' own steps, and plays it from the same
     * moment. Runs in the player's scope, so closing the dialog that asked for it does not cancel it.
     */
    fun changeQuality(height: Int) {
        if (released) return
        scope.launch {
            val step = SettingsRepository.PLAY_QUALITIES.firstOrNull { it >= height }
                ?: SettingsRepository.PLAY_QUALITIES.last()
            SettingsRepository.setPlayQuality(app, step)
            reload()
        }
    }

    /** Finds the stream again, for a changed quality, and carries on from the same moment. */
    fun reload() = start(player.currentPosition.coerceAtLeast(0L), playWhenReady = player.playWhenReady)

    /**
     * Plays the sound track [id] from the same moment. Only the sound is swapped where it is
     * kept apart from the picture; a manifest switches inside itself; anything else is found
     * again with the track asked for.
     */
    fun selectAudio(id: String) {
        if (released || id == audioTrackId) return
        val track = audioTracks.firstOrNull { it.id == id } ?: return
        pickedAudio = track
        val stream = current
        if (stream == null || loadJob?.isActive == true) {
            reload()
            return
        }
        if (stream.isAdaptive) {
            if (!PlayerFactory.selectManifestAudio(player, id)) reload()
            return
        }
        val swapped = stream.withAudio(track) ?: run {
            reload()
            return
        }
        current = swapped
        audioTrackId = id
        attempt = 0
        player.setMediaSource(PlayerFactory.sourceFor(swapped, metadata), player.currentPosition.coerceAtLeast(0L))
        player.prepare()
    }

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

    /**
     * Playing on with the app out of sight: the picture is switched off, so no video is
     * decoded (and an adaptive stream loads none) while nothing can show it, and the
     * position is read less often. Undone when the app comes back.
     */
    fun playOutOfSight(away: Boolean) {
        if (released || quiet == away) return
        quiet = away
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, away)
            .build()
    }

    fun mute(muted: Boolean) {
        player.volume = if (muted) 0f else 1f
        isMuted = muted
    }

    fun release() {
        if (released) return
        released = true
        loadJob?.cancel()
        stopRead()
        engineWatch?.cancel()
        ticker?.cancel()
        player.removeListener(listener)
        player.release()
    }

    /** Whether [engine] would play this link from another source than the one playing. */
    private fun changesStream(engine: ListingSource): Boolean {
        val playing = current?.engine ?: return true
        return playing != engine && (engine == ListingSource.YT_DLP || NewPipeEngine.handlesStream(url))
    }

    private fun stopRead() {
        readId?.let(StreamResolver::stopRead)
        readId = null
    }

    private companion object {
        const val TICK_MS = 200L
        const val QUIET_TICK_MS = 1_000L
    }
}
