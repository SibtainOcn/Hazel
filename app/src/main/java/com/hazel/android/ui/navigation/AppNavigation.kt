package com.hazel.android.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hazel.android.R
import com.hazel.android.download.BatchState
import com.hazel.android.data.DownloadQueueRepository
import com.hazel.android.ui.screens.queue.QueueScreen
import com.hazel.android.ui.components.HazelBottomBar
import com.hazel.android.ui.components.BottomBarItem
import com.hazel.android.update.HazelUpdater
import com.hazel.android.update.UpdateTokens
import com.hazel.android.data.SettingsRepository
import kotlinx.coroutines.launch
import com.hazel.android.ui.motion.M3Motion
import com.hazel.android.ui.screens.converter.ConverterScreen
import com.hazel.android.ui.screens.cookies.CookiesScreen
import com.hazel.android.ui.screens.download.DownloadScreen
import com.hazel.android.ui.screens.download.openBatterySettings
import com.hazel.android.ui.screens.history.HistoryScreen
import com.hazel.android.ui.screens.more.AppearanceScreen
import com.hazel.android.ui.screens.more.SponsorScreen
import com.hazel.android.ui.screens.more.FetchSettingsScreen
import com.hazel.android.ui.screens.more.MoreScreen
import com.hazel.android.ui.screens.more.StorageCleanupScreen
import com.hazel.android.ui.screens.more.StorageLocationsScreen
import com.hazel.android.ui.screens.more.ProcessingScreen
import com.hazel.android.ui.screens.more.AdvancedScreen
import com.hazel.android.ui.screens.more.BackupScreen
import com.hazel.android.ui.screens.more.ToolsScreen
import com.hazel.android.ui.screens.more.SoftwareUpdateScreen
import com.hazel.android.ui.screens.more.HazelUpdateScreen
import com.hazel.android.update.YtDlpUpdateScreen
import com.hazel.android.update.UpdateScreen

sealed class Screen(
    val route: String,
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val icon: Int
) {
    data object Download : Screen("download", R.string.nav_home, R.drawable.home)
    data object History : Screen("history", R.string.nav_history, R.drawable.downloads_tab)
    data object Queue : Screen("queue", R.string.nav_queue, R.drawable.queue_tab)
    data object More : Screen("more", R.string.nav_more, R.drawable.more_tab)
}

