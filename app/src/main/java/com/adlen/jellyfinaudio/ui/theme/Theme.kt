package com.adlen.jellyfinaudio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Black,
    primaryContainer = AccentDark,
    onPrimaryContainer = Black,
    secondary = Accent,
    onSecondary = Black,
    background = Background,
    onBackground = TextPrimary,
    surface = BackgroundElevated,
    onSurface = TextPrimary,
    surfaceVariant = Card,
    onSurfaceVariant = TextSubdued,
    error = Negative,
    onError = TextPrimary,
)

@Composable
fun JellyfinAudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content,
    )
}
