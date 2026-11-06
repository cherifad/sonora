package com.adlen.sonora.app.ui.immersive.pages

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.adlen.sonora.engine.R
import com.adlen.sonora.app.data.preferences.DownloadQuality
import com.adlen.sonora.app.data.preferences.StreamingQuality
import com.adlen.sonora.app.data.preferences.getDownloadQuality
import com.adlen.sonora.app.data.preferences.getStreamingQuality
import com.adlen.sonora.app.data.preferences.setDownloadQuality
import com.adlen.sonora.app.data.preferences.setStreamingQuality
import com.adlen.sonora.app.ui.immersive.Immersive
import com.adlen.sonora.app.ui.look.rememberReduceMotion
import com.adlen.sonora.app.ui.navigation.Routes
import com.adlen.sonora.app.ui.screens.installedRoute
import com.adlen.sonora.app.ui.screens.shareDiagnosticReport
import kotlinx.coroutines.launch

/** Where the Immersive Settings are: the list of categories, one category, or a choice inside it. */
private enum class SettingsPage { Root, Playback, StreamingWifi, StreamingCell, Crossfade, Downloads, DownloadQuality, Data, Display, Look, Rotation, Language, App }

private val StreamingChoices = listOf(
    Triple(StreamingQuality.AUTO, R.string.quality_streaming_auto, R.string.quality_streaming_auto_detail),
    Triple(StreamingQuality.LOW, R.string.quality_streaming_low, R.string.quality_streaming_low_detail),
    Triple(StreamingQuality.NORMAL, R.string.quality_streaming_normal, R.string.quality_streaming_normal_detail),
    Triple(StreamingQuality.HIGH, R.string.quality_streaming_high, R.string.quality_streaming_high_detail),
)

private val DownloadChoices = listOf(
    Triple(DownloadQuality.OFF, R.string.quality_download_off, R.string.quality_download_off_detail),
    Triple(DownloadQuality.SMALL, R.string.quality_download_small, R.string.quality_download_small_detail),
    Triple(DownloadQuality.CD, R.string.quality_download_cd, R.string.quality_download_cd_detail),
    Triple(DownloadQuality.HI_RES, R.string.quality_download_hires, R.string.quality_download_hires_detail),
    Triple(DownloadQuality.ASK, R.string.quality_download_ask, R.string.quality_download_ask_detail),
)

/**
 * Settings of the Immersive look, replacing the Classic `SettingsScreen`: the same preferences
 * and actions (every switch writes through the same stores) as iOS-style inset groups, one page
 * per category and one for each choice (quality, language, look...). A pushed page slides in on a
 * spring and back leaves it; with "reduce motion" they cross-fade.
 */
