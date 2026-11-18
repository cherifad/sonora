// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.data.preferences

import android.app.KeyguardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Ask for the phone's own lock (fingerprint, face or PIN) when Sonora comes back to the front.
 * Off until the user asks for it; useless without a screen lock, so the row says so instead of
 * silently doing nothing.
 */
object LockPref {
    private const val KEY = "app_lock"

    var enabled by mutableStateOf(prefs(com.adlen.sonora.app.di.Graph.app).getBoolean(KEY, false))
        private set

    fun set(context: Context, on: Boolean) {
        enabled = on
        prefs(context).edit().putBoolean(KEY, on).apply()
    }

    /** The phone can prove who is holding it: a PIN, a pattern, a fingerprint or a face. */
    fun deviceLocked(context: Context): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true

    private fun prefs(context: Context) = context.getSharedPreferences("sonora_prefs", Context.MODE_PRIVATE)
}

/**
 * Whether this process has already been unlocked. A cold start is locked even when the user
 * launched the app themselves; a rotation or a recreation is not, or turning the phone would
 * ask for the fingerprint again.
 */
object LockSession {
    var unlocked by mutableStateOf(false)
}

/**
 * When the lock screen stands in front of the app. Pure, so it is tested without a phone: the
 * user asked for it, the phone has something to ask for, and the app was left.
 */
internal object LockRule {
    fun wanted(enabled: Boolean, deviceSecure: Boolean, leftApp: Boolean): Boolean =
        enabled && deviceSecure && leftApp
}
