package com.adlen.jellyfinaudio.data.subsonic

import com.adlen.jellyfinaudio.data.ServerConfig
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

object SubsonicUrls {

    /** Builds an authenticated cover-art URL suitable for Coil or an ImageView. */
    fun coverArt(config: ServerConfig, id: String?, size: Int = 300): String? {
        if (id.isNullOrBlank()) return null
        return buildString {
            append(config.normalizedBaseUrl)
            append("rest/getCoverArt.view")
            append("?id=").append(encode(id))
            append("&size=").append(size)
            append(authQuery(config))
        }
    }

    /** Builds an authenticated stream URL for ExoPlayer. */
    fun stream(
        config: ServerConfig,
        id: String,
        maxBitRate: Int? = null,
        format: String? = null,
    ): String = buildString {
        append(config.normalizedBaseUrl)
        append("rest/stream.view")
        append("?id=").append(encode(id))
        if (maxBitRate != null) append("&maxBitRate=").append(maxBitRate)
        if (format != null) append("&format=").append(encode(format))
        append(authQuery(config))
    }

    private fun authQuery(config: ServerConfig): String {
        val salt = UUID.randomUUID().toString().replace("-", "").take(16)
        val token = SubsonicAuthInterceptor.md5(config.password + salt)
        return buildString {
            append("&u=").append(encode(config.username))
            append("&t=").append(token)
            append("&s=").append(salt)
            append("&v=").append(SubsonicAuthInterceptor.API_VERSION)
            append("&c=").append(SubsonicAuthInterceptor.CLIENT_NAME)
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
