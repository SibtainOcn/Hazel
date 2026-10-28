package com.hazel.android.ui.screens.more

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.update.UpdateTokens

/**
 * What an update is doing: a soft nine-sided cookie in the state's colour beside a title
 * and a line. While [working] the cookie holds wavy progress, live from [progress], or
 * turning when that is null.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UpdateStatusHeader(
    title: String,
    line: String,
    tone: Color,
    onTone: Color,
    icon: ImageVector,
    working: Boolean,
    progress: Float?
) {
    Row(modifier = Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(if (working) tone.copy(alpha = 0.18f) else tone),
            contentAlignment = Alignment.Center
        ) {
            when {
                working && progress != null -> {
                    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "markProgress")
                    CircularWavyProgressIndicator(
                        progress = { shown },
                        modifier = Modifier.size(44.dp),
                        color = tone,
                        trackColor = tone.copy(alpha = 0.2f)
                    )
                    Icon(icon, null, tint = tone, modifier = Modifier.size(18.dp))
                }
                working -> CircularWavyProgressIndicator(modifier = Modifier.size(44.dp), color = tone, trackColor = tone.copy(alpha = 0.2f))
                else -> Icon(icon, null, tint = onTone, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = UpdateTokens.OnSurface)
            Text(line, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = tone,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The state's buttons as one connected pair: the action in the state's colour, the other tonal. */
@Composable
internal fun UpdateStateButtons(buttons: List<Triple<String, Boolean, () -> Unit>>, tone: Color, onTone: Color) {
    if (buttons.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        buttons.forEachIndexed { index, (text, filled, onClick) ->
            val start = if (index == 0) 26.dp else 8.dp
            val end = if (index == buttons.lastIndex) 26.dp else 8.dp
            Surface(
                onClick = onClick,
                shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end),
                color = if (filled) tone else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (filled) onTone else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
