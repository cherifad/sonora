package com.adlen.sonora.app.di

import android.app.Application
import com.adlen.sonora.host.android.SonoraRuntime
import com.adlen.sonora.subsonic.SubsonicConfig
import com.adlen.sonora.subsonic.SubsonicSettings
import com.adlen.sonora.app.data.api.Api
import com.adlen.sonora.app.ui.repository.AppRepository

/** Manual replacement for Spotui's Hilt graph: app-wide singletons. */
object Graph {
    lateinit var app: Application
        private set

    fun init(application: Application) {
        app = application
        // The data saver first: on a metered network with it on, nothing may go out, not even the page.
        com.adlen.sonora.app.data.DataSaver.init(application)
        // The Subsonic backend (porting spike): no Web Player, no Spotify session.
        // The setup screen replaces the placeholder on first run.
        val subsonic = SubsonicSettings.load(application)
        SonoraRuntime.startSubsonic(
            application,
            subsonic ?: SubsonicConfig("http://127.0.0.1/", "", ""),
        )
        // The streaming quality the user chose, set on the player at every page load.
        com.adlen.sonora.app.data.preferences.applyStreamingQuality(application)
        // The crossfade length (0 = off), in the player from the first song on.
        com.adlen.sonora.app.data.preferences.applyCrossfade(com.adlen.sonora.app.data.preferences.getCrossfadeSeconds(application))
        com.adlen.sonora.app.data.local.ListenHistory.init(application)
        // Downloaded songs (private storage), and the downloads left unfinished last time.
        // Supporter thank-yous (accent colour) before any screen draws.
        com.adlen.sonora.app.data.api.SupportReminder.init(application)
        com.adlen.sonora.app.data.offline.OfflineStore.init(application)
        com.adlen.sonora.app.data.offline.OfflineMode.init(application)
        com.adlen.sonora.app.data.offline.OfflineCollections.init(application)
        SongPlayer.startLibraryOutbox(application)
        com.adlen.sonora.app.data.offline.OfflineCollections.refreshAll(application)
        com.adlen.sonora.app.data.local.LocalPlaylists.init(application)
        com.adlen.sonora.app.data.local.LibraryOrder.init(application)
        runCatching { com.adlen.sonora.app.data.DataSaverGlide.install(application) }
        com.adlen.sonora.app.data.offline.OfflineDownloader.resumePending()
        // Episodes have their own queue and their own file: resumed the same way.
        com.adlen.sonora.app.data.offline.PodcastDownloader.resumePending()
        // Installed extensions: each enabled one loads in its own sandbox, off the main thread.
        com.adlen.sonora.app.extensions.Extensions.init(application)
    }

    val api: Api by lazy { Api(app) }
    val repository: AppRepository by lazy { AppRepository(api) }
    val currentSongState: CurrentSongState by lazy { CurrentSongState() }
}
