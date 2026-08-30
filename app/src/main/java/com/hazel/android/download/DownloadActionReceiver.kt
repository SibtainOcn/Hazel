package com.hazel.android.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hazel.android.MainActivity

/**
 * Where the notification's buttons find the download they are meant to act on.
 *
 * A notification action arrives as a broadcast, and a broadcast has no view model of its
 * own. The one driving the download registers itself here while it lives, so pressing Pause
 * in the shade reaches the same object the button on the card reaches, and the two cannot
 * disagree about what the download is doing.
 *
 * The reference is cleared when that view model is torn down. Holding a dead one would keep
 * a screen's worth of state alive for as long as the process, and would take its orders on
 * behalf of a download that no longer exists.
 */
object DownloadCommands {

    @Volatile private var active: DownloadViewModel? = null

    fun register(viewModel: DownloadViewModel) {
        active = viewModel
    }

    fun unregister(viewModel: DownloadViewModel) {
        if (active === viewModel) active = null
    }

    fun current(): DownloadViewModel? = active
}

/**
 * Turns a press on Pause, Resume or Cancel in the shade into the same call the screen makes.
 *
 * Nothing is decided here. The view model owns what a pause means and what a resume picks
 * up from, and this only carries the press across, so the notification and the card cannot
 * drift into doing two different things by the same name.
 */
class DownloadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val viewModel = DownloadCommands.current()

        when (intent.action) {
            ACTION_PAUSE -> viewModel?.pauseDownload()

            ACTION_RESUME ->
                if (viewModel != null) {
                    viewModel.resumeDownload()
                } else {
                    // The process was rebuilt since the download was paused, so there is
                    // nothing here to resume with. The queue is on disk and the app picks
                    // it up as it starts, so opening the app is the resume.
                    openApp(context)
                }

            ACTION_CANCEL ->
                if (viewModel != null) {
                    viewModel.cancelDownload()
                } else {
                    DownloadNotificationHelper.cancelPaused(context)
                    DownloadNotificationHelper.cancelProgress(context)
                }
        }
    }

    private fun openApp(context: Context) {
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        runCatching { context.startActivity(launch) }
    }

    companion object {
        const val ACTION_PAUSE = "com.hazel.android.action.PAUSE"
        const val ACTION_RESUME = "com.hazel.android.action.RESUME"
        const val ACTION_CANCEL = "com.hazel.android.action.CANCEL"
    }
}
