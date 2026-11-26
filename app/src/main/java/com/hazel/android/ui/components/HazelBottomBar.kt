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
import androidx.graphics.shapes.Morph
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.graphics.shapes.toPath

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
 * accessibility services. The chosen tab's icon sits on an M3 shape, a 6-sided cookie and a
 * 4-leaf clover in turn along the bar, morphing in from a circle with a spring; a press
 * squeezes the tab and springs it back.
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
            // One calm spring, no bounce; read only while drawing, so the bar is never
            // recomposed for it and a tap starts nothing else.
            val grow = animateFloatAsState(
                if (selected) 1f else 0f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                label = "tabShape"
            )
            val tone = com.hazel.android.ui.theme.accentTint()
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(
                        selected = selected,
                        onClick = { onSelect(item) },
                        role = Role.Tab,
                        interactionSource = interaction,
                        indication = null
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.pressScale(interaction, pressed = TAB_PRESSED), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(INDICATOR_SIZE)
                            .drawBehind {
                                val g = grow.value
                                if (g <= 0.001f) return@drawBehind
                                val path = morph.toPath(g.coerceIn(0f, 1f)).asComposePath()
                                path.transform(Matrix().apply { scale(size.width * g, size.height * g) })
                                val inset = size.width * (1f - g) / 2f
                                translate(inset, size.height * (1f - g) / 2f) { drawPath(path, tone) }
                            }
                    )
                    Box {
                        Icon(
                            painter = painterResource(item.icon),
                            contentDescription = stringResource(item.label),
                            modifier = Modifier.size(ICON_SIZE),
                            tint = if (selected) com.hazel.android.ui.theme.accentInk()
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = UNSELECTED_ALPHA)
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
}

/** The bar's height above the navigation bar; screens shown with it keep this much room. */
val HazelBottomBarHeight = 56.dp
private val BAR_HEIGHT = HazelBottomBarHeight
private val ICON_SIZE = 24.dp
private val INDICATOR_SIZE = 46.dp

/** Two shapes in turn along the bar, so neighbouring tabs never share one. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val TabShapes = listOf(MaterialShapes.Cookie6Sided, MaterialShapes.Clover4Leaf)
private const val TAB_PRESSED = 0.86f
private const val UNSELECTED_ALPHA = 0.55f
