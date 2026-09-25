package com.hazel.android.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import com.hazel.android.update.HazelUpdater
import com.hazel.android.update.UpdateListGroup
import com.hazel.android.update.UpdateListItem
import com.hazel.android.update.UpdateSectionLabel
import com.hazel.android.update.UpdateTokens
import com.hazel.android.update.UpdateTopBar
import com.hazel.android.update.YtDlpUpdater

/**
 * Software Update hub screen displaying components:
 * 1. Hazel update (App updates)
 * 2. yt-dlp update (Engine updates)
 */
@Composable
fun SoftwareUpdateScreen(
    onBack: () -> Unit,
    onNavigateToHazelUpdate: () -> Unit,
    onNavigateToYtDlpUpdate: () -> Unit
) {
    val context = LocalContext.current
    val isFdroid = HazelUpdater.isFdroid()
    val hazelVersion = HazelUpdater.installedVersion()
    val ytdlpVersion = YtDlpUpdater.cachedVersion(context) ?: "Default"

    val hazelUpdateAvailable by SettingsRepository.getHazelUpdateAvailable(context).collectAsState(initial = false)
    val ytDlpUpdateAvailable by SettingsRepository.getYtDlpUpdateAvailable(context).collectAsState(initial = false)

    Scaffold(
        topBar = {
            UpdateTopBar(
                title = "Software update",
                onBack = onBack
            )
        },
        containerColor = UpdateTokens.Bg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            // Hero distribution overview banner - sleek, compact, and optimized
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(UpdateTokens.Surface)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    Text(
                        text = if (isFdroid) "F-Droid Distribution" else "GitHub Release",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UpdateTokens.AccentStrong
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "System Components",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = UpdateTokens.OnSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isFdroid) {
                            "F-Droid builds adhere strictly to repository policies. App binaries are managed by F-Droid."
                        } else {
                            "Keep your Hazel application and yt-dlp extractor engine updated with the latest improvements."
                        },
                        fontSize = 13.sp,
                        color = UpdateTokens.OnSurfaceVar,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            UpdateSectionLabel(text = "Components", isFirst = true)

            UpdateListGroup {
                // Row 1: Hazel update
                UpdateListItem(
                    painter = painterResource(R.drawable.splash_icon),
                    primary = "Hazel",
                    secondary = if (isFdroid) {
                        "v$hazelVersion · Managed by F-Droid"
                    } else {
                        "Version $hazelVersion installed"
                    },
                    showDivider = false,
                    onClick = onNavigateToHazelUpdate,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (hazelUpdateAvailable) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(8.dp)
                                        .background(Color(0xFFFF3B30), CircleShape)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = UpdateTokens.OnSurfaceDim,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )

                // Row 2: yt-dlp update
                UpdateListItem(
                    icon = Icons.Filled.Code,
                    primary = "yt-dlp",
                    secondary = "Engine $ytdlpVersion",
                    showDivider = true,
                    onClick = onNavigateToYtDlpUpdate,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (ytDlpUpdateAvailable) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(8.dp)
                                        .background(Color(0xFFFF3B30), CircleShape)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = UpdateTokens.OnSurfaceDim,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            UpdateSectionLabel(text = "Device & architecture")

            val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "Universal"
            val allAbis = Build.SUPPORTED_ABIS.joinToString(", ")
            val deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
            val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

            UpdateListGroup {
                UpdateListItem(
                    painter = painterResource(R.drawable.ic_software_update),
                    primary = "Device architecture",
                    secondary = "$primaryAbi · Supports $allAbis",
                    showDivider = false
                )
                UpdateListItem(
                    icon = Icons.Filled.Smartphone,
                    primary = "Device & system",
                    secondary = "$deviceModel · $osVersion",
                    showDivider = true
                )
                UpdateListItem(
                    icon = Icons.Filled.Info,
                    primary = "Application target",
                    secondary = "${if (isFdroid) "F-Droid build" else "GitHub Direct release"} · v$hazelVersion",
                    showDivider = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
