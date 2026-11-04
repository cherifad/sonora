package com.adlen.jellyfinaudio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.adlen.jellyfinaudio.ui.theme.Background
import com.adlen.jellyfinaudio.ui.theme.Black

/**
 * Extracts a stable, darkened colour from the artwork so entity headers can use
 * the artwork-derived gradients Spotify uses. Falls back to a hue derived from
 * the artwork id, which keeps the gradient stable before the image loads.
 */
@Composable
fun rememberArtworkColor(model: Any?, seed: String? = null): Color {
    val context = LocalContext.current
    val fallback = remember(seed, model) {
        seededArtworkColor(seed ?: model?.toString())
    }
    var extracted by remember(model) { mutableStateOf<Color?>(null) }

    LaunchedEffect(model) {
        if (model == null) {
            extracted = null
            return@LaunchedEffect
        }
        extracted = runCatching {
            val request = ImageRequest.Builder(context)
                .data(model)
                .size(96)
                .build()
            val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
            val bitmap = result?.image?.toBitmap() ?: return@runCatching null
            val palette = Palette.from(bitmap).generate()
            val argb = listOf(
                palette.getDarkVibrantColor(0),
                palette.getVibrantColor(0),
                palette.getMutedColor(0),
                palette.getDominantColor(0),
            ).firstOrNull { it != 0 } ?: return@runCatching null
            normalizeArtworkColor(argb)
        }.getOrNull()
    }

    return extracted ?: fallback
}

/** A full-screen vertical gradient: saturated artwork colour fading into the app background. */
fun artworkGradient(color: Color): Brush = Brush.verticalGradient(
    0f to lerp(color, Black, 0.08f),
    0.30f to color,
    0.62f to lerp(color, Background, 0.62f),
    0.85f to lerp(color, Background, 0.85f),
    1f to Background,
)

/** Hue-stable placeholder colour for an artwork id/name. */
fun seededArtworkColor(seed: String?): Color {
    val hash = (seed ?: "jellyfin").hashCode()
    val hue = ((hash % 360) + 360) % 360
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.45f, 0.40f)))
}

/** Saturated, stable colour for a "Browse all" category tile. */
fun browseColorFor(seed: String?): Color {
    val hash = (seed ?: "jellyfin").hashCode()
    val hue = ((hash % 360) + 360) % 360
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.60f, 0.62f)))
}

private fun normalizeArtworkColor(argb: Int): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    hsv[1] = hsv[1].coerceIn(0.35f, 0.90f)
    hsv[2] = hsv[2].coerceIn(0.25f, 0.55f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
