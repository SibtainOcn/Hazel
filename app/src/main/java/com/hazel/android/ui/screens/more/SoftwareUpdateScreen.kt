package com.hazel.android.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazel.android.R
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
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Hero distribution overview banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(UpdateTokens.Surface)
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = if (isFdroid) "F-Droid Distribution" else "GitHub Release",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UpdateTokens.AccentStrong
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "System Components",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = UpdateTokens.OnSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isFdroid) {
                            "F-Droid builds adhere strictly to repository policies. App binaries are managed by F-Droid."
                        } else {
                            "Keep your Hazel application and yt-dlp extractor engine updated with the latest improvements."
                        },
                        fontSize = 13.5.sp,
                        color = UpdateTokens.OnSurfaceVar,
                        lineHeight = 19.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = UpdateTokens.OnSurfaceDim,
                            modifier = Modifier.size(16.dp)
                        )
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = UpdateTokens.OnSurfaceDim,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            UpdateSectionLabel(text = "About distribution")

            UpdateListGroup {
                UpdateListItem(
                    primary = "Channel flavor",
                    secondary = if (isFdroid) "F-Droid Open Source" else "GitHub Direct Releases",
                    showDivider = false
                )
                UpdateListItem(
                    primary = "Verified binaries",
                    secondary = "Cryptographic integrity guaranteed",
                    showDivider = true
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
