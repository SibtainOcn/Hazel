package com.hazel.android.ui.screens.gallery

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hazel.android.gallery.GallerySearchSite
import com.hazel.android.ui.components.ChipWave
import com.hazel.android.ui.components.PickChip

/**
 * The sites an image word search can go to. None is picked until one is tapped, and tapping
 * the picked one again unpicks it ([onSelect] gets null). Links never use this row.
 */
@Composable
internal fun GallerySiteRow(
    selected: GallerySearchSite?,
    onSelect: (GallerySearchSite?) -> Unit,
    modifier: Modifier = Modifier,
    wave: ChipWave? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GallerySearchSite.entries.forEachIndexed { index, site ->
            PickChip(
                label = site.label,
                selected = site == selected,
                onClick = { onSelect(if (site == selected) null else site) },
                wave = wave?.let { w -> { w.at(index) } }
            )
        }
    }
}
