package com.hazel.android.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * The runtime permissions Hazel asks for, and the two occasions it asks.
 *
 * Notifications, from Android 13, because a download that finishes while the app is closed
 * has nothing else to say so with.
 *
 * Writing to shared storage, up to Android 10 only. Above that a finished file is published
 * through MediaStore, which grants an app access to the entries it creates itself and needs
 * no permission at all. Below that there is no MediaStore route: the file is written
 * straight into `Download/Hazel` or `Music/Hazel`, and WRITE_EXTERNAL_STORAGE is the only
 * thing that permits it. Nothing used to ask for it, so on Android 7 through 10 every
 * finished download and every converted file failed that last step and stayed in the app's
 * own folder, where the user cannot find it. It failed quietly, which is why it lasted.
 */
object PermissionHelper {

    private var runtimeLauncher: ActivityResultLauncher<Array<String>>? = null

    /**
     * Must be called in Activity.onCreate() BEFORE setContent().
     */
    fun register(activity: ComponentActivity) {
        runtimeLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* result ignored: both permissions degrade rather than block */ }
    }

    /**
     * Request notification permission (Android 13+).
     */
    fun ensureNotificationPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                runtimeLauncher?.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }

    /**
     * Whether this version of Android needs a permission before a file can be written into
     * the user's own Download or Music folder.
     *
     * False from Android 11, where MediaStore does the publishing. The permission is not
     * merely unnecessary there: it is declared with a maxSdkVersion, so asking would be
     * asking for something the app does not hold and cannot be granted.
     */
    fun needsLegacyStorageWrite(): Boolean = Build.VERSION.SDK_INT < 30

    /** Whether a file can be written into shared storage as things currently stand. */
    fun canWriteSharedStorage(context: Context): Boolean =
        !needsLegacyStorageWrite() || ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Asks for the shared storage write, on the versions that need one.
     *
     * Called as a run starts rather than at launch, so the request arrives attached to the
     * thing it is for. A refusal is not fatal: the file still converts and still downloads,
     * it just lands in the app's own folder, and the screen says so instead of pretending
     * it went somewhere else.
     */
    fun ensureSharedStorageWrite(context: Context) {
        if (needsLegacyStorageWrite() && !canWriteSharedStorage(context)) {
            runtimeLauncher?.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }
}
