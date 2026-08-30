package com.hazel.android.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Keeps a running download alive once the app is no longer on screen.
 *
 * Android suspends an app's work shortly after it stops being visible, and kills the process
 * outright when the task is swiped away. A download is minutes of network work that the user
 * has already asked for and walked away from, which is exactly the case a foreground service
 * exists for: while one is running the system leaves the process alone, so a set of ten links
 * finishes on its own with the phone in a pocket.
 *
 * The service does no work of its own. It holds the same progress notification the download
 * already posts, so the two do not stack up as separate entries, and it exists only for as
 * long as there is something to download.
 */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val notification = DownloadNotificationHelper.buildProgressNotification(
            context = this,
            // Negative until the engine reports its first line, which draws the bar as
            // indeterminate rather than as a download sitting at nought percent.
            progress = -1,
            statusLine = "Starting download",
            mediaTitle = title
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                DownloadNotificationHelper.PROGRESS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(DownloadNotificationHelper.PROGRESS_NOTIFICATION_ID, notification)
        }

        // Not restarted on its own if the system does kill it: the queue lives in the app
        // and a service that came back with nothing to do would post a notification for a
        // download that is not running.
        return START_NOT_STICKY
    }

    companion object {
        private const val EXTRA_TITLE = "hazel.download.title"

        /**
         * Asks the system to keep the process alive for a download.
         *
         * Failures are swallowed: a device that refuses the service still downloads
         * perfectly well while the app is open, and losing the download over the way the
         * system felt about a service call would be the worse outcome.
         */
        fun start(context: Context, title: String) {
            runCatching {
                val intent = Intent(context, DownloadService::class.java)
                    .putExtra(EXTRA_TITLE, title)
                context.startForegroundService(intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, DownloadService::class.java))
            }
        }
    }
}
