package com.hazel.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A quiet placeholder for a screen with nothing to show: a cookie badge, a title and one hint. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyState(
    icon: Painter,
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    badge: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(34.dp))
            }
            if (badge != null) {
                Box(modifier = Modifier.align(Alignment.BottomCenter).offset(y = 12.dp)) { badge() }
            }
        }
        Spacer(modifier = Modifier.height(if (badge != null) 28.dp else 16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            textAlign = TextAlign.Center
        )
        if (hint != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
