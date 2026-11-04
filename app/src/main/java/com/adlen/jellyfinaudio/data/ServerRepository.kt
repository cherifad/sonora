package com.adlen.jellyfinaudio.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adlen.jellyfinaudio.data.subsonic.SubsonicClientFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.serverDataStore: DataStore<Preferences> by preferencesDataStore(name = "server")

@Singleton
class ServerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val baseUrl = stringPreferencesKey("base_url")
        val username = stringPreferencesKey("username")
        val password = stringPreferencesKey("password")
    }

    val config: Flow<ServerConfig?> = context.serverDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { prefs ->
            val baseUrl = prefs[Keys.baseUrl] ?: return@map null
            val username = prefs[Keys.username] ?: return@map null
            val password = prefs[Keys.password] ?: return@map null
            ServerConfig(baseUrl, username, password)
        }

    suspend fun save(config: ServerConfig) {
        val normalized = config.copy(baseUrl = normalizeBaseUrl(config.baseUrl))
        context.serverDataStore.edit { prefs ->
            prefs[Keys.baseUrl] = normalized.baseUrl
            prefs[Keys.username] = normalized.username
            // TODO: move to EncryptedSharedPreferences/Keystore before release.
            prefs[Keys.password] = normalized.password
        }
    }

    suspend fun test(config: ServerConfig): Result<Unit> = runCatching {
        val normalized = config.copy(baseUrl = normalizeBaseUrl(config.baseUrl))
        val response = SubsonicClientFactory.create(normalized).ping().response
        check(response.status == "ok") {
            response.error?.message ?: "Server returned status ${response.status}"
        }
    }

    companion object {
        fun normalizeBaseUrl(raw: String): String {
            var value = raw.trim()
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                value = "http://$value"
            }
            if (!value.endsWith("/")) value += "/"
            return value
        }
    }
}
