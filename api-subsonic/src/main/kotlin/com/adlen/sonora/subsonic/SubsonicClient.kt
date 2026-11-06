// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Collections
import kotlin.random.Random

data class SubsonicConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
    /** OpenSubsonic `apiKeyAuthentication`: when set, it replaces `u`/`t`/`s`. */
    val apiKey: String? = null,
) {
    fun normalized(): SubsonicConfig {
        var url = baseUrl.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        return copy(baseUrl = "$url/", apiKey = apiKey?.trim()?.takeIf { it.isNotBlank() })
    }
}

/**
 * A small Subsonic client: JSON only, token auth (`u`/`t`/`s`) or an OpenSubsonic API key,
 * one call per endpoint. The server can be changed at runtime (setup screen) through
 * [updateConfig].
 */
class SubsonicClient(
    initialConfig: SubsonicConfig,
    private val http: OkHttpClient = OkHttpClient.Builder().build(),
) {

    @Volatile
    var config: SubsonicConfig = initialConfig.normalized()
        private set

    @Volatile
    private var base: HttpUrl = config.baseUrl.toHttpUrl()

    /** What the server said about itself; null until [probeCapabilities] runs. */
    @Volatile
    var capabilities: ServerCapabilities? = null
        private set

    /** Optional endpoints this server already refused: asked once, then skipped. */
    private val unsupported = Collections.synchronizedSet(mutableSetOf<String>())

    fun updateConfig(newConfig: SubsonicConfig) {
        config = newConfig.normalized()
        base = config.baseUrl.toHttpUrl()
        capabilities = null
        unsupported.clear()
    }

    suspend fun ping(): JSONObject = call("ping")

    /**
     * One probe per session: `ping` carries the server type/version and whether it speaks
     * OpenSubsonic; the extension list says which optional calls are worth making.
     */
    suspend fun probeCapabilities(force: Boolean = false): ServerCapabilities {
        if (!force) capabilities?.let { return it }
        val ping = ping()
        val extensions = if (ping.optBoolean("openSubsonic")) {
            callOptional("getOpenSubsonicExtensions")
                ?.array("openSubsonicExtensions")
                ?.mapNotNull { it.optString("name").takeIf(String::isNotBlank) }
                ?.toSet()
                .orEmpty()
        } else {
            emptySet()
        }
        val probed = ServerCapabilities(
            type = ping.optString("type").takeIf { it.isNotBlank() },
            serverVersion = ping.optString("serverVersion").takeIf { it.isNotBlank() },
            apiVersion = ping.optString("version").takeIf { it.isNotBlank() },
            openSubsonic = ping.optBoolean("openSubsonic"),
            extensions = extensions,
        )
        capabilities = probed
        return probed
    }

    /**
     * An optional call (lyrics, biography, similar artists): a server that does not implement
     * it is remembered, so the next screen does not pay for the same failed round-trip. A
     * network failure and an auth failure still propagate: they are not "unsupported".
     */
    suspend fun callOptional(method: String, params: Map<String, String> = emptyMap()): JSONObject? {
        if (method in unsupported) return null
        return try {
            call(method, params)
        } catch (e: SubsonicException.NotFound) {
            unsupported += method
            null
        } catch (e: SubsonicException.Protocol) {
            unsupported += method
            null
        } catch (e: SubsonicException.Unsupported) {
            unsupported += method
            null
        } catch (e: SubsonicException.Server) {
            // A 404/405 is "this bridge does not have the endpoint"; code 0 is the generic
            // Subsonic "failed" some servers use for the same thing. A 5xx may be transient.
            if ((e.fromHttp && e.code in 400..499) || (!e.fromHttp && e.code == 0)) {
                unsupported += method
                null
            } else {
                throw e
            }
        }
    }

    private fun endpoint(method: String, params: List<Pair<String, String>>): HttpUrl {
        val builder = base.newBuilder().addPathSegments("rest/$method.view")
        val apiKey = config.apiKey
        if (!apiKey.isNullOrBlank()) {
            builder.addQueryParameter("apiKey", apiKey)
        } else {
            val salt = randomSalt()
            val token = md5(config.password + salt)
            builder.addQueryParameter("u", config.username)
                .addQueryParameter("t", token)
                .addQueryParameter("s", salt)
        }
        builder.addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Sonora")
            .addQueryParameter("f", "json")
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    suspend fun call(method: String, params: Map<String, String> = emptyMap()): JSONObject =
        callPairs(method, params.entries.map { it.key to it.value })

    suspend fun callPairs(method: String, params: List<Pair<String, String>>): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(endpoint(method, params)).build()
            val response = try {
                http.newCall(request).execute()
            } catch (e: java.io.IOException) {
                // The network refused the server: the app shows its downloads until it is back.
                onReachable?.invoke(false)
                throw SubsonicNetworkException("Could not reach ${config.baseUrl}", e)
            }
            response.use { response ->
                onReachable?.invoke(true)
                val text = response.body.string()
                if (!response.isSuccessful) {
                    // Some servers put the Subsonic error in the body of a 401/404.
                    val root = text.takeIf { it.isNotBlank() }
                        ?.let { runCatching { envelope(it) }.getOrNull() }
                    val error = root?.optJSONObject("error")
                    if (root?.optString("status") == "failed" && error != null) {
                        throw subsonicFailure(error.optInt("code", 0), error.optString("message"))
                    }
                    throw SubsonicException.Server(response.code, "Subsonic HTTP ${response.code}", fromHttp = true)
                }
                val root = envelope(text)
                if (root.optString("status") != "ok") {
                    val error = root.optJSONObject("error")
                    throw subsonicFailure(error?.optInt("code") ?: 0, error?.optString("message").orEmpty())
                }
                root
            }
        }

    private fun envelope(text: String): JSONObject = try {
        JSONObject(text).optJSONObject("subsonic-response")
            ?: throw SubsonicException.Protocol("Not a Subsonic response")
    } catch (e: org.json.JSONException) {
        throw SubsonicException.Protocol("Not a Subsonic response: ${e.message}")
    }

    fun streamUrl(id: String): String = endpoint("stream", listOf("id" to id)).toString()

    /**
     * The URL to save for offline listening: the original file, or a server-side transcode
     * (`format`/`maxBitRate`), so "small" downloads stay small and the phone does no encoding.
     */
    fun downloadUrl(id: String, format: String? = null, maxBitRateKbps: Int? = null): String {
        val params = buildList {
            add("id" to id)
            if (!format.isNullOrBlank()) add("format" to format)
            if (maxBitRateKbps != null && maxBitRateKbps > 0) add("maxBitRate" to maxBitRateKbps.toString())
        }
        return endpoint("stream", params).toString()
    }

    fun coverUrl(coverArt: String?, size: Int = 300): String? {
        val id = coverArt?.takeIf { it.isNotBlank() } ?: return null
        return endpoint("getCoverArt", listOf("id" to id, "size" to size.toString())).toString()
    }

    /** The avatar image of a user (`getAvatar` returns the image itself, not JSON). */
    fun avatarUrl(username: String): String? =
        username.takeIf { it.isNotBlank() }?.let { endpoint("getAvatar", listOf("username" to it)).toString() }

    private fun randomSalt(): String =
        (1..12).map { ALPHANUM[Random.nextInt(ALPHANUM.length)] }.joinToString("")

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object Reach {
        private const val ALPHANUM = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        /**
         * Told when a request could not reach the server (false) or the server answered (true).
         * The app's offline mode listens: a server out of reach plays downloads, like no network.
         */
        @Volatile
        var onReachable: ((Boolean) -> Unit)? = null
    }
}

