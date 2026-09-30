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
 * accessibility services. The chosen tab reads at full strength and the rest recede, which
 * is enough to say where the user is without a pill behind the icon.
 */
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
        items.forEach { item ->
            val selected = item.route == selectedRoute
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
                Box {
                    Icon(
                        painter = painterResource(item.icon),
                        contentDescription = stringResource(item.label),
                        modifier = Modifier.size(ICON_SIZE),
                        tint = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (selected) 1f else UNSELECTED_ALPHA
                        )
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
private val ICON_SIZE = 26.dp
private const val UNSELECTED_ALPHA = 0.55f
