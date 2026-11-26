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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.hazel.android.ui.components.glassStyleLabel
import com.hazel.android.ui.theme.GlassStyle
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.ui.theme.glassScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Temporary: the five new glass styles side by side on dark and light at 400 dpi, each with a
 * glass card, saved as one PNG per theme. Delete before nightly goes to main.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GlassStylesLookTest(private val dark: Boolean, private val styleKey: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}-{1}")
        fun cases() = listOf(true, false).flatMap { d -> listOf("rose", "ember", "lagoon", "dusk", "sand").map { arrayOf<Any>(d, it) } }
    }

    @get:Rule
    val compose = createComposeRule()

    private val fresh = listOf(GlassStyle.ROSE, GlassStyle.EMBER, GlassStyle.LAGOON, GlassStyle.DUSK, GlassStyle.SAND)

    @Test
    fun `new styles come right after Mono`() {
        assertEquals(listOf(GlassStyle.MONO) + fresh, GlassStyle.entries.take(6))
    }

    @Test
    fun `new styles look`() {
        RuntimeEnvironment.setQualifiers("en-w411dp-h891dp-400dpi")
        compose.setContent {
            val style = GlassStyle.fromKey(styleKey)
            HazelTheme(darkTheme = dark, accentName = "White", glass = true, glassStyle = style) {
                Box(Modifier.fillMaxSize().glassScreen()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(glassStyleLabel(style), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Box(
                            Modifier.padding(top = 8.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp)
                        ) { Text("Glass card", color = MaterialTheme.colorScheme.onSurface) }
                    }
                }
            }
        }
        compose.waitForIdle()
        saveScreen("glass-$styleKey-${if (dark) "dark" else "light"}")
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
