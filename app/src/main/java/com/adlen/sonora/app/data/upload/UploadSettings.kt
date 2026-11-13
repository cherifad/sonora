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

    /** The saved ingest URL, even before a token was issued. */
    fun url(context: Context): String? =
        prefs(context).getString(KEY_URL, null)?.takeIf { it.isNotBlank() }?.let(::normalize)

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

    /**
     * A URL the way it is stored: without a trailing slash, and with a scheme. An address that
     * only works on the LAN (an IP, localhost, a bare host name) keeps plain HTTP; a public name
     * is served over TLS, so a typed `ingest.example.com` becomes `https://ingest.example.com`
     * even when the user does not say so.
     */
    fun normalize(url: String): String {
        val value = url.trim().trimEnd('/')
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        val host = value.substringBefore('/').substringBefore(':')
        val local = host.isBlank() || host == "localhost" || value.startsWith("[") ||
            !host.contains('.') || host.endsWith(".local") || IPV4.matches(host)
        return (if (local) "http://" else "https://") + value
    }

    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    /** The country-style second levels that belong to the domain, not to the server name. */
    private val SECOND_LEVELS = setOf("co", "com", "org", "net", "gov", "edu", "ac")

    /**
     * A sensible ingest address for a music server: `navidrome-ingest` on the domain the server
     * already uses. `music.example.com` and `jellyfin.example.com` both suggest
     * `navidrome-ingest.example.com`, which is the name people register for the companion
     * service; a bare address (an IP, a single label) keeps the host and the ingest default port.
     *
     * The suggestion is HTTPS: a registered name is published behind TLS, so the user does not
     * have to think about the scheme.
     */
    fun suggestUrl(serverBaseUrl: String): String {
        val fallback = "http://<ingest-host>:8088"
        if (serverBaseUrl.isBlank()) return fallback
        val uri = runCatching { java.net.URI(normalize(serverBaseUrl)) }.getOrNull() ?: return fallback
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return fallback
        if (host.contains(':') || IPV4.matches(host) || !host.contains('.')) return "http://$host:8088"
        return "https://navidrome-ingest." + rootDomain(host)
    }

    /** The registrable part of a host: `musics.servperso.me` and `jellyfin.example.com` give `servperso.me` and `example.com`. */
    private fun rootDomain(host: String): String {
        val labels = host.lowercase().split('.').filter { it.isNotBlank() }
        if (labels.size <= 2) return labels.joinToString(".")
        val secondLast = labels[labels.size - 2]
        val take = if (secondLast.length == 2 && secondLast in SECOND_LEVELS) 3 else 2
        return labels.takeLast(take).joinToString(".")
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
