package com.hazel.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * One choice in a row of sources or sites: a pill that becomes a tinted tab led by a
 * seven-sided cookie tick when picked. [wave] lifts and tints it while a [ChipWave] passes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PickChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wave: (() -> Float)? = null
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val resting by animateDpAsState(if (selected) 12.dp else 20.dp, label = "pickChip")
    val radius by pressCorner(interaction, resting = resting, pressed = 8.dp)
    val container by animateColorAsState(
        if (selected) colors.primary.copy(alpha = 0.22f) else colors.surfaceContainerHigh,
        label = "pickFill"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(radius),
        color = container,
        contentColor = if (selected) colors.primary else colors.onSurface,
        interactionSource = interaction,
        modifier = modifier
            .graphicsLayer {
                val k = wave?.invoke() ?: 0f
                translationY = -WAVE_LIFT.toPx() * k
                scaleX = 1f + WAVE_GROW * k
                scaleY = 1f + WAVE_GROW * k
            }
            .pressScale(interaction)
            .height(40.dp)
            .semantics { this.selected = selected }
    ) {
        Row(
            modifier = Modifier
                .drawBehind {
                    val k = wave?.invoke() ?: 0f
                    if (k > 0f) drawRect(WAVE_TINT.copy(alpha = WAVE_ALPHA * k))
                }
                .padding(start = if (selected) 6.dp else 16.dp, end = if (selected) 14.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(MaterialShapes.Cookie7Sided.toShape())
                        .background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/**
 * A wave across a row of chips, one after another, then a pause, on repeat. Its clock is read
 * only while drawing, so a running wave redraws the chips without recomposing anything.
 */
@Stable
class ChipWave internal constructor() {
    internal val clock = mutableFloatStateOf(IDLE)

    /** How far chip [index] is lifted right now, 0 to 1. */
    fun at(index: Int): Float {
        val t = clock.floatValue
        if (t < 0f) return 0f
        val local = (t - index * STAGGER_MS) / PULSE_MS
        return if (local <= 0f || local >= 1f) 0f else sin(local * PI).toFloat()
    }
}

/**
 * Runs a [ChipWave] over [chips] chips while [active]: the first after a short wait, then one
 * every [GAP_MS]. A change of [nudge] sends one at once.
 */
@Composable
fun rememberChipWave(active: Boolean, chips: Int, nudge: Int = 0): ChipWave {
    val wave = remember { ChipWave() }
    val count by rememberUpdatedState(chips)
    LaunchedEffect(active, nudge) {
        wave.clock.floatValue = IDLE
        if (!active) return@LaunchedEffect
        delay(if (nudge > 0) 0L else START_MS)
        while (true) {
            val length = PULSE_MS + STAGGER_MS * (count - 1).coerceAtLeast(0)
            val start = withFrameMillis { it }
            var elapsed = 0L
            while (elapsed < length) {
                elapsed = withFrameMillis { it } - start
                wave.clock.floatValue = elapsed.toFloat()
            }
            wave.clock.floatValue = IDLE
            delay(GAP_MS)
        }
    }
    return wave
}

private const val IDLE = -1f
private const val PULSE_MS = 420f
private const val STAGGER_MS = 90f
private const val START_MS = 700L
private const val GAP_MS = 3_000L
private val WAVE_LIFT = 3.dp
private const val WAVE_GROW = 0.04f

/** A fixed blue, apart from the accent, that reads on dark, light and glass alike. */
private val WAVE_TINT = Color(0xFF4F86F7)
private const val WAVE_ALPHA = 0.42f
