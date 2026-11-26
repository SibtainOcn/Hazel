package com.hazel.android.ui.screens.gallery

import androidx.annotation.OptIn
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.hazel.android.R
import com.hazel.android.download.extractor.ListingSource
import com.hazel.android.download.playback.PlayableStream
import com.hazel.android.download.playback.PlayerFactory
import com.hazel.android.download.playback.StreamResolver
import com.hazel.android.ui.components.HazelLoadingIndicator
import com.hazel.android.ui.components.ShapeIconButton
import com.hazel.android.ui.components.player.PlayerSurface
import com.hazel.android.ui.components.player.ScrubTime
import com.hazel.android.ui.components.player.drawPillThumb
import com.hazel.android.ui.components.player.drawSeekLine
import com.hazel.android.ui.components.player.drawWavyLine
import com.hazel.android.ui.components.player.formatClock
import com.hazel.android.ui.components.player.rememberSeekWave
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A gallery video played in place, in the app's own player and through the proxy.
 *
 * [direct] is the site's own file and plays at once. A file gallery-dl leaves to yt-dlp has
 * only its [page], which is read the way Home plays a link (sign-in, quality, separate
 * sound); a direct file that stops working (an expired address) falls back to it too.
 *
 * Nothing is loaded until Play is tapped. The player is released as soon as its page is no
 * longer the one shown (or the viewer closes), so swiping through a long set never holds
 * more than one decoder, and it pauses when the app goes to the background.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun GalleryVideo(
    direct: String?,
    page: String?,
    active: Boolean,
    /** Given the player while it exists, so the viewer can draw its seek bar; null once released. */
    onPlayer: (ExoPlayer?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val key = direct ?: page
    var player by remember(key) { mutableStateOf<ExoPlayer?>(null) }
    var playing by remember(key) { mutableStateOf(false) }
    var buffering by remember(key) { mutableStateOf(false) }
    var failed by remember(key) { mutableStateOf(false) }
    var directFailed by remember(key) { mutableStateOf(false) }
    var reading by remember(key) { mutableStateOf<Job?>(null) }
    var readId by remember(key) { mutableStateOf<String?>(null) }

    fun stopReading() {
        reading?.cancel()
        reading = null
        readId?.let(StreamResolver::stopRead)
        readId = null
    }

    fun start() {
        failed = false
        stopReading()
        val made = player ?: PlayerFactory.create(context).also { created ->
            created.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                override fun onPlaybackStateChanged(state: Int) {
                    buffering = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_ENDED) {
                        created.pause()
                        created.seekTo(0)
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (stream(created) == direct) directFailed = true
                    failed = true
                    buffering = false
                }
            })
            player = created
            onPlayer(created)
        }
        made.volume = if (muted) 0f else 1f
        val file = direct?.takeUnless { directFailed && page != null }
        if (file != null) {
            made.setMediaSource(PlayerFactory.sourceFor(PlayableStream(url = file, headers = HEADERS, engine = ListingSource.YT_DLP)))
            made.prepare()
            made.play()
            return
        }
        val link = page ?: return
        buffering = true
        val id = StreamResolver.newReadId().also { readId = it }
        reading = scope.launch {
            try {
                val stream = StreamResolver.resolve(link, processId = id)
                made.setMediaSource(PlayerFactory.sourceFor(stream))
                PlayerFactory.applyAudio(made, stream)
                made.prepare()
                made.play()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = true
                buffering = false
            } finally {
                if (readId == id) readId = null
            }
        }
    }

    DisposableEffect(key, active) {
        onDispose {
            stopReading()
            // Only a page that had a player clears the viewer's, so a neighbour leaving cannot.
            player?.let {
                it.release()
                onPlayer(null)
            }
            player = null
            playing = false
            buffering = false
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player?.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val current = player
    if (current == null || !active) {
        if (active) PlayButton(onClick = ::start, modifier = modifier)
        return
    }
    Box(modifier = modifier.fillMaxSize()) {
        PlayerSurface(
            player = current,
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (current.isPlaying) current.pause() else if (!failed) current.play()
                }
        )
        when {
            failed -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.player_failed), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                ShapeIconButton(onClick = ::start, filled = true, size = 56.dp) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.player_retry))
                }
            }
            buffering -> HazelLoadingIndicator(modifier = Modifier.align(Alignment.Center), size = 56.dp)
            !playing -> PlayButton(onClick = { current.play() }, modifier = Modifier.align(Alignment.Center))
        }
        // Top right sits clear of the viewer's close button and counter.
        ShapeIconButton(
            onClick = {
                muted = !muted
                current.volume = if (muted) 0f else 1f
            },
            size = 44.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(if (muted) R.string.player_unmute else R.string.player_mute),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun PlayButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ShapeIconButton(onClick = onClick, filled = true, size = 64.dp, modifier = modifier) {
        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.player_play), modifier = Modifier.size(32.dp))
    }
}

