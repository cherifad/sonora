// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adlen.sonora.app.ui.immersive.pages.ImmersiveGroupRow
import com.adlen.sonora.app.ui.theme.AppMuted
import com.adlen.sonora.app.ui.theme.AppSans
import com.adlen.sonora.app.ui.theme.AppText
import com.adlen.sonora.engine.R
import com.adlen.sonora.host.android.SonoraRuntime
import com.adlen.sonora.subsonic.SubsonicTls

private fun reset(context: android.content.Context) {
    SubsonicTls.reset(context)
    Toast.makeText(context, context.getString(R.string.tls_reset_done), Toast.LENGTH_SHORT).show()
}

/** Classic Settings row: forget the pinned self-signed certificate after it changed. */
@Composable
fun ServerTrustSettingsRow() {
    val context = LocalContext.current
    if (SonoraRuntime.subsonicApi == null) return
    Row(
        modifier = Modifier.fillMaxWidth().clickable { reset(context) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.tls_reset_title), color = AppText, fontFamily = AppSans, fontSize = 16.sp)
            Text(stringResource(R.string.tls_reset_detail), color = AppMuted, fontFamily = AppSans, fontSize = 12.sp)
        }
    }
}

/** Immersive Settings row, same action. */
@Composable
fun ServerTrustImmersiveRow() {
    val context = LocalContext.current
    if (SonoraRuntime.subsonicApi == null) return
    ImmersiveGroupRow(
        stringResource(R.string.tls_reset_title),
        detail = stringResource(R.string.tls_reset_detail),
        icon = Icons.Rounded.Lock,
        chevron = true,
        onClick = { reset(context) },
    )
}
