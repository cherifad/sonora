// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adlen.sonora.app.data.local.ListenHistory
import com.adlen.sonora.app.ui.immersive.Immersive
import com.adlen.sonora.app.ui.immersive.components.immersiveClickable
import com.adlen.sonora.app.ui.theme.AppMuted
import com.adlen.sonora.app.ui.theme.AppSans
import com.adlen.sonora.app.ui.theme.AppText
import com.adlen.sonora.engine.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The listening totals shown on the user's own profile, from [ListenHistory] (on this phone
 * only). Tapping the card opens the full recap.
 */
@Composable
fun ProfileListeningStats(
    immersive: Boolean,
    onOpenRecap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recap by produceState<ListenHistory.Recap?>(null) {
        value = withContext(Dispatchers.IO) { ListenHistory.recap(0) }
    }
    val r = recap ?: return
    val accent = if (immersive) Immersive.Accent else com.adlen.sonora.app.ui.theme.AppAccent
    val card = if (immersive) Immersive.Raised else Color(0xFF1C1C1E)
    val shape = if (immersive) Immersive.Card else RoundedCornerShape(14.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(card)
            .then(
                if (immersive) Modifier.immersiveClickable(onClick = onOpenRecap)
                else Modifier.clickable(onClick = onOpenRecap),
            )
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(r.minutes.toString(), stringResource(R.string.profile_stats_minutes), immersive)
            Stat(r.listens.toString(), stringResource(R.string.profile_stats_listens), immersive)
        }
        if (r.topArtists.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.recap_top_artists),
                style = if (immersive) Immersive.Subhead else TextStyle(fontFamily = AppSans, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                color = if (immersive) Immersive.TextSecondary else AppMuted,
            )
            r.topArtists.take(3).forEach { artist ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        artist.name,
                        style = if (immersive) Immersive.Body else TextStyle(fontFamily = AppSans, fontSize = 14.sp),
                        color = if (immersive) Immersive.Text else AppText,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.recap_plays, artist.count),
                        style = if (immersive) Immersive.Caption else TextStyle(fontFamily = AppSans, fontSize = 12.sp),
                        color = if (immersive) Immersive.TextSecondary else AppMuted,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.profile_stats_see_all),
            style = if (immersive) Immersive.BodyStrong.copy(color = accent) else TextStyle(fontFamily = AppSans, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = accent,
        )
    }
}

@Composable
private fun Stat(value: String, label: String, immersive: Boolean) {
    Column {
        Text(
            value,
            style = if (immersive) Immersive.Title else TextStyle(fontFamily = AppSans, fontSize = 24.sp, fontWeight = FontWeight.Bold),
            color = if (immersive) Immersive.Text else AppText,
        )
        Text(
            label,
            style = if (immersive) Immersive.Caption else TextStyle(fontFamily = AppSans, fontSize = 12.sp),
            color = if (immersive) Immersive.TextSecondary else AppMuted,
        )
    }
}
