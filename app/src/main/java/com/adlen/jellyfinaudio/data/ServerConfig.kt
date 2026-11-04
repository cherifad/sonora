package com.adlen.jellyfinaudio.data

data class ServerConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    /** Retrofit requires a trailing slash on the base URL. */
    val normalizedBaseUrl: String
        get() = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
}
