package com.hazel.android.ui.screens.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.gallery.GalleryReading
import com.hazel.android.ui.theme.accentInk
import com.hazel.android.ui.theme.accentTint

/**
 * Shown in the header only while several pasted links are read on the Gallery tab: how many
 * of them are in so far. A tap opens the Gallery tab.
 */
@Composable
fun GalleryReadingPill(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val progress by GalleryReading.progress.collectAsState()
    val last = remember { arrayOf<GalleryReading.Progress?>(null) }
    progress?.let { last[0] = it }
    AnimatedVisibility(
        visible = progress != null,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = modifier
    ) {
        val shown = progress ?: last[0] ?: return@AnimatedVisibility
        val label = stringResource(R.string.gallery_reading_links, shown.read, shown.total)
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(accentTint())
                .clickable(onClick = onOpen)
                .semantics { contentDescription = label }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = accentInk()
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${shown.read}/${shown.total}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accentInk(),
                maxLines = 1
            )
        }
    }
}
