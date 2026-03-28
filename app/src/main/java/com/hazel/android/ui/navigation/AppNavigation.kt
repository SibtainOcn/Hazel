package com.hazel.android.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hazel.android.R
import com.hazel.android.ui.components.AccentPickerDialog
import com.hazel.android.ui.motion.M3Motion
import com.hazel.android.ui.theme.AccentColors
import com.hazel.android.ui.screens.about.AboutScreen
import com.hazel.android.ui.screens.converter.ConverterScreen
import com.hazel.android.ui.screens.download.DownloadScreen
import com.hazel.android.ui.screens.download.MultiLinksReviewScreen
import com.hazel.android.ui.screens.download.BulkEditorScreen
import com.hazel.android.ui.screens.guide.GuideScreen
import com.hazel.android.ui.screens.history.HistoryScreen
import com.hazel.android.ui.screens.player.PlayerScreen
import com.hazel.android.ui.screens.settings.SettingsScreen
import com.hazel.android.ui.screens.settings.StorageLocationsScreen

sealed class Screen(
    val route: String,
    val title: String,
    @DrawableRes val icon: Int
) {
    data object Download : Screen("download", "Download", R.drawable.download)
    data object Converter : Screen("converter", "Converter", R.drawable.music)
    data object Player : Screen("player", "Listen", R.drawable.player_ic)
    data object History : Screen("history", "History", R.drawable.history)
    data object Settings : Screen("settings", "Settings", R.drawable.settings)
}

private val bottomNavItems = listOf(
    Screen.Download,
    Screen.Converter,
    Screen.Player,
    Screen.History,
    Screen.Settings,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    sharedUrl: String?,
    onSharedUrlConsumed: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    accentName: String,
    onAccentChanged: (String) -> Unit
) {
    val navController = rememberNavController()

    val rotation by animateFloatAsState(
        targetValue = if (isDarkTheme) 360f else 0f,
        animationSpec = tween(400),
        label = "themeToggleRotation"
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isSubScreen = currentRoute in listOf("about", "logs", "multi_links_review", "bulk_editor", "guide", "storage_locations")

    val isPlayerTab = currentRoute == "player"

    var showAccentPicker by remember { mutableStateOf(false) }
    val currentAccent = AccentColors.find { it.name == accentName } ?: AccentColors.first()

    Scaffold(
        topBar = {
            if (!isSubScreen && !isPlayerTab) {
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
                                text = "Hazel",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        // Accent chip
                        Surface(
                            modifier = Modifier
                                .height(32.dp)
                                .clickable { showAccentPicker = true },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(currentAccent.dark)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Accent",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        // Theme toggle
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                contentDescription = if (isDarkTheme) "Switch to light" else "Switch to dark",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.rotate(rotation)
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
                NavigationBar(
                    containerColor = if (isDarkTheme) androidx.compose.ui.graphics.Color(0xFF000000)
                                     else MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val currentDestination = navBackStackEntry?.destination

                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(id = screen.icon),
                                    contentDescription = screen.title,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = { Text(screen.title, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = if (isDarkTheme) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)
                                                      else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                unselectedTextColor = if (isDarkTheme) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)
                                                      else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
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
                val downloadViewModel: com.hazel.android.download.DownloadViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel()
                DownloadScreen(
                    sharedUrl = sharedUrl,
                    onSharedUrlConsumed = onSharedUrlConsumed,
                    onNavigateToLogs = { navController.navigate("logs") },
                    onNavigateToMultiLinksReview = { navController.navigate("multi_links_review") },
                    onNavigateToBulkEditor = { navController.navigate("bulk_editor") },
                    onNavigateToGuide = { navController.navigate("guide") },
                    downloadViewModel = downloadViewModel
                )
            }
            composable(Screen.Converter.route) {
                ConverterScreen()
            }
            composable(Screen.Player.route) {
                PlayerScreen()
            }
            composable(Screen.History.route) {
                HistoryScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    accentName = accentName,
                    onAccentChanged = onAccentChanged,
                    onNavigateToAbout = { navController.navigate("about") },
                    onNavigateToGuide = { navController.navigate("guide") },
                    onNavigateToStorageLocations = { navController.navigate("storage_locations") }
                )
            }
            composable("storage_locations") {
                StorageLocationsScreen(onBack = { navController.popBackStack() })
            }
            composable("guide") {
                GuideScreen(onBack = { navController.popBackStack() })
            }
            composable("about") {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable("logs") {
                com.hazel.android.ui.screens.logs.LogViewerScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("multi_links_review") {
                val downloadEntry = remember(navController) {
                    navController.getBackStackEntry(Screen.Download.route)
                }
                val downloadViewModel: com.hazel.android.download.DownloadViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel(downloadEntry)
                MultiLinksReviewScreen(
                    urls = downloadViewModel.multiLinkUrls,
                    onBack = { navController.popBackStack() },
                    onConfirm = { navController.popBackStack() }
                )
            }
            composable("bulk_editor") {
                val downloadEntry = remember(navController) {
                    navController.getBackStackEntry(Screen.Download.route)
                }
                val downloadViewModel: com.hazel.android.download.DownloadViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel(downloadEntry)
                BulkEditorScreen(
                    onBack = { navController.popBackStack() },
                    onStartDownload = { urls ->
                        downloadViewModel.startDownload(
                            navController.context,
                            urls, downloadViewModel.isVideoSelection, "Bulk"
                        )
                        navController.popBackStack()
                    }
                )
            }
        }
    }

    // Accent picker dialog overlay
    if (showAccentPicker) {
        AccentPickerDialog(
            accentName = accentName,
            onAccentChanged = onAccentChanged,
            onDismiss = { showAccentPicker = false }
        )
    }
}
