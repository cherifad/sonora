// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adlen.sonora.app.ui.theme.AppAccent
import com.adlen.sonora.app.ui.theme.AppMuted
import com.adlen.sonora.app.ui.theme.AppText
import com.adlen.sonora.engine.R
import com.adlen.sonora.host.android.SonoraRuntime
import com.adlen.sonora.subsonic.ServerCapabilities
import com.adlen.sonora.subsonic.SubsonicServerExtras
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The server's library scan, in Settings. Hidden on a bridge that does not expose
 * `getScanStatus`/`startScan` (a Jellyfin server, for example).
 */
@Composable
fun ServerScanRow() {
    val api = SonoraRuntime.subsonicApi ?: return
    if (!api.supports(ServerCapabilities.Feature.SCAN)) return
    var status by remember { mutableStateOf<SubsonicServerExtras.ScanStatus?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        status = runCatching { api.serverExtras.scanStatus() }.getOrNull()
    }
    val detail = when {
        busy -> stringResource(R.string.scan_started)
        status?.scanning == true -> stringResource(R.string.scan_running, status?.count ?: 0)
        (status?.lastScan ?: 0L) > 0L -> stringResource(
            R.string.scan_last,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(status?.lastScan ?: 0L)),
        )
        else -> stringResource(R.string.scan_never)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.scan_title), color = AppText, fontSize = 16.sp)
            Text(detail, color = AppMuted, fontSize = 12.sp)
        }
        TextButton(
            onClick = {
                busy = true
                scope.launch {
                    runCatching { api.serverExtras.startScan() }
                    delay(1_000)
                    status = runCatching { api.serverExtras.scanStatus() }.getOrNull()
                    busy = false
                }
            },
        ) {
            Text(stringResource(R.string.scan_now), color = AppAccent, fontSize = 14.sp)
        }
    }
}
