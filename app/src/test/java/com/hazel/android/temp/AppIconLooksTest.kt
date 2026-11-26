package com.hazel.android.temp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.hazel.android.ui.screens.more.AppearanceScreen
import com.hazel.android.ui.theme.GlassStyle
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.ui.theme.glassScreen
import com.hazel.android.util.AppIcon
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Temporary: the three app icons as the launcher draws them (circle and rounded square) and
 * the Appearance screen with its App icon row, on Glass dark, Glass light, Dark and Light at
 * 400 dpi. Delete before nightly goes to main.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppIconLooksTest(private val theme: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases() = listOf("glass-dark", "glass-light", "dark", "light").map { arrayOf<Any>(it) }
    }

    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("en-w411dp-h891dp-400dpi")
        val dark = theme.endsWith("dark")
        val glass = theme.startsWith("glass")
        compose.setContent {
            HazelTheme(darkTheme = dark, accentName = "White", glass = glass) {
                val ground = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                Box(if (glass) ground.glassScreen() else ground) { content() }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `icons`() {
        show {
            val context = LocalContext.current
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppIcon.entries.forEach { icon ->
                    val image = ContextCompat.getDrawable(context, icon.icon)!!.toBitmap(400, 400).asImageBitmap()
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Image(image, null, Modifier.size(150.dp).clip(CircleShape))
                        Image(image, null, Modifier.size(150.dp).clip(RoundedCornerShape(40.dp)))
                    }
                }
            }
        }
        saveScreen("app-icons-$theme")
    }

    @Test
    fun `appearance row`() {
        show {
            AppearanceScreen(
                isDarkTheme = theme.endsWith("dark"), onToggleTheme = {}, accentName = "White", onAccentChanged = {},
                glassEnabled = theme.startsWith("glass"), onToggleGlass = {}, glassStyle = GlassStyle.DEFAULT,
                onGlassStyleChanged = {}, onBack = {}
            )
        }
        saveScreen("appearance-$theme")
    }
}

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
