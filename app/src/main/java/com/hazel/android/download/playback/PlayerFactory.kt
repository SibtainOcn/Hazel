package com.hazel.android.download.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource

/**
 * Builds players and the sources they play. Media3 is set up here and nowhere else, so a
 * change in its API is fixed in this file.
 */
@OptIn(UnstableApi::class)
object PlayerFactory {

    fun create(context: Context): ExoPlayer {
        // A short start buffer, so playback begins as soon as a few seconds have arrived.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(START_BUFFER_MS, MAX_BUFFER_MS, PLAYBACK_BUFFER_MS, REBUFFER_MS)
            .build()
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        return ExoPlayer.Builder(context.applicationContext)
            .setLoadControl(loadControl)
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    fun sourceFor(stream: PlayableStream): MediaSource {
        val main = source(stream.url, stream.headers, stream.isHls)
        val audio = stream.audioUrl?.let { source(it, stream.audioHeaders, isHls = false) }
        return if (audio != null) MergingMediaSource(main, audio) else main
    }

    private fun source(url: String, headers: Map<String, String>, isHls: Boolean): MediaSource {
        val agent = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(agent ?: BROWSER_USER_AGENT)
            .setDefaultRequestProperties(headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) })
        val item = MediaItem.fromUri(url)
        return if (isHls || url.substringBefore('?').endsWith(".m3u8")) {
            HlsMediaSource.Factory(http).createMediaSource(item)
        } else {
            ProgressiveMediaSource.Factory(http).createMediaSource(item)
        }
    }

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val START_BUFFER_MS = 5_000
    private const val MAX_BUFFER_MS = 50_000
    private const val PLAYBACK_BUFFER_MS = 1_000
    private const val REBUFFER_MS = 2_500
}
