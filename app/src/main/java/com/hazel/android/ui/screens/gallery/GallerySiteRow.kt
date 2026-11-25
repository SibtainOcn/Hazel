package com.hazel.android.ui.screens.gallery

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.gallery.GallerySearchSite
import com.hazel.android.ui.components.pressCorner
import com.hazel.android.ui.components.pressScale

/**
 * Where an image search goes: a "Search on" label and one chip per site. The chosen site turns
 * into a tertiary tab led by a seven-sided cookie, a sibling of the search source chips above.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GallerySiteRow(
    selected: GallerySearchSite,
    onSelect: (GallerySearchSite) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.fetch_settings_search_on),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        GallerySearchSite.entries.forEach { site ->
            SiteChip(label = site.label, selected = site == selected, onClick = { onSelect(site) })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SiteChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val resting by animateDpAsState(if (selected) 12.dp else 20.dp, label = "siteChip")
    val radius by pressCorner(interaction, resting = resting, pressed = 8.dp)
    val container by animateColorAsState(if (selected) colors.tertiary else colors.surfaceContainerHigh, label = "siteFill")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(radius),
        color = container,
        contentColor = if (selected) colors.onTertiary else colors.onSurface,
        interactionSource = interaction,
        modifier = Modifier
            .pressScale(interaction)
            .height(40.dp)
            .semantics { this.selected = selected }
    ) {
        Row(
            modifier = Modifier.padding(start = if (selected) 6.dp else 16.dp, end = if (selected) 14.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(MaterialShapes.Cookie7Sided.toShape())
                        .background(colors.onTertiary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.tertiary)
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
