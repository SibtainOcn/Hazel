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
 * Simplified permission helper for Hazel.
 *
 * Downloads and conversions write to the app-specific dir and are published through
 * MediaStore, so no storage permission is required. The only runtime permission the
 * app asks for is POST_NOTIFICATIONS (Android 13+) for download progress notifications.
 */
object PermissionHelper {

    private var runtimeLauncher: ActivityResultLauncher<Array<String>>? = null

    /**
     * Must be called in Activity.onCreate() BEFORE setContent().
     */
    fun register(activity: ComponentActivity) {
        runtimeLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* result ignored — notifications are optional */ }
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
}
