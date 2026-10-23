package com.example.houseelf.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Палитра рассчитана на светлый фон, поэтому тема одна: без динамических цветов Android 12+ и без тёмного режима.
private val DuoColorScheme = lightColorScheme(
    primary = Duo.Teal,
    onPrimary = Color.White,
    primaryContainer = Duo.TealSoft,
    onPrimaryContainer = Duo.TealDark,
    secondary = Duo.Amber,
    onSecondary = Duo.Ink,
    background = Duo.Bg,
    onBackground = Duo.Ink,
    surface = Color.White,
    onSurface = Duo.Ink,
    surfaceVariant = Duo.Track,
    onSurfaceVariant = Duo.Muted,
    outline = Duo.Line,
    error = Duo.Red,
    onError = Color.White,
)

@Composable
fun HouseelfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DuoColorScheme,
        typography = Typography,
        content = content,
    )
}
