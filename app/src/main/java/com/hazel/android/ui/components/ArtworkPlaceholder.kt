package com.hazel.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.hazel.android.R

/**
 * What stands where a thumbnail goes when there is none, or it has not loaded yet, or it
 * failed: the Hazel bolt on a soft accent cookie over the surface tone. Drawn under the
 * picture, so a picture that arrives simply covers it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtworkPlaceholder(modifier: Modifier = Modifier.fillMaxSize()) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight(0.46f)
                .heightIn(max = 84.dp)
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.update),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.5f),
                tint = accent
            )
        }
    }
}
