// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adlen.sonora.app.data.upload.UploadAuth
import com.adlen.sonora.app.data.upload.UploadSettings
import com.adlen.sonora.app.ui.immersive.Immersive
import com.adlen.sonora.app.ui.theme.AppAccent
import com.adlen.sonora.app.ui.theme.AppMuted
import com.adlen.sonora.app.ui.theme.AppSans
import com.adlen.sonora.app.ui.theme.AppText
import com.adlen.sonora.engine.R
import kotlinx.coroutines.launch

/**
 * The ingest URL, the Wi-Fi rule and the Connect button. Shared by the Settings dialog and
 * the first-run setup: the user never copies a token, [UploadAuth] trades the Subsonic login
 * for one. [trailing] receives whether a token was just issued (for Close/Continue/Skip).
 *
 * [immersive] switches the fields and the button to the Immersive look; the caller picks it
 * from [com.adlen.sonora.app.data.preferences.LookPref].
 */
@Composable
fun UploadConnectForm(
    initialUrl: String,
    onConnected: (UploadAuth.Session) -> Unit,
    modifier: Modifier = Modifier,
    immersive: Boolean = false,
    trailing: @Composable (connected: Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(initialUrl) }
    var wifiOnly by remember { mutableStateOf(UploadSettings.isWifiOnly(context)) }
    var connecting by remember { mutableStateOf(false) }
    var connectedAs by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val fieldColors = if (immersive) {
        OutlinedTextFieldDefaults.colors(
            focusedTextColor = Immersive.Text,
            unfocusedTextColor = Immersive.Text,
            focusedContainerColor = Color.White.copy(alpha = 0.08f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
            cursorColor = Immersive.Accent,
            focusedBorderColor = Immersive.Accent,
            unfocusedBorderColor = Immersive.Separator,
            focusedLabelColor = Immersive.Accent,
            unfocusedLabelColor = Immersive.TextSecondary,
        )
    } else {
        OutlinedTextFieldDefaults.colors()
    }

    Column(modifier) {
        if (immersive) {
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Text(
                    stringResource(R.string.upload_settings_url),
                    style = Immersive.Caption,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
                BasicTextField(
                    value = url,
                    onValueChange = { url = it; connectedAs = null; error = null },
                    singleLine = true,
                    enabled = !connecting,
                    textStyle = Immersive.Body,
                    cursorBrush = SolidColor(Immersive.Accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.08f), Immersive.Button)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                )
            }
        } else {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; connectedAs = null; error = null },
                label = { Text(stringResource(R.string.upload_settings_url)) },
                singleLine = true,
                enabled = !connecting,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.upload_settings_wifi),
                    style = if (immersive) Immersive.Body else TextStyle(fontFamily = AppSans, fontSize = 15.sp),
                    color = if (immersive) Immersive.Text else AppText,
                )
                Text(
                    stringResource(R.string.upload_settings_wifi_detail),
                    style = if (immersive) Immersive.Caption else TextStyle(fontFamily = AppSans, fontSize = 12.sp),
                    color = if (immersive) Immersive.TextSecondary else AppMuted,
                )
            }
            Switch(
                checked = wifiOnly,
                enabled = !connecting,
                colors = if (immersive) {
                    SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Immersive.Accent,
                        uncheckedThumbColor = Immersive.TextSecondary,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                    )
                } else {
                    SwitchDefaults.colors()
                },
                onCheckedChange = {
                    wifiOnly = it
                    UploadSettings.setWifiOnly(context, it)
                },
            )
        }
        connectedAs?.let {
            Text(
                it,
                style = if (immersive) Immersive.Subhead else TextStyle(fontFamily = AppSans, fontSize = 13.sp),
                color = if (immersive) Immersive.Accent else AppAccent,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        error?.let {
            Text(
                it,
                style = if (immersive) Immersive.Subhead else TextStyle(fontFamily = AppSans, fontSize = 13.sp),
                color = if (immersive) Immersive.Destructive else Color(0xFFFF6B6B),
                modifier = Modifier.padding(top = 8.dp),
            )
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
                        onConnected(session)
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
            shape = if (immersive) Immersive.Button else ButtonDefaults.shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (immersive) Immersive.Accent else AppAccent,
                contentColor = Color.Black,
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (connecting) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text(
                    stringResource(R.string.upload_settings_connecting),
                    style = if (immersive) Immersive.BodyStrong.copy(color = Color.Black) else TextStyle(fontFamily = AppSans, fontWeight = FontWeight.Bold, color = Color.Black),
                )
            } else {
                Text(
                    stringResource(R.string.upload_settings_connect),
                    style = if (immersive) Immersive.BodyStrong.copy(color = Color.Black) else TextStyle(fontFamily = AppSans, fontWeight = FontWeight.Bold, color = Color.Black),
                )
            }
        }
        trailing(connectedAs != null)
    }
}