internal fun JSONObject.array(key: String): List<JSONObject> {
    val array: JSONArray = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

internal fun <T> pageOf(items: List<T>, offset: Int, limit: Int): com.adlen.sonora.api.Page<T> {
    val start = offset.coerceAtLeast(0).coerceAtMost(items.size)
    val end = (start + limit.coerceAtLeast(0)).coerceAtMost(items.size)
    return com.adlen.sonora.api.Page(
        items = items.subList(start, end),
        offset = start,
        total = items.size,
        nextOffset = end.takeIf { it < items.size },
    )
}

/**
 * A page the server already sliced. The API does not return a total, so [total] is the
 * furthest item seen and [nextOffset] is set only when a full page came back: the caller
 * stops on the first short page instead of walking empty pages forever.
 */
internal fun <T> serverPage(items: List<T>, offset: Int, limit: Int): com.adlen.sonora.api.Page<T> {
    val hasMore = limit > 0 && items.size >= limit
    return com.adlen.sonora.api.Page(
        items = items,
        offset = offset,
        total = offset + items.size,
        nextOffset = if (hasMore) offset + items.size else null,
    )
}

internal fun <T> emptyPage(): com.adlen.sonora.api.Page<T> =
    com.adlen.sonora.api.Page(emptyList(), 0, 0, null)
