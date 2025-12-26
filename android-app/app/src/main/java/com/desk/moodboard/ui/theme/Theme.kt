package com.desk.moodboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
