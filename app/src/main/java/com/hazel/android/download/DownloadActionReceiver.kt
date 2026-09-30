package com.hazel.android.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hazel.android.data.DownloadQueueRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

            // With the app closed since the pause there is no view model yet, and one is made
            // for the resume: it reads the queue from disk and runs it in the background.
            // Opening the app instead did not resume anything (a paused download is kept
            // paused when the app starts), and Android 12 and later do not let a notification
            // button open an activity from here at all.
            ACTION_RESUME -> (viewModel ?: DownloadViewModelHolder.get()).resumeDownload()

            ACTION_CANCEL ->
                if (viewModel != null) {
                    viewModel.cancelDownload()
                } else {
                    DownloadNotificationHelper.cancelPaused(context)
                    DownloadNotificationHelper.cancelProgress(context)
                    // Given up for good: without this the paused download was still on the
                    // record, and came back paused the next time the app opened.
                    val app = context.applicationContext
                    val pending = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            DownloadQueueRepository.load(app).filter { it.paused }.forEach {
                                DownloadQueueRepository.remove(app, it.url)
                                runCatching { workDirFor(it.url).deleteRecursively() }
                            }
                        } finally {
                            pending.finish()
                        }
                    }
                }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.hazel.android.action.PAUSE"
        const val ACTION_RESUME = "com.hazel.android.action.RESUME"
        const val ACTION_CANCEL = "com.hazel.android.action.CANCEL"
    }
}
