// Subsonic/OpenSubsonic backend for Myspoti. GPL-3.0.
package app.myspoti.subsonic

import android.content.Context

/** Server settings, persisted outside the Spotify session. */
object SubsonicSettings {
    private const val PREFS = "subsonic"
    private const val KEY_URL = "url"
    private const val KEY_USER = "username"
    private const val KEY_PASS = "password"

    fun load(context: Context): SubsonicConfig? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        val user = prefs.getString(KEY_USER, null)?.takeIf { it.isNotBlank() } ?: return null
        val pass = prefs.getString(KEY_PASS, null) ?: return null
        return SubsonicConfig(url, user, pass).normalized()
    }

    fun save(context: Context, config: SubsonicConfig) {
        val normalized = config.normalized()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL, normalized.baseUrl)
            .putString(KEY_USER, normalized.username)
            .putString(KEY_PASS, normalized.password)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
