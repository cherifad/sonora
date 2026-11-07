// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.data.upload

import android.content.Context
import com.adlen.sonora.subsonic.SubsonicConfig
import com.adlen.sonora.subsonic.SubsonicSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Turns the Subsonic credentials the app already holds into an ingest token.
 *
 * The app sends the same `u`/`t`/`s` it would send to the server (or its OpenSubsonic API
 * key); the ingest forwards them to `ping` and only issues a token when the server accepts
 * them. The password never leaves the phone.
 */
object UploadAuth {

    enum class Failure { NO_SERVER, REFUSED, UNREACHABLE, SERVER_ERROR }

    class ExchangeException(val failure: Failure, message: String) : IOException(message)

    data class Session(val target: UploadTarget, val user: String, val server: String)

    private val http = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Connects to [url] and stores the issued token, or throws an [ExchangeException]. */
    suspend fun connect(context: Context, url: String, wifiOnly: Boolean): Session = withContext(Dispatchers.IO) {
        val config = SubsonicSettings.load(context)
            ?: throw ExchangeException(Failure.NO_SERVER, "no Subsonic server configured")
        val base = UploadSettings.normalize(url)
        val payload = credentials(config)
        val request = try {
            Request.Builder()
                .url("$base/auth/exchange")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
        } catch (e: IllegalArgumentException) {
            throw ExchangeException(Failure.UNREACHABLE, e.message ?: "bad URL")
        }
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw ExchangeException(Failure.UNREACHABLE, e.message ?: "unreachable")
        }
        response.use {
            val text = it.body?.string().orEmpty()
            if (it.code == 401 || it.code == 403) {
                throw ExchangeException(Failure.REFUSED, text)
            }
            if (!it.isSuccessful) {
                throw ExchangeException(Failure.SERVER_ERROR, "HTTP ${it.code}: $text")
            }
            val json = runCatching { JSONObject(text) }.getOrElse {
                throw ExchangeException(Failure.SERVER_ERROR, "invalid response")
            }
            val token = json.optString("token").takeIf { value -> value.isNotBlank() }
                ?: throw ExchangeException(Failure.SERVER_ERROR, "no token in response")
            val target = UploadTarget(base, token)
            UploadSettings.save(context, target, wifiOnly)
            Session(
                target = target,
                user = json.optString("user", config.username),
                server = json.optString("server", "Subsonic"),
            )
        }
    }

    /**
     * Re-runs the exchange with the saved URL and Subsonic credentials. Used when the ingest
     * answers 401, so an expired token heals without asking the user anything.
     */
    suspend fun refresh(context: Context): UploadTarget? {
        val current = UploadSettings.load(context)
        val url = current?.baseUrl ?: UploadSettings.url(context) ?: return null
        return runCatching {
            connect(context, url, UploadSettings.isWifiOnly(context)).target
        }.getOrNull()
    }

    private fun credentials(config: SubsonicConfig): JSONObject {
        val apiKey = config.apiKey
        if (!apiKey.isNullOrBlank()) {
            return JSONObject().put("apiKey", apiKey).put("u", config.username)
        }
        val salt = randomSalt()
        val token = md5(config.password + salt)
        return JSONObject()
            .put("u", config.username)
            .put("t", token)
            .put("s", salt)
    }

    private fun randomSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
