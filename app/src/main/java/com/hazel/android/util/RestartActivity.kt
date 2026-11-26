package com.hazel.android.util

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.core.content.pm.ShortcutManagerCompat
import com.hazel.android.HazelActivity

/**
 * Restarts Hazel. It runs in a process of its own (":restart"), so it can end the app's main
 * process and then open Hazel fresh, which a process can't do for itself reliably.
 */
class RestartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mainPid = intent.getIntExtra(EXTRA_MAIN_PID, -1)
        if (mainPid > 0 && mainPid != Process.myPid()) Process.killProcess(mainPid)
        startActivity(
            Intent(this, HazelActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
        Process.killProcess(Process.myPid())
    }

    companion object {
        private const val EXTRA_MAIN_PID = "main_pid"

        /**
         * Switches to [icon] and restarts Hazel on it. Shortcuts made for the old icon's
         * entries are dropped first; the app publishes fresh ones for the new icon when it starts.
         */
        fun switchIcon(activity: Activity, icon: AppIcon) {
            AppIcon.apply(activity, icon)
            runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(activity.applicationContext) }
            activity.startActivity(
                Intent(activity, RestartActivity::class.java)
                    .putExtra(EXTRA_MAIN_PID, Process.myPid())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            activity.finishAffinity()
        }
    }
}
