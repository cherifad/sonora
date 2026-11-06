package com.adlen.sonora.app.ui.components

import com.adlen.sonora.app.ui.theme.tone
import com.adlen.sonora.app.ui.theme.AppText
import com.adlen.sonora.app.ui.theme.AppMuted

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adlen.sonora.engine.BuildConfig
import com.adlen.sonora.engine.R
import kotlinx.coroutines.launch

// The fork's own source, so the GPL's "corresponding source" is one tap away (a build
// with -PupdateRepo points both this and the update check at another repository).
private val repo get() = BuildConfig.UPDATE_REPO.ifBlank { "cherifad/sonora" }

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** About: the version, the source code and the update check. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val check = com.adlen.sonora.app.data.api.rememberUpdateCheckState(context)
    val update = com.adlen.sonora.app.updateRelease.value
        ?: remember { com.adlen.sonora.app.data.api.UpdateChecker.known(context) }
    LaunchedEffect(Unit) {
        if (com.adlen.sonora.app.data.preferences.AutoUpdateCheckPref.on) {
            com.adlen.sonora.app.data.api.UpdateChecker.check(context)?.let {
                com.adlen.sonora.app.updateRelease.value = it
            }
        }
    }
    com.adlen.sonora.app.extensions.ProtectedSurfaceEffect(com.adlen.sonora.extensions.ProtectedSurface.ABOUT)
    // Opened in full: half open, it hid the update row at the bottom.
    LookSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = tone(0xFF282828),
    ) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
                Image(
                    painterResource(R.drawable.app_mark), contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
                )
                Column(Modifier.padding(start = 14.dp)) {
                    Text(stringResource(R.string.app_name), color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${stringResource(R.string.settings_item_version_title)} ${BuildConfig.VERSION_NAME}",
                        color = AppMuted, fontSize = 13.sp)
                }
            }
            Text(stringResource(R.string.about_free), color = AppMuted, fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp))
            AboutRow(stringResource(R.string.about_github), stringResource(R.string.about_github_detail)) { openUrl(context, "https://github.com/$repo") }
            if (update != null) {
                AboutRow(
                    stringResource(R.string.settings_update_available, update.version),
                    stringResource(R.string.settings_update_available_detail),
                ) { com.adlen.sonora.app.updateRelease.value = update }
            } else {
                AboutRow(
                    stringResource(R.string.settings_update_refresh),
                    if (check.checking) stringResource(R.string.settings_update_checking) else check.lastCheckedText,
                ) { scope.launch { check.run() } }
            }
            check.message?.takeIf { check.isError }?.let {
                Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun AboutRow(title: String, detail: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(title, color = AppText, fontSize = 16.sp)
        Text(detail, color = AppMuted, fontSize = 12.sp)
    }
}
