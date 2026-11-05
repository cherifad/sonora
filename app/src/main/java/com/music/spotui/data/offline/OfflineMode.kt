package com.music.spotui.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Whether the app can reach the server right now, as one state every screen reads: a working
 * network *and* a server that answers (a LAN server is out of reach away from home, even when
 * mobile data is on), or the data saver applying on mobile data. Offline, only downloaded
 * songs play and the lists say which items cannot.
 */
object OfflineMode {
    /** A working connection (not "validated": some VPNs never pass Android's check). */
    private val network = mutableStateOf(true)

    /** The last answer of the Subsonic server: the backend reports each call's outcome. */
    private val server = mutableStateOf(true)

    /** True while only downloads can play. */
    val offline: Boolean get() = !network.value || !server.value || com.music.spotui.data.DataSaver.active

    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        val ctx = context.applicationContext
        refresh(ctx)
        if (started) return
        started = true
        // The backend tells every call's outcome; the app decides what it means.
        app.myspoti.subsonic.SubsonicClient.onReachable = { ok ->
            if (server.value != ok) {
                app.myspoti.host.android.Logbook.log("offline mode: server ${if (ok) "answers" else "is out of reach"}")
            }
            server.value = ok
        }
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = refresh(ctx)
                override fun onLost(network: Network) = refresh(ctx)
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh(ctx)
            })
        }
        // Offline, a ping every few seconds tells when the server is back (a network callback
        // only fires for the network, not for the server behind it).
        scope.launch {
            while (true) {
                delay(10_000)
                if (!offline) continue
                val api = app.myspoti.host.android.MyspotiRuntime.subsonicApi ?: continue
                server.value = api.ping()
            }
        }
    }

    fun refresh(context: Context) {
        network.value = OfflineStore.isOnline(context)
    }
}
