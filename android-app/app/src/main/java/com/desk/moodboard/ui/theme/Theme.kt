package com.desk.moodboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
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

private val EInkColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    secondary = Color.Black,
    onSecondary = Color.White,
    tertiary = Color.Black,
    background = Color.White,
    surface = Color.White,
    onSurface = Color.Black,
    onBackground = Color.Black,
    surfaceVariant = Color.White,
    outline = Color.Black,
)

@Composable
fun MoodboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    eInkMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        eInkMode -> EInkColors
        darkTheme -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    val density = LocalDensity.current
    val adjustedDensity = if (eInkMode) {
        Density(density.density, density.fontScale * 1.2f)
    } else {
        density
    }
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
        }
    }

    CompositionLocalProvider(
        LocalEInkMode provides eInkMode,
        LocalDensity provides adjustedDensity,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}
