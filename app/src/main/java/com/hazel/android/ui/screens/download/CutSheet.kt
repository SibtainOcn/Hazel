package com.hazel.android.ui.screens.download

import com.hazel.android.ui.components.ActionsRow
import com.hazel.android.ui.components.EqualWidthActions
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.download.OneOffOptions
import com.hazel.android.download.formatTimestamp
import com.hazel.android.download.parseTimestamp
import com.hazel.android.download.playback.PlaybackController
import com.hazel.android.ui.components.FlatChip
import com.hazel.android.ui.components.player.PlayerSurface
import com.hazel.android.ui.components.player.rememberPlaybackController
import com.hazel.android.ui.components.keepFlingInSheet
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/**
 * A sheet that keeps only part of the media: a range picked on a slider over its length, or typed
 * exactly, with the choice of exact cut points (slower, as the ends are encoded again) or
 * cut points at the nearest keyframe.
 *
 * A preview plays the range on a loop above the slider. Moving the start plays from the new
 * start; moving the end plays the last moments before it, so each cut can be judged by eye.
 *
 * The whole length picked is no cut at all, so it clears rather than asking yt-dlp to cut
 * out everything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CutSheet(
    url: String,
    thumbnail: String?,
    durationSeconds: Int,
    current: OneOffOptions,
    onApply: (start: Double, end: Double, precise: Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val initialStart = if (current.hasSection) current.sectionStart else 0.0
    val controller = rememberPlaybackController(url, startAtMs = (initialStart * 1000).toLong())
    val duration = durationSeconds.toDouble().takeIf { it > 0 }
        ?: (controller.durationMs / 1000.0).takeIf { it > 0 }
        ?: 0.0

    var startText by remember { mutableStateOf(formatTimestamp(initialStart)) }
    var endText by remember {
        mutableStateOf(
            when {
                current.hasSection -> formatTimestamp(current.sectionEnd)
                durationSeconds > 0 -> formatTimestamp(durationSeconds.toDouble())
                else -> ""
            }
        )
    }
    var precise by remember { mutableStateOf(current.preciseCuts) }

    // Media whose length was unknown until the player read it gets its end filled in then.
    LaunchedEffect(duration) {
        if (endText.isBlank() && duration > 0) endText = formatTimestamp(duration)
    }

    val start = parseTimestamp(startText)
    val end = parseTimestamp(endText)
    val valid = start != null && end != null && end > start && (duration <= 0 || end <= duration + 0.5)

    // Playback stays inside the range: reaching the end goes back to the start.
    SideEffect {
        val from = start
        val to = end
        controller.onProgress = { position ->
            if (from != null && to != null && to > from && position >= (to * 1000).toLong()) {
                controller.seekTo((from * 1000).toLong())
            }
        }
    }
    LaunchedEffect(controller) {
        snapshotFlow { parseTimestamp(startText) }.distinctUntilChanged().drop(1).collect { from ->
            if (from != null) controller.seekTo((from * 1000).toLong())
        }
    }
    LaunchedEffect(controller) {
        snapshotFlow { parseTimestamp(endText) }.distinctUntilChanged().drop(1).collect { to ->
            if (to != null) {
                val from = parseTimestamp(startText) ?: 0.0
                controller.seekTo((maxOf(from, to - END_PREVIEW_SECONDS) * 1000).toLong())
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .keepFlingInSheet()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ContentCut,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    stringResource(R.string.options_cut_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            CutPreview(controller = controller, thumbnail = thumbnail)
            Spacer(modifier = Modifier.height(6.dp))
            PreviewControls(
                controller = controller,
                onJumpStart = { start?.let { controller.seekTo((it * 1000).toLong()) } },
                onJumpEnd = {
                    end?.let { controller.seekTo((maxOf(start ?: 0.0, it - END_PREVIEW_SECONDS) * 1000).toLong()) }
                }
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (duration > 0) {
                val from = (start ?: 0.0).coerceIn(0.0, duration).toFloat()
                val to = (end ?: duration).coerceIn(0.0, duration).toFloat()
                RangeSlider(
                    value = minOf(from, to)..maxOf(from, to),
                    onValueChange = { range ->
                        startText = formatTimestamp(range.start.toDouble())
                        endText = formatTimestamp(range.endInclusive.toDouble())
                    },
                    valueRange = 0f..duration.toFloat()
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        formatTimestamp(0.0),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatTimestamp(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Text(
                    stringResource(R.string.options_cut_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Half the width each while both labels fit; one above the other when not.
            EqualWidthActions(spacing = 8.dp) {
                FlatChip(
                    label = stringResource(R.string.options_cut_start_here),
                    onClick = { startText = formatTimestamp(controller.positionMs / 1000.0) }
                )
                FlatChip(
                    label = stringResource(R.string.options_cut_end_here),
                    onClick = { endText = formatTimestamp(controller.positionMs / 1000.0) }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                EditableField(
                    label = stringResource(R.string.options_cut_start),
                    value = startText,
                    onValueChange = { startText = it },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                EditableField(
                    label = stringResource(R.string.options_cut_end),
                    value = endText,
                    onValueChange = { endText = it },
                    modifier = Modifier.weight(1f)
                )
            }
            if (!valid && startText.isNotBlank() && endText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.options_cut_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ToggleRow(
                label = stringResource(R.string.options_cut_precise),
                checked = precise,
                onCheckedChange = { precise = it }
            )
            Text(
                stringResource(R.string.options_cut_precise_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            // Clear at the start, Cancel and Apply at the end; they move under Clear, and Apply
            // under Cancel, only when they do not fit one line.
            ActionsRow(
                modifier = Modifier.fillMaxWidth(),
                spacing = 8.dp,
                start = if (current.hasSection) {
                    { TextButton(onClick = onClear) { Text(stringResource(R.string.options_cut_clear)) } }
                } else null
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.download_cancel)) }
                Surface(
                    onClick = {
                        if (start != null && end != null) {
                            val whole = duration > 0 && start <= 0.05 && end >= duration - 0.05
                            if (whole) onClear() else onApply(start, end, precise)
                        }
                    },
                    enabled = valid,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (valid) 1f else 0.38f),
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.options_cut_apply), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** The picture, with the artwork standing in until it arrives. A tap pauses or plays. */
@Composable
private fun CutPreview(controller: PlaybackController, thumbnail: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = controller::toggle
            ),
        contentAlignment = Alignment.Center
    ) {
        val ready = controller.phase == PlaybackController.Phase.READY
        if (thumbnail != null && (!ready || !controller.hasVideo)) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (controller.hasVideo) {
            PlayerSurface(player = controller.player, modifier = Modifier.fillMaxSize())
        }
        when {
            controller.phase == PlaybackController.Phase.FAILED -> Text(
                stringResource(R.string.player_failed),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
            !ready || controller.isBuffering -> CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/** Jump to the start, play or pause, jump to the end, the time now, and sound on or off. */
@Composable
private fun PreviewControls(
    controller: PlaybackController,
    onJumpStart: () -> Unit,
    onJumpEnd: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onJumpStart) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.options_cut_jump_start))
        }
        IconButton(onClick = controller::toggle) {
            Icon(
                if (controller.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (controller.isPlaying) R.string.player_pause else R.string.player_play
                )
            )
        }
        IconButton(onClick = onJumpEnd) {
            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.options_cut_jump_end))
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            formatTimestamp(controller.positionMs / 1000.0).substringBefore('.'),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        IconButton(onClick = { controller.mute(!controller.isMuted) }) {
            Icon(
                if (controller.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(
                    if (controller.isMuted) R.string.player_unmute else R.string.player_mute
                )
            )
        }
    }
}

/** How much of the range's end is played when the end moves. */
private const val END_PREVIEW_SECONDS = 1.5
