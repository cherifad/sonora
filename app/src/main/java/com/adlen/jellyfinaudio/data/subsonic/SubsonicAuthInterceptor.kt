package com.adlen.jellyfinaudio.data.subsonic

import com.adlen.jellyfinaudio.data.ServerConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import java.util.UUID

/**
 * Adds Subsonic token authentication to every request:
 * token = md5(password + salt).
 */
class SubsonicAuthInterceptor(
    private val config: ServerConfig,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val salt = UUID.randomUUID().toString().replace("-", "").take(16)
        val token = md5(config.password + salt)

        val url = chain.request().url.newBuilder()
            .addQueryParameter("u", config.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_NAME)
            .addQueryParameter("f", "json")
            .build()

        return chain.proceed(chain.request().newBuilder().url(url).build())
    }

    companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "JellyfinAudio"

        fun md5(value: String): String {
            val digest = MessageDigest.getInstance("MD5")
            return digest.digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(java.util.Locale.ROOT, it.toInt() and 0xFF) }
        }
    }
}
