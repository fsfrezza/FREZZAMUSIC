package com.frezzamusic.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import com.frezzamusic.app.BuildConfig

private val FrezzaDarkColors = darkColorScheme(
    primary = Color(0xFF75F3FA),
    secondary = Color(0xFF75F3FA),
    background = Color(0xFF0D1114),
    surface = Color(0xFF171B1E),
    onBackground = Color(0xFFF2F5F6),
    onSurface = Color(0xFFF2F5F6)
)

private val SolasiasDarkColors = darkColorScheme(
    primary = Color(0xFFE2B85C),
    secondary = Color(0xFFF0D99A),
    background = Color(0xFF11100D),
    surface = Color(0xFF1D1A14),
    onBackground = Color(0xFFF7F1E3),
    onSurface = Color(0xFFF7F1E3)
)

private val TheFrezzaDarkColors = darkColorScheme(
    primary = Color(0xFFD783FF),
    secondary = Color(0xFF8FD9FF),
    background = Color(0xFF100D14),
    surface = Color(0xFF1C1722),
    onBackground = Color(0xFFF5EEFA),
    onSurface = Color(0xFFF5EEFA)
)

@Composable
fun FrezzaTheme(content: @Composable () -> Unit) {
    val colors = when (BuildConfig.PROJECT_MODE) {
        "SOLASIAS" -> SolasiasDarkColors
        "THEFREZZA" -> TheFrezzaDarkColors
        else -> FrezzaDarkColors
    }
    val geometricTypography = Typography(
        headlineMedium = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold),
        headlineSmall = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold),
        titleLarge = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold),
        titleMedium = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold),
        bodyLarge = TextStyle(fontFamily=FontFamily.SansSerif),
        bodyMedium = TextStyle(fontFamily=FontFamily.SansSerif),
        bodySmall = TextStyle(fontFamily=FontFamily.SansSerif),
        labelLarge = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold),
        labelMedium = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Medium)
    )
    MaterialTheme(
        colorScheme = colors,
        typography = geometricTypography,
        content = content
    )
}
