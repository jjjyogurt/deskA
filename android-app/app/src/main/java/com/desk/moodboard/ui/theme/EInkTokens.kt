package com.desk.moodboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalEInkMode = staticCompositionLocalOf { false }

@Composable
fun primaryTextColor(): Color = MaterialTheme.colorScheme.onSurface

@Composable
fun secondaryTextColor(): Color {
    val onSurface = MaterialTheme.colorScheme.onSurface
    return if (LocalEInkMode.current) onSurface else onSurface.copy(alpha = 0.6f)
}

@Composable
fun eInkTextColorOr(color: Color): Color {
    return if (LocalEInkMode.current) MaterialTheme.colorScheme.onSurface else color
}

@Composable
fun appBackgroundColor(): Color = MaterialTheme.colorScheme.background

@Composable
fun appSurfaceColor(): Color = MaterialTheme.colorScheme.surface
