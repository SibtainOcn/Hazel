package com.hazel.android.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Simplified permission helper for Hazel.
 *
 * After the MediaStore storage fix, the app NO LONGER needs MANAGE_EXTERNAL_STORAGE.
 * Downloads and conversions write to app-specific dir (no permission needed).
 * Only READ permissions are needed for the music library scanner.
 *
 * Permission matrix:
 *   API 24–32 : READ_EXTERNAL_STORAGE (for music library scan)
 *   API 33+   : READ_MEDIA_AUDIO (for music library scan)
 *
 * Downloads/conversions: NO permission needed (uses getExternalFilesDir + MediaStore)
 */
object PermissionHelper {

    private var runtimeLauncher: ActivityResultLauncher<Array<String>>? = null
    private var activity: ComponentActivity? = null

    private val pendingSuccessCallbacks = mutableListOf<() -> Unit>()
    private val pendingDenialCallbacks = mutableListOf<() -> Unit>()
    private var rationaleShownThisSession = false
    private var requestInFlight = false

    /**
     * Must be called in Activity.onCreate() BEFORE setContent().
     */
    fun register(activity: ComponentActivity) {
        this.activity = activity

        runtimeLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            requestInFlight = false

            if (results.isEmpty()) {
                fireDenialCallbacks()
                return@registerForActivityResult
            }

            val allGranted = results.values.all { it }
            if (allGranted) {
                fireSuccessCallbacks()
            } else {
                val permanentlyDenied = results.keys.any { perm ->
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
                            && ContextCompat.checkSelfPermission(activity, perm) != PackageManager.PERMISSION_GRANTED
                }
                if (permanentlyDenied) {
                    showPermanentDenialMessage(activity)
                }
                fireDenialCallbacks()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────

    /**
     * Request READ permissions on startup (for music scanner).
     * Downloads/conversions don't need any permission.
     */
    fun ensureReadPermissionsOnStart(activity: ComponentActivity) {
        if (hasReadPermissions(activity)) {
            ensureNotificationPermission(activity)
            return
        }

        showRationaleAndRequest(activity, onGranted = {
            ensureNotificationPermission(activity)
        }, onDenied = {
            try {
                Toast.makeText(
                    activity,
                    "Storage read permission needed for music library",
                    Toast.LENGTH_LONG
                ).show()
            } catch (_: Exception) { }
        })
    }

    /**
     * Ensure read permissions before scanning music library.
     * Downloads/conversions should call onReady directly — they don't need permission.
     */
    fun ensureReadPermission(
        context: Context,
        onReady: () -> Unit,
        onDenied: (() -> Unit)? = null
    ) {
        if (hasReadPermissions(context)) {
            onReady()
            return
        }

        val act = activity
        if (act == null) {
            onDenied?.invoke()
            return
        }

        showRationaleAndRequest(act, onGranted = onReady, onDenied = {
            onDenied?.invoke()
        })
    }

    /**
     * Check if we have READ permissions for the music scanner.
     */
    fun hasReadPermissions(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        } catch (_: Exception) {
            false
        }
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

    // Keep old API name as alias for backward compatibility
    fun hasAllStoragePermissions(context: Context): Boolean = hasReadPermissions(context)
    fun ensureStoragePermission(
        context: Context,
        onReady: () -> Unit,
        onDenied: (() -> Unit)? = null
    ) = ensureReadPermission(context, onReady, onDenied)

    // ──────────────────────────────────────────────────────────────
    //  Internal
    // ──────────────────────────────────────────────────────────────

    private fun showRationaleAndRequest(
        activity: ComponentActivity,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) {
        if (requestInFlight) {
            pendingSuccessCallbacks.add(onGranted)
            pendingDenialCallbacks.add(onDenied)
            return
        }

        pendingSuccessCallbacks.add(onGranted)
        pendingDenialCallbacks.add(onDenied)

        // Directly request — system shows its own standard permission prompt
        executePermissionRequest()
    }

    private fun executePermissionRequest() {
        requestInFlight = true
        try {
            val perms = if (Build.VERSION.SDK_INT >= 33) {
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            runtimeLauncher?.launch(perms)
                ?: run { requestInFlight = false; fireDenialCallbacks() }
        } catch (_: Exception) {
            requestInFlight = false
            fireDenialCallbacks()
        }
    }

    private fun fireSuccessCallbacks() {
        val callbacks = pendingSuccessCallbacks.toList()
        pendingSuccessCallbacks.clear()
        pendingDenialCallbacks.clear()
        callbacks.forEach { try { it.invoke() } catch (_: Exception) { } }
    }

    private fun fireDenialCallbacks() {
        val callbacks = pendingDenialCallbacks.toList()
        pendingSuccessCallbacks.clear()
        pendingDenialCallbacks.clear()
        callbacks.forEach { try { it.invoke() } catch (_: Exception) { } }
    }

    private fun showPermanentDenialMessage(activity: ComponentActivity) {
        try {
            AlertDialog.Builder(activity)
                .setTitle("Permission Required")
                .setMessage(
                    "Storage read permission was permanently denied.\n\n" +
                    "Please enable it in Settings → Apps → Hazel → Permissions " +
                    "to browse your music library."
                )
                .setPositiveButton("Open Settings") { dialog, _ ->
                    dialog.dismiss()
                    openAppSettings(activity)
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } catch (_: Exception) {
            try {
                Toast.makeText(activity, "Please enable storage permission in Settings", Toast.LENGTH_LONG).show()
            } catch (_: Exception) { }
        }
    }

    private fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