private val bottomNavItems = listOf(
    Screen.Download,
    Screen.History,
    Screen.Queue,
    Screen.More,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    pendingShares: List<com.hazel.android.MainActivity.SharedLink> = emptyList(),
    pendingFailure: String? = null,
    pendingRoute: String? = null,
    onPendingRouteConsumed: () -> Unit = {},
    onPendingFailureConsumed: () -> Unit = {},
    onSharesConsumed: () -> Unit = {},
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    accentName: String,
    onAccentChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val incognito by SettingsRepository.getIncognito(context).collectAsState(initial = false)
    val hazelUpdateAvailable by SettingsRepository.getHazelUpdateAvailable(context).collectAsState(initial = false)

    val navController = rememberNavController()

    // Handle deep navigation triggered from shortcuts or share overlay
    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { route ->
            navController.navigate(route)
            onPendingRouteConsumed()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isSubScreen = currentRoute in listOf(
        "storage_locations", "appearance", "tools", "converter", "update", "cookies", "fetch_settings", "storage_cleanup", "sponsor", "software_update", "hazel_update", "ytdlp_update",
        "processing", "advanced"
    )

    val downloadViewModel: com.hazel.android.download.DownloadViewModel =
        remember { com.hazel.android.download.DownloadViewModelHolder.get() }

    Scaffold(
        topBar = {
            // Only over the home screen. The other two carry their own headings, and the
            // app's name above those made two titles stacked on top of each other, with the
            // screen's own one pushed down a bar's height for nothing. The incognito switch
            // goes with it: what it changes is what a download records, which is decided
            // here and nowhere else.
            if (currentRoute == Screen.Download.route) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.update),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        // For GitHub release builds only (not yt-dlp): when Hazel app update is available,
                        // display a theme-adaptive, accent-independent "Update" pill next to incognito icon
                        if (!HazelUpdater.isFdroid() && hazelUpdateAvailable) {
                            val isDark = isSystemInDarkTheme()
                            val pillBg = if (isDark) UpdateTokens.UpdateContainer else Color(0xFFFFEECC)
                            val pillFg = if (isDark) UpdateTokens.Update else Color(0xFF8F4D00)

                            Row(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .clip(CircleShape)
                                    .background(pillBg)
                                    .clickable {
                                        navController.navigate("hazel_update")
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = "Update Available",
                                    tint = pillFg,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Update",
                                    color = pillFg,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Reads as on or off at a glance: lit and on a filled ground while
                        // it is active, plain and muted while it is not. A mode that
                        // silently changes what the app records has to be visible from the
                        // screen it affects, not buried in settings.
                        IconButton(
                            onClick = {
                                scope.launch {
                                    SettingsRepository.setIncognito(context, !incognito)
                                }
                            },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.incognito),
                                contentDescription = if (incognito) {
                                    stringResource(R.string.nav_incognito_on)
                                } else {
                                    stringResource(R.string.nav_incognito_off)
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (incognito) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .padding(6.dp),
                                tint = if (incognito) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        bottomBar = {
            if (!isSubScreen) {
                val downloadState by downloadViewModel.state.collectAsState()
                val savedQueue by remember(context) { DownloadQueueRepository.getQueue(context) }
                    .collectAsState(initial = emptyList())
                // Lit while anything is downloading, paused part way or waiting its turn,
                // so the queue is found without having to go and look.
                val queueBusy = downloadState.isDownloading || savedQueue.isNotEmpty() ||
                    downloadState.batch.any {
                        it.state == BatchState.DOWNLOADING || it.state == BatchState.PAUSED ||
                            it.state == BatchState.QUEUED
                    }
                val currentDestination = navBackStackEntry?.destination
                HazelBottomBar(
                    items = bottomNavItems.map { screen ->
                        BottomBarItem(
                            route = screen.route,
                            icon = screen.icon,
                            label = screen.titleRes,
                            showDot = screen == Screen.Queue && queueBusy &&
                                currentDestination?.route != Screen.Queue.route
                        )
                    },
                    selectedRoute = bottomNavItems.firstOrNull { screen ->
                        currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    }?.route,
                    containerColor = if (isDarkTheme) Color(0xFF000000)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    onSelect = { item ->
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = Screen.Download.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { M3Motion.forwardEnter() },
            exitTransition = { M3Motion.forwardExit() },
            popEnterTransition = { M3Motion.backEnter() },
            popExitTransition = { M3Motion.backExit() }
        ) {
            composable(Screen.Download.route) {
                DownloadScreen(
                    pendingShares = pendingShares,
                    pendingFailure = pendingFailure,
                    onPendingFailureConsumed = onPendingFailureConsumed,
                    onSharesConsumed = onSharesConsumed,
                    downloadViewModel = downloadViewModel,
                    onOpenQueue = {
                        navController.navigate(Screen.Queue.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen()
            }
            composable(Screen.Queue.route) {
                QueueScreen(downloadViewModel = downloadViewModel)
            }
            composable(Screen.More.route) {
                MoreScreen(
                    onNavigateToAppearance = { navController.navigate("appearance") },
                    onNavigateToConverter = { navController.navigate("converter") },
                    onNavigateToStorageLocations = { navController.navigate("storage_locations") },
                    onNavigateToProcessing = { navController.navigate("processing") },
                    onNavigateToAdvanced = { navController.navigate("advanced") },
                    onNavigateToBackup = { navController.navigate("backup") },
                    onNavigateToCookies = { navController.navigate("cookies") },
                    onNavigateToFetchSettings = { navController.navigate("fetch_settings") },
                    onNavigateToSponsor = { navController.navigate("sponsor") },
                    onOpenBatterySettings = { openBatterySettings(context) },
                    onNavigateToStorageCleanup = { navController.navigate("storage_cleanup") },
                    onNavigateToUpdate = { navController.navigate("software_update") }
                )
            }
            composable("cookies") {
                CookiesScreen(onBack = { navController.popBackStack() })
            }
            composable("storage_cleanup") {
                StorageCleanupScreen(onBack = { navController.popBackStack() })
            }
            composable("fetch_settings") {
                FetchSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("sponsor") {
                SponsorScreen(onBack = { navController.popBackStack() })
            }

            composable("processing") {
                ProcessingScreen(onBack = { navController.popBackStack() })
            }
            composable("advanced") {
                AdvancedScreen(onBack = { navController.popBackStack() })
            }
            composable("backup") {
                BackupScreen(onBack = { navController.popBackStack() })
            }
            composable("storage_locations") {
                StorageLocationsScreen(onBack = { navController.popBackStack() })
            }
            composable("appearance") {
                AppearanceScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    accentName = accentName,
                    onAccentChanged = onAccentChanged,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("tools") {
                ToolsScreen(onBack = { navController.popBackStack() })
            }
            composable("converter") {
                ConverterScreen(onBack = { navController.popBackStack() })
            }
            composable("software_update") {
                SoftwareUpdateScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToHazelUpdate = { navController.navigate("hazel_update") },
                    onNavigateToYtDlpUpdate = { navController.navigate("ytdlp_update") }
                )
            }
            composable("hazel_update") {
                HazelUpdateScreen(onBack = { navController.popBackStack() })
            }
            composable("ytdlp_update") {
                YtDlpUpdateScreen(onBack = { navController.popBackStack() })
            }
            composable("update") {
                SoftwareUpdateScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToHazelUpdate = { navController.navigate("hazel_update") },
                    onNavigateToYtDlpUpdate = { navController.navigate("ytdlp_update") }
                )
            }
        }
    }
}
