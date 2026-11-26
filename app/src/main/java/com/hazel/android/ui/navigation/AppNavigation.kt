package com.hazel.android.ui.navigation

import com.hazel.android.ui.theme.GlassStyle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import com.hazel.android.ui.components.pressScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.hazel.android.ui.components.HazelBottomBarHeight
import com.hazel.android.ui.components.player.PlayerOverlay
import com.hazel.android.ui.components.player.PlayerSession
import com.hazel.android.ui.components.player.rememberPlaybackController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NamedNavArgument
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hazel.android.R
import com.hazel.android.download.BatchState
import com.hazel.android.data.DownloadHistoryRepository
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
import com.hazel.android.update.GalleryDlUpdateScreen
import com.hazel.android.update.YtDlpUpdateScreen
import com.hazel.android.update.UpdateScreen

sealed class Screen(
    val route: String,
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val icon: Int
) {
    data object Download : Screen("download", R.string.nav_home, R.drawable.home)
    data object History : Screen("history", R.string.nav_history, R.drawable.downloads_tab)
    data object Gallery : Screen("gallery", R.string.nav_gallery, R.drawable.gallery_tab)
    data object Queue : Screen("queue", R.string.nav_queue, R.drawable.queue_tab)
    data object More : Screen("more", R.string.nav_more, R.drawable.more_tab)
}

private val bottomNavItems = listOf(
    Screen.Download,
    Screen.Gallery,
    Screen.History,
    Screen.Queue,
    Screen.More,
)

private val tabRoutes = bottomNavItems.map { it.route }.toSet()

/** Screens shown without the bottom bar. */
private val subScreenRoutes = setOf(
    "storage_locations", "appearance", "tools", "converter", "update", "cookies", "fetch_settings", "storage_cleanup", "sponsor", "software_update", "hazel_update", "ytdlp_update", "gallery_dl_update",
    "processing", "advanced", "proxy", "gallery_settings"
)

/**
 * A destination that keeps the room for the bottom bar itself. The room moves with the
 * screen, so a screen leaving or arriving never changes size while the bar comes or goes.
 */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit
) = composable(route, arguments) { entry ->
    val barRoom = if (route in subScreenRoutes) 0.dp else HazelBottomBarHeight
    Box(modifier = Modifier.fillMaxSize().padding(bottom = barRoom)) { content(entry) }
}

