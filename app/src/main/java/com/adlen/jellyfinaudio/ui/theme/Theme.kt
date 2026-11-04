package com.adlen.jellyfinaudio.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

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
    ) {
        // Material3 only sets LocalContentColor inside a Surface. The shell paints
        // its own gradients, so provide the dark-theme default explicitly.
        CompositionLocalProvider(LocalContentColor provides TextPrimary) {
            content()
        }
    }
}
