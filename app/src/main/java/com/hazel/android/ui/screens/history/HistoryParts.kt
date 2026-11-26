package com.hazel.android.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.download.DownloadViewModelHolder
import com.hazel.android.gallery.queue.GalleryQueue
import com.hazel.android.ui.theme.accentInk
import com.hazel.android.ui.theme.accentTint
import com.hazel.android.ui.theme.accentTintSolid

/** What is downloading now, from either engine: its title and share done, null while unknown. */
internal data class ActiveDownload(val title: String, val progress: Float?)

/**
 * The download in progress, at the top of the Downloads screen; a tap opens the Queue's
 * Running tab. Reads the run's state here only, so progress ticks redraw this card alone.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ActiveDownloadCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val state by DownloadViewModelHolder.get().state.collectAsState()
    val jobs by GalleryQueue.jobs.collectAsState()
    val active = state.active?.takeIf { state.isDownloading }?.let {
        ActiveDownload(it.title.ifBlank { it.url }, state.progress.takeUnless { state.unmeasured })
    } ?: jobs.firstOrNull { it.state == GalleryQueue.State.RUNNING }?.let { job ->
        val total = job.request.files?.size ?: job.request.indexes?.size ?: 0
        ActiveDownload(
            job.request.title.ifBlank { job.request.url },
            if (total > 0) job.fetched.toFloat() / total else null
        )
    } ?: return
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = accentTint()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(accentInk().copy(alpha = 0.14f), MaterialShapes.Cookie9Sided.toShape()),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, tint = accentInk())
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        active.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentInk(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    active.progress?.let {
                        Text(
                            "${(it.coerceIn(0f, 1f) * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = accentInk(),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                val track = accentInk().copy(alpha = 0.2f)
                val bar = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                val progress = active.progress
                if (progress == null) {
                    LinearProgressIndicator(modifier = bar, color = scheme.primary, trackColor = track)
                } else {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = bar,
                        color = scheme.primary,
                        trackColor = track,
                        drawStopIndicator = {}
                    )
                }
            }
        }
    }
}

/** An entry's options button: three dots on a tonal seven-sided cookie. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CookieMenuButton(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(MaterialShapes.Cookie7Sided.toShape())
            .background(accentTintSolid())
            .clickable(onClick = onClick, onClickLabel = stringResource(R.string.history_card_options)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_more_vert),
            contentDescription = stringResource(R.string.history_card_options),
            modifier = Modifier.size(20.dp),
            tint = accentInk()
        )
    }
}

/** The card's audio, video or picture mark on the same cookie as its options button. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CardKindMark(icon: ImageVector) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(accentTintSolid(), MaterialShapes.Cookie7Sided.toShape()),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = accentInk())
    }
}
