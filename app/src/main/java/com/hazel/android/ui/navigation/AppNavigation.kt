package com.hazel.android.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hazel.android.R
import com.hazel.android.ui.motion.M3Motion
import com.hazel.android.ui.screens.converter.ConverterScreen
import com.hazel.android.ui.screens.cookies.CookiesScreen
import com.hazel.android.ui.screens.download.DownloadScreen
import com.hazel.android.ui.screens.more.AppearanceScreen
import com.hazel.android.ui.screens.more.FetchSettingsScreen
import com.hazel.android.ui.screens.more.MoreScreen
import com.hazel.android.ui.screens.more.StorageCleanupScreen
import com.hazel.android.ui.screens.more.StorageLocationsScreen
import com.hazel.android.ui.screens.more.ToolsScreen
import com.hazel.android.update.UpdateScreen

sealed class Screen(
    val route: String,
    val title: String,
    @DrawableRes val icon: Int
) {
    data object Download : Screen("download", "Home", R.drawable.download)
    data object More : Screen("more", "More", R.drawable.settings)
}

private val bottomNavItems = listOf(
    Screen.Download,
    Screen.More,
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

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isSubScreen = currentRoute in listOf(
        "storage_locations", "appearance", "tools", "converter", "update", "cookies", "fetch_settings", "storage_cleanup"
    )

    Scaffold(
        topBar = {
            if (!isSubScreen) {
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
                    downloadViewModel = downloadViewModel
                )
            }
            composable(Screen.More.route) {
                MoreScreen(
                    onNavigateToAppearance = { navController.navigate("appearance") },
                    onNavigateToTools = { navController.navigate("tools") },
                    onNavigateToStorageLocations = { navController.navigate("storage_locations") },
                    onNavigateToCookies = { navController.navigate("cookies") },
                    onNavigateToFetchSettings = { navController.navigate("fetch_settings") },
                    onNavigateToStorageCleanup = { navController.navigate("storage_cleanup") },
                    onNavigateToUpdate = { navController.navigate("update") }
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
                ToolsScreen(
                    onNavigateToConverter = { navController.navigate("converter") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("converter") {
                ConverterScreen(onBack = { navController.popBackStack() })
            }
            composable("update") {
                UpdateScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
