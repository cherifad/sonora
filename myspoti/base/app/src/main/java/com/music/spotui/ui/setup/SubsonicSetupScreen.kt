// Subsonic/OpenSubsonic backend for Myspoti. GPL-3.0.
package com.music.spotui.ui.setup

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.myspoti.host.android.MyspotiRuntime
import app.myspoti.subsonic.SubsonicClient
import app.myspoti.subsonic.SubsonicConfig
import app.myspoti.subsonic.SubsonicSettings
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppSans
import com.music.spotui.ui.theme.AppText
import kotlinx.coroutines.launch

/** First-run setup: no Spotify login, just the Subsonic server. */
@Composable
fun SubsonicSetupScreen(onDone: () -> Unit) {
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
            ServerForm(
                initial = SubsonicSettings.load(LocalContext.current),
                onCancel = null,
                onSaved = { onDone() },
            )
        }
    }
}

/** Re-open the server form from Settings. */
@Composable
fun SubsonicSetupDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppBackground)
                    .padding(24.dp),
            ) {
                Text(
                    text = "Server",
                    color = AppText,
                    fontFamily = AppSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                )
                Spacer(Modifier.height(16.dp))
                ServerForm(
                    initial = SubsonicSettings.load(LocalContext.current),
                    onCancel = onDismiss,
                    onSaved = { onSaved() },
                )
            }
        }
    }
}

@Composable
private fun ServerForm(
    initial: SubsonicConfig?,
    onCancel: (() -> Unit)?,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var username by remember { mutableStateOf(initial?.username.orEmpty()) }
    var password by remember { mutableStateOf(initial?.password.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
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

    fun connect() {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank() || username.isBlank() || password.isBlank()) {
            error = "Fill in the server URL, username and password."
            return
        }
        val config = SubsonicConfig(trimmedUrl, username.trim(), password)
        scope.launch {
            busy = true
            error = null
            val result = runCatching { SubsonicClient(config).ping() }
            busy = false
            result.onSuccess {
                SubsonicSettings.save(context, config)
                MyspotiRuntime.updateSubsonicConfig(config)
                onSaved()
            }.onFailure {
                error = it.message ?: "Could not connect to the server."
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

    if (error != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = error.orEmpty(),
            color = Color(0xFFFF6B6B),
            fontFamily = AppSans,
            fontSize = 14.sp,
        )
    }

    Spacer(Modifier.height(20.dp))
    Button(
        onClick = { connect() },
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppAccent,
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
            Text("Connect", fontFamily = AppSans, fontWeight = FontWeight.Bold)
        }
    }
    if (onCancel != null) {
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = AppMuted, fontFamily = AppSans)
        }
    }
}
