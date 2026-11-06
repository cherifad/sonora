package com.adlen.sonora.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adlen.sonora.engine.R
import com.adlen.sonora.app.data.offline.OfflineMode
import com.adlen.sonora.app.ui.immersive.Immersive
import com.adlen.sonora.app.ui.navigation.isImmersive
import com.adlen.sonora.app.ui.theme.AppMuted
import com.adlen.sonora.app.ui.theme.AppText
import com.adlen.sonora.app.ui.theme.tone

/**
 * "Offline: only downloads play", shown at the top of the lists that would otherwise look
 * normal and fail on tap. Nothing when the app is online.
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    if (!OfflineMode.offline) return
    val immersive = isImmersive()
    val titleColor = if (immersive) Immersive.Text else AppText
    val detailColor = if (immersive) Immersive.TextSecondary else AppMuted
    val fill = if (immersive) Immersive.Raised else tone(0xFF2A2A2A)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = detailColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.offline_banner_title), color = titleColor, fontSize = 14.sp)
            Text(stringResource(R.string.offline_banner_detail), color = detailColor, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}
