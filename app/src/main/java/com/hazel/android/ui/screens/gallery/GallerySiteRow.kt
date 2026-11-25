package com.hazel.android.ui.screens.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.gallery.GallerySearchSite
import com.hazel.android.ui.components.pressScale

/**
 * Where an image search goes: a globe badge leading an M3 connected button group of sites.
 * Inner corners stay tight; the chosen site morphs into a full tertiary pill with a check.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GallerySiteRow(
    selected: GallerySearchSite,
    onSelect: (GallerySearchSite) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.fetch_settings_search_on)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(MaterialShapes.Cookie4Sided.toShape())
                .background(colors.tertiaryContainer)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.TravelExplore, contentDescription = null, tint = colors.onTertiaryContainer, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val sites = GallerySearchSite.entries
            sites.forEachIndexed { i, site ->
                SiteSegment(
                    label = site.label,
                    selected = site == selected,
                    first = i == 0,
                    last = i == sites.lastIndex,
                    onClick = { onSelect(site) }
                )
            }
        }
    }
}

@Composable
private fun SiteSegment(label: String, selected: Boolean, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val round = 18.dp
    val inner = if (pressed) 4.dp else 6.dp
    val start by animateDpAsState(if (selected || first) round else inner, label = "siteStart")
    val end by animateDpAsState(if (selected || last) round else inner, label = "siteEnd")
    val container by animateColorAsState(if (selected) colors.tertiary else colors.surfaceContainerHigh, label = "siteFill")
    val content by animateColorAsState(if (selected) colors.onTertiary else colors.onSurfaceVariant, label = "siteInk")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end),
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = Modifier
            .pressScale(interaction)
            .height(36.dp)
            .semantics { this.selected = selected }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
