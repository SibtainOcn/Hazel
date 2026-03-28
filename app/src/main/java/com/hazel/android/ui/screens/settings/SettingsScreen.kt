package com.hazel.android.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.ui.theme.AccentColors
import com.hazel.android.ui.components.AccentPickerDialog
import com.hazel.android.util.StoragePaths
import com.hazel.android.util.openInAppBrowser

@Composable
fun SettingsScreen(
    accentName: String,
    onAccentChanged: (String) -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToGuide: () -> Unit = {},
    onNavigateToStorageLocations: () -> Unit = {},
    onNavigateToUpdate: () -> Unit = {}
) {
    val context = LocalContext.current
    var accentMenuExpanded by remember { mutableStateOf(false) }

    val currentAccent = AccentColors.find { it.name == accentName } ?: AccentColors.first()

    // Read version name for display
    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (_: Exception) { "1.0.0" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // ── Main Settings Card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            // Download location — read-only
            ListItem(
                headlineContent = { Text("Download Location") },
                supportingContent = {
                    Text(
                        StoragePaths.DOWNLOADS_DISPLAY,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                },
                leadingContent = {
                    Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToStorageLocations() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Accent Color row — opens centered dialog
            ListItem(
                headlineContent = { Text("Accent Color") },
                supportingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(currentAccent.dark)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(accentName)
                    }
                },
                leadingContent = {
                    Icon(Icons.Filled.Palette, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { accentMenuExpanded = true }
            )

            // Centered dialog for accent picker
            if (accentMenuExpanded) {
                AccentPickerDialog(
                    accentName = accentName,
                    onAccentChanged = onAccentChanged,
                    onDismiss = { accentMenuExpanded = false }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Check for updates — navigates to dedicated UpdateScreen
            ListItem(
                headlineContent = { Text("Check for Updates") },
                supportingContent = { Text("v$versionName") },
                leadingContent = {
                    Icon(Icons.Filled.Update, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToUpdate() }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Guide & About ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            ListItem(
                headlineContent = { Text("Quick Guide", fontWeight = FontWeight.Medium) },
                supportingContent = { Text("Step-by-step instructions for all features") },
                leadingContent = {
                    Icon(Icons.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToGuide() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            ListItem(
                headlineContent = { Text("License", fontWeight = FontWeight.Medium) },
                supportingContent = { Text("GNU General Public License v3.0") },
                leadingContent = {
                    Icon(Icons.Filled.Description, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { 
                    openInAppBrowser(context, "https://github.com/SibtainOcn/Hazel/blob/main/LICENSE")
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            ListItem(
                headlineContent = { Text("About Hazel", fontWeight = FontWeight.Medium) },
                supportingContent = { Text("Version, credits & licenses") },
                leadingContent = {
                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToAbout() }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Buy Me a Coffee + Social Icons — single row ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // BMC button — left aligned
            Image(
                painter = painterResource(R.drawable.bmc_button),
                contentDescription = "Buy Me a Coffee",
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        openInAppBrowser(context, "https://buymeacoffee.com/sibtainocn")
                    },
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.weight(1f))

            // Instagram
            Card(
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        openInAppBrowser(context, "https://instagram.com/hazel.android")
                    },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_instagram),
                        contentDescription = "Instagram",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // GitHub
            Card(
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        openInAppBrowser(context, "https://github.com/SibtainOcn")
                    },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_github),
                        contentDescription = "GitHub",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
