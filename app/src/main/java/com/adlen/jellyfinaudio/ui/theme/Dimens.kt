package com.adlen.jellyfinaudio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Encore spacing scale (2/4/6/8/12/16/20/24/32/40/48/64 dp). */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 6.dp
    val m = 8.dp
    val l = 12.dp
    val xl = 16.dp
    val xxl = 20.dp
    val xxxl = 24.dp
    val huge = 32.dp
    val giant = 40.dp
    val massive = 48.dp
}

/** Encore corner-radius scale. */
object SpotifyShapes {
    val hairline = RoundedCornerShape(2.dp)
    val small = RoundedCornerShape(4.dp)
    val medium = RoundedCornerShape(6.dp)
    val card = RoundedCornerShape(8.dp)
    val large = RoundedCornerShape(16.dp)
    val pill = RoundedCornerShape(999.dp)
}

/** Fixed sizes shared by the shell and the players. */
object Dimens {
    val miniPlayerHeight = 56.dp
    val navBarHeight = 56.dp
    val playlistCoverSize = 168.dp
    val cardWidth = 156.dp
    val carouselCardWidth = 160.dp
    val trackRowHeight = 56.dp
}
