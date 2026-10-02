package com.frezzamusic.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FrezzaDarkColors = darkColorScheme(
    primary = Color(0xFF75F3FA),
    secondary = Color(0xFF75F3FA),
    background = Color(0xFF0D1114),
    surface = Color(0xFF171B1E),
    onBackground = Color(0xFFF2F5F6),
    onSurface = Color(0xFFF2F5F6)
)

@Composable
fun FrezzaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FrezzaDarkColors,
        typography = Typography(),
        content = content
    )
}
