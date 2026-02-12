package com.desk.moodboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.desk.moodboard.ui.assistant.AssistantScreen
import com.desk.moodboard.ui.assistant.AssistantViewModel
import com.desk.moodboard.ui.focus.FocusScreen
import com.desk.moodboard.ui.health.HealthScreen
import com.desk.moodboard.ui.home.HomeScreen
import com.desk.moodboard.ui.settings.SettingsScreen
import com.desk.moodboard.ui.theme.AccentOrange
import com.desk.moodboard.ui.theme.Dimens
import com.desk.moodboard.ui.theme.MoodboardTheme
import com.desk.moodboard.ui.theme.appBackgroundColor
import com.desk.moodboard.ui.theme.appSurfaceColor
import com.desk.moodboard.ui.theme.eInkTextColorOr
import com.desk.moodboard.ui.theme.secondaryTextColor
import com.desk.moodboard.ui.settings.SettingsViewModel
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Hide system bars (status bar and navigation bar) for full screen
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            val settingsViewModel: SettingsViewModel = koinViewModel()
            val eInkEnabled by settingsViewModel.eInkEnabled.collectAsStateWithLifecycle()

            MoodboardTheme(
                darkTheme = false,
                eInkMode = eInkEnabled,
            ) {
                MoodboardApp()
            }
        }
    }
}

private data class NavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
)

@Composable
private fun MoodboardApp() {
    val navController = rememberNavController()
    val navItems = listOf(
        NavItem("home", "Home", null),
        NavItem("health", "Health", null),
        NavItem("focus", "Focus", null),
        NavItem("settings", "Settings", null),
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            Surface(
                color = appSurfaceColor(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.navHeight)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (!selected) {
                                        navController.navigate(item.route) {
                                            launchSingleTop = true
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            restoreState = true
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (selected) eInkTextColorOr(AccentOrange) else secondaryTextColor()
                            )
                        }
                    }
                }
            }
        },
        containerColor = appBackgroundColor(),
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = navItems.first().route,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { androidx.compose.animation.EnterTransition.None },
                exitTransition = { androidx.compose.animation.ExitTransition.None },
                popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                popExitTransition = { androidx.compose.animation.ExitTransition.None },
            ) {
                composable("home") { HomeScreen() }
                composable("health") { HealthScreen() }
                composable("focus") { FocusScreen() }
                composable("settings") { SettingsScreen() }
            }
        }
    }
}
