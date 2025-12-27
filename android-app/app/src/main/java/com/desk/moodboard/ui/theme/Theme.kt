package com.desk.moodboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import android.app.Activity

private val LightColors = lightColorScheme(
    primary = YogurtNavy,
    onPrimary = Color.White,
    secondary = YogurtBlue,
    onSecondary = Color.White,
    tertiary = YogurtMint,
    background = YogurtCream,
    surface = YogurtSilk,
    onSurface = YogurtNavy,
    onBackground = YogurtNavy,
    surfaceVariant = YogurtSky,
    outline = YogurtSilver,
)

private val DarkColors = darkColorScheme(
    primary = YogurtSilk,
    onPrimary = YogurtNavy,
    secondary = YogurtBlue,
    onSecondary = YogurtNavy,
    tertiary = YogurtMint,
    background = YogurtNavy,
    surface = Color(0xFF2D3135),
    onSurface = YogurtSilk,
    onBackground = YogurtSilk,
    surfaceVariant = YogurtNavy,
    outline = YogurtGrey,
)

@Composable
fun MoodboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
