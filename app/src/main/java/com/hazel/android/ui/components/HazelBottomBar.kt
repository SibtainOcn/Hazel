package com.hazel.android.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.graphics.shapes.Morph
import com.hazel.android.ui.components.player.MorphShape

/** One destination on the bottom bar. */
data class BottomBarItem(
    val route: String,
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
    /** Draws the red dot that says something is happening there. */
    val showDot: Boolean = false
)

/**
 * The app's bottom bar: icons only, on a short bar.
 *
 * The destinations are few and their icons are the ones every app uses for them, so a
 * label under each only doubled the bar's height. The label is still the icon's name for
 * accessibility services. Each tab has its own M3 shape behind its icon when chosen (a
 * cookie, a pentagon, a clover, a gem), morphing in from a circle with a spring, so moving
 * between tabs reads as one shape becoming another.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HazelBottomBar(
    items: List<BottomBarItem>,
    selectedRoute: String?,
    containerColor: Color,
    onSelect: (BottomBarItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(BAR_HEIGHT),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val selected = item.route == selectedRoute
            val target = TabShapes[index % TabShapes.size]
            val morph = remember(target) { Morph(MaterialShapes.Circle, target) }
            // Overshoots a little on the way in, the expressive spring; gone when not chosen.
            val grow by animateFloatAsState(
                if (selected) 1f else 0f,
                spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
                label = "tabShape"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(
                        selected = selected,
                        onClick = { onSelect(item) },
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 28.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (grow > 0.001f) {
                    val progress = grow.coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .size(INDICATOR_SIZE)
                            .graphicsLayer {
                                scaleX = grow
                                scaleY = grow
                                rotationZ = (1f - progress) * -45f
                            }
                            .clip(MorphShape(morph, progress))
                            // The same soft tone as the logo pill in the header.
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    )
                }
                Box {
                    Icon(
                        painter = painterResource(item.icon),
                        contentDescription = stringResource(item.label),
                        modifier = Modifier.size(ICON_SIZE),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else UNSELECTED_ALPHA)
                    )
                    if (item.showDot) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 3.dp, y = (-2).dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                    }
                }
            }
        }
    }
}

private val BAR_HEIGHT = 56.dp
private val ICON_SIZE = 24.dp
private val INDICATOR_SIZE = 46.dp

/** Home, History, Queue, More, in the bar's order. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val TabShapes = listOf(
    MaterialShapes.Cookie7Sided,
    MaterialShapes.Pentagon,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Gem
)
private const val UNSELECTED_ALPHA = 0.55f
