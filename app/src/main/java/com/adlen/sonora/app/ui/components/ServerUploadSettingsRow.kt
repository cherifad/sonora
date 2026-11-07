// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.adlen.sonora.app.data.upload.UploadAuth
import com.adlen.sonora.app.data.upload.UploadSettings
import com.adlen.sonora.app.ui.immersive.pages.ImmersiveGroupRow
import com.adlen.sonora.app.ui.theme.AppAccent
import com.adlen.sonora.app.ui.theme.AppBackground
import com.adlen.sonora.app.ui.theme.AppMuted
import com.adlen.sonora.app.ui.theme.AppSans
import com.adlen.sonora.app.ui.theme.AppText
import com.adlen.sonora.engine.R
import kotlinx.coroutines.launch

/**
 * The ingest URL and the Wi-Fi rule. No token to copy: "Connect" proves the Subsonic
 * credentials the app already holds, and the ingest issues the upload token.
 */
@Composable
fun ServerUploadDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val initial = UploadSettings.load(context)
    var url by remember { mutableStateOf(initial?.baseUrl ?: UploadSettings.url(context).orEmpty()) }
    var wifiOnly by remember { mutableStateOf(UploadSettings.isWifiOnly(context)) }
    var connecting by remember { mutableStateOf(false) }
    var connectedAs by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val close = { if (connectedAs != null) onSaved() else onDismiss() }
    Dialog(onDismissRequest = close) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppBackground)
                .padding(20.dp),
        ) {
            Text(
                stringResource(R.string.upload_settings_title),
                color = AppText,
                fontFamily = AppSans,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; connectedAs = null; error = null },
                label = { Text(stringResource(R.string.upload_settings_url)) },
                singleLine = true,
                enabled = !connecting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.upload_settings_wifi), color = AppText, fontFamily = AppSans, fontSize = 15.sp)
                    Text(stringResource(R.string.upload_settings_wifi_detail), color = AppMuted, fontFamily = AppSans, fontSize = 12.sp)
                }
                Switch(
                    checked = wifiOnly,
                    enabled = !connecting,
                    onCheckedChange = {
                        wifiOnly = it
                        UploadSettings.setWifiOnly(context, it)
                    },
                )
            }
            connectedAs?.let {
                Text(it, color = AppAccent, fontFamily = AppSans, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp))
            }
            error?.let {
                Text(it, color = androidx.compose.ui.graphics.Color(0xFFFF6B6B), fontFamily = AppSans, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (url.isBlank()) {
                        error = context.getString(R.string.upload_settings_missing_url)
                        return@Button
                    }
                    connecting = true
                    error = null
                    connectedAs = null
                    scope.launch {
                        try {
                            val session = UploadAuth.connect(context, url, wifiOnly)
                            connectedAs = context.getString(
                                R.string.upload_settings_connected, session.user, session.server,
                            )
                            Toast.makeText(context, context.getString(R.string.upload_settings_saved), Toast.LENGTH_SHORT).show()
                        } catch (e: UploadAuth.ExchangeException) {
                            error = context.getString(
                                when (e.failure) {
                                    UploadAuth.Failure.NO_SERVER -> R.string.upload_settings_no_server
                                    UploadAuth.Failure.REFUSED -> R.string.upload_settings_refused
                                    UploadAuth.Failure.UNREACHABLE -> R.string.upload_settings_unreachable
                                    UploadAuth.Failure.SERVER_ERROR -> R.string.upload_settings_server_error
                                },
                            )
                        } finally {
                            connecting = false
                        }
                    }
                },
                enabled = !connecting,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (connecting) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = AppText, strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text(stringResource(R.string.upload_settings_connecting))
                } else {
                    Text(stringResource(R.string.upload_settings_connect))
                }
            }
            TextButton(onClick = close, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        if (connectedAs != null) R.string.upload_settings_close else R.string.upload_settings_cancel,
                    ),
                    color = AppMuted,
                )
            }
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
