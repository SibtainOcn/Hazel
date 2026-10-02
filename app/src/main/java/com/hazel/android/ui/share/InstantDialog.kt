package com.hazel.android.ui.share

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hazel.android.R
import kotlinx.coroutines.delay

/**
 * What Hazel Instant shows over the app a link was shared from. The download has already
 * been handed over by the time it appears, so it only confirms, and closes itself after a
 * few seconds. OK closes it sooner; the tune button opens Synthesizing, where the settings
 * Instant downloads with are changed.
 */
@Composable
fun InstantDialog(onConfirm: () -> Unit, onTune: () -> Unit) {
    val close by rememberUpdatedState(onConfirm)
    var secondsLeft by remember { mutableIntStateOf(AUTO_CLOSE_SECONDS) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
        close()
    }

    Dialog(onDismissRequest = onConfirm) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    ShimmeringBolt()
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.instant_started),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.instant_closing, secondsLeft),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalIconButton(onClick = onTune) {
                        Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.instant_settings))
                    }
                    Button(onClick = onConfirm) {
                        Text(stringResource(R.string.instant_ok), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * The bolt with a band of light sweeping across it. One float animated, drawn over a 30dp
 * icon on its own layer, so each frame redraws only the icon; it stops with the dialog.
 */
@Composable
private fun ShimmeringBolt() {
    val accent = MaterialTheme.colorScheme.primary
    val sweep by rememberInfiniteTransition(label = "bolt").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )
    Icon(
        painterResource(R.drawable.ic_hazel_bolt),
        contentDescription = null,
        tint = accent,
        modifier = Modifier
            .size(30.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val x = size.width * sweep
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent),
                        start = Offset(x - size.width * 0.5f, 0f),
                        end = Offset(x + size.width * 0.5f, size.height)
                    ),
                    blendMode = BlendMode.SrcAtop
                )
            }
    )
}

private const val AUTO_CLOSE_SECONDS = 3