/** True when both ends of the move are bottom bar tabs, which are peers rather than a step deeper. */
private fun androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    pendingShares: List<com.hazel.android.MainActivity.SharedLink> = emptyList(),
    pendingFailure: com.hazel.android.download.FailureNotice? = null,
    pendingRoute: String? = null,
    onPendingRouteConsumed: () -> Unit = {},
    onPendingFailureConsumed: () -> Unit = {},
    onSharesConsumed: () -> Unit = {},
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    accentName: String,
    onAccentChanged: (String) -> Unit,
    glassEnabled: Boolean,
    onToggleGlass: () -> Unit,
    glassStyle: GlassStyle,
    onGlassStyleChanged: (GlassStyle) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val incognito by remember(context) { SettingsRepository.getIncognito(context) }.collectAsState(initial = false)
    val hazelUpdateAvailable by remember(context) { SettingsRepository.getHazelUpdateAvailable(context) }.collectAsState(initial = false)

    val navController = rememberNavController()

    // The queue tab a notification asked for, after the '#' of its route.
    var queueTab by remember { mutableStateOf<String?>(null) }

    // Handle deep navigation triggered from shortcuts, the share overlay and notifications

    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { target ->
            val route = target.substringBefore('#')
            target.substringAfter('#', "").takeIf { it.isNotEmpty() }?.let { queueTab = it }
            // A tab is opened the way the bottom bar opens it. Pushed as a plain screen, it
            // sat on top of Home, and Home's own button then restored that same stack with
            // the tab still on top of it, so pressing Home seemed to do nothing.
            if (bottomNavItems.any { it.route == route }) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            } else {
                navController.navigate(route)
            }
            onPendingRouteConsumed()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isSubScreen = currentRoute in subScreenRoutes
    val historyUnseen by remember(context) { DownloadHistoryRepository.hasUnseen(context) }.collectAsState(initial = false)
    LaunchedEffect(currentRoute, historyUnseen) {
        if (historyUnseen && currentRoute == Screen.History.route) DownloadHistoryRepository.markSeen(context)
    }

    val downloadViewModel: com.hazel.android.download.DownloadViewModel =
        remember { com.hazel.android.download.DownloadViewModelHolder.get() }

    // Held above the screens, so the video plays on in the mini player on every screen.
    val playerSession = remember { PlayerSession() }
    val playback = playerSession.playingUrl?.let { rememberPlaybackController(it) }

    // A download that could not reach its folder (an SD card taken out, full or no longer
    // granting access) is reported wherever the user is in the app, including after it ran
    // in the background or was started from the share sheet. Only this field is watched,
    // so download progress does not recompose the whole app.
    val saveFallback by remember(downloadViewModel) {
        downloadViewModel.state
            .map { it.saveFallback }
            .distinctUntilChanged()
    }.collectAsState(initial = null)
    saveFallback?.let { fallback ->
        com.hazel.android.ui.screens.download.SaveFallbackDialog(
            wanted = fallback.wanted,
            savedTo = fallback.savedTo,
            titles = fallback.titles,
            onDismiss = downloadViewModel::clearSaveFallback
        )
    }

    com.hazel.android.ui.screens.more.BackupRestoreOffer()

    // The home header is part of the home screen and moves with it. The other screens carry
    // their own headings. The incognito switch goes with it: what it changes is what a
    // download records, which is decided here and nowhere else.
    val openTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val homeTopBar: @Composable () -> Unit = {
        TopAppBar(
            title = {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // One tonal pill holding the bolt and the name; a tap opens Support.
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button) {
                                navController.navigate("sponsor") { launchSingleTop = true }
                            }
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
                            .padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.update),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = com.hazel.android.ui.theme.accentInk()
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        com.hazel.android.ui.screens.gallery.GalleryReadingPill(onOpen = { openTab(Screen.Gallery.route) })
                    }
                }
            },
            actions = {
                // For GitHub release builds only (not yt-dlp): when Hazel app update is available,
                // display a theme-adaptive, accent-independent "Update" pill next to incognito icon
                if (!HazelUpdater.isFdroid() && hazelUpdateAvailable) {
                    // The app's own theme, which can differ from the system's.
                    val isDark = isDarkTheme
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
                            contentDescription = stringResource(R.string.home_update_available),
                            tint = pillFg,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.home_update),
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
                // Always on the same soft tone as the logo pill, so the header is
                // balanced; filled with the accent while it is on.
                val incognitoPress = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val incognitoFill by androidx.compose.animation.animateColorAsState(
                    if (incognito) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                    label = "incognito"
                )
                Surface(
                    onClick = {
                        scope.launch {
                            SettingsRepository.setIncognito(context, !incognito)
                        }
                    },
                    interactionSource = incognitoPress,
                    shape = CircleShape,
                    color = incognitoFill,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .pressScale(incognitoPress, pressed = 0.9f)
                        .size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.incognito),
                            contentDescription = if (incognito) {
                                stringResource(R.string.nav_incognito_on)
                            } else {
                                stringResource(R.string.nav_incognito_off)
                            },
                            modifier = Modifier.size(24.dp),
                            tint = if (incognito) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.primary
            )
        )
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = !isSubScreen,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                val downloadState by downloadViewModel.state.collectAsState()
                val savedQueue by remember(context) { DownloadQueueRepository.getQueue(context) }
                    .collectAsState(initial = emptyList())
                // Lit while anything is downloading, paused part way or waiting its turn,
                // so the queue is found without having to go and look.
                val galleryJobs by com.hazel.android.gallery.queue.GalleryQueue.jobs.collectAsState()
                val queueBusy = downloadState.isDownloading || savedQueue.isNotEmpty() || galleryJobs.isNotEmpty() ||
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
                            showDot = currentDestination?.route != screen.route && when (screen) {
                                Screen.Queue -> queueBusy
                                Screen.History -> historyUnseen
                                else -> false
                            }
                        )
                    },
                    selectedRoute = bottomNavItems.firstOrNull { screen ->
                        currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    }?.route,
                    containerColor = when {
                        glassEnabled && isDarkTheme -> Color.Black.copy(alpha = 0.3f)
                        glassEnabled -> Color.White.copy(alpha = 0.45f)
                        isDarkTheme -> Color(0xFF000000)
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    },
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
        val layoutDirection = LocalLayoutDirection.current
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        NavHost(
            navController = navController,
            startDestination = Screen.Download.route,
            modifier = Modifier.padding(
                start = innerPadding.calculateStartPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = navBarBottom
            ),
            enterTransition = { if (isTabSwitch()) M3Motion.tabEnter() else M3Motion.forwardEnter() },
            exitTransition = { if (isTabSwitch()) M3Motion.tabExit() else M3Motion.forwardExit() },
            popEnterTransition = { if (isTabSwitch()) M3Motion.tabEnter() else M3Motion.backEnter() },
            popExitTransition = { if (isTabSwitch()) M3Motion.tabExit() else M3Motion.backExit() }
        ) {
            screen(Screen.Download.route) {
                Column(modifier = Modifier.fillMaxSize()) {
                    homeTopBar()
                    Box(modifier = Modifier.weight(1f)) {
                        DownloadScreen(
                            pendingShares = pendingShares,
                            pendingFailure = pendingFailure,
                            onPendingFailureConsumed = onPendingFailureConsumed,
                            onSharesConsumed = onSharesConsumed,
                            downloadViewModel = downloadViewModel,
                            playerSession = playerSession,
                            playback = playback,
                            onOpenGallery = { openTab(Screen.Gallery.route) },
                            onOpenQueue = {
                                navController.navigate(Screen.Queue.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
            screen(Screen.History.route) {
                HistoryScreen(
                    onOpenRunning = {
                        queueTab = "running"
                        openTab(Screen.Queue.route)
                    },
                    onDownloadAgain = { entry ->
                        downloadViewModel.instantDownload(context, entry.url, audioOnlyOverride = !entry.isVideo)
                    },
                    onFetchAgain = { entry ->
                        // Read the way a shared link is, then shown on Home, where its sheet opens.
                        downloadViewModel.onUrlChange(entry.url)
                        downloadViewModel.fetchAll(listOf(entry.url))
                        navController.navigate(Screen.Download.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            screen(Screen.Gallery.route) {
                Column(modifier = Modifier.fillMaxSize()) {
                    homeTopBar()
                    Box(modifier = Modifier.weight(1f)) {
                        com.hazel.android.ui.screens.gallery.GalleryScreen(
                            onVideoLinks = { links ->
                                downloadViewModel.fetchAll(links)
                                openTab(Screen.Download.route)
                            },
                            onVideoSearch = { query, source ->
                                downloadViewModel.search(query, source)
                                openTab(Screen.Download.route)
                            }
                        )
                    }
                }
            }
            screen(Screen.Queue.route) {
                QueueScreen(
                    downloadViewModel = downloadViewModel,
                    openTab = queueTab,
                    onTabOpened = { queueTab = null }
                )
            }
            screen(Screen.More.route) {
                MoreScreen(
                    onNavigateToAppearance = { navController.navigate("appearance") },
                    onNavigateToConverter = { navController.navigate("converter") },
                    onNavigateToStorageLocations = { navController.navigate("storage_locations") },
                    onNavigateToGallerySettings = { navController.navigate("gallery_settings") },
                    onNavigateToProcessing = { navController.navigate("processing") },
                    onNavigateToAdvanced = { navController.navigate("advanced") },
                    onNavigateToBackup = { navController.navigate("backup") },
                    onNavigateToCookies = { navController.navigate("cookies") },
                    onNavigateToFetchSettings = { navController.navigate("fetch_settings") },
                    onNavigateToSponsor = { navController.navigate("sponsor") },
                    onOpenBatterySettings = { openBatterySettings(context) },
                    onNavigateToStorageCleanup = { navController.navigate("storage_cleanup") },
                    onNavigateToUpdate = { navController.navigate("software_update") },
                    onNavigateToDocumentation = { navController.navigate("docs") },
                    onNavigateToSettingsSearch = { navController.navigate("settings_search") }
                )
            }
            screen("settings_search") {
                com.hazel.android.ui.screens.more.SettingsSearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { route -> navController.navigate(route) },
                    onOpenBatterySettings = { openBatterySettings(context) }
                )
            }
            screen("docs") {
                com.hazel.android.ui.screens.more.DocumentationScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { page -> navController.navigate("docs/${page.name}") }
                )
            }
            screen(
                "docs/{page}?version={version}",
                arguments = listOf(navArgument("version") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                com.hazel.android.ui.screens.more.DocumentationPageScreen(
                    page = com.hazel.android.ui.screens.more.DocPage.fromName(entry.arguments?.getString("page")),
                    onBack = { navController.popBackStack() },
                    version = entry.arguments?.getString("version")
                )
            }
            screen("cookies") {
                CookiesScreen(onBack = { navController.popBackStack() })
            }
            screen("storage_cleanup") {
                StorageCleanupScreen(onBack = { navController.popBackStack() })
            }
            screen("fetch_settings") {
                FetchSettingsScreen(onBack = { navController.popBackStack() })
            }
            screen("sponsor") {
                SponsorScreen(onBack = { navController.popBackStack() })
            }

            screen("processing") {
                ProcessingScreen(onBack = { navController.popBackStack() })
            }
            screen("advanced") {
                AdvancedScreen(
                    onBack = { navController.popBackStack() },
                    onOpenProxy = { navController.navigate("proxy") }
                )
            }
            screen("proxy") {
                com.hazel.android.ui.screens.more.ProxyScreen(onBack = { navController.popBackStack() })
            }
            screen("backup") {
                BackupScreen(onBack = { navController.popBackStack() })
            }
            screen("gallery_settings") {
                com.hazel.android.ui.screens.more.GallerySettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCookies = { navController.navigate("cookies") },
                    onOpenProxy = { navController.navigate("proxy") }
                )
            }
            screen("storage_locations") {
                StorageLocationsScreen(onBack = { navController.popBackStack() })
            }
            screen("appearance") {
                AppearanceScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    accentName = accentName,
                    onAccentChanged = onAccentChanged,
                    glassEnabled = glassEnabled,
                    onToggleGlass = onToggleGlass,
                    glassStyle = glassStyle,
                    onGlassStyleChanged = onGlassStyleChanged,
                    onBack = { navController.popBackStack() }
                )
            }
            screen("tools") {
                ToolsScreen(onBack = { navController.popBackStack() })
            }
            screen("converter") {
                ConverterScreen(onBack = { navController.popBackStack() })
            }
            screen("software_update") {
                SoftwareUpdateScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToHazelUpdate = { navController.navigate("hazel_update") },
                    onNavigateToYtDlpUpdate = { navController.navigate("ytdlp_update") },
                    onNavigateToGalleryDlUpdate = { navController.navigate("gallery_dl_update") },
                    onOpenWhatsNew = { version ->
                        navController.navigate(
                            "docs/${com.hazel.android.ui.screens.more.DocPage.CHANGELOG.name}?version=${android.net.Uri.encode(version)}"
                        )
                    }
                )
            }
            screen("hazel_update") {
                HazelUpdateScreen(
                    onBack = { navController.popBackStack() },
                    onOpenWhatsNew = { version ->
                        navController.navigate(
                            "docs/${com.hazel.android.ui.screens.more.DocPage.CHANGELOG.name}?version=${android.net.Uri.encode(version)}"
                        )
                    }
                )
            }
            screen("ytdlp_update") {
                YtDlpUpdateScreen(onBack = { navController.popBackStack() })
            }
            screen("gallery_dl_update") {
                GalleryDlUpdateScreen(onBack = { navController.popBackStack() })
            }
            screen("update") {
                SoftwareUpdateScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToHazelUpdate = { navController.navigate("hazel_update") },
                    onNavigateToYtDlpUpdate = { navController.navigate("ytdlp_update") },
                    onNavigateToGalleryDlUpdate = { navController.navigate("gallery_dl_update") },
                    onOpenWhatsNew = { version ->
                        navController.navigate(
                            "docs/${com.hazel.android.ui.screens.more.DocPage.CHANGELOG.name}?version=${android.net.Uri.encode(version)}"
                        )
                    }
                )
            }
        }

        if (playerSession.playingUrl != null) {
            PlayerLayer(
                session = playerSession,
                playback = playback,
                downloadViewModel = downloadViewModel,
                onDownload = {
                    playerSession.sheetRequested = true
                    if (currentRoute != Screen.Download.route) openTab(Screen.Download.route)
                },
                modifier = Modifier.padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = navBarBottom + if (isSubScreen) 0.dp else HazelBottomBarHeight
                )
            )
        }
    }
}

/** The player's views over every screen. Only the results are watched, so download progress does not recompose it. */
@Composable
private fun PlayerLayer(
    session: PlayerSession,
    playback: com.hazel.android.download.playback.PlaybackController?,
    downloadViewModel: com.hazel.android.download.DownloadViewModel,
    onDownload: () -> Unit,
    modifier: Modifier
) {
    val results by remember(downloadViewModel) {
        downloadViewModel.state.map { it.results }.distinctUntilChanged()
    }.collectAsState(initial = downloadViewModel.state.value.results)
    PlayerOverlay(
        session = session,
        playback = playback,
        results = results,
        onDownload = { info ->
            downloadViewModel.selectResult(info)
            downloadViewModel.resolveFormats(info)
            onDownload()
        },
        modifier = modifier
    )
}
