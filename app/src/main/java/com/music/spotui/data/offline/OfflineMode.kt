package com.music.spotui.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.mutableStateOf

/**
 * Whether the app is offline, as one state every screen reads: no usable network, or the
 * data saver applying on mobile data. Offline, only downloaded songs play and the lists say
 * which items cannot.
 */
object OfflineMode {
    /** A working connection (not "validated": some VPNs never pass Android's check). */
    val online = mutableStateOf(true)

    /** True while only downloads can play. */
    val offline: Boolean get() = !online.value || com.music.spotui.data.DataSaver.active

    private var started = false

    fun init(context: Context) {
        val app = context.applicationContext
        refresh(app)
        if (started) return
        started = true
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = refresh(app)
                override fun onLost(network: Network) = refresh(app)
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh(app)
            })
        }
    }

    fun refresh(context: Context) {
        online.value = OfflineStore.isOnline(context)
    }
}