@Composable
fun ImmersiveSettingsScreen(navController: NavController) {
    // The pages pushed so far, so back pops one before it leaves Settings.
    // Saved by name, so a recreation (a language change, say) keeps the page the user is on.
    val stack = rememberSaveable(
        saver = listSaver(
            save = { list -> list.map { it.name } },
            restore = { names -> names.map { SettingsPage.valueOf(it) }.toMutableStateList() },
        ),
    ) { mutableStateListOf<SettingsPage>() }
    val page = stack.lastOrNull() ?: SettingsPage.Root
    val reduce = rememberReduceMotion()
    var pushing by remember { mutableStateOf(true) }
    val pop: () -> Unit = { pushing = false; stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.isNotEmpty(), onBack = pop)
    val open: (SettingsPage) -> Unit = { pushing = true; stack += it }
    val back: () -> Unit = { if (stack.isNotEmpty()) pop() else navController.popBackStack() }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            when {
                reduce -> fadeIn(tween(com.adlen.sonora.app.ui.immersive.motion.ImmersiveMotion.FADE_MS)) togetherWith fadeOut(tween(com.adlen.sonora.app.ui.immersive.motion.ImmersiveMotion.FADE_MS))
                !pushing ->
                    (slideInHorizontally(tween(com.adlen.sonora.app.ui.immersive.motion.ImmersiveMotion.MEDIUM_MS)) { -it / 3 } + fadeIn(tween(com.adlen.sonora.app.ui.immersive.motion.ImmersiveMotion.MEDIUM_MS))) togetherWith
                        (slideOutHorizontally(tween(com.adlen.sonora.app.ui.immersive.motion.ImmersiveMotion.MEDIUM_MS)) { it } + fadeOut(tween(160)))
                else ->
                    (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1))) { it } + fadeIn(tween(160))) togetherWith
                        (slideOutHorizontally(tween(com.adlen.sonora.app.ui.immersive.motion.ImmersiveMotion.MEDIUM_MS)) { -it / 3 } + fadeOut(tween(com.adlen.sonora.app.ui.immersive.motion.ImmersiveMotion.MEDIUM_MS)))
            }
        },
        label = "settings pages",
    ) { p ->
        when (p) {
            SettingsPage.Root -> SettingsRoot(navController, open, back)
            SettingsPage.Playback -> PlaybackPage(navController, open, back)
            SettingsPage.StreamingWifi -> StreamingPage(cellular = false, back)
            SettingsPage.StreamingCell -> StreamingPage(cellular = true, back)
            SettingsPage.Crossfade -> CrossfadePage(back)
            SettingsPage.Downloads -> DownloadsPage(navController, open, back)
            SettingsPage.DownloadQuality -> DownloadQualityPage(back)
            SettingsPage.Data -> DataPage(back)
            SettingsPage.Display -> DisplayPage(open, back)
            SettingsPage.Look -> LookPage(back)
            SettingsPage.Rotation -> RotationPage(back)
            SettingsPage.Language -> LanguagePage(back)
            SettingsPage.App -> AppPage(navController, back)
        }
    }
}

@Composable
private fun SettingsRoot(navController: NavController, open: (SettingsPage) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val config = com.adlen.sonora.subsonic.SubsonicSettings.load(context)
    val scope = rememberCoroutineScope()
    var showServer by remember { mutableStateOf(false) }
    if (showServer) {
        com.adlen.sonora.app.ui.setup.SubsonicSetupDialog(
            onDismiss = { showServer = false },
            onSaved = {
                showServer = false
                (context as? android.app.Activity)?.recreate()
            },
        )
    }
    ImmersivePage(title = stringResource(R.string.settings_title), onBack = back) {
        immersiveGroup("root") {
            row {
                ImmersiveGroupRow(
                    "Server",
                    detail = config?.baseUrl ?: "No server configured",
                    icon = Icons.Rounded.Cloud,
                    chevron = true,
                    onClick = { showServer = true },
                )
            }
            if (com.adlen.sonora.host.android.SonoraRuntime.subsonicApi
                    ?.supports(com.adlen.sonora.subsonic.ServerCapabilities.Feature.SCAN) == true) {
                row {
                    ImmersiveGroupRow(
                        stringResource(R.string.scan_title),
                        detail = stringResource(R.string.scan_now),
                        icon = Icons.Rounded.Refresh,
                        chevron = true,
                        onClick = {
                            scope.launch {
                                runCatching {
                                    com.adlen.sonora.host.android.SonoraRuntime.subsonicApi?.serverExtras?.startScan()
                                }
                            }
                        },
                    )
                }
            }
            row { ImmersiveGroupRow(stringResource(R.string.settings_section_playback), icon = Icons.Rounded.PlayArrow, chevron = true, onClick = { open(SettingsPage.Playback) }) }
            row { ImmersiveGroupRow(stringResource(R.string.settings_section_downloads), icon = Icons.Rounded.Download, chevron = true, onClick = { open(SettingsPage.Downloads) }) }
            row { ImmersiveGroupRow(stringResource(R.string.settings_section_data), icon = Icons.Rounded.SignalCellularAlt, chevron = true, onClick = { open(SettingsPage.Data) }) }
            row { ImmersiveGroupRow(stringResource(R.string.settings_section_display), icon = Icons.Rounded.Palette, chevron = true, onClick = { open(SettingsPage.Display) }) }
            row { ImmersiveGroupRow(stringResource(R.string.settings_section_app), icon = Icons.Rounded.Info, chevron = true, onClick = { open(SettingsPage.App) }) }
        }
    }
}

