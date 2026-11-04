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
import coil3.request.allowHardware
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
        try {
            val request = ImageRequest.Builder(context)
                .data(model)
                .size(96)
                .allowHardware(false)
                .build()
            val result = SingletonImageLoader.get(context).execute(request)
            val image = (result as? SuccessResult)?.image
            val bitmap = image?.toBitmap()
            if (bitmap != null) {
                val palette = Palette.from(bitmap).generate()
                val candidates = listOf(
                    palette.getDarkVibrantColor(0),
                    palette.getVibrantColor(0),
                    palette.getMutedColor(0),
                    palette.getDominantColor(0),
                )
                val argb = candidates.firstOrNull { it != 0 }
                if (argb != null) {
                    extracted = normalizeArtworkColor(argb)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "palette extraction failed", t)
        }
    }

    return extracted ?: fallback
}

private const val TAG = "ArtworkColor"

/** A full-screen vertical gradient: saturated artwork colour fading into the app background. */
fun artworkGradient(color: Color): Brush = Brush.verticalGradient(
    0f to lerp(color, Black, 0.06f),
    0.18f to color,
    0.42f to lerp(color, Background, 0.58f),
    0.62f to lerp(color, Background, 0.84f),
    0.80f to Background,
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
    val index = ((hash % browseTilePalette.size) + browseTilePalette.size) % browseTilePalette.size
    return Color(browseTilePalette[index])
}

// Spotify's Browse tiles use a small curated set of vivid colours rather than a
// full hue wheel; keeping that set makes the grid read correctly.
private val browseTilePalette = intArrayOf(
    0xFF8400E7.toInt(),
    0xFF1E3264.toInt(),
    0xFF148A08.toInt(),
    0xFFE8115B.toInt(),
    0xFFFF6437.toInt(),
    0xFF0D73EC.toInt(),
    0xFFE13300.toInt(),
    0xFF8D67AB.toInt(),
    0xFF477D95.toInt(),
    0xFFF037A5.toInt(),
    0xFFB02897.toInt(),
    0xFFA56752.toInt(),
)

private fun normalizeArtworkColor(argb: Int): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    hsv[1] = hsv[1].coerceIn(0.35f, 0.90f)
    hsv[2] = hsv[2].coerceIn(0.25f, 0.55f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
