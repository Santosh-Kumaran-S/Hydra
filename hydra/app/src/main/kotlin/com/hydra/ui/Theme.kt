package com.hydra.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1565C0), onPrimary = Color.White, secondary = Color(0xFF00838F),
    background = Color(0xFFF4F8FF), surface = Color.White, onSurface = Color(0xFF0B1B33),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF90CAF9), onPrimary = Color(0xFF0B1B33), secondary = Color(0xFF4DD0E1),
    background = Color(0xFF0B1B33), surface = Color(0xFF12284A), onSurface = Color(0xFFE8F1FF),
)

@Composable fun HydraTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
