// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.adlen.sonora.host.android.SonoraRuntime
import com.adlen.sonora.subsonic.SubsonicClient
import com.adlen.sonora.subsonic.SubsonicConfig
import com.adlen.sonora.subsonic.SubsonicFailure
import com.adlen.sonora.subsonic.SubsonicSettings
import com.adlen.sonora.app.data.preferences.Look
import com.adlen.sonora.app.data.preferences.LookPref
import com.adlen.sonora.app.data.upload.UploadSettings
import com.adlen.sonora.app.ui.components.UploadConnectForm
import com.adlen.sonora.app.ui.immersive.Immersive
import com.adlen.sonora.app.ui.theme.AppAccent
import com.adlen.sonora.app.ui.theme.AppBackground
import com.adlen.sonora.app.ui.theme.AppMuted
import com.adlen.sonora.app.ui.theme.AppSans
import com.adlen.sonora.app.ui.theme.AppText
import kotlinx.coroutines.launch

/** First-run setup: no Spotify login, just the Subsonic server, then the optional upload target. */
@Composable
fun SubsonicSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (step == 0) {
                Text(
                    text = "Connect to your server",
                    color = AppText,
                    fontFamily = AppSans,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Subsonic / OpenSubsonic. Jellyfin + Subfin: http://<host>:8096/subfin/ — Navidrome: http://<host>:4533/",
                    color = AppMuted,
                    fontFamily = AppSans,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(24.dp))
                SubsonicServerForm(
                    initial = SubsonicSettings.load(context),
                    onCancel = null,
                    onSaved = { step = 1 },
                )
            } else {
                val server = SubsonicSettings.load(context)
                val suggestion = UploadSettings.suggestUrl(server?.baseUrl.orEmpty())
                val suggestedHost = suggestion.removePrefix("http://").removePrefix("https://").substringBefore('/')
                val rule = if (suggestedHost.contains("-upload.")) {
                    "your server name with -upload before the domain"
                } else {
                    "the same host on port 8088"
                }
                Text(
                    text = "Upload your music (optional)",
                    color = AppText,
                    fontFamily = AppSans,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Run the companion ingest service next to your server, then connect it here so Sonora " +
                        "can send your local files to the library. Recommended address: $suggestion ($rule).",
                    color = AppMuted,
                    fontFamily = AppSans,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(24.dp))
                UploadConnectForm(
                    initialUrl = UploadSettings.load(context)?.baseUrl
                        ?: UploadSettings.url(context)
                        ?: suggestion,
                    onConnected = {},
                    trailing = { connected ->
                        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (connected) "Continue" else "Skip for now",
                                color = AppMuted,
                                fontFamily = AppSans,
                            )
                        }
                    },
                )
            }
        }
    }
}

/** Re-open the server form from Settings. */
@Composable
fun SubsonicSetupDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val immersive = LookPref.look == Look.IMMERSIVE
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .clip(if (immersive) Immersive.Card else RoundedCornerShape(16.dp))
                    .background(if (immersive) Immersive.Raised else AppBackground)
                    .padding(24.dp),
            ) {
                if (immersive) {
                    Text(text = "Server", style = Immersive.Title)
                } else {
                    Text(
                        text = "Server",
                        color = AppText,
                        fontFamily = AppSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
                SubsonicServerForm(
                    initial = SubsonicSettings.load(LocalContext.current),
                    onCancel = onDismiss,
                    onSaved = { onSaved() },
                )
            }
        }
    }
}

@Composable
internal fun SubsonicServerForm(
    initial: SubsonicConfig?,
    onCancel: (() -> Unit)?,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val immersive = LookPref.look == Look.IMMERSIVE
    var url by remember { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var username by remember { mutableStateOf(initial?.username.orEmpty()) }
    var password by remember { mutableStateOf(initial?.password.orEmpty()) }
    var apiKey by remember { mutableStateOf(initial?.apiKey.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
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
        OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppText,
            unfocusedTextColor = AppText,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            cursorColor = AppAccent,
            focusedBorderColor = AppAccent,
            unfocusedBorderColor = AppMuted,
            focusedLabelColor = AppAccent,
            unfocusedLabelColor = AppMuted,
        )
    }

    fun connect() {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank() || username.isBlank() || password.isBlank()) {
            error = "Fill in the server URL, username and password."
            return
        }
        val config = SubsonicConfig(
            trimmedUrl,
            username.trim(),
            password,
            apiKey.trim().takeIf { it.isNotBlank() },
        )
        scope.launch {
            busy = true
            error = null
            val result = runCatching {
                SubsonicClient(config, com.adlen.sonora.subsonic.SubsonicTls.client(context, config))
                    .probeCapabilities()
            }
            busy = false
            result.onSuccess {
                SubsonicSettings.save(context, config)
                SonoraRuntime.updateSubsonicConfig(config)
                onSaved()
            }.onFailure {
                error = (it as? SubsonicFailure)?.userMessage
                    ?: it.message
                    ?: "Could not connect to the server."
            }
        }
    }

    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text("Server URL") },
        singleLine = true,
        colors = fieldColors,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    if (url.trim().startsWith("http://")) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "HTTP sends the password and the token in clear text on the network. Use HTTPS when the server supports it.",
            style = if (immersive) Immersive.Caption else TextStyle(fontFamily = AppSans, fontSize = 12.sp, lineHeight = 16.sp),
            color = Color(0xFFFFB74D),
        )
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text("Username") },
        singleLine = true,
        colors = fieldColors,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password / app password") },
        singleLine = true,
        colors = fieldColors,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = apiKey,
        onValueChange = { apiKey = it },
        label = { Text("API key (optional)") },
        singleLine = true,
        colors = fieldColors,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        supportingText = {
            Text(
                "Only for servers that advertise OpenSubsonic API-key login; it replaces the password.",
                style = if (immersive) Immersive.Caption else TextStyle(fontFamily = AppSans, fontSize = 12.sp),
                color = if (immersive) Immersive.TextSecondary else AppMuted,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )

    if (error != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = error.orEmpty(),
            style = if (immersive) Immersive.Subhead else TextStyle(fontFamily = AppSans, fontSize = 14.sp),
            color = if (immersive) Immersive.Destructive else Color(0xFFFF6B6B),
        )
    }

    Spacer(Modifier.height(20.dp))
    Button(
        onClick = { connect() },
        enabled = !busy,
        shape = if (immersive) Immersive.Button else ButtonDefaults.shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (immersive) Immersive.Accent else AppAccent,
            contentColor = Color.Black,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color.Black,
            )
        } else {
            Text(
                "Connect",
                style = if (immersive) Immersive.BodyStrong.copy(color = Color.Black) else TextStyle(fontFamily = AppSans, fontWeight = FontWeight.Bold),
            )
        }
    }
    if (onCancel != null) {
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Cancel",
                style = if (immersive) Immersive.BodyStrong.copy(color = Immersive.TextSecondary) else TextStyle(fontFamily = AppSans),
                color = if (immersive) Immersive.TextSecondary else AppMuted,
            )
        }
    }
}
