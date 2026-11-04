package com.adlen.jellyfinaudio.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adlen.jellyfinaudio.data.ServerConfig
import com.adlen.jellyfinaudio.ui.theme.Accent
import com.adlen.jellyfinaudio.ui.theme.BackgroundElevated
import com.adlen.jellyfinaudio.ui.theme.Black
import com.adlen.jellyfinaudio.ui.theme.Dimens
import com.adlen.jellyfinaudio.ui.theme.SpotifyShapes
import com.adlen.jellyfinaudio.ui.theme.Spacing
import com.adlen.jellyfinaudio.ui.theme.TextPrimary
import com.adlen.jellyfinaudio.ui.theme.TextSubdued
import com.adlen.jellyfinaudio.ui.theme.White

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onShowAll: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onShowAll != null) {
            Text(
                text = "Show all",
                style = MaterialTheme.typography.labelMedium,
                color = TextSubdued,
                modifier = Modifier
                    .clip(SpotifyShapes.pill)
                    .clickable(onClick = onShowAll)
                    .padding(horizontal = Spacing.m, vertical = Spacing.xs),
            )
        }
    }
}

@Composable
fun FilterChipRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        labels.forEachIndexed { index, label ->
            SpotifyFilterChip(
                label = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
fun SpotifyFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(SpotifyShapes.pill)
            .background(if (selected) White else White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.m),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Black else TextPrimary,
        )
    }
}

@Composable
fun MediaCard(
    config: ServerConfig?,
    coverArtId: String?,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = Dimens.carouselCardWidth,
    shape: Shape = SpotifyShapes.card,
    circular: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "cardScale")

    Column(
        modifier = modifier
            .width(width)
            .scale(scale)
            .clip(SpotifyShapes.card)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        AlbumArt(
            config = config,
            coverArtId = coverArtId,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            size = 400,
            shape = if (circular) CircleShape else shape,
        )
        Spacer(Modifier.height(Spacing.m))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSubdued,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The compact two-column tile Spotify puts at the top of Home. */
@Composable
fun QuickPickTile(
    config: ServerConfig?,
    coverArtId: String?,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.miniPlayerHeight)
            .clip(SpotifyShapes.small)
            .background(BackgroundElevated)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(
            config = config,
            coverArtId = coverArtId,
            modifier = Modifier.size(Dimens.miniPlayerHeight),
            size = 200,
            shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.l),
        )
    }
}

@Composable
fun BrowseTile(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(96.dp)
            .clip(SpotifyShapes.card)
            .background(color)
            .clickable(onClick = onClick)
            .padding(Spacing.l),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.72f),
        )
    }
}

@Composable
fun PlayButton(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (playing) "Pause" else "Play",
            tint = Black,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

@Composable
fun TrackRow(
    index: Int,
    title: String,
    subtitle: String?,
    durationLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onMore: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (isPlaying) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Now playing",
                    tint = Accent,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(
                    text = "$index",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSubdued,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = if (isPlaying) Accent else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSubdued,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (durationLabel != null) {
            Text(
                text = durationLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSubdued,
                modifier = Modifier.padding(horizontal = Spacing.l),
            )
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(
                    imageVector = Icons.Rounded.MoreHoriz,
                    contentDescription = "More",
                    tint = TextSubdued,
                )
            }
        }
    }
}