/** Instagram serves its video files only to a browser-like agent, as gallery-dl sends. */
private val HEADERS = mapOf("User-Agent" to "Mozilla/5.0")

/** Kept while the app runs, so a muted viewer stays muted from one video to the next. */
private var muted by mutableStateOf(false)

/** The address the player was last given, to tell a failed direct file from a read page. */
private fun stream(player: ExoPlayer): String? = player.currentMediaItem?.localConfiguration?.uri?.toString()

/**
 * Time played, the seek bar and the full length, drawn like the video player on Home: the
 * played part flows as a wave while it plays, a finger lays it flat, lengthens the thumb and
 * shows the time under it, and the jump happens on release. A tap jumps at once. The time is
 * read four times a second while playing, once a second while paused, and only while shown.
 */
@Composable
internal fun VideoSeekRow(player: ExoPlayer, modifier: Modifier = Modifier) {
    var position by remember(player) { mutableLongStateOf(0L) }
    var buffered by remember(player) { mutableLongStateOf(0L) }
    var duration by remember(player) { mutableLongStateOf(0L) }
    var playing by remember(player) { mutableStateOf(player.isPlaying) }
    var scrub by remember(player) { mutableStateOf<Float?>(null) }
    LaunchedEffect(player) {
        while (isActive) {
            position = player.currentPosition.coerceAtLeast(0L)
            buffered = player.bufferedPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0 } ?: 0L
            playing = player.isPlaying
            delay(if (playing) SEEK_TICK_PLAYING_MS else SEEK_TICK_PAUSED_MS)
        }
    }
    val length by rememberUpdatedState(duration)
    val enabled = duration > 0
    val seek = { fraction: Float ->
        val to = (fraction.coerceIn(0f, 1f) * length).toLong()
        player.seekTo(to)
        position = to
    }
    val played = scrub ?: if (enabled) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val loaded = if (enabled) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val thumb by animateDpAsState(if (scrub != null) 22.dp else 16.dp, label = "gallerySeekThumb")
    val accent = MaterialTheme.colorScheme.primary
    val (wave, wavePhase) = rememberSeekWave(flowing = playing && scrub == null)

    Column(modifier = modifier.fillMaxWidth()) {
        scrub?.let { ScrubTime(text = formatClock((it * duration).toLong()), fraction = it, modifier = Modifier.fillMaxWidth()) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatClock((played * duration).toLong()),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(10.dp))
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .pointerInput(enabled, player) {
                        if (!enabled) return@pointerInput
                        detectTapGestures { offset -> seek(offset.x / size.width) }
                    }
                    .pointerInput(enabled, player) {
                        if (!enabled) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { offset -> scrub = (offset.x / size.width).coerceIn(0f, 1f) },
                            onDragEnd = {
                                scrub?.let(seek)
                                scrub = null
                            },
                            onDragCancel = { scrub = null }
                        ) { change, _ ->
                            change.consume()
                            scrub = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    }
            ) {
                val y = size.height / 2f
                val stroke = if (scrub != null) 4.dp.toPx() else 3.dp.toPx()
                drawSeekLine(y, stroke, 1f, Color.White.copy(alpha = 0.25f), from = played)
                drawSeekLine(y, stroke, loaded, Color.White.copy(alpha = 0.45f), from = played)
                drawWavyLine(y, stroke, played, accent, wave.value, wavePhase.value)
                drawPillThumb(size.width * played, y, accent, height = thumb.toPx(), width = 5.dp.toPx())
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                formatClock(duration),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.75f)
            )
        }
    }
}

private const val SEEK_TICK_PLAYING_MS = 250L
private const val SEEK_TICK_PAUSED_MS = 1000L
