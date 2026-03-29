package com.hazel.android.player

import android.app.PendingIntent
import android.content.Intent
import com.hazel.android.R
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Standard MediaSession with default layout (perfectly symmetric)
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        // Use our app icon for the notification
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification)
        setMediaNotificationProvider(notificationProvider)
    }

    private fun clearAndKillService(session: MediaSession? = mediaSession) {
        val player = session?.player
        player?.pause()
        player?.stop()
        player?.clearMediaItems()
        
        // Force remove notification and stop service
        stopForeground(STOP_FOREGROUND_REMOVE)
        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancelAll()
        stopSelf()
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            android.os.Process.killProcess(android.os.Process.myPid())
        }, 300)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Auto clear notification and kill playback when app is swiped away
        clearAndKillService(mediaSession)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
