package com.hazel.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.toArgb
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.hazel.android.MainActivity
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import com.hazel.android.ui.theme.AccentColors
import com.hazel.android.ui.theme.DarkBackground
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * The 4×1 home-screen widget: Paste, Audio, Video and Downloads, each on its own M3
 * Expressive shape in the app's accent, on a surface that follows the app's theme.
 */
class QuickActionsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val views = build(context.applicationContext)
                ids.forEach { manager.updateAppWidget(it, views) }
            } catch (e: Exception) {
                Log.w("Hazel", "Quick actions widget update failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {

        /** Redraws every placed widget, after the accent or theme changed. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = runCatching {
                manager.getAppWidgetIds(ComponentName(app, QuickActionsWidget::class.java))
            }.getOrNull() ?: return
            if (ids.isEmpty()) return
            app.sendBroadcast(
                Intent(app, QuickActionsWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }

        private suspend fun build(context: Context): RemoteViews {
            val dark = SettingsRepository.isDarkTheme(context).first() ?: true
            val accentName = SettingsRepository.getAccentColor(context).first()
            val accent = AccentColors.find { it.name == accentName } ?: AccentColors.first()
            val fill = (if (dark) accent.dark else accent.light).toArgb()
            val onFill = if (dark) DarkBackground.toArgb() else android.graphics.Color.WHITE
            val label = if (dark) LABEL_DARK else LABEL_LIGHT
            val side = (SHAPE_DP * context.resources.displayMetrics.density).toInt()

            return RemoteViews(context.packageName, R.layout.widget_quick_actions).apply {
                setInt(
                    R.id.widget_root, "setBackgroundResource",
                    if (dark) R.drawable.widget_surface_dark else R.drawable.widget_surface_light
                )
                ACTIONS.forEach { action ->
                    setImageViewBitmap(action.shapeId, shapeBitmap(action.shape, side, fill))
                    setInt(action.iconId, "setColorFilter", onFill)
                    setTextColor(action.labelId, label)
                    setOnClickPendingIntent(action.cellId, action.intent(context))
                }
            }
        }

        /** [polygon] filled with [color], fitted by its own bounds into a [side]-pixel square. */
        private fun shapeBitmap(polygon: RoundedPolygon, side: Int, color: Int): Bitmap {
            val bounds = polygon.calculateBounds()
            val width = bounds[2] - bounds[0]
            val height = bounds[3] - bounds[1]
            val scale = min(side / width, side / height)
            val path = polygon.toPath().apply {
                transform(Matrix().apply {
                    postTranslate(-bounds[0], -bounds[1])
                    postScale(scale, scale)
                    postTranslate((side - width * scale) / 2f, (side - height * scale) / 2f)
                })
            }
            return Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).also {
                Canvas(it).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
            }
        }

        private fun openApp(context: Context, route: String, requestCode: Int): PendingIntent =
            PendingIntent.getActivity(
                context, requestCode,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_NAVIGATE_TO, route),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun fromClipboard(context: Context, mode: WidgetClipActivity.Mode, requestCode: Int): PendingIntent =
            PendingIntent.getActivity(
                context, requestCode,
                WidgetClipActivity.intent(context, mode),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private class Action(
            val cellId: Int,
            val shapeId: Int,
            val iconId: Int,
            val labelId: Int,
            val shape: RoundedPolygon,
            val intent: (Context) -> PendingIntent
        )

        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        private val ACTIONS = listOf(
            Action(R.id.action_paste, R.id.shape_paste, R.id.icon_paste, R.id.label_paste, MaterialShapes.Pentagon) {
                fromClipboard(it, WidgetClipActivity.Mode.SHEET, requestCode = 5001)
            },
            Action(R.id.action_audio, R.id.shape_audio, R.id.icon_audio, R.id.label_audio, MaterialShapes.Cookie6Sided) {
                fromClipboard(it, WidgetClipActivity.Mode.AUDIO, requestCode = 5002)
            },
            Action(R.id.action_video, R.id.shape_video, R.id.icon_video, R.id.label_video, MaterialShapes.Gem) {
                fromClipboard(it, WidgetClipActivity.Mode.VIDEO, requestCode = 5003)
            },
            Action(R.id.action_history, R.id.shape_history, R.id.icon_history, R.id.label_history, MaterialShapes.Clover4Leaf) {
                openApp(it, "history", 5004)
            }
        )

        private const val SHAPE_DP = 44f
        private const val LABEL_DARK = 0xFFEEF1F3.toInt()
        private const val LABEL_LIGHT = 0xFF15181B.toInt()
    }
}
