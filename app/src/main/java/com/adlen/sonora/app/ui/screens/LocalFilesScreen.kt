package com.adlen.sonora.app.ui.screens

import com.adlen.sonora.app.ui.theme.headerTint
import com.adlen.sonora.app.ui.theme.tone
import com.adlen.sonora.app.ui.theme.AppText
import com.adlen.sonora.app.ui.theme.AppMuted
import com.adlen.sonora.app.ui.theme.AppAccent

import kotlinx.coroutines.launch
import android.app.Activity
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.layout.navigationBarsPadding
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.adlen.sonora.engine.R
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.CrossFade
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.adlen.sonora.app.data.local.LocalMusic
import com.adlen.sonora.app.data.local.LocalTrack
import com.adlen.sonora.app.data.upload.LocalUploadIndex
import com.adlen.sonora.app.data.upload.UploadEntry
import com.adlen.sonora.app.data.upload.UploadManager
import com.adlen.sonora.app.data.upload.UploadOrigin
import com.adlen.sonora.app.data.upload.UploadSettings
import com.adlen.sonora.app.data.upload.UploadState
import com.adlen.sonora.app.ui.components.LocalDeleteFlow
import com.adlen.sonora.app.ui.components.ServerUploadDialog
import com.adlen.sonora.app.ui.components.uploadStateLabel
import com.adlen.sonora.app.di.LocalPlayer
import com.adlen.sonora.app.di.SongPlayer
import com.adlen.sonora.app.ui.components.pressable
import com.adlen.sonora.app.ui.theme.AppBackground

