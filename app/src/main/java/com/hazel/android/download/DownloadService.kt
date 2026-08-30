package com.hazel.android.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

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
        // Going into the foreground is the first thing done and it is done unconditionally,
        // before the intent is so much as read. Calling startForegroundService puts the
        // process under a promise the system enforces by killing it, and a stop is not a
        // release from that promise: it is another reason to keep it, quickly.
        enterForeground(intent?.getStringExtra(EXTRA_TITLE).orEmpty())

        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Not restarted on its own if the system does kill it: the queue lives in the app
        // and a service that came back with nothing to do would post a notification for a
        // download that is not running.
        return START_NOT_STICKY
    }

    private fun enterForeground(title: String) {
        val notification = DownloadNotificationHelper.buildProgressNotification(
            context = this,
            // Negative until the engine reports its first line, which draws the bar as
            // indeterminate rather than as a download sitting at nought percent.
            progress = -1,
            statusLine = "Starting download",
            mediaTitle = title
        )

        // Failing to go foreground is worth surviving. The promise is then already broken
        // and there is nothing further this can do about it, but throwing here would take
        // the download down as well as the service.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    DownloadNotificationHelper.PROGRESS_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(DownloadNotificationHelper.PROGRESS_NOTIFICATION_ID, notification)
            }
        }
    }

    companion object {
        private const val EXTRA_TITLE = "hazel.download.title"
        private const val ACTION_STOP = "hazel.download.stop"

        /**
         * Whether a start has been asked for and not yet taken back.
         *
         * Both calls come from the one process, so a plain flag answers it. It is here to
         * keep [stop] from starting a service purely in order to stop it, which would put a
         * notification in the shade for the instant it took.
         */
        @Volatile
        private var startRequested = false

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
                startRequested = true
            }
        }

        /**
         * Stops the service by asking it to stop itself, rather than by calling stopService.
         *
         * stopService on a service the system has been told to start but has not yet
         * created leaves the start on the books with nothing left to satisfy it, and the
         * system kills the process for it a few seconds later:
         *
         *   RemoteServiceException$ForegroundServiceDidNotStartInTimeException
         *
         * That is not a hypothetical race. A download refused before it began, on the
         * Wi-Fi only setting, is started and stopped within a few milliseconds of each
         * other, which loses that race nearly every time. Going through the service means
         * onStartCommand runs, goes foreground, and then stops: the promise is kept even
         * when the whole life of the service is a fraction of a second.
         */
        fun stop(context: Context) {
            if (!startRequested) return
            startRequested = false
            runCatching {
                val intent = Intent(context, DownloadService::class.java)
                    .setAction(ACTION_STOP)
                context.startForegroundService(intent)
            }
        }
    }
}
