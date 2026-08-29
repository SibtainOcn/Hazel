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
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.DownloadNotificationHelper
import com.hazel.android.ui.navigation.AppNavigation
import com.hazel.android.ui.screens.SplashScreen
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.util.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    var sharedUrl by mutableStateOf<String?>(null)
        private set

    /**
     * True when the link arrived through the direct share target rather than the ordinary
     * one. The two entry points are the same screen; this is the whole of the difference
     * between them, and it means "do not ask, just download".
     */
    var sharedDirectly by mutableStateOf(false)
        private set

    /** A failure the user tapped a notification to come back to, or null. */
    var pendingFailure by mutableStateOf<String?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Switch from splash theme to real theme immediately
        setTheme(R.style.Theme_Hazel)

        enableEdgeToEdge()
        handleShareIntent(intent)

        // Register permission launcher (used lazily for the notification permission)
        PermissionHelper.register(this)

        setContent {
            val scope = rememberCoroutineScope()

            val savedTheme by SettingsRepository.isDarkTheme(this).collectAsState(initial = null)
            val isDark = savedTheme ?: true // Default to dark on first install

            val accentName by SettingsRepository.getAccentColor(this).collectAsState(initial = null)

            // The preferences decide the whole palette, so the app waits for them rather
            // than painting once in the wrong colours and correcting itself.
            val resolvedAccent = accentName

            // The splash is held for one full sweep of its highlight. Preferences usually
            // load faster than that, and without the floor the splash would appear and
            // vanish within a frame or two, which reads as a glitch rather than a launch.
            var minimumShown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(SPLASH_MINIMUM_MS)
                minimumShown = true
            }

            if (resolvedAccent == null || !minimumShown) {
                HazelTheme(darkTheme = isDark) { SplashScreen() }
                return@setContent
            }

            HazelTheme(darkTheme = isDark, accentName = resolvedAccent) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        sharedUrl = sharedUrl,
                        sharedDirectly = sharedDirectly,
                        pendingFailure = pendingFailure,
                        onPendingFailureConsumed = { pendingFailure = null },
                        onSharedUrlConsumed = {
                            sharedUrl = null
                            sharedDirectly = false
                        },
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
            // The alias and the activity resolve to the same class, so which of the two the
            // user picked in the share sheet is only visible in the component name.
            sharedDirectly = intent.component?.className?.endsWith("DirectShareActivity") == true
        }

        intent?.getStringExtra(DownloadNotificationHelper.EXTRA_FAILURE_MESSAGE)
            ?.takeIf { it.isNotBlank() }
            ?.let { pendingFailure = it }
    }
}

/** One sweep of the splash highlight, which is the shortest it is worth showing for. */
private const val SPLASH_MINIMUM_MS = 1400L