// ---------------------------------------------------------------- Playback

private val ImmersiveSourceLook = com.adlen.sonora.app.ui.screens.SourceLook(
    Immersive.Text, Immersive.TextSecondary, Immersive.Accent, Immersive.BodyStrong, Immersive.Caption)

/** The order a source action asks extensions in, as a card ([com.adlen.sonora.app.ui.screens.SourceOrderList]); nothing without any such extension. */
private fun androidx.compose.foundation.lazy.LazyListScope.sourcesGroup(action: com.adlen.sonora.extensions.Action, navController: NavController) {
    if (com.adlen.sonora.app.extensions.Extensions.sources(action).isEmpty()) return
    val audio = action == com.adlen.sonora.extensions.Action.AUDIO_SOURCE
    immersiveGroup("sources:${action.key}",
        headerRes = if (audio) R.string.sources_audio_title else R.string.sources_download_title,
        footerRes = if (audio) R.string.sources_audio_footer else R.string.sources_download_footer) {
        row {
            com.adlen.sonora.app.ui.screens.SourceOrderList(action, spotifyLast = audio, look = ImmersiveSourceLook,
                onOpen = { navController.navigate(com.adlen.sonora.app.ui.screens.installedRoute(it)) },
                rowPadding = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun PlaybackPage(navController: NavController, open: (SettingsPage) -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val config = com.adlen.sonora.subsonic.SubsonicSettings.load(context)
    var showServer by remember { mutableStateOf(false) }
    if (showServer) {
        com.adlen.sonora.app.ui.setup.SubsonicSetupDialog(
            onDismiss = { showServer = false },
            onSaved = {
                showServer = false
                (context as? android.app.Activity)?.recreate()
            },
        )
    }
    ImmersivePage(title = stringResource(R.string.settings_section_playback), onBack = back) {
        immersiveGroup("server") {
            row {
                ImmersiveGroupRow(
                    "Server",
                    detail = config?.baseUrl ?: "No server configured",
                    icon = Icons.Rounded.Cloud,
                    chevron = true,
                    onClick = { showServer = true },
                )
            }
        }
    }
}

@Composable
private fun StreamingPage(cellular: Boolean, back: () -> Unit) {
    val ctx = LocalContext.current
    var current by remember(cellular) { mutableStateOf(getStreamingQuality(ctx, cellular)) }
    ImmersivePage(
        title = stringResource(if (cellular) R.string.quality_streaming_cell_title else R.string.quality_streaming_wifi_title),
        onBack = back,
    ) {
        immersiveGroup("choices", footerRes = R.string.quality_streaming_detail) {
            StreamingChoices.forEach { (value, label, detail) ->
                row {
                    ImmersiveChoiceRow(stringResource(label), selected = current == value, detail = stringResource(detail),
                        onClick = { current = value; setStreamingQuality(ctx, value, cellular) })
                }
            }
        }
    }
}

@Composable
private fun CrossfadePage(back: () -> Unit) {
    val ctx = LocalContext.current
    val pref = com.adlen.sonora.app.data.preferences.CrossfadePref
    ImmersivePage(title = stringResource(R.string.settings_crossfade_title), onBack = back) {
        immersiveGroup("choices", footerRes = R.string.settings_crossfade_detail) {
            for (v in listOf(0, 3, 6, 9, 12)) {
                row {
                    ImmersiveChoiceRow(
                        if (v == 0) stringResource(R.string.settings_crossfade_off) else stringResource(R.string.settings_crossfade_seconds, v),
                        selected = pref.seconds == v, onClick = { pref.set(ctx, v) },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Downloads

@Composable
private fun DownloadsPage(navController: NavController, open: (SettingsPage) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val quality = getDownloadQuality(ctx)
    var ask by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableStateOf(com.adlen.sonora.app.data.offline.StorageInfo.cacheBytes(ctx)) }
    var wifiOnly by remember { mutableStateOf(com.adlen.sonora.app.data.preferences.isWifiOnly(ctx)) }
    var maxBytes by remember { mutableStateOf(com.adlen.sonora.app.data.preferences.maxDownloadBytes(ctx)) }
    val size = android.text.format.Formatter.formatShortFileSize(ctx, com.adlen.sonora.app.data.offline.OfflineStore.totalBytes())
    val count = com.adlen.sonora.app.data.offline.OfflineStore.tracks.size
    val cache = android.text.format.Formatter.formatShortFileSize(ctx, cacheBytes)
    val free = android.text.format.Formatter.formatShortFileSize(ctx, com.adlen.sonora.app.data.offline.StorageInfo.freeBytes(ctx))
    // The first extension of the download order, when it announces settings here; its role quality
    // takes the place of the app's (kept, and still passed to every extension as `quality`).
    val registry by com.adlen.sonora.app.extensions.Extensions.registry.collectAsStateWithLifecycle()
    val subsonic = com.adlen.sonora.host.android.SonoraRuntime.isSubsonic
    val ext = remember(registry) { if (subsonic) null else com.adlen.sonora.app.extensions.Extensions.sectionPlacement(com.adlen.sonora.extensions.SettingSection.DOWNLOADS) }
    val extHeader = ext?.let { stringResource(R.string.ext_section_settings, it.name) }
    ImmersivePage(title = stringResource(R.string.settings_section_downloads), onBack = back) {
        if (ext?.hidesAppQuality != true) immersiveGroup("quality", footerRes = R.string.quality_download_detail) {
            row {
                ImmersiveGroupRow(stringResource(R.string.quality_download_title),
                    value = stringResource(DownloadChoices.first { it.first == quality }.second), chevron = true,
                    onClick = { open(SettingsPage.DownloadQuality) })
            }
        }
        if (ext != null && extHeader != null) extensionSectionGroup(ext, extHeader)
        // Where downloads come from: the download-source extensions, in the order they are asked.
        // Subsonic serves its own files, so there is no source to choose.
        if (!subsonic) {
            sourcesGroup(com.adlen.sonora.extensions.Action.DOWNLOAD_SOURCE, navController)
            if (com.adlen.sonora.app.extensions.Extensions.sources(com.adlen.sonora.extensions.Action.DOWNLOAD_SOURCE).isEmpty()) immersiveGroup("source") {
                row {
                    ImmersiveGroupRow(
                        stringResource(R.string.downloads_source_row_title),
                        detail = stringResource(
                            if (com.adlen.sonora.app.extensions.Extensions.hasDownloadSource()) R.string.downloads_source_row_on
                            else R.string.downloads_source_row_off,
                        ),
                        chevron = true, onClick = { navController.navigate(Routes.Extensions.route) },
                    )
                }
            }
        }
        immersiveGroup("storage") {
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.settings_storage_downloads_title),
                    detail = stringResource(R.string.settings_storage_downloads_detail, count, size),
                )
            }
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.download_wifi_only),
                    checked = wifiOnly,
                    onChecked = {
                        wifiOnly = it
                        com.adlen.sonora.app.data.preferences.setWifiOnly(ctx, it)
                    },
                    detail = stringResource(R.string.download_wifi_only_detail),
                )
            }
            row {
                val limits = listOf(2L, 8L, 32L, 0L)
                val limitText = if (maxBytes <= 0) stringResource(R.string.download_storage_limit_unlimited)
                    else android.text.format.Formatter.formatShortFileSize(ctx, maxBytes)
                ImmersiveGroupRow(
                    stringResource(R.string.download_storage_limit),
                    detail = limitText,
                    onClick = {
                        val next = limits[(limits.indexOf(maxBytes).takeIf { it >= 0 }?.plus(1) ?: 0) % limits.size]
                        maxBytes = next
                        com.adlen.sonora.app.data.preferences.setMaxDownloadBytes(ctx, next)
                    },
                )
            }
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.settings_storage_cache_clear), detail = cache,
                    onClick = {
                        com.adlen.sonora.app.data.offline.StorageInfo.clearCache(ctx) { left ->
                            cacheBytes = left
                            android.widget.Toast.makeText(ctx, ctx.getString(R.string.settings_storage_cache_cleared), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
            row {
                ImmersiveGroupRow(stringResource(R.string.settings_storage_free_title), detail = free)
            }
        }
        if (com.adlen.sonora.app.data.offline.OfflineStore.tracks.isNotEmpty()) immersiveGroup("remove") {
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.settings_item_remove_downloads_title), detail = stringResource(R.string.downloads_size, size),
                    destructive = true, onClick = { ask = true },
                )
            }
        }
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        containerColor = Immersive.Raised,
        title = { Text(stringResource(R.string.settings_storage_remove_downloads_dialog_title), style = Immersive.Headline) },
        text = { Text(stringResource(R.string.playlist_entity_remove_download_dialog_description), style = Immersive.Subhead) },
        confirmButton = {
            TextButton(onClick = { ask = false; com.adlen.sonora.app.data.offline.OfflineDownloader.removeAll() }) {
                Text(stringResource(R.string.playlist_entity_remove_download_dialog_positive_button), color = Immersive.Destructive)
            }
        },
        dismissButton = {
            TextButton(onClick = { ask = false }) { Text(stringResource(R.string.add_to_playlist_button_cancel), color = Immersive.Accent) }
        },
    )
}

@Composable
private fun DownloadQualityPage(back: () -> Unit) {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf(getDownloadQuality(ctx)) }
    ImmersivePage(title = stringResource(R.string.quality_download_title), onBack = back) {
        immersiveGroup("choices", footerRes = R.string.quality_download_detail) {
            DownloadChoices.forEach { (value, label, detail) ->
                row {
                    ImmersiveChoiceRow(stringResource(label), selected = current == value, detail = stringResource(detail),
                        onClick = { current = value; setDownloadQuality(ctx, value) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Data and battery

@Composable
private fun DataPage(back: () -> Unit) {
    val ctx = LocalContext.current
    val energy by com.adlen.sonora.power.EnergySettings.state.collectAsStateWithLifecycle()
    // Battery optimization off = music and Connect keep going with the screen off.
    var exempt by remember { mutableStateOf(com.adlen.sonora.power.BatteryExemption.isExempt(ctx)) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        exempt = com.adlen.sonora.power.BatteryExemption.isExempt(ctx)
    }
    ImmersivePage(title = stringResource(R.string.settings_section_data), onBack = back) {
        immersiveGroup("data") {
            row {
                ImmersiveSwitchRow(stringResource(R.string.energy_saver_title), checked = energy.manualSaver,
                    onChecked = com.adlen.sonora.power.EnergySettings::setSaver, detail = stringResource(R.string.energy_saver_summary))
            }
            row {
                ImmersiveSwitchRow(stringResource(R.string.data_saver_title), checked = com.adlen.sonora.app.data.DataSaver.enabled,
                    onChecked = com.adlen.sonora.app.data.DataSaver::set, detail = stringResource(R.string.data_saver_summary))
            }
            row {
                ImmersiveSwitchRow(
                    stringResource(R.string.background_playback_title), checked = exempt,
                    onChecked = {
                        if (exempt) ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        else com.adlen.sonora.power.BatteryExemption.request(ctx)
                    },
                    detail = stringResource(if (exempt) R.string.background_playback_on else R.string.background_playback_off),
                )
            }
            // Samsung's "Sleeping apps" is a second list: the app has to be in Never sleeping apps too.
            if (com.adlen.sonora.power.BatteryExemption.isSamsung()) row {
                ImmersiveGroupRow(
                    stringResource(R.string.background_playback_open), detail = stringResource(R.string.background_playback_samsung),
                    chevron = true, onClick = { com.adlen.sonora.power.BatteryExemption.openAppSettings(ctx) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Display

@Composable
private fun DisplayPage(open: (SettingsPage) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val energy by com.adlen.sonora.power.EnergySettings.state.collectAsStateWithLifecycle()
    val look = com.adlen.sonora.app.data.preferences.LookPref.look
    val rotation = com.adlen.sonora.app.data.preferences.OrientationPref.mode
    val language = remember { com.adlen.sonora.app.data.AppLanguage.current(ctx) }
    val auto = stringResource(R.string.language_auto)
    ImmersivePage(title = stringResource(R.string.settings_section_display), onBack = back) {
        immersiveGroup("look") {
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.settings_look_title),
                    value = stringResource(if (look == com.adlen.sonora.app.data.preferences.Look.CLASSIC) R.string.look_classic else R.string.look_immersive),
                    chevron = true, onClick = { open(SettingsPage.Look) },
                )
            }
            row { AccentRow(com.adlen.sonora.app.data.preferences.Look.IMMERSIVE) }
        }
        immersiveGroup("display") {
            if (look == com.adlen.sonora.app.data.preferences.Look.IMMERSIVE) row {
                val glass = com.adlen.sonora.app.ui.immersive.shell.liquidGlassAvailability()
                val unsupported = glass == com.adlen.sonora.app.ui.immersive.shell.LiquidGlassAvailability.UNSUPPORTED
                ImmersiveSwitchRow(
                    stringResource(R.string.settings_liquid_glass_title),
                    checked = com.adlen.sonora.app.data.preferences.LiquidGlassPref.on && !unsupported,
                    enabled = !unsupported,
                    onChecked = { com.adlen.sonora.app.data.preferences.LiquidGlassPref.set(ctx, it) },
                    detail = stringResource(
                        when (glass) {
                            com.adlen.sonora.app.ui.immersive.shell.LiquidGlassAvailability.UNSUPPORTED -> R.string.settings_liquid_glass_unsupported
                            com.adlen.sonora.app.ui.immersive.shell.LiquidGlassAvailability.REDUCED -> R.string.settings_liquid_glass_reduced
                            com.adlen.sonora.app.ui.immersive.shell.LiquidGlassAvailability.AVAILABLE -> R.string.settings_liquid_glass_detail
                        },
                    ),
                )
            }
            row {
                ImmersiveSwitchRow(stringResource(R.string.settings_amoled_theme), checked = energy.blackPreference,
                    onChecked = com.adlen.sonora.power.EnergySettings::setBlackBackground, detail = stringResource(R.string.settings_amoled_theme_subtitle))
            }
            if (look == com.adlen.sonora.app.data.preferences.Look.CLASSIC) {
                row {
                    ImmersiveSwitchRow(stringResource(R.string.settings_player_lyrics_card_title), checked = com.adlen.sonora.app.data.preferences.PlayerLyricsCardPref.on,
                        onChecked = { com.adlen.sonora.app.data.preferences.PlayerLyricsCardPref.set(ctx, it) }, detail = stringResource(R.string.settings_player_lyrics_card_detail))
                }
            }
            if (look == com.adlen.sonora.app.data.preferences.Look.IMMERSIVE) {
                row {
                    ImmersiveSwitchRow(stringResource(R.string.settings_player_capsule_title), checked = com.adlen.sonora.app.data.preferences.PlayerCapsulePref.on,
                        onChecked = { com.adlen.sonora.app.data.preferences.PlayerCapsulePref.set(ctx, it) }, detail = stringResource(R.string.settings_player_capsule_detail))
                }
                row {
                    ImmersiveSwitchRow(stringResource(R.string.settings_player_modes_title), checked = com.adlen.sonora.app.data.preferences.PlayerModesPref.on,
                        onChecked = { com.adlen.sonora.app.data.preferences.PlayerModesPref.set(ctx, it) }, detail = stringResource(R.string.settings_player_modes_detail))
                }
            }
        }
        immersiveGroup("layout") {
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.settings_rotation_title),
                    value = stringResource(rotationLabel(rotation)), chevron = true, onClick = { open(SettingsPage.Rotation) },
                )
            }
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.settings_item_app_language_title),
                    value = if (language == com.adlen.sonora.app.data.AppLanguage.SYSTEM) auto else com.adlen.sonora.app.data.AppLanguage.nativeName(language),
                    chevron = true, onClick = { open(SettingsPage.Language) },
                )
            }
        }
    }
}

private fun rotationLabel(mode: String): Int = when (mode) {
    com.adlen.sonora.app.data.preferences.OrientationPref.AUTO -> R.string.settings_rotation_auto
    com.adlen.sonora.app.data.preferences.OrientationPref.LANDSCAPE -> R.string.settings_rotation_landscape
    else -> R.string.settings_rotation_portrait
}

/** The accent colour of one look, for everyone (the same palette the supporter sheet uses). */
@Composable
private fun AccentRow(look: com.adlen.sonora.app.data.preferences.Look) {
    val ctx = LocalContext.current
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.supporter_accent), style = Immersive.Body)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 10.dp)) {
            for (a in com.adlen.sonora.app.data.api.SupportReminder.Accent.entries) {
                val selected = com.adlen.sonora.app.data.api.SupportReminder.accentOf(look) == a
                val name = stringResource(when (a) {
                    com.adlen.sonora.app.data.api.SupportReminder.Accent.GREEN -> R.string.color_green
                    com.adlen.sonora.app.data.api.SupportReminder.Accent.BLUE -> R.string.color_blue
                    com.adlen.sonora.app.data.api.SupportReminder.Accent.PURPLE -> R.string.color_purple
                    com.adlen.sonora.app.data.api.SupportReminder.Accent.PINK -> R.string.color_pink
                    com.adlen.sonora.app.data.api.SupportReminder.Accent.ORANGE -> R.string.color_orange
                })
                Box(
                    Modifier.size(38.dp)
                        .semantics { contentDescription = name; role = Role.RadioButton }
                        .clip(CircleShape).background(a.color)
                        .then(if (selected) Modifier.border(3.dp, Immersive.Text, CircleShape) else Modifier)
                        .clickable { com.adlen.sonora.app.data.api.SupportReminder.setAccent(ctx, look, a) },
                )
            }
        }
    }
}

