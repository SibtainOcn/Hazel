package com.hazel.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hazel.android.data.AppUpdater
import com.hazel.android.data.SettingsRepository
import com.hazel.android.data.UpdateChecker
import com.hazel.android.ui.components.UpdateDialog
import com.hazel.android.ui.components.UpdateDialogState
import com.hazel.android.ui.navigation.AppNavigation
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.util.PermissionHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    var sharedUrl by mutableStateOf<String?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Switch from splash theme to real theme immediately
        setTheme(R.style.Theme_Hazel)

        enableEdgeToEdge()
        handleShareIntent(intent)

        // Clean up stale update APKs from previous sessions
        AppUpdater.cleanupUpdates(this)

        // Register permission launcher (used lazily when music scanner needs READ)
        PermissionHelper.register(this)

        setContent {
            val scope = rememberCoroutineScope()

            val savedTheme by SettingsRepository.isDarkTheme(this).collectAsState(initial = null)
            val isDark = savedTheme ?: true // Default to dark on first install

            val accentName by SettingsRepository.getAccentColor(this).collectAsState(initial = null)

            // Don't render until accent preference is loaded to prevent theme flash
            val resolvedAccent = accentName ?: return@setContent

            HazelTheme(darkTheme = isDark, accentName = resolvedAccent) {
                // ── Update state machine ──
                var updateDialogState by remember { mutableStateOf<UpdateDialogState?>(null) }
                var downloadedApk by remember { mutableStateOf<File?>(null) }
                var downloadJob by remember { mutableStateOf<Job?>(null) }
                var downloadedBytes by remember { mutableLongStateOf(0L) }
                var totalBytes by remember { mutableLongStateOf(0L) }

                // Auto-check for updates on launch
                LaunchedEffect(Unit) {
                    val currentVersion = try {
                        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
                    } catch (_: Exception) { "1.0.0" }
                    val info = UpdateChecker.check(currentVersion)
                    if (info != null) {
                        updateDialogState = UpdateDialogState.Found(info)
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        sharedUrl = sharedUrl,
                        onSharedUrlConsumed = { sharedUrl = null },
                        isDarkTheme = isDark,
                        onToggleTheme = {
                            scope.launch { SettingsRepository.setDarkTheme(this@MainActivity, !isDark) }
                        },
                        accentName = resolvedAccent,
                        onAccentChanged = { name ->
                            scope.launch { SettingsRepository.setAccentColor(this@MainActivity, name) }
                        }
                    )
                }

                // Update dialog overlay
                val currentState = updateDialogState
                if (currentState != null) {
                    // Sync downloading progress into state
                    val displayState = if (currentState is UpdateDialogState.Downloading) {
                        currentState.copy(downloaded = downloadedBytes, total = totalBytes)
                    } else currentState

                    UpdateDialog(
                        state = displayState,
                        onDismiss = {
                            updateDialogState = null
                        },
                        onDownload = {
                            val info = when (currentState) {
                                is UpdateDialogState.Found -> currentState.info
                                else -> return@UpdateDialog
                            }
                            val url = info.apkDownloadUrl
                            if (url.isNullOrBlank()) {
                                // No APK asset found — fallback to browser
                                startActivity(
                                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(info.htmlUrl))
                                )
                                updateDialogState = null
                                return@UpdateDialog
                            }
                            // Start download
                            downloadedBytes = 0L
                            totalBytes = info.apkSize
                            updateDialogState = UpdateDialogState.Downloading(info)
                            downloadJob = scope.launch {
                                val file = AppUpdater.downloadUpdate(
                                    context = this@MainActivity,
                                    url = url,
                                    onProgress = { dl, tot ->
                                        downloadedBytes = dl
                                        totalBytes = tot
                                    }
                                )
                                if (file != null) {
                                    downloadedApk = file
                                    updateDialogState = UpdateDialogState.Ready(info)
                                } else {
                                    // Download failed — dismiss
                                    updateDialogState = null
                                }
                            }
                        },
                        onCancel = {
                            downloadJob?.cancel()
                            downloadJob = null
                            AppUpdater.cleanupUpdates(this@MainActivity)
                            updateDialogState = null
                        },
                        onInstall = {
                            val apk = downloadedApk
                            if (apk != null && apk.exists()) {
                                AppUpdater.installUpdate(this@MainActivity, apk)
                            }
                            updateDialogState = null
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
        }
    }
}
