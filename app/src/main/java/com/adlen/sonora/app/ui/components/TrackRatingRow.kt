// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adlen.sonora.app.ui.theme.AppAccent
import com.adlen.sonora.app.ui.theme.AppMuted
import com.adlen.sonora.app.ui.theme.AppSans
import com.adlen.sonora.app.ui.theme.AppText
import com.adlen.sonora.engine.R

/**
 * The 1–5 star control of a track, shown only when the server exposes ratings. Tapping the
 * current value clears it (0), which is the Subsonic convention.
 */
@Composable
internal fun TrackRatingRow(
    rating: Int?,
    enabled: Boolean = true,
    onRate: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = null,
                tint = AppMuted.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.rating_title),
            color = AppText.copy(alpha = if (enabled) 1f else 0.4f),
            fontFamily = AppSans,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
        )
        Spacer(Modifier.weight(1f))
        val stars = rating ?: 0
        (1..5).forEach { value ->
            IconButton(
                onClick = { if (enabled) onRate(if (stars == value) 0 else value) },
                enabled = enabled,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = if (stars >= value) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = stringResource(R.string.rating_star, value),
                    tint = if (stars >= value) AppAccent else AppMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
