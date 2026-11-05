package com.myspoti.auto

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.support.v4.media.MediaBrowserCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.myspoti.host.android.PlaybackService
import app.myspoti.subsonic.SubsonicConfig
import app.myspoti.subsonic.SubsonicSettings
import com.music.spotui.di.AutoBrowser
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Android Auto's contract, on a device: the media browser service the car binds to, and the
 * folders it is given (Home, Library, Downloads) — built from the Subsonic server, not from a
 * Spotify page.
 *
 * Runs only when the test Navidrome answers at 10.0.2.2:4533 (the emulator's host). Start it with
 * `navidrome` on the host, then `./gradlew :app:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class AutoBrowserTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var previous: SubsonicConfig? = null

    private val service = ComponentName(context, PlaybackService::class.java)

    @Before
    fun useTestServer() {
        assumeTrue("the test Navidrome must answer on 10.0.2.2:4533", serverAnswers())
        previous = SubsonicSettings.load(context)
        val config = SubsonicConfig("http://10.0.2.2:4533/", "admin", "admin")
        SubsonicSettings.save(context, config)
        app.myspoti.host.android.MyspotiRuntime.updateSubsonicConfig(config)
    }

    @After
    fun restoreServer() {
        val old = previous ?: return
        SubsonicSettings.save(context, old)
        app.myspoti.host.android.MyspotiRuntime.updateSubsonicConfig(old)
    }

    @Test
    fun theCarFindsTheMediaBrowserService() {
        val intent = Intent("android.media.browse.MediaBrowserService").setPackage(context.packageName)
        val found = context.packageManager.queryIntentServices(intent, PackageManager.GET_META_DATA)
        val info = found.map { it.serviceInfo }.firstOrNull { it.name == PlaybackService::class.java.name }
        assertTrue("PlaybackService must answer the MediaBrowserService action", info != null)
        assertTrue("Android Auto can only bind an exported service", info!!.exported)
    }

    @Test
    fun theRootOffersHomeLibraryAndDownloads() {
        withBrowser { browser ->
            assertEquals(AutoBrowser.ROOT, browser.root)
            val ids = children(browser, AutoBrowser.ROOT).map { it.mediaId }.toSet()
            assertEquals(setOf("home", "library", "downloads"), ids)
        }
    }

    @Test
    fun theLibraryListsTheSubsonicCollections() {
        withBrowser { browser ->
            val ids = children(browser, "library").map { it.mediaId }.toSet()
            assertEquals(setOf("liked", "playlists", "albums", "artists"), ids)
        }
    }

    @Test
    fun anAlbumBrowseGivesPlayableSongs() = runBlocking {
        val albums = AutoBrowser.children(context, "albums")
        assertTrue("the server has albums", albums.isNotEmpty())
        assertTrue("albums are browsable folders", albums.all { it.isBrowsable && it.mediaId.orEmpty().startsWith("al:") })

        val songs = AutoBrowser.children(context, albums.first().mediaId.orEmpty())
        assertTrue("the album has songs", songs.isNotEmpty())
        assertTrue("the list opens on a radio entry", songs.first().mediaId.orEmpty().startsWith("radio|"))
        val tracks = songs.filter { it.mediaId.orEmpty().startsWith("t|") }
        assertTrue("the album lists its songs", tracks.isNotEmpty())
        assertTrue("each song can be played", tracks.all { it.isPlayable })
    }

    @Test
    fun searchAnswersTheServer() = runBlocking {
        val results = AutoBrowser.search("mira")
        assertTrue("the search finds something", results.isNotEmpty())
        assertTrue("results are songs, albums or artists", results.all { !it.mediaId.isNullOrBlank() })
    }

    // ---------------------------------------------------------------- helpers

    private fun withBrowser(block: (MediaBrowserCompat) -> Unit) {
        val latch = CountDownLatch(1)
        var failed = false
        lateinit var browser: MediaBrowserCompat
        // MediaBrowserCompat builds a Handler: it must be created on a looper thread (the main one).
        instrumentation.runOnMainSync {
            browser = MediaBrowserCompat(context, service, object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() = latch.countDown()
                override fun onConnectionFailed() {
                    failed = true
                    latch.countDown()
                }

                override fun onConnectionSuspended() = latch.countDown()
            }, null)
            browser.connect()
        }
        try {
            assertTrue("the media browser connects", latch.await(15, TimeUnit.SECONDS))
            assertFalse("the media browser connects", failed)
            block(browser)
        } finally {
            instrumentation.runOnMainSync { if (browser.isConnected) browser.disconnect() }
        }
    }

    /** Subscribes to a folder and waits for the car's answer (the service loads it off the main thread). */
    private fun children(browser: MediaBrowserCompat, parentId: String): List<MediaBrowserCompat.MediaItem> {
        val latch = CountDownLatch(1)
        var items: List<MediaBrowserCompat.MediaItem> = emptyList()
        var error: String? = null
        val callback = object : MediaBrowserCompat.SubscriptionCallback() {
            override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
                items = children
                latch.countDown()
            }

            override fun onError(parentId: String) {
                error = "error for $parentId"
                latch.countDown()
            }
        }
        instrumentation.runOnMainSync { browser.subscribe(parentId, callback) }
        assertTrue("the folder $parentId answers", latch.await(30, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { runCatching { browser.unsubscribe(parentId) } }
        assertEquals(null, error)
        return items
    }

    private fun serverAnswers(): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("10.0.2.2", 4533), 2_000) }
    }.isSuccess
}
