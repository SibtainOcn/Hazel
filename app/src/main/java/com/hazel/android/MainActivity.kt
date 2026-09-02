package com.hazel.android

import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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

    /**
     * Shares waiting to be picked up, oldest first.
     *
     * A list rather than one slot. The activity is single-task, so several shares in a row
     * arrive as several intents on the same instance, and a single slot meant each one
     * overwrote the last before the screen had read it: sharing three links in a row
     * downloaded whichever of them happened to be looked at.
     *
     * [SharedLink.direct] says which of the two share targets it came through. They resolve
     * to the same class, so the component name is the only place the difference shows, and
     * it is the whole difference: the direct one means "do not ask, just download".
     */
    val pendingShares = mutableStateListOf<SharedLink>()

    data class SharedLink(
        val url: String,
        val direct: Boolean,
        /** Where it came from, for the line shown while it is being read. */
        val source: String = ""
    )

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

            // The download engine unpacks itself on a background thread, but the unpacking
            // still competes for the same CPU and disk as the first frame. Asked for only
            // once that frame exists, so the launch is not paying for it.
            LaunchedEffect(Unit) {
                withFrameNanos { }
                HazelApp.instance.startLibraryInit()
            }

            val savedTheme by SettingsRepository.isDarkTheme(this).collectAsState(initial = null)
            val isDark = savedTheme ?: true // Default to dark on first install

            val accentName by SettingsRepository.getAccentColor(this).collectAsState(initial = null)

            // The preferences decide the whole palette, so the app waits for them rather
            // than painting once in the wrong colours and correcting itself.
            val resolvedAccent = accentName

            // The splash is held for what is left of one short budget counted from the
            // start of the process, so the time already spent on the system's launch
            // window counts towards it instead of being added to it. Preferences usually
            // load faster than that, and without a floor the splash could appear and
            // vanish within a frame or two, which reads as a glitch rather than a launch.
            var minimumShown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(remainingSplashHoldMs())
                minimumShown = true
            }

            if (resolvedAccent == null || !minimumShown) {
                HazelTheme(darkTheme = isDark) { SplashScreen() }
                return@setContent
            }

            HazelTheme(darkTheme = isDark, accentName = resolvedAccent) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        pendingShares = pendingShares,
                        pendingFailure = pendingFailure,
                        onPendingFailureConsumed = { pendingFailure = null },
                        onSharesConsumed = { pendingShares.clear() },
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

    /**
     * How much longer to hold the splash, in milliseconds.
     *
     * What the user waits through is one span, not two: the system's launch window and
     * then this screen, which are drawn to look the same. So the budget is measured from
     * the moment the process started, and a slow start spends the budget rather than
     * extending it. A fast start still gets [SPLASH_FLOOR_MS], because a splash that comes
     * and goes inside a couple of frames looks like a fault.
     */
    private fun remainingSplashHoldMs(): Long {
        val sinceProcessStart = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        return (SPLASH_BUDGET_MS - sinceProcessStart).coerceIn(SPLASH_FLOOR_MS, SPLASH_BUDGET_MS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotBlank() }
                ?.let { link ->
                    // The alias and the activity resolve to the same class, so which of the
                    // two the user picked in the share sheet is only visible in the
                    // component name.
                    val direct =
                        intent.component?.className?.endsWith("DirectShareActivity") == true
                    pendingShares.add(SharedLink(link, direct, sourceLabelFor(link)))
                }
        }

        intent?.getStringExtra(DownloadNotificationHelper.EXTRA_FAILURE_MESSAGE)
            ?.takeIf { it.isNotBlank() }
            ?.let { pendingFailure = it }
    }

    /**
     * What to call where a shared link came from, for the line shown while it is being read.
     *
     * The app that did the sharing is the honest answer and the one the user will recognise,
     * so it is asked for first. Android does not always say, and a share forwarded through
     * another app can name the wrong one, so the link's own site stands in: for the purpose
     * of "this is what is being read", the site is just as good.
     */
    private fun sourceLabelFor(link: String): String {
        val referrerPackage = referrer
            ?.takeIf { it.scheme == "android-app" }
            ?.host
            ?.takeIf { it != packageName }

        if (referrerPackage != null) {
            runCatching {
                val info = packageManager.getApplicationInfo(referrerPackage, 0)
                packageManager.getApplicationLabel(info).toString()
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }

        return runCatching { java.net.URI(link).host.orEmpty() }
            .getOrDefault("")
            .removePrefix("www.")
            .ifBlank { "the link" }
    }
}

/** The whole launch, from the process starting to the app being on screen. */
private const val SPLASH_BUDGET_MS = 900L

/** The least the splash is worth showing for once it is up. */
private const val SPLASH_FLOOR_MS = 350L
