package com.desk.moodboard

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
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
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.desk.moodboard.data.preferences.UserPreferences
import com.desk.moodboard.ui.assistant.AssistantScreen
import com.desk.moodboard.ui.assistant.AssistantViewModel
import com.desk.moodboard.ui.focus.AwayModeViewModel
import com.desk.moodboard.ui.focus.FocusScreen
import com.desk.moodboard.ui.health.HealthScreen
import com.desk.moodboard.ui.home.HomeScreen
import com.desk.moodboard.ui.settings.SettingsScreen
import com.desk.moodboard.ui.settings.SettingsViewModel
import com.desk.moodboard.ui.theme.AccentOrange
import com.desk.moodboard.ui.theme.Dimens
import com.desk.moodboard.ui.theme.MoodboardTheme
import com.desk.moodboard.ui.theme.appBackgroundColor
import com.desk.moodboard.ui.theme.appSurfaceColor
import com.desk.moodboard.ui.theme.eInkTextColorOr
import com.desk.moodboard.ui.theme.secondaryTextColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val startupLanguage = runBlocking { UserPreferences(applicationContext).appLanguage.first() }
        val startupLocales = startupLanguage.toLocaleListCompat()
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != startupLocales.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(startupLocales)
        }

        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

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

        // Delay immersive mode until first frame to avoid transient black during recreation.
        window.decorView.post {
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

private data class NavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
)

@Composable
private fun MoodboardApp() {
    val navController = rememberNavController()
    val awayModeViewModel: AwayModeViewModel = koinViewModel()
    val awayUiState by awayModeViewModel.uiState.collectAsStateWithLifecycle()
    val navItems = listOf(
        NavItem("home", R.string.nav_home, null),
        NavItem("health", R.string.nav_health, null),
        NavItem("focus", R.string.nav_focus, null),
        NavItem("settings", R.string.nav_settings, null),
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            if (!awayUiState.isAway) {
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
                                    text = stringResource(item.labelRes),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                    color = if (selected) eInkTextColorOr(AccentOrange) else secondaryTextColor()
                                )
                            }
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
                composable("focus") { FocusScreen(awayModeViewModel = awayModeViewModel) }
                composable("settings") {
                    SettingsScreen(
                        onApplyLanguage = { selectedLanguage ->
                            val targetLocales = selectedLanguage.toLocaleListCompat()
                            if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != targetLocales.toLanguageTags()) {
                                AppCompatDelegate.setApplicationLocales(targetLocales)
                            }
                        }
                    )
                }
            }
        }
    }
}
