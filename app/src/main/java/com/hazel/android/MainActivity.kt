package com.hazel.android

import com.hazel.android.ui.theme.GlassStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import com.hazel.android.ui.theme.glassScreen
import android.content.Context
import android.content.Intent
import android.animation.ValueAnimator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.DownloadNotificationHelper
import com.hazel.android.ui.navigation.AppNavigation
import com.hazel.android.ui.screens.SplashScreen
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.util.AppLocale
import com.hazel.android.util.PermissionHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /**
     * Shares waiting to be picked up, oldest first.
     *
     * A list rather than one slot. The activity is single-task, so several shares in a row
     * arrive as several intents on the same instance, and a single slot meant each one
     * overwrote the last before the screen had read it: sharing three links in a row
     * downloaded whichever of them happened to be looked at.
     */
    val pendingShares = mutableStateListOf<SharedLink>()

    data class SharedLink(
        val url: String,
        /** Where it came from, for the line shown while it is being read. */
        val source: String = ""
    )

    /** A failure the user tapped a notification to come back to, or null. */
    var pendingFailure by mutableStateOf<String?>(null)
        private set

    /** Direct navigation target requested by shortcuts or share overlay, or null. */
    var pendingRoute by mutableStateOf<String?>(null)
        private set

    companion object {
        const val EXTRA_NAVIGATE_TO = "hazel.navigate.to"
    }

    /**
     * Applies the chosen language before anything is inflated.
     *
     * Only Android 12 and earlier need this. From 13 the platform owns the per-app language
     * and hands the activity a context that already has it, so [AppLocale.wrap] gives that
     * one straight back rather than arguing with it.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

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

            // Plays once per cold launch; skipped when the system has animations turned off.
            var splashDone by rememberSaveable { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
            var homeWanted by rememberSaveable { mutableStateOf(false) }
            var homeShown by remember { mutableStateOf(false) }

            // The download engine unpacks on a background thread but still competes for CPU
            // and disk, so it waits until the splash is over and a frame of home is drawn.
            LaunchedEffect(splashDone) {
                if (!splashDone) return@LaunchedEffect
                withFrameNanos { }
                HazelApp.instance.startLibraryInit()
                com.hazel.android.update.HazelUpdater.checkUpdatesSilently(applicationContext)
            }

            val savedTheme by remember(this) { SettingsRepository.isDarkTheme(this) }.collectAsState(initial = null)
            val isDark = savedTheme ?: true // Default to dark on first install

            val accentName by remember(this) { SettingsRepository.getAccentColor(this) }.collectAsState(initial = null)
            val glassPref by remember(this) { SettingsRepository.isGlassEnabled(this) }.collectAsState(initial = null)
            val glassStyleKey by remember(this) { SettingsRepository.getGlassStyle(this) }.collectAsState(initial = null)

            // The preferences decide the whole palette, so the app waits for them rather
            // than painting once in the wrong colours and correcting itself.
            val resolvedAccent = accentName

            val glass = glassPref
            val glassStyle = glassStyleKey?.let { GlassStyle.fromKey(it) }
            val ready = resolvedAccent != null && glass != null && glassStyle != null

            Box(Modifier.fillMaxSize()) {
                // Home is built only once the splash logo has settled, so its heavy first
                // layout never lands on the frames the splash is animating.
                if (resolvedAccent != null && glass != null && glassStyle != null && (splashDone || homeWanted)) {
                    // The splash opens onto home only once home draws smoothly, so its first
                    // loads and image decodes happen behind the still logo, not during the reveal.
                    LaunchedEffect(Unit) {
                        var last = withFrameNanos { it }
                        val giveUpAt = last + HOME_SETTLE_LIMIT_NS
                        var smooth = 0
                        while (smooth < HOME_SMOOTH_FRAMES && last < giveUpAt) {
                            val now = withFrameNanos { it }
                            smooth = if (now - last < SMOOTH_FRAME_NS) smooth + 1 else 0
                            last = now
                        }
                        homeShown = true
                    }
                    HazelTheme(darkTheme = isDark, accentName = resolvedAccent, glass = glass, glassStyle = glassStyle) {
                        Surface(
                            modifier = Modifier.fillMaxSize().glassScreen(),
                            color = if (glass) Color.Transparent else MaterialTheme.colorScheme.surface
                        ) {
                            AppNavigation(
                                pendingShares = pendingShares,
                                pendingFailure = pendingFailure,
                                pendingRoute = pendingRoute,
                                onPendingRouteConsumed = { pendingRoute = null },
                                onPendingFailureConsumed = { pendingFailure = null },
                                onSharesConsumed = { pendingShares.clear() },
                                isDarkTheme = isDark,
                                onToggleTheme = {
                                    scope.launch { SettingsRepository.setDarkTheme(this@MainActivity, !isDark) }
                                },
                                accentName = resolvedAccent,
                                onAccentChanged = { name ->
                                    scope.launch { SettingsRepository.setAccentColor(this@MainActivity, name) }
                                },
                                glassEnabled = glass,
                                onToggleGlass = {
                                    scope.launch { SettingsRepository.setGlassEnabled(this@MainActivity, !glass) }
                                },
                                glassStyle = glassStyle,
                                onGlassStyleChanged = { style ->
                                    scope.launch { SettingsRepository.setGlassStyle(this@MainActivity, style.key) }
                                }
                            )
                        }
                    }
                }
                if (!splashDone) {
                    HazelTheme(darkTheme = isDark) {
                        SplashScreen(
                            ready = ready,
                            homeShown = homeShown,
                            onWantHome = { homeWanted = true },
                            onFinished = { splashDone = true }
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
            intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotBlank() }
                ?.let { link -> pendingShares.add(SharedLink(link, sourceLabelFor(link))) }
        }

        intent?.getStringExtra(DownloadNotificationHelper.EXTRA_FAILURE_MESSAGE)
            ?.takeIf { it.isNotBlank() }
            ?.let { pendingFailure = it }

        intent?.getStringExtra(EXTRA_NAVIGATE_TO)
            ?.takeIf { it.isNotBlank() }
            ?.let { pendingRoute = it }
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

private const val HOME_SMOOTH_FRAMES = 3
private const val SMOOTH_FRAME_NS = 24_000_000L
private const val HOME_SETTLE_LIMIT_NS = 700_000_000L
