package com.hazel.android.ui.share

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazel.android.R
import com.hazel.android.data.CookieRepository
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.DownloadOptions
import java.net.URI

// Theme color tokens matching sheet-instant.html design specifications
private val SheetBgColor = Color(0xFF0A0A0A)
private val SurfaceCardColor = Color(0xFF141414)
private val GrabberColor = Color(0xFF2C2C2C)
private val OutlineBorderColor = Color(0xFF2C2C2C)
private val AccentColor = Color(0xFF8FD6B8)
private val AccentContainerColor = Color(0xFF0E3327)
private val AccentOnColor = Color(0xFF003824)
private val AccentStrongColor = Color(0xFFA9E6CC)
private val TextOnSurfaceColor = Color(0xFFF2F2F0)
private val TextMutedColor = Color(0xFFB8B8B4)
private val TextDimColor = Color(0xFF7A7A77)
private val TagBgColor = Color(0xFF1A1A1A)

/**
 * Hazel Instant share confirmation bottom sheet matching sheet-instant.html and user specifications.
 *
 * Displays a lightweight confirmation sheet directly over host apps:
 * - Top drag handle grabber (36dp x 4dp)
 * - Header with transparent Hazel SVG app logo in a 44dp accent-container badge
 * - Settings Tune icon button (44dp) navigating directly to Hazel Instant preferences
 * - Link info card with domain, broken URL, and active preset chips (Video/Audio, quality ceiling, extras)
 * - Symmetrically styled 52dp action buttons: Cancel (outlined) and Download Now (primary filled)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstantShareSheet(
    url: String,
    sourceLabel: String,
    onDownload: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isVideo by SettingsRepository.getQuickIsVideo(context).collectAsState(initial = true)
    val maxHeight by SettingsRepository.getQuickMaxHeight(context).collectAsState(initial = 0)
    val audioLanguage by SettingsRepository.getInstantAudioLanguage(context).collectAsState(initial = "")
    val options by SettingsRepository.getInstantOptions(context).collectAsState(initial = DownloadOptions())
    val useCookies by CookieRepository.getUseCookies(context).collectAsState(initial = false)

    val hostName = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: sourceLabel

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
                .padding(bottom = 20.dp)
        ) {
            // Header Row: Official Hazel SVG logo badge + titles + Tune settings button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
            ) {
                // Hazel official SVG logo inside styled badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.splash_icon),
                        contentDescription = stringResource(R.string.share_overlay_instant_title),
                        tint = AccentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.share_overlay_instant_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextOnSurfaceColor
                    )
                    Text(
                        text = stringResource(R.string.share_overlay_instant_subtitle),
                        fontSize = 13.sp,
                        color = TextDimColor
                    )
                }

                // Settings Tune icon button with 44dp boundary and outline border
                Surface(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, OutlineBorderColor),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = stringResource(R.string.more_hazel_instant),
                            tint = TextMutedColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Link info & configuration preview card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceCardColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 22.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = hostName.ifBlank { "Media Link" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMutedColor
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = url,
                        fontSize = 13.sp,
                        color = TextDimColor,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Preset configuration summary badges
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Media Type (Video vs Audio)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = AccentContainerColor
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Audiotrack,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = AccentStrongColor
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isVideo) "Video" else "Audio only",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AccentStrongColor
                                )
                            }
                        }

                        // Quality Ceiling badge
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = AccentContainerColor
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = AccentStrongColor
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isVideo) {
                                        if (maxHeight > 0) "${maxHeight}p Limit" else "Best Quality"
                                    } else "Best Audio",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AccentStrongColor
                                )
                            }
                        }

                        if (audioLanguage.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = TagBgColor,
                                border = BorderStroke(1.dp, OutlineBorderColor)
                            ) {
                                Text(
                                    text = audioLanguage,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextMutedColor,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Optional extras tags row
                    if (options.embedThumbnail || options.embedSubs || options.sponsorBlockFilters.isNotEmpty() || useCookies) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (options.embedThumbnail) {
                                ExtraTag("Art")
                            }
                            if (options.embedSubs) {
                                ExtraTag("Subtitles")
                            }
                            if (options.sponsorBlockFilters.isNotEmpty()) {
                                ExtraTag("SponsorBlock")
                            }
                            if (useCookies) {
                                ExtraTag("Cookies")
                            }
                        }
                    }
                }
            }

            // Action Buttons: Symmetrically sized 52dp buttons matching sheet-instant.html
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
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
                        text = stringResource(R.string.share_overlay_instant_cancel),
                        maxLines = 1,
                        softWrap = false,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentColor,
                        contentColor = AccentOnColor
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.download),
                        contentDescription = null,
                        tint = AccentOnColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.share_overlay_instant_download),
                        maxLines = 1,
                        softWrap = false,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtraTag(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = TagBgColor,
        border = BorderStroke(1.dp, OutlineBorderColor)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextMutedColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}
