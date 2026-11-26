package com.hazel.android.ui.screens.gallery

import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.hazel.android.ui.components.HazelLoadingIndicator
import com.hazel.android.ui.components.ShapeIconButton
import com.hazel.android.ui.components.player.PlayerSurface

/**
 * A gallery video played in place, in the app's own player and through the proxy.
 *
 * Nothing is loaded until Play is tapped. The player is released as soon as its page is no
 * longer the one shown (or the viewer closes), so swiping through a long set never holds
 * more than one decoder, and it pauses when the app goes to the background.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun GalleryVideo(url: String, active: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var player by remember(url) { mutableStateOf<ExoPlayer?>(null) }
    var playing by remember(url) { mutableStateOf(false) }
    var buffering by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }

    fun start() {
        failed = false
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
                    failed = true
                    buffering = false
                }
            })
            player = created
        }
        made.setMediaSource(PlayerFactory.sourceFor(PlayableStream(url = url, headers = HEADERS, engine = ListingSource.YT_DLP)))
        made.volume = if (muted) 0f else 1f
        made.prepare()
        made.play()
    }

    DisposableEffect(url, active) {
        onDispose {
            player?.release()
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
