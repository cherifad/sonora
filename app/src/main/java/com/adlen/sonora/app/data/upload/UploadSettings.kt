// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.data.upload

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences

data class UploadTarget(val baseUrl: String, val token: String)

/**
 * The companion ingest service the app uploads to. The token is a secret: it lives in
 * EncryptedSharedPreferences, like the server password.
 */
object UploadSettings {
    private const val PREFS = "upload_target"
    private const val MASTER_KEY = "sonora_upload"
    private const val KEY_URL = "url"
    private const val KEY_TOKEN = "token"
    private const val KEY_WIFI = "wifi_only"

    @Volatile
    private var cached: SharedPreferences? = null

    fun load(context: Context): UploadTarget? {
        val prefs = prefs(context)
        val url = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        val token = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        return UploadTarget(normalize(url), token)
    }

    fun save(context: Context, target: UploadTarget, wifiOnly: Boolean) {
        prefs(context).edit()
            .putString(KEY_URL, normalize(target.baseUrl))
            .putString(KEY_TOKEN, target.token.trim())
            .putBoolean(KEY_WIFI, wifiOnly)
            .apply()
    }

    fun isWifiOnly(context: Context): Boolean = prefs(context).getBoolean(KEY_WIFI, true)

    fun setWifiOnly(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_WIFI, on).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        cached = null
    }

    fun normalize(url: String): String {
        var value = url.trim().trimEnd('/')
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://$value"
        return value
    }

    private fun prefs(context: Context): SharedPreferences {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val app = context.applicationContext
            val secure = runCatching {
                EncryptedSharedPreferences.create(
                    PREFS,
                    MASTER_KEY,
                    app,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrNull()
            return (secure ?: app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)).also { cached = it }
        }
    }
}
