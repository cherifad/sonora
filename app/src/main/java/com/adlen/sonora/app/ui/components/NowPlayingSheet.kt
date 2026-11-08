// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.adlen.sonora.app.ui.theme.AppBackground
import com.adlen.sonora.app.ui.theme.AppMuted
import com.adlen.sonora.app.ui.theme.AppSans
import com.adlen.sonora.app.ui.theme.AppText
import com.adlen.sonora.engine.R
import com.adlen.sonora.host.android.SonoraRuntime
import com.adlen.sonora.subsonic.ServerCapabilities
import com.adlen.sonora.subsonic.SubsonicServerExtras

/** What other clients are playing on this server (`getNowPlaying`). */
@Composable
fun NowPlayingSheet(onDismiss: () -> Unit) {
    val api = SonoraRuntime.subsonicApi ?: return
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<SubsonicServerExtras.RemotePlay>>(emptyList()) }
    LaunchedEffect(Unit) {
        val result = runCatching { api.serverExtras.nowPlaying() }
        loading = false
        failed = result.isFailure
        entries = result.getOrDefault(emptyList())
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppBackground)
                .padding(20.dp),
        ) {
            Text(
                text = stringResource(R.string.now_playing_title),
                color = AppText,
                fontFamily = AppSans,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            when {
                loading -> CircularProgressIndicator()
                failed -> Text(stringResource(R.string.now_playing_error), color = AppMuted, fontFamily = AppSans, fontSize = 14.sp)
                entries.isEmpty() -> Text(stringResource(R.string.now_playing_empty), color = AppMuted, fontFamily = AppSans, fontSize = 14.sp)
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(entries) { entry ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(
                                entry.track.name,
                                color = AppText,
                                fontFamily = AppSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                entry.track.artists.joinToString(", ") { it.name },
                                color = AppMuted,
                                fontFamily = AppSans,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(R.string.now_playing_who, entry.username, entry.minutesAgo),
                                color = AppMuted,
                                fontFamily = AppSans,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.now_playing_close), color = AppMuted, fontFamily = AppSans)
            }
        }
    }
}

/** The Classic Settings row that opens [NowPlayingSheet]; hidden without the capability. */
@Composable
fun NowPlayingSettingsRow() {
    val api = SonoraRuntime.subsonicApi ?: return
    if (!api.supports(ServerCapabilities.Feature.NOW_PLAYING)) return
    val admin by api.isAdmin.collectAsState()
    if (admin == false) return
    var show by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { show = true }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.now_playing_title), color = AppText, fontFamily = AppSans, fontSize = 16.sp)
            Text(stringResource(R.string.now_playing_detail), color = AppMuted, fontFamily = AppSans, fontSize = 12.sp)
        }
    }
    if (show) NowPlayingSheet { show = false }
}
