package com.adlen.jellyfinaudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.adlen.jellyfinaudio.data.ServerConfig
import com.adlen.jellyfinaudio.data.subsonic.SubsonicUrls
import com.adlen.jellyfinaudio.ui.theme.Card
import com.adlen.jellyfinaudio.ui.theme.TextSubdued

@Composable
fun AlbumArt(
    config: ServerConfig?,
    coverArtId: String?,
    modifier: Modifier = Modifier,
    size: Int = 300,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    val model = remember(config, coverArtId, size) {
        config?.let { SubsonicUrls.coverArt(it, coverArtId, size) }
    }

    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(Card),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = TextSubdued,
            )
        }
    }
}
