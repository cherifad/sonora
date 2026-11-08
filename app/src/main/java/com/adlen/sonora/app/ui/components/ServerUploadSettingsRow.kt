// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.adlen.sonora.app.data.preferences.Look
import com.adlen.sonora.app.data.preferences.LookPref
import com.adlen.sonora.app.data.upload.UploadSettings
import com.adlen.sonora.app.ui.immersive.Immersive
import com.adlen.sonora.app.ui.immersive.pages.ImmersiveGroupRow
import com.adlen.sonora.app.ui.theme.AppBackground
import com.adlen.sonora.app.ui.theme.AppMuted
import com.adlen.sonora.app.ui.theme.AppSans
import com.adlen.sonora.app.ui.theme.AppText
import com.adlen.sonora.engine.R
import androidx.compose.ui.text.TextStyle

/**
 * The ingest URL and the Wi-Fi rule. No token to copy: "Connect" proves the Subsonic
 * credentials the app already holds, and the ingest issues the upload token.
 */
@Composable
fun ServerUploadDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val immersive = LookPref.look == Look.IMMERSIVE
    val initial = UploadSettings.load(context)?.baseUrl ?: UploadSettings.url(context)
    val close = {
        // A token may already have been issued (before this dialog or by Connect).
        if (UploadSettings.load(context) != null) onSaved() else onDismiss()
    }
    Dialog(onDismissRequest = close) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .clip(if (immersive) Immersive.Card else RoundedCornerShape(16.dp))
                .background(if (immersive) Immersive.Raised else AppBackground)
                .padding(20.dp),
        ) {
            if (immersive) {
                Text(stringResource(R.string.upload_settings_title), style = Immersive.Title)
            } else {
                Text(
                    stringResource(R.string.upload_settings_title),
                    color = AppText,
                    fontFamily = AppSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            UploadConnectForm(
                initialUrl = initial.orEmpty(),
                onConnected = {},
                immersive = immersive,
                trailing = { connected ->
                    TextButton(onClick = close, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(
                                if (connected) R.string.upload_settings_close else R.string.upload_settings_cancel,
                            ),
                            style = if (immersive) Immersive.BodyStrong.copy(color = Immersive.TextSecondary) else TextStyle(fontFamily = AppSans),
                            color = if (immersive) Immersive.TextSecondary else AppMuted,
                        )
                    }
                },
            )
        }
    }
}

/** Classic Settings row. */
@Composable
fun ServerUploadSettingsRow() {
    val context = LocalContext.current
    var target by remember { mutableStateOf(UploadSettings.load(context)) }
    var show by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { show = true }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.upload_settings_title), color = AppText, fontFamily = AppSans, fontSize = 16.sp)
            Text(target?.baseUrl ?: stringResource(R.string.upload_settings_not_set), color = AppMuted, fontFamily = AppSans, fontSize = 12.sp)
        }
    }
    if (show) ServerUploadDialog(
        onDismiss = { show = false },
        onSaved = { target = UploadSettings.load(context); show = false },
    )
}

/** Immersive Settings row. */
@Composable
fun ServerUploadImmersiveRow() {
    val context = LocalContext.current
    var target by remember { mutableStateOf(UploadSettings.load(context)) }
    var show by remember { mutableStateOf(false) }
    ImmersiveGroupRow(
        stringResource(R.string.upload_settings_title),
        detail = target?.baseUrl ?: stringResource(R.string.upload_settings_not_set),
        icon = Icons.Rounded.CloudUpload,
        chevron = true,
        onClick = { show = true },
    )
    if (show) ServerUploadDialog(
        onDismiss = { show = false },
        onSaved = { target = UploadSettings.load(context); show = false },
    )
}
