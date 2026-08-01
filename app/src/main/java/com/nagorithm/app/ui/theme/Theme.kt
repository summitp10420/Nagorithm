package com.nagorithm.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF00658F),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF4F616E),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF83CFFF),
    onPrimary = Color(0xFF00344C),
    secondary = Color(0xFFB6C9D8),
    background = Color(0xFF0B1F2A),
    surface = Color(0xFF12252F),
)

@Composable
fun NagorithmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
