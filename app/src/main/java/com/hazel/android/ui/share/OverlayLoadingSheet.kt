package com.hazel.android.ui.share

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazel.android.R
import com.hazel.android.ui.components.ShimmerHost
import com.hazel.android.ui.components.shimmerBlock
import kotlinx.coroutines.delay
import java.net.URI

// Theme color tokens matching sheet-fetching.html
private val SheetBgColor = Color(0xFF0A0A0A)
private val GrabberColor = Color(0xFF2C2C2C)
private val OutlineBorderColor = Color(0xFF2C2C2C)
private val RailTrackColor = Color(0xFF1F1F1F)
private val AccentColor = Color(0xFF8FD6B8)
private val AccentContainerColor = Color(0xFF0E3327)
private val AccentTrackColor = Color(0x2E8FD6B8)
private val TextOnSurfaceColor = Color(0xFFF2F2F0)
private val TextMutedColor = Color(0xFFB8B8B4)
private val TextDimColor = Color(0xFF7A7A77)

/**
 * Modern loading bottom sheet strictly matching sheet-fetching.html.
 *
 * Renders an active spinner badge, dynamic progress status stage messages ("Connecting to...",
 * "Reading media details...", "Finding source qualities...", "Almost ready..."), a progressively
 * filling accent progress rail, full media-card skeleton preview with Hazel's canonical
 * ShimmerHost sweep, and a full-width Cancel button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayLoadingSheet(
    url: String,
    sourceLabel: String,
    progressMessage: String = "",
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val host = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: sourceLabel
    val shortDisplayUrl = remember(url) {
        val uri = runCatching { URI(url) }.getOrNull()
        if (uri != null) {
            val h = uri.host?.removePrefix("www.").orEmpty()
            val p = uri.path.orEmpty()
            (h + p).trimEnd('/')
        } else url
    }

    // Dynamic phase transitions: Steps smoothly through phases without blocking the actual fetch
    var stageIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            stageIndex = (stageIndex + 1) % 4
        }
    }

    val currentStatusText = if (progressMessage.isNotBlank()) {
        progressMessage
    } else {
        when (stageIndex) {
            0 -> stringResource(R.string.share_overlay_stage_connecting, host.ifBlank { "source" })
            1 -> stringResource(R.string.share_overlay_stage_reading)
            2 -> stringResource(R.string.share_overlay_stage_qualities)
            else -> stringResource(R.string.share_overlay_stage_ready)
        }
    }

    // Smooth continuous progress rail animation matching CSS animation: rail-grow 3s infinite
    val infiniteTransition = rememberInfiniteTransition(label = "railAnim")
    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.96f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "railProgress"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBgColor,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GrabberColor)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Header Row: Active animated spinner badge + status headline & link path
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
            ) {
                // Fetch badge with spinning active arc ring around Hazel official SVG logo
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        strokeWidth = 3.5.dp,
                        color = AccentColor,
                        trackColor = AccentTrackColor
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentContainerColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.splash_icon),
                            contentDescription = null,
                            tint = AccentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentStatusText,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextOnSurfaceColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = shortDisplayUrl,
                        fontSize = 13.sp,
                        color = TextDimColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Progress Rail: Thin track with animated dynamic fill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(RailTrackColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(AccentColor)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Skeleton Preview: Exact match with sheet-fetching.html layout using Hazel's ShimmerHost
            ShimmerHost(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Skeleton media card layout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Square thumbnail placeholder
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .shimmerBlock(RoundedCornerShape(16.dp))
                        )

                        // Headline & detail lines
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.80f)
                                    .height(12.dp)
                                    .shimmerBlock(RoundedCornerShape(6.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.50f)
                                    .height(12.dp)
                                    .shimmerBlock(RoundedCornerShape(6.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.35f)
                                    .height(10.dp)
                                    .shimmerBlock(RoundedCornerShape(6.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Skeleton chips row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(58.dp)
                                .height(32.dp)
                                .shimmerBlock(RoundedCornerShape(16.dp))
                        )
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(32.dp)
                                .shimmerBlock(RoundedCornerShape(16.dp))
                        )
                        Box(
                            modifier = Modifier
                                .width(66.dp)
                                .height(32.dp)
                                .shimmerBlock(RoundedCornerShape(16.dp))
                        )
                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .height(32.dp)
                                .shimmerBlock(RoundedCornerShape(16.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Action Button: Full-width Cancel button
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, OutlineBorderColor),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = TextMutedColor
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.share_overlay_cancel_fetch),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
