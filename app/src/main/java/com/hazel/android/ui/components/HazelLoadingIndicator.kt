package com.hazel.android.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The shape that turns while it morphs between rounded polygons: Material 3's expressive
 * loading indicator, used wherever the app is waiting on something with no known duration.
 *
 * It is the real component rather than a rebuild of it, since the app already sits on the
 * material3 release that ships it. There is no pulse: the shape morphs and rotates at a
 * constant rate, which reads as work in progress rather than as a heartbeat.
 *
 * Use this for indeterminate waits only. Anything with a real percentage, such as an active
 * download, should show that percentage instead.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HazelLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    LoadingIndicator(
        modifier = modifier.size(size),
        color = color
    )
}
