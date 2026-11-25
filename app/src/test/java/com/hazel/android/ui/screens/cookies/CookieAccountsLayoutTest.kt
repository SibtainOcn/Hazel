package com.hazel.android.ui.screens.cookies

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.hazel.android.R
import com.hazel.android.data.CookieEntry
import com.hazel.android.ui.saveScreen
import com.hazel.android.ui.screens.download.CookiesDialogContent
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.ui.theme.glassScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CookieAccountsLayoutTest(private val locale: String, private val theme: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun cases() = listOf("en", "de", "ru", "hi", "ja").flatMap { locale ->
            THEMES.keys.map { arrayOf<Any>(locale, it) }
        }

        /** Each theme with a different accent, the palest and darkest ones included. */
        private val THEMES = mapOf("glass-dark" to "White", "glass-light" to "Rose", "dark" to "Cyan", "light" to "Lavender")

        private const val FUTURE = 4_000_000_000L
        private const val PAST = 1_600_000_000L
        private fun line(domain: String, name: String, expires: Long) = "$domain\tTRUE\t/\tTRUE\t$expires\t$name\tv"

        val ENTRIES = listOf(
            CookieEntry(1, "https://www.instagram.com", "Instagram", line(".instagram.com", "sessionid", FUTURE)),
            CookieEntry(2, "https://x.com", "X", line(".x.com", "auth_token", PAST)),
            CookieEntry(3, "https://www.reddit.com", "Reddit", line(".reddit.com", "csrf_token", FUTURE)),
            CookieEntry(4, "https://www.pixiv.net", "", line(".pixiv.net", "PHPSESSID", FUTURE))
        )
    }

    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("$locale-w320dp-h568dp")
        compose.setContent {
            HazelTheme(darkTheme = theme.endsWith("dark"), accentName = THEMES.getValue(theme), glass = theme.startsWith("glass")) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).glassScreen()) { content() }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertFits(text: String) {
        val width = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.widthPixels
        val match = compose.onAllNodesWithText(text, substring = true)[0]
        runCatching { match.performScrollTo() }
        val node = match.assertIsDisplayed().fetchSemanticsNode()
        assertTrue("$text runs off the edge", node.positionOnScreen.x + node.size.width <= width + 1)
    }

    @Test
    fun `accounts, states and add buttons fit a small screen at the largest font`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        show {
            CookiesBody(
                useCookies = true, entries = ENTRIES, importing = false, onBack = {}, onUseCookies = {},
                onSignIn = {}, onImportFile = {}, onImportClipboard = {}, onExport = {}, onDeleteAll = {},
                onOpen = {}, onSignInAgain = {}, onQuickSite = { _, _ -> }
            )
        }
        assertFits(context.getString(R.string.cookies_add_sign_in))
        assertFits(context.getString(R.string.cookies_import))
        assertFits(context.getString(R.string.cookies_state_active))
        assertFits(context.getString(R.string.cookies_expired))
        assertFits(context.getString(R.string.cookies_state_signed_out))
        assertFits("Pixiv")
        saveScreen("cookie-accounts-$theme-$locale")
    }

    @Test
    fun `expired dialog shows the state, settings row and both buttons`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        show {
            CookiesDialogContent(
                siteName = "X", site = "twitter.com", entry = ENTRIES[1], useCookies = true, checking = false,
                onUseCookies = {}, onOpenSettings = {}, onSignIn = {}, onCheck = {}, onDismiss = {}
            )
        }
        assertFits(context.getString(R.string.cookies_dialog_expired_title))
        assertFits(context.getString(R.string.cookies_settings))
        assertFits(context.getString(R.string.cookies_not_now))
        assertFits(context.getString(R.string.cookies_sign_in_again))
        saveScreen("cookie-dialog-$theme-$locale")
    }
}
