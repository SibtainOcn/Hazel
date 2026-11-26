package com.hazel.android.temp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import com.hazel.android.gallery.ProfileSection
import com.hazel.android.gallery.ProfileSite
import com.hazel.android.ui.components.BottomBarItem
import com.hazel.android.ui.components.FlatChip
import com.hazel.android.ui.components.HazelBottomBar
import com.hazel.android.ui.components.SoftIconButton
import com.hazel.android.ui.navigation.Screen
import com.hazel.android.ui.screens.download.GettingStartedDialog
import com.hazel.android.ui.screens.download.MediaGallerySwitch
import com.hazel.android.ui.screens.download.NoResultsDialog
import com.hazel.android.ui.screens.download.SearchGuideScreen
import com.hazel.android.ui.screens.gallery.GallerySheetSkeleton
import com.hazel.android.ui.screens.gallery.ProfileSectionsSheet
import com.hazel.android.ui.screens.history.CardKindMark
import com.hazel.android.ui.screens.history.CookieMenuButton
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
import java.io.File

/**
 * Temporary: the tonal fill fix and the screens added in the last few PRs, drawn on Glass dark,
 * Glass light, Dark and Light with a neutral and a coloured accent at 400 dpi. Each case saves a
 * PNG to build/outputs/robolectric-shots for a look. Delete before nightly goes to main.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ThemeLooksTest(private val theme: String, private val accent: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun cases() = listOf("glass-dark", "glass-light", "dark", "light")
            .flatMap { t -> listOf("White", "Cyan").map { arrayOf<Any>(t, it) } }
    }

    @get:Rule
    val compose = createComposeRule()

    private val dark get() = theme.endsWith("dark")
    private val glass get() = theme.startsWith("glass")

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("en-w411dp-h891dp-400dpi")
        compose.setContent {
            HazelTheme(darkTheme = dark, accentName = accent, glass = glass) {
                val ground = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                Box(if (glass) ground.glassScreen() else ground) { content() }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `tonal parts`() {
        show {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftIconButton({}) { Icon(Icons.Filled.Search, null) }
                    SoftIconButton({}) { Icon(Icons.Filled.FilterList, null) }
                    SoftIconButton({}) { Icon(Icons.Filled.MoreVert, null) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlatChip("Date added", {})
                    FlatChip("Audio", {}, icon = Icons.Filled.MusicNote)
                    FlatChip("Video", {}, icon = Icons.Filled.Videocam, selected = true)
                }
                MediaGallerySwitch(gallery = true, onGallery = {})
                // History card marks sit on a picture, so they stay solid.
                Box(
                    Modifier.fillMaxWidth().height(120.dp)
                        .background(Brush.linearGradient(listOf(Color(0xFFE8C27A), Color(0xFF2A4D7A))), RoundedCornerShape(20.dp))
                        .padding(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardKindMark(Icons.Filled.Videocam)
                        CookieMenuButton {}
                    }
                }
                Box(Modifier.size(1.dp))
                HazelBottomBar(
                    items = listOf(Screen.Download, Screen.Gallery, Screen.History, Screen.Queue, Screen.More)
                        .map { BottomBarItem(it.route, it.icon, it.titleRes) },
                    selectedRoute = Screen.Gallery.route,
                    containerColor = Color.Transparent,
                    onSelect = {}
                )
            }
        }
        assertTrue(compose.onAllNodesWithText("Date added").fetchSemanticsNodes().isNotEmpty())
        saveScreen("tonal-$theme-$accent")
    }

    @Test
    fun `gallery sheet skeleton`() {
        show { GallerySheetSkeleton() }
        saveScreen("skeleton-$theme-$accent")
    }

    @Test
    fun `profile sections sheet`() {
        show {
            ProfileSectionsSheet(
                site = ProfileSite.INSTAGRAM, title = "aljazeeraenglish",
                chosen = listOf(ProfileSection.IG_STORIES, ProfileSection.IG_POSTS), onChosen = {},
                signedIn = true, onDismiss = {}, onFetch = {}
            )
        }
        assertTrue(compose.onAllNodesWithText("Stories").fetchSemanticsNodes().isNotEmpty())
        saveScreen("profile-sections-$theme-$accent")
    }

    @Test
    fun `search guide`() {
        show { SearchGuideScreen(onBack = {}) }
        saveScreen("search-guide-$theme-$accent")
    }

    @Test
    fun `getting started`() {
        show { GettingStartedDialog(onOpenBatterySettings = {}, onDismiss = {}) }
        saveScreen("getting-started-$theme-$accent")
    }

    @Test
    fun `profile turned away dialog`() {
        show {
            NoResultsDialog(
                message = "HTTP redirect to home page", canFetchCookies = false, canContinue = false,
                link = "https://www.instagram.com/someone/", onCopyLog = {}, onGetCookies = {},
                onContinueAnyway = {}, onDismiss = {},
                title = "Profile turned away for now",
                body = "Too many requests in a short time can cause this. Wait a few minutes and try again, or sign in again if it keeps happening."
            )
        }
        assertTrue(compose.onAllNodesWithText("Profile turned away for now").fetchSemanticsNodes().isNotEmpty())
        saveScreen("turned-away-$theme-$accent")
    }
}

/** Saves every open window bottom to top, so sheets and dialogs show over the screen. */
private fun saveScreen(name: String) {
    runCatching {
        val views = openWindows().filter { it.width > 0 && it.height > 0 }
        if (views.isEmpty()) return
        val bitmap = Bitmap.createBitmap(views.maxOf { it.width }, views.maxOf { it.height }, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        views.forEach { view ->
            val at = IntArray(2).also(view::getLocationOnScreen)
            canvas.save()
            canvas.translate(at[0].toFloat(), at[1].toFloat())
            view.draw(canvas)
            canvas.restore()
        }
        val dir = File("build/outputs/robolectric-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

@Suppress("UNCHECKED_CAST")
private fun openWindows(): List<View> {
    val global = Class.forName("android.view.WindowManagerGlobal")
    val instance = global.getMethod("getInstance").invoke(null)
    val field = global.getDeclaredField("mViews").apply { isAccessible = true }
    return (field.get(instance) as List<View>).toList()
}
