package com.hazel.android.ui.share

import com.hazel.android.ui.theme.glassBackdrop
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.hazel.android.ui.screens.download.contrastOf
import com.hazel.android.ui.theme.LocalDarkTheme
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
import com.hazel.android.ui.components.CookieBadge
import kotlinx.coroutines.delay

/**
 * What Hazel Instant shows over the app a link was shared from. The download has already
 * been handed over by the time it appears, so it only confirms, and closes itself after a
 * few seconds. OK closes it sooner; the tune button opens Synthesizing, where the settings
 * Instant downloads with are changed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InstantDialog(onConfirm: () -> Unit, onTune: () -> Unit, glass: Boolean = false) {
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
        Surface(
            modifier = Modifier.glassBackdrop(RoundedCornerShape(28.dp), enabled = glass),
            shape = RoundedCornerShape(28.dp),
            color = if (glass) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val accent = MaterialTheme.colorScheme.primary
                val dark = LocalDarkTheme.current
                val backdrop = if (glass) Color.White.copy(alpha = 0.05f).compositeOver(if (dark) Color.Black else Color.White)
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                val tones = remember(accent, backdrop) { boltTones(accent, backdrop) }
                CookieBadge(size = 64.dp, tint = tones.cookie) {
                    ShimmeringBolt(tones)
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalIconButton(
                        onClick = onTune,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.size(
                            IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)
                        ),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            Icons.Filled.Tune,
                            contentDescription = stringResource(R.string.instant_settings),
                            modifier = Modifier.size(IconButtonDefaults.mediumIconSize)
                        )
                    }
                    Button(
                        onClick = onConfirm,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = ButtonDefaults.MediumContainerHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)
                    ) {
                        Text(
                            stringResource(R.string.instant_ok),
                            style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/** The badge's solid cookie, the bolt at rest, and the band that shines across it. */
internal data class BoltTones(val cookie: Color, val dim: Color, val shine: Color)

/**
 * Cookie, then bolt, then shine, each a clear step further from [backdrop]: the bolt rests a
 * little toward the cookie and the shine goes past the accent, lighter on dark and darker on
 * light, so even a white or gold accent has room to shine.
 */
internal fun boltTones(accent: Color, backdrop: Color): BoltTones {
    val cookie = accent.copy(alpha = BOLT_COOKIE_ALPHA).compositeOver(backdrop)
    val away = if (backdrop.luminance() < 0.5f) Color.White else Color.Black
    val dim = listOf(lerp(accent, cookie, BOLT_DIM_TOWARD_COOKIE), accent, lerp(accent, away, 0.2f))
        .firstOrNull { contrastOf(it, cookie) >= BOLT_MIN_REST_CONTRAST } ?: lerp(accent, away, 0.4f)
    return BoltTones(cookie = cookie, dim = dim, shine = lerp(dim, away, BOLT_SHINE_PAST_REST))
}

private const val BOLT_COOKIE_ALPHA = 0.22f
private const val BOLT_DIM_TOWARD_COOKIE = 0.3f
private const val BOLT_SHINE_PAST_REST = 0.7f
internal const val BOLT_MIN_REST_CONTRAST = 1.8f

/**
 * The bolt at rest with a narrow, sharp band of shine sweeping left to right, then a short
 * rest. The sweep is read only while drawing, so frames never recompose.
 */
@Composable
private fun ShimmeringBolt(tones: BoltTones) {
    val accent = tones.shine
    val dim = tones.dim
    val sweep = rememberInfiniteTransition(label = "bolt").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BOLT_SHIMMER_MS, easing = LinearEasing), RepeatMode.Restart),
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
                val band = size.width * BOLT_BAND
                val x = size.width * (sweep.value * BOLT_TRAVEL - (BOLT_TRAVEL - 1f) / 2f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to dim, 0.5f to accent, 1f to dim,
                        startX = x - band / 2f,
                        endX = x + band / 2f
                    ),
                    blendMode = BlendMode.SrcAtop
                )
            }
    )
}

/** One sweep and its rest; the band covers the bolt for about two thirds of it. */
private const val BOLT_SHIMMER_MS = 1_500

/** Band width as a share of the bolt, narrow so its edges read sharp. */
private const val BOLT_BAND = 0.6f

/** How far the band travels per cycle, in bolt widths; past the bolt it rests off its edge. */
private const val BOLT_TRAVEL = 2.2f

private const val AUTO_CLOSE_SECONDS = 3
