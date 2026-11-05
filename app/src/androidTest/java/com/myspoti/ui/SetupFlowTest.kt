package com.myspoti.ui

import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.myspoti.MainActivity
import app.myspoti.host.android.MyspotiRuntime
import app.myspoti.subsonic.SubsonicConfig
import app.myspoti.subsonic.SubsonicSettings
import com.music.spotui.data.preferences.Look
import com.music.spotui.data.preferences.LookPref
import com.music.spotui.data.preferences.setQualityChosen
import com.music.spotui.ui.screens.Setup
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.Socket

/**
 * The first-run onboarding, on a device: server → look → downloads and storage → permissions → Home.
 * Runs against the test Navidrome (10.0.2.2:4533); skipped when it does not answer.
 *
 * Clears the setup preferences for its run and puts them back afterwards, so the other UI tests
 * still find a configured app.
 */
@RunWith(AndroidJUnit4::class)
class SetupFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    companion object {
        private const val TIMEOUT_MS = 40_000L
        private const val SERVER = "http://10.0.2.2:4533/"
        private lateinit var appContext: Context

        @BeforeClass
        @JvmStatic
        fun clearSetup() {
            // No assumption here (it would run in every test): the flow itself skips Server errors.
            appContext = InstrumentationRegistry.getInstrumentation().targetContext
            appContext.getSharedPreferences("myspoti_prefs", Context.MODE_PRIVATE).edit().clear().commit()
            appContext.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE).edit().clear().commit()
            SubsonicSettings.clear(appContext)
            // The look step's preview animates forever, which a Compose test cannot idle on:
            // mark it (and the storage one) seen, so the onboarding is server → permissions.
            LookPref.set(appContext, Look.IMMERSIVE)
            LookPref.setChosen(appContext)
            setQualityChosen(appContext)
        }

        @AfterClass
        @JvmStatic
        fun restoreSetup() {
            val config = SubsonicConfig(SERVER, "admin", "admin")
            SubsonicSettings.save(appContext, config)
            MyspotiRuntime.updateSubsonicConfig(config)
            Setup.done(appContext)
            LookPref.set(appContext, Look.IMMERSIVE)
            LookPref.setChosen(appContext)
            setQualityChosen(appContext)
        }

        private fun serverAnswers(): Boolean = runCatching {
            Socket().use { it.connect(InetSocketAddress("10.0.2.2", 4533), 2_000) }
        }.isSuccess
    }

    @Test
    fun onboardingConnectsTheServerAndReachesHome() {
        if (!serverAnswers()) {
            println("SetupFlowTest: no test Navidrome on 10.0.2.2:4533, skipped")
            return
        }

        // Step 1: the server (the first screen has the URL, user and password fields).
        waitFor("Connect to your server", 30_000)
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size >= 3 }
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("10.0.2.2:4533")
        fields[1].performTextInput("admin")
        fields[2].performTextInput("admin")
        compose.onNodeWithText("Connect").performClick()

        // Step 2: permissions (the look and storage steps are marked seen above), then Home.
        waitFor("Set up Sonora", 60_000)
        compose.onAllNodesWithText("Continue").onFirst().performScrollTo().performClick()
        waitFor("Liked Songs", 60_000)
    }

    /** Waits for a text, printing the tree once if it never comes. */
    private fun waitFor(text: String, timeout: Long) {
        val found = runCatching {
            compose.waitUntil(timeout) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        }
        if (found.isFailure) {
            println("--- screen while waiting for $text ---")
            println(compose.onRoot().printToString())
            throw found.exceptionOrNull()!!
        }
    }
}
