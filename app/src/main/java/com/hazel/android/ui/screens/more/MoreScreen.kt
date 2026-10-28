package com.hazel.android.ui.screens.more

import android.app.Activity
import android.os.Build
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.ui.components.ShapeIconButton
import com.hazel.android.ui.screens.download.isIgnoringBatteryOptimizations
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.collectAsState
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.extractor.ListingSource
import com.hazel.android.download.formatFileSize
import com.hazel.android.update.YtDlpUpdater
import com.hazel.android.util.AppLocale
import com.hazel.android.util.TempStorage

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MoreScreen(
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToConverter: () -> Unit = {},
    onNavigateToStorageLocations: () -> Unit = {},
    onNavigateToCookies: () -> Unit = {},
    onNavigateToFetchSettings: () -> Unit = {},
    onOpenBatterySettings: () -> Unit = {},
    onNavigateToStorageCleanup: () -> Unit = {},
    onNavigateToUpdate: () -> Unit = {},
    onNavigateToSponsor: () -> Unit = {},
    onNavigateToProcessing: () -> Unit = {},
    onNavigateToAdvanced: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToDocumentation: () -> Unit = {},
    onNavigateToSettingsSearch: () -> Unit = {}
) {
    val context = LocalContext.current
    val listingSource by remember(context) { SettingsRepository.getListingSource(context) }.collectAsState(initial = ListingSource.DEFAULT)
    val hasUpdateAvailable by remember(context) { SettingsRepository.getHasUpdateAvailable(context) }.collectAsState(initial = false)

    // Read when the screen appears so the row can show what clearing would free.
    var tempBytes by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) { tempBytes = TempStorage.totalBytes(context) }

    // Read once as the screen is built, then again on every return to it, so coming back
    // from the system settings shows the answer that was just given.
    //
    // The first read happens here rather than being left to the resume below. Inside a
    // navigation destination the lifecycle is the back stack entry's own, so it resumes on
    // every visit to this tab, which meant the screen drew once on an assumed answer and
    // corrected itself a moment later: the card appeared out of nowhere and pushed
    // everything under it down. The call is a single lookup, cheap enough to make before
    // the first frame instead of guessing.
    val lifecycleOwner = LocalLifecycleOwner.current
    var batteryExempt by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryExempt = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The language picker, and the note that follows a change. The label is read back from
    // wherever the choice actually lives, which on Android 13 and later is the system rather
    // than the app, so a change made in the system settings shows here too.
    var languageDialogOpen by remember { mutableStateOf(false) }
    // Saved rather than merely remembered. Setting the language on Android 13 and later
    // hands the work to the platform, which rebuilds the activity on the spot; a flag held
    // only in composition dies with it, and the note that explains the change never
    // appeared on exactly the versions where the change is most abrupt.
    var languageChanged by rememberSaveable { mutableStateOf(false) }
    var languageTag by remember { mutableStateOf(AppLocale.tag(context)) }
    val languageLabel = AppLocale.label(context)

    if (languageDialogOpen) {
        LanguageSheet(
            current = languageTag,
            onConfirm = { tag ->
                languageDialogOpen = false
                // Confirming without having picked anything different is a no-op, so the
                // note that follows a change is not shown for a sheet that was only opened.
                if (tag != languageTag) {
                    AppLocale.set(context, tag)
                    languageTag = tag
                    languageChanged = true
                }
            },
            onDismiss = { languageDialogOpen = false }
        )
    }

    if (languageChanged) {
        LanguageChangedDialog(
            onDismiss = {
                languageChanged = false
                // Android 13 and later has already redrawn everything in the new language.
                // Below that nothing has changed yet, and rebuilding the activity is what
                // makes the wrapped context take effect.
                if (Build.VERSION.SDK_INT < 33) {
                    (context as? Activity)?.recreate()
                }
            }
        )
    }

    // yt-dlp engine version, as recorded by the last in-app engine update

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp)
    ) {
        // The screen names itself, the way the downloads list does, and stays pinned with
        // its search while the settings below it scroll.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.more_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            // Every setting, found by name, from its own screen. Drawn the way the home
            // search bar draws its icon: a seven-sided cookie in the solid accent. The
            // padding keeps the row exactly as tall as the 48dp button it replaces.
            ShapeIconButton(
                onClick = onNavigateToSettingsSearch,
                modifier = Modifier.padding(4.dp),
                filled = true,
                shape = MaterialShapes.Cookie7Sided.toShape()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = stringResource(R.string.settings_search),
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {

        // Android suspends a long network job shortly after the app leaves the foreground,
        // so without this exemption a download stops when the screen is turned off. It
        // heads the screen while it is missing and disappears once granted, because at that
        // point there is nothing left to do about it.
        if (!batteryExempt) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = onOpenBatterySettings
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.battery_charge),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.more_battery_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.more_battery_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                .copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // ── Main options card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          // A name too long for its row at a large font breaks at a syllable with a hyphen,
          // rather than leaving its last letter alone on the next line.
          androidx.compose.material3.ProvideTextStyle(
              androidx.compose.material3.LocalTextStyle.current.copy(
                  hyphens = androidx.compose.ui.text.style.Hyphens.Auto,
                  lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph
              )
          ) {
            // Appearance: theme and accent colour
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_appearance)) },
                leadingContent = {
                    Icon(Icons.Filled.Palette, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = { MoreChevron() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToAppearance() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Processing: what every download starts from
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_processing)) },
                leadingContent = {
                    Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = { MoreChevron() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToProcessing() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Advanced: engine options for difficult sources
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_advanced)) },
                leadingContent = {
                    Icon(Icons.Filled.Build, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = { MoreChevron() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToAdvanced() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.more_backup)) },
                leadingContent = {
                    Icon(Icons.Filled.SettingsBackupRestore, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = { MoreChevron() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToBackup() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Download location, read-only
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_downloads)) },
                leadingContent = {
                    Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = { MoreChevron() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToStorageLocations() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Language, with the current choice under it. On the right, a long language name
            // at a large font squeezed the row's own name until it broke apart.
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_language)) },
                supportingContent = {
                    Text(
                        languageLabel,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                },
                leadingContent = {
                    Icon(Icons.Filled.Language, null, tint = MaterialTheme.colorScheme.primary)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { languageDialogOpen = true }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Cookies: saved sign-ins that let the downloader reach gated media
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_cookies)) },
                leadingContent = {
                    Icon(Icons.Filled.Cookie, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = { MoreChevron() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToCookies() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Link reading: network bounds used while resolving a pasted link
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_link_reading)) },
                leadingContent = {
                    Icon(Icons.Filled.Speed, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = { MoreChevron() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToFetchSettings() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Working files the app holds, with the running total under the name so it is
            // visible without opening the screen, and leaves the name room at a large font.
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_temporary_files)) },
                supportingContent = {
                    Text(
                        formatFileSize(tempBytes).ifBlank { "0 KB" },
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Filled.DeleteSweep, null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = { MoreChevron() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToStorageCleanup() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // The one tool the app has, reached directly. It used to sit behind a Tools
            // screen that held nothing else, which is a tap and a screen to say one word.
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_converter)) },
                leadingContent = {
                    Icon(
                        painterResource(R.drawable.music), null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                trailingContent = { MoreChevron() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToConverter() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Software update screen (Hazel app & yt-dlp engine)
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_software_update)) },
                leadingContent = {
                    Box(modifier = Modifier.size(24.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_software_update),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        if (hasUpdateAvailable) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .align(Alignment.TopEnd)
                                    .background(Color(0xFFFF3B30), CircleShape)
                            )
                        }
                    }
                },
                trailingContent = { MoreChevron() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToUpdate() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Above the two lines that leave the app, since this one is a screen of its
            // own and the ones under it are addresses.
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_support)) },
                leadingContent = {
                    Icon(Icons.Filled.Favorite, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = { MoreChevron() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToSponsor() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // The source is offered on the Support screen above, where it sits among the
            // other ways of helping that cost nothing. It was here as well, so the same
            // address was reachable by two routes that behaved differently, and the one
            // that stayed is the one with something to say about why anyone would follow it.

            // The first-launch carousel again, for anyone who skipped it or installed before
            // it said what it says now.
            var guideOpen by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_getting_started)) },
                leadingContent = {
                    Icon(Icons.Filled.Bolt, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = { MoreChevron() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { guideOpen = true }
            )
            if (guideOpen) {
                com.hazel.android.ui.screens.download.GettingStartedDialog(
                    onOpenBatterySettings = onOpenBatterySettings,
                    onDismiss = { guideOpen = false }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // The guide, FAQ, what's new and the licence, read in the app. Each page has a
            // button to open it in the browser, for saving or sharing it.
            ListItem(
                headlineContent = { Text(stringResource(R.string.more_documentation)) },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook, null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = { MoreChevron() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToDocumentation() }
            )
          }
        }
        }
    }
}

/**
 * The arrow at the end of a row that opens a screen. At a large font it is left out, so the
 * row's name has the room instead of breaking a long word.
 */
@Composable
private fun MoreChevron() {
    if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) return
    Icon(
        Icons.AutoMirrored.Filled.ArrowForwardIos, null,
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        modifier = Modifier.size(16.dp)
    )
}
