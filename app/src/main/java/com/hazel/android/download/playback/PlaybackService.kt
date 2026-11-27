package com.hazel.android.download.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.hazel.android.HazelActivity
import com.hazel.android.R
import com.hazel.android.download.MediaInfo

/**
 * Keeps Home's player going once the app is left, with play/pause on a media notification
 * and the lock screen. It owns no player: the one playing in the app is lent to it through
 * [BackgroundPlayback] while "Keep playing in background" is on.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.play_background_channel)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) }
        )
        val session = BackgroundPlayback.session
        if (session == null) {
            stopSelf()
            return
        }
        runCatching { addSession(session) }.onFailure {
            Log.w(TAG, "Media session not added: ${it.javaClass.simpleName}")
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = BackgroundPlayback.session

    // The player lives with the app's screen, so a swipe from recents ends playback here too.
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    private companion object {
        const val CHANNEL_ID = "playback"
        const val TAG = "HazelPlayback"
    }
}

/** The one player lent to [PlaybackService]; used on the main thread only. */
@OptIn(UnstableApi::class)
object BackgroundPlayback {
    private const val TAG = "HazelPlayback"
    private const val SESSION_ID = "home"

    var session: MediaSession? = null
        private set

    /** Lends [player] to the service and starts it. Called while the app is on screen. */
    @MainThread
    fun attach(context: Context, player: ExoPlayer) {
        if (session?.player === player) return
        session?.let { detach(context, it.player as? ExoPlayer ?: return@let) }
        val app = context.applicationContext
        val open = PendingIntent.getActivity(
            app, 0,
            Intent(app, HazelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val built = runCatching {
            MediaSession.Builder(app, player)
                .setId(SESSION_ID)
                .setSessionActivity(open)
                .setBitmapLoader(PlayerFactory.bitmapLoader(app))
                .build()
        }.getOrElse {
            Log.w(TAG, "Media session not built: ${it.javaClass.simpleName}")
            return
        }
        session = built
        // Streaming with the screen off needs the CPU and Wi-Fi kept awake while it plays.
        player.setWakeMode(C.WAKE_MODE_NETWORK)
        runCatching { app.startService(Intent(app, PlaybackService::class.java)) }.onFailure {
            Log.w(TAG, "Playback service not started: ${it.javaClass.simpleName}")
            detach(app, player)
        }
    }

    /** Takes [player] back from the service, if it is the one lent, and stops the service. */
    @MainThread
    fun detach(context: Context, player: ExoPlayer) {
        val lent = session ?: return
        if (lent.player !== player) return
        session = null
        val app = context.applicationContext
        runCatching { app.stopService(Intent(app, PlaybackService::class.java)) }
        runCatching { player.setWakeMode(C.WAKE_MODE_NONE) }
        lent.release()
    }

    /** What the notification and lock screen show for [info]. */
    fun metadataOf(info: MediaInfo): MediaMetadata = MediaMetadata.Builder()
        .setTitle(info.title.ifBlank { info.url })
        .setArtist(info.uploader.takeIf { it.isNotBlank() })
        .setArtworkUri(info.thumbnail?.takeIf { it.startsWith("http") }?.let(android.net.Uri::parse))
        .build()
}