/** "Local Files": the device's own audio files, played by [LocalPlayer]. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun LocalFilesScreen(navController: NavController) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(LocalMusic.hasPermission(context)) }
    var askedOnce by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf<List<LocalTrack>?>(null) }
    var find by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok; askedOnce = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = LocalMusic.hasPermission(context) }
    LaunchedEffect(granted) { if (granted) tracks = LocalMusic.tracks(context, refresh = true) }

    val np by SongPlayer.nowPlaying
    val playingLocal = LocalPlayer.active.value
    val uploads by LocalUploadIndex.entries.collectAsState()
    val uploadProgress by UploadManager.progress.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val uploaded = tracks.orEmpty().filter {
        uploads[LocalUploadIndex.key(it)]?.state == UploadState.UPLOADED
    }
    var showUploadSettings by remember { mutableStateOf(false) }
    if (showUploadSettings) ServerUploadDialog(
        onDismiss = { showUploadSettings = false },
        onSaved = { showUploadSettings = false },
    )
    var showDelete by remember { mutableStateOf(false) }
    val delete = LocalDeleteFlow(
        candidates = uploaded,
        uploads = uploads,
        showDialog = showDelete,
        onDismiss = { showDelete = false },
        onDeleted = { removed ->
            removed.forEach { LocalUploadIndex.remove(LocalUploadIndex.key(it)) }
            scope.launch { tracks = LocalMusic.tracks(context, refresh = true) }
        },
    )
    var adding by remember { mutableStateOf<LocalTrack?>(null) }
    adding?.let { t -> AddLocalToPlaylistSheet(t, onDismiss = { adding = null }) }
    var choosingFolders by remember { mutableStateOf(false) }
    if (choosingFolders) LocalFoldersSheet(
        onChanged = { scope.launch { tracks = LocalMusic.tracks(context) } },
        onDismiss = { choosingFolders = false },
    )

    Box(Modifier.fillMaxSize().background(AppBackground)) {
        when {
            !granted -> EmptyState(
                title = stringResource(R.string.local_files_empty_section_title),
                subtitle = stringResource(R.string.local_files_empty_section_subtitle),
                button = stringResource(if (askedOnce) R.string.local_files_empty_section_button_go_to_settings else R.string.local_files_empty_section_dialog_permission_rationale_accept),
            ) {
                if (askedOnce) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                else launcher.launch(LocalMusic.permission)
            }
            tracks?.isEmpty() == true -> EmptyState(
                title = stringResource(R.string.local_files_empty_section_no_files_title),
                subtitle = stringResource(R.string.local_files_none_subtitle),
                button = null,
            ) {}
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(headerTint(Color(0xFF2E5C46), 0f), AppBackground)))
                            .statusBarsPadding().padding(horizontal = 16.dp),
                    ) {
                        Spacer(Modifier.height(56.dp))
                        Image(
                            painterResource(R.drawable.local_files_cover), null,
                            modifier = Modifier.align(Alignment.CenterHorizontally).size(200.dp).clip(RoundedCornerShape(6.dp)),
                        )
                        Text(stringResource(R.string.local_files_title), color = AppText, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 20.dp))
                        val n = tracks?.size ?: 0
                        Text(pluralStringResource(R.plurals.local_files_count, n, n), color = AppMuted, fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp))
                        uploadProgress?.let { progress ->
                            Text(
                                stringResource(R.string.local_files_uploading, progress.done + 1, progress.total, progress.title),
                                color = AppAccent, fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        if (delete.deleting) {
                            Text(
                                stringResource(R.string.local_files_delete_checking),
                                color = AppMuted, fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { choosingFolders = true },
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7F7F7F)),
                            ) { Text(stringResource(R.string.local_folders_button), color = AppText, fontSize = 13.sp) }
                            Spacer(Modifier.width(8.dp))
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    if (UploadSettings.load(context) == null) showUploadSettings = true
                                    else tracks?.let { UploadManager.enqueue(context, it) }
                                },
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7F7F7F)),
                            ) { Text(stringResource(R.string.local_files_upload_button), color = AppText, fontSize = 13.sp) }
                            if (uploaded.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { showDelete = true }, enabled = !delete.deleting) {
                                    Icon(
                                        Icons.Rounded.DeleteSweep,
                                        contentDescription = stringResource(R.string.local_files_delete_button),
                                        tint = AppText,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Icon(painterResource(R.drawable.ic_player_shuffle), stringResource(R.string.play_mode_item_shuffle_title), tint = AppText,
                                modifier = Modifier.size(28.dp).pressable { tracks?.let { if (it.isNotEmpty()) LocalPlayer.play(it, it.indices.random(), shuffle = true) } })
                            Spacer(Modifier.width(20.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(56.dp)
                                    .pressable(0.92f) {
                                        when {
                                            playingLocal && SongPlayer.isPlaying() -> SongPlayer.pause()
                                            playingLocal -> SongPlayer.play()
                                            else -> tracks?.let { LocalPlayer.play(it, 0) }
                                        }
                                    }
                                    .clip(CircleShape).background(com.adlen.sonora.app.ui.theme.AppAccent),
                            ) {
                                Icon(painterResource(if (playingLocal && SongPlayer.isPlaying()) R.drawable.ic_playing else R.drawable.play_svgrepo_com),
                                    stringResource(R.string.content_description_play_button), tint = Color.Black, modifier = Modifier.size(26.dp))
                            }
                        }
                    }
                }
                item(key = "find") {
                    com.adlen.sonora.app.ui.components.FindInListField(find, { find = it }, stringResource(R.string.local_files_find_hint),
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                val shown = tracks.orEmpty().filter { com.adlen.sonora.app.ui.components.matchesFind(find, it.title, it.artist, it.album) }
                if (shown.isEmpty() && find.isNotBlank()) item(key = "find-empty") {
                    com.adlen.sonora.app.ui.components.FindInListEmpty(stringResource(R.string.assisted_curation_search_empty_view_no_result_title, find.trim()))
                }
                itemsIndexed(shown, key = { _, t -> t.id }) { _, t ->
                    val current = playingLocal && np.uri == "local:${t.id}"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        // The whole folder plays from this song, even when the list is filtered.
                        modifier = Modifier.fillMaxWidth().pressable(0.97f) { tracks?.let { all -> LocalPlayer.play(all, all.indexOf(t).coerceAtLeast(0)) } }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        GlideImage(
                            transition = CrossFade,
                            model = t.artUri, contentDescription = null, contentScale = ContentScale.Crop,
                            loading = placeholder(R.drawable.placeholder), failure = placeholder(R.drawable.local_files_cover),
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
                        )
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(t.title, color = if (current) com.adlen.sonora.app.ui.theme.AppAccent else AppText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (t.artist.isNotBlank()) Text(t.artist, color = AppMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        uploads[LocalUploadIndex.key(t)]?.let { entry ->
                            Text(
                                text = uploadStateLabel(entry),
                                color = if (entry.state == UploadState.UPLOADED) AppAccent else AppMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                        Icon(painterResource(R.drawable.ic_add), stringResource(R.string.context_menu_add_to_playlist), tint = AppText,
                            modifier = Modifier.size(24.dp).pressable(0.92f) { adding = t })
                    }
                }
                item { Spacer(Modifier.height(com.adlen.sonora.app.ui.components.bottomBarsSpace())) }
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.statusBarsPadding().padding(12.dp).size(40.dp).clip(CircleShape)
                .background(AppBackground.copy(alpha = 0.4f)).pressable { navController.navigateUp() },
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_navigate_back), tint = AppText)
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, button: String?, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
    ) {
        Image(painterResource(R.drawable.local_files_cover), null, modifier = Modifier.size(120.dp).clip(RoundedCornerShape(6.dp)))
        Text(title, color = AppText, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp))
        Text(subtitle, color = AppMuted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        if (button != null) Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(top = 24.dp).height(48.dp).pressable(0.96f, onClick = onClick)
                .clip(RoundedCornerShape(50)).background(AppText).padding(horizontal = 28.dp),
        ) {
            Text(button, color = com.adlen.sonora.app.ui.theme.AppBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}


/** The phone's music folders, each shown in Local Files or left out (WhatsApp audio…). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun LocalFoldersSheet(onChanged: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var folders by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var version by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { folders = LocalMusic.folders(context) }
    com.adlen.sonora.app.ui.components.LookSheet(onDismissRequest = onDismiss, containerColor = tone(0xFF282828)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.local_folders_title), color = AppText, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp))
            Text(stringResource(R.string.local_folders_detail), color = AppMuted, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            LazyColumn {
                items(folders.size) { i ->
                    val (folder, count) = folders[i]
                    @Suppress("UNUSED_EXPRESSION") version
                    val shown = !com.adlen.sonora.app.data.local.LocalFolders.isExcluded(context, folder)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            val name = folder.trimEnd('/').substringAfterLast('/').ifBlank { folder.ifBlank { "/" } }
                            Text(name, color = AppText, fontSize = 15.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("$folder · ${pluralStringResource(R.plurals.local_files_count, count, count)}", color = Color(0xFF9A9A9A),
                                fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        androidx.compose.material3.Switch(
                            checked = shown,
                            onCheckedChange = {
                                com.adlen.sonora.app.data.local.LocalFolders.set(context, folder, it)
                                version++
                                onChanged()
                            },
                            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = AppAccent),
                        )
                    }
                }
            }
        }
    }
}
