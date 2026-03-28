package com.hazel.android.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import com.hazel.android.R
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        private const val ACTION_DISMISS = "com.hazel.android.ACTION_DISMISS"
    }

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

        // Custom dismiss command
        val dismissCommand = SessionCommand(ACTION_DISMISS, Bundle.EMPTY)
        val dismissButton = CommandButton.Builder()
            .setDisplayName("Close")
            .setIconResId(R.drawable.ic_close)
            .setSessionCommand(dismissCommand)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCustomLayout(ImmutableList.of(dismissButton))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult
                        .DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                        .add(dismissCommand)
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == ACTION_DISMISS) {
                        session.player.stop()
                        session.player.clearMediaItems()
                        // Release player & session
                        session.player.release()
                        session.release()
                        mediaSession = null

                        // Force remove notification and stop service
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        notificationManager.cancelAll()
                        stopSelf()

                        // Wait for service to tear down then kill app
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }, 300)
                    }
                    return Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_SUCCESS)
                    )
                }
            })
            .build()

        // Use our app icon for the notification
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification)
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: run {
            stopSelf()
            return
        }
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
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
