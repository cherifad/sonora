// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences

/**
 * Server settings, persisted outside the Spotify session. The password and the API key live
 * in an EncryptedSharedPreferences file backed by the Android Keystore; a device that ran the
 * old plain file is migrated once and the plain file is emptied.
 */
object SubsonicSettings {
    private const val PREFS = "subsonic"
    private const val SECURE_PREFS = "subsonic_secure"
    private const val MASTER_KEY = "sonora_subsonic"
    private const val KEY_URL = "url"
    private const val KEY_USER = "username"
    private const val KEY_PASS = "password"
    private const val KEY_API_KEY = "apiKey"
    private const val KEY_FOLDER = "musicFolderId"

    @Volatile
    private var cached: SharedPreferences? = null

    fun load(context: Context): SubsonicConfig? {
        val prefs = prefs(context)
        val url = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        val user = prefs.getString(KEY_USER, null)?.takeIf { it.isNotBlank() } ?: return null
        val pass = prefs.getString(KEY_PASS, null) ?: return null
        val apiKey = prefs.getString(KEY_API_KEY, null)
        return SubsonicConfig(url, user, pass, apiKey).normalized()
    }

    fun save(context: Context, config: SubsonicConfig) {
        val normalized = config.normalized()
        prefs(context).edit()
            .putString(KEY_URL, normalized.baseUrl)
            .putString(KEY_USER, normalized.username)
            .putString(KEY_PASS, normalized.password)
            .apply {
                if (normalized.apiKey != null) putString(KEY_API_KEY, normalized.apiKey) else remove(KEY_API_KEY)
            }
            .apply()
    }

    /** The selected music folder, or null for every library. */
    fun musicFolder(context: Context): String? =
        prefs(context).getString(KEY_FOLDER, null)?.takeIf { it.isNotBlank() }

    fun setMusicFolder(context: Context, id: String?) {
        prefs(context).edit().apply {
            if (id.isNullOrBlank()) remove(KEY_FOLDER) else putString(KEY_FOLDER, id)
        }.apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        // The old plain file must not survive a logout either.
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        cached = null
    }

    private fun prefs(context: Context): SharedPreferences {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val app = context.applicationContext
            val legacy = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val secure = runCatching {
                // security-crypto 1.0's overload: file, master-key alias, context, schemes.
                EncryptedSharedPreferences.create(
                    SECURE_PREFS,
                    MASTER_KEY,
                    app,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrNull()
            if (secure != null && secure.all.isEmpty() && legacy.all.isNotEmpty()) {
                secure.edit().apply {
                    legacy.all.forEach { (key, value) ->
                        when (value) {
                            is String -> putString(key, value)
                            is Boolean -> putBoolean(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is Float -> putFloat(key, value)
                            is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
                        }
                    }
                }.apply()
                legacy.edit().clear().apply()
            }
            // A Keystore that cannot be opened (an unusual device) keeps the app working on the
            // old file rather than locking the user out; the next save tries the secure one again.
            return (secure ?: legacy).also { cached = it }
        }
    }
}
