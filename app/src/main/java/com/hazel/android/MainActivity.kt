package com.hazel.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hazel.android.data.SettingsRepository
import com.hazel.android.ui.components.UpdateDialog
import com.hazel.android.ui.components.UpdateDialogState
import com.hazel.android.ui.navigation.AppNavigation
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.update.UpdateViewModel
import com.hazel.android.util.PermissionHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    var sharedUrl by mutableStateOf<String?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Switch from splash theme to real theme immediately
        setTheme(R.style.Theme_Hazel)

        enableEdgeToEdge()
        handleShareIntent(intent)

        // Register permission launcher (used lazily when music scanner needs READ)
        PermissionHelper.register(this)

        setContent {
            val scope = rememberCoroutineScope()

            val savedTheme by SettingsRepository.isDarkTheme(this).collectAsState(initial = null)
            val isDark = savedTheme ?: true // Default to dark on first install

            val accentName by SettingsRepository.getAccentColor(this).collectAsState(initial = null)

            // Don't render until accent preference is loaded to prevent theme flash
            val resolvedAccent = accentName ?: return@setContent

            // ── Single UpdateViewModel — shared across entire app ──
            val updateViewModel: UpdateViewModel = viewModel()

            HazelTheme(darkTheme = isDark, accentName = resolvedAccent) {

                // Auto-check for updates & cleanup old APKs
                LaunchedEffect(Unit) {
                    updateViewModel.cleanupIfUpdated()
                    updateViewModel.autoCheckOnLaunch()
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
                        },
                        updateViewModel = updateViewModel
                    )
                }

                // ── Initial popup dialog (first-launch update notification) ──
                val showDialog by updateViewModel.showInitialDialog.collectAsState()
                val uiState by updateViewModel.uiState.collectAsState()

                if (showDialog) {
                    val dialogState = when (val s = uiState) {
                        is UpdateViewModel.UiState.Available -> UpdateDialogState.Found(s.info)
                        is UpdateViewModel.UiState.Downloading ->
                            UpdateDialogState.Downloading(s.info, s.downloaded, s.total)
                        is UpdateViewModel.UiState.ReadyToInstall -> UpdateDialogState.Ready(s.info)
                        else -> null
                    }

                    if (dialogState != null) {
                        UpdateDialog(
                            state = dialogState,
                            onDismiss = { updateViewModel.dismissDialog() },
                            onDownload = { updateViewModel.startDownload() },
                            onCancel = { updateViewModel.cancelDownload() },
                            onInstall = { updateViewModel.installUpdate() },
                            onChangelog = {
                                com.hazel.android.util.openInAppBrowser(
                                    this@MainActivity,
                                    "https://sibtainocn.github.io/Hazel/"
                                )
                            },
                            onKeepInBackground = { updateViewModel.backgroundDownload() }
                        )
                    }
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
