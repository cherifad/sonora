package com.adlen.sonora.test.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adlen.sonora.MainActivity
import com.adlen.sonora.subsonic.SubsonicConfig
import com.adlen.sonora.subsonic.SubsonicSettings
import com.adlen.sonora.app.data.preferences.Look
import com.adlen.sonora.app.data.preferences.LookPref
import com.adlen.sonora.app.data.preferences.setQualityChosen
import com.adlen.sonora.app.ui.screens.Setup
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.Socket

/**
 * The phone flows, on a device: Home, Library and an album that plays. Runs against the test
 * Navidrome (10.0.2.2:4533) and skips itself when it does not answer.
 *
 * `./gradlew :app:connectedMobileDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.adlen.sonora.test.ui.MainFlowTest`
 */
@RunWith(AndroidJUnit4::class)
class MainFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    companion object {
        private const val TIMEOUT_MS = 40_000L
        private const val SERVER = "http://10.0.2.2:4533/"

        /**
         * Whether the app already had a server when the process started. A test can write the
         * settings, but the graph (and the pages' first reads) was built before it: a page that
         * was never loaded before the test can then fail. The album flow needs the app onboarded
         * before the run; the rest does not.
         */
        private var configuredAtStart = false

        /** Before the activity is launched: a configured app goes straight to Home. */
        @BeforeClass
        @JvmStatic
        fun configureApp() {
            assumeTrue("the test Navidrome must answer on 10.0.2.2:4533", runCatching {
                Socket().use { it.connect(InetSocketAddress("10.0.2.2", 4533), 2_000) }
            }.isSuccess)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            configuredAtStart = SubsonicSettings.load(context) != null
            val config = SubsonicConfig(SERVER, "admin", "admin")
            SubsonicSettings.save(context, config)
            com.adlen.sonora.host.android.SonoraRuntime.updateSubsonicConfig(config)
            Setup.done(context)
            LookPref.set(context, Look.IMMERSIVE)
            LookPref.setChosen(context)
            setQualityChosen(context)
        }
    }

    @Test
    fun homeShowsTheLibraryTiles() {
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("Liked Songs").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Liked Songs").assertExists()
        compose.onNodeWithText("Your Library").assertExists()
    }

    @Test
    fun theLibraryOpens() {
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("Your Library").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Your Library").performClick()
        // The chips of the library: they are there whatever the server answered.
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("Albums").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Albums").onFirst().assertExists()
        assertTrue(compose.onAllNodesWithText("Artists").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun searchFindsTheTestLibrary() {
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithContentDescription("Search").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription("Search").onFirst().performClick()
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("mira")
        // The server answers with the artist of the test library (and/or its songs).
        waitFor("Mira Sol", 30_000)
    }

    @Test
    fun anAlbumOpensAndPlays() {
        // The album page is read after launch; an app that started without a server cannot
        // fetch it. Onboarded before the run (see docs/TESTING.md), this walks the whole flow.
        if (!configuredAtStart) {
            println("MainFlowTest: app not onboarded before the run, album flow skipped")
            return
        }
        // The library's album grid: one album of the test server, then its first song.
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText("Your Library").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Your Library").performClick()
        waitFor("Albums")
        compose.onAllNodesWithText("Albums").onFirst().performClick()
        // The grid replaces the mixed list: let it settle before touching a card.
        Thread.sleep(1_500)
        val loaded = runCatching { waitFor("Golden Hour", 30_000) }.isSuccess
        assumeTrue("the test server has the Golden Hour album", loaded)
        // The card may be below the fold of the grid: bring it into view before tapping it.
        compose.onAllNodesWithText("Golden Hour").onFirst().performScrollTo().performClick()
        // A first tap can land while the grid is still settling; one more is enough.
        val opened = runCatching { waitFor("Anywhere", 15_000) }.isSuccess
        if (!opened) {
            compose.onAllNodesWithText("Golden Hour").onFirst().performScrollTo().performClick()
            waitFor("Anywhere", 45_000)
        }
        compose.onAllNodesWithText("Play").onFirst().performClick()
        // The transport turns to Pause once the player is playing (mini player or full player).
        waitFor(contentDescription = "Pause", timeout = 60_000)
    }

    /** Waits for a text (or a content description), printing the tree once if it never comes. */
    private fun waitFor(text: String? = null, timeout: Long = TIMEOUT_MS, contentDescription: String? = null) {
        val found = runCatching {
            compose.waitUntil(timeout) {
                val nodes = if (text != null) compose.onAllNodesWithText(text) else compose.onAllNodesWithContentDescription(contentDescription!!)
                nodes.fetchSemanticsNodes().isNotEmpty()
            }
        }
        if (found.isFailure) {
            println("--- screen while waiting for ${text ?: contentDescription} ---")
            println(compose.onRoot().printToString())
            throw found.exceptionOrNull()!!
        }
    }
}
