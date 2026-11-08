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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.adlen.sonora.app.ui.immersive.pages.ImmersiveGroupRow
import com.adlen.sonora.app.ui.theme.AppAccent
import com.adlen.sonora.app.ui.theme.AppBackground
import com.adlen.sonora.app.ui.theme.AppMuted
import com.adlen.sonora.app.ui.theme.AppSans
import com.adlen.sonora.app.ui.theme.AppText
import com.adlen.sonora.engine.R
import com.adlen.sonora.host.android.SonoraRuntime
import com.adlen.sonora.subsonic.SubsonicServerExtras

/** The "All libraries" choice and the server's folders, with the current one ticked. */
@Composable
private fun MusicFolderDialog(
    folders: List<SubsonicServerExtras.MusicFolder>,
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppBackground)
                .padding(20.dp),
        ) {
            Text(
                stringResource(R.string.music_folder_title),
                color = AppText,
                fontFamily = AppSans,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    FolderChoice(
                        name = stringResource(R.string.music_folder_all),
                        selected = selected == null,
                        onClick = { onPick(null) },
                    )
                }
                items(folders) { folder ->
                    FolderChoice(
                        name = folder.name,
                        selected = selected == folder.id,
                        onClick = { onPick(folder.id) },
                    )
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.music_folder_close), color = AppMuted, fontFamily = AppSans)
            }
        }
    }
}

@Composable
private fun FolderChoice(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    ) {
        Text(
            name,
            color = AppText,
            fontFamily = AppSans,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = AppAccent)
    }
}

private fun pick(api: com.adlen.sonora.subsonic.SubsonicApi, id: String?) {
    api.selectMusicFolder(id)
    // The screens cache the old library: drop it so the next visit is scoped.
    com.adlen.sonora.app.data.api.Api.HomeCache.clear()
    com.adlen.sonora.app.ui.screens.ContextViewModel.clearCache()
}

/** Classic Settings row: only shown when the server exposes more than one music folder. */
@Composable
fun MusicFolderSettingsRow() {
    val api = SonoraRuntime.subsonicApi ?: return
    var folders by remember { mutableStateOf<List<SubsonicServerExtras.MusicFolder>>(emptyList()) }
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { folders = runCatching { api.musicFolders() }.getOrDefault(emptyList()) }
    if (folders.size <= 1) return
    val selected = api.currentMusicFolder
    val current = folders.firstOrNull { it.id == selected }?.name
        ?: stringResource(R.string.music_folder_all)
    Row(
        modifier = Modifier.fillMaxWidth().clickable { show = true }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.music_folder_title), color = AppText, fontFamily = AppSans, fontSize = 16.sp)
            Text(current, color = AppMuted, fontFamily = AppSans, fontSize = 12.sp)
        }
    }
    if (show) MusicFolderDialog(folders, selected, onPick = { id -> pick(api, id); show = false }) { show = false }
}

/** Immersive Settings row, same data and dialog. */
@Composable
fun MusicFolderImmersiveRow() {
    val api = SonoraRuntime.subsonicApi ?: return
    var folders by remember { mutableStateOf<List<SubsonicServerExtras.MusicFolder>>(emptyList()) }
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { folders = runCatching { api.musicFolders() }.getOrDefault(emptyList()) }
    if (folders.size <= 1) return
    val selected = api.currentMusicFolder
    val current = folders.firstOrNull { it.id == selected }?.name
        ?: stringResource(R.string.music_folder_all)
    ImmersiveGroupRow(
        stringResource(R.string.music_folder_title),
        detail = current,
        icon = Icons.Rounded.Folder,
        chevron = true,
        onClick = { show = true },
    )
    if (show) MusicFolderDialog(folders, selected, onPick = { id -> pick(api, id); show = false }) { show = false }
}