@Composable
private fun LookPage(back: () -> Unit) {
    val ctx = LocalContext.current
    val current = com.adlen.sonora.app.data.preferences.LookPref.look
    ImmersivePage(title = stringResource(R.string.settings_look_title), onBack = back) {
        immersiveGroup("choices") {
            for (l in com.adlen.sonora.app.data.preferences.Look.entries) {
                val classic = l == com.adlen.sonora.app.data.preferences.Look.CLASSIC
                row {
                    ImmersiveChoiceRow(
                        stringResource(if (classic) R.string.look_classic else R.string.look_immersive),
                        selected = current == l,
                        detail = stringResource(if (classic) R.string.look_classic_detail else R.string.look_immersive_detail),
                        onClick = { com.adlen.sonora.app.data.preferences.LookPref.set(ctx, l) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RotationPage(back: () -> Unit) {
    val ctx = LocalContext.current
    val pref = com.adlen.sonora.app.data.preferences.OrientationPref
    ImmersivePage(title = stringResource(R.string.settings_rotation_title), onBack = back) {
        immersiveGroup("choices") {
            for ((value, label) in listOf(
                pref.PORTRAIT to R.string.settings_rotation_portrait,
                pref.AUTO to R.string.settings_rotation_auto,
                pref.LANDSCAPE to R.string.settings_rotation_landscape,
            )) {
                row { ImmersiveChoiceRow(stringResource(label), selected = pref.mode == value, onClick = { pref.set(ctx, value) }) }
            }
        }
    }
}

/** "App language": Automatic (French or English) or any language with Spotify's labels. */
@Composable
private fun LanguagePage(back: () -> Unit) {
    val ctx = LocalContext.current
    val current = remember { com.adlen.sonora.app.data.AppLanguage.current(ctx) }
    val auto = stringResource(R.string.language_auto)
    fun label(tag: String) = if (tag == com.adlen.sonora.app.data.AppLanguage.SYSTEM) auto else com.adlen.sonora.app.data.AppLanguage.nativeName(tag)
    ImmersivePage(title = stringResource(R.string.settings_item_app_language_title), onBack = back) {
        immersiveGroup("choices", footerRes = R.string.language_partial_note) {
            com.adlen.sonora.app.data.AppLanguage.AVAILABLE.forEach { tag ->
                row {
                    ImmersiveChoiceRow(
                        label(tag), selected = tag == current,
                        detail = if (tag == com.adlen.sonora.app.data.AppLanguage.SYSTEM) stringResource(R.string.language_auto_detail) else null,
                        onClick = {
                            if (tag != current) {
                                com.adlen.sonora.app.data.AppLanguage.apply(ctx, tag)
                                // Screens take the new language when the activity is rebuilt.
                                generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }
                                    .filterIsInstance<android.app.Activity>().firstOrNull()?.recreate()
                            }
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- App

@Composable
private fun AppPage(navController: NavController, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val check = com.adlen.sonora.app.data.api.rememberUpdateCheckState(ctx)
    ImmersivePage(title = stringResource(R.string.settings_section_app), onBack = back) {
        immersiveGroup("updates") {
            row {
                ImmersiveSwitchRow(stringResource(R.string.settings_auto_update_title), checked = com.adlen.sonora.app.data.preferences.AutoUpdateCheckPref.on,
                    onChecked = { com.adlen.sonora.app.data.preferences.AutoUpdateCheckPref.set(ctx, it) }, detail = stringResource(R.string.settings_auto_update_detail))
            }
            row {
                val known = com.adlen.sonora.app.updateRelease.value ?: remember { com.adlen.sonora.app.data.api.UpdateChecker.known(ctx) }
                val detail = buildString {
                    if (known != null) appendLine(ctx.getString(R.string.settings_update_available, known.version))
                    check.message?.takeIf { known == null || check.isError }?.let { appendLine(it) }
                    append(check.lastCheckedText)
                }
                ImmersiveGroupRow(
                    stringResource(R.string.settings_about_version, com.adlen.sonora.engine.BuildConfig.VERSION_NAME),
                    detail = detail,
                    detailColor = if (check.isError) Immersive.Destructive else Immersive.TextSecondary,
                    onClick = if (known != null) ({ com.adlen.sonora.app.updateRelease.value = known }) else null,
                    trailing = {
                        if (check.checking) androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Immersive.Accent)
                        else androidx.compose.material3.IconButton(onClick = { scope.launch { check.run() } }) {
                            androidx.compose.material3.Icon(Icons.Rounded.Refresh, stringResource(R.string.settings_update_refresh), tint = Immersive.Accent)
                        }
                    },
                )
            }
        }
        immersiveGroup("repair") {
            row {
                ImmersiveGroupRow(
                    stringResource(R.string.diagnostic_export_title), detail = stringResource(R.string.diagnostic_export_detail),
                    onClick = {
                        val file = runCatching { com.adlen.sonora.host.android.Logbook.export(ctx) }.getOrNull()
                        if (file != null) shareDiagnosticReport(ctx, file)
                        else android.widget.Toast.makeText(ctx, ctx.getString(R.string.diagnostic_export_failed), android.widget.Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}
