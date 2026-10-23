package com.hazel.android.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import java.io.File

/** Where test screenshots land, inside the build folder so they are never committed. */
val SHOTS_DIR = File("build/outputs/robolectric-shots")

/**
 * Saves the whole screen as a PNG for a person to look at: every open window drawn bottom to
 * top, so sheets and dialogs show over the screen behind them. Never fails a test.
 */
fun saveScreen(name: String) {
    runCatching {
        val views = openWindows().filter { it.width > 0 && it.height > 0 }
        if (views.isEmpty()) return
        val width = views.maxOf { it.width }
        val height = views.maxOf { it.height }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        views.forEach { view ->
            val at = IntArray(2).also(view::getLocationOnScreen)
            canvas.save()
            canvas.translate(at[0].toFloat(), at[1].toFloat())
            view.draw(canvas)
            canvas.restore()
        }
        SHOTS_DIR.mkdirs()
        File(SHOTS_DIR, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

@Suppress("UNCHECKED_CAST")
private fun openWindows(): List<View> {
    val global = Class.forName("android.view.WindowManagerGlobal")
    val instance = global.getMethod("getInstance").invoke(null)
    val field = global.getDeclaredField("mViews").apply { isAccessible = true }
    return (field.get(instance) as List<View>).toList()
}
