// Subsonic/OpenSubsonic backend for Myspoti. GPL-3.0.
package app.myspoti.subsonic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.random.Random

data class SubsonicConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    fun normalized(): SubsonicConfig {
        var url = baseUrl.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        return copy(baseUrl = "$url/")
    }
}

/**
 * A small Subsonic client: JSON only, token auth (`u`/`t`/`s`), one call per endpoint.
 * The server can be changed at runtime (setup screen) through [updateConfig].
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

    fun updateConfig(newConfig: SubsonicConfig) {
        config = newConfig.normalized()
        base = config.baseUrl.toHttpUrl()
    }

    suspend fun ping(): JSONObject = call("ping")

    private fun endpoint(method: String, params: List<Pair<String, String>>): HttpUrl {
        val salt = randomSalt()
        val token = md5(config.password + salt)
        val builder = base.newBuilder()
            .addPathSegments("rest/$method.view")
            .addQueryParameter("u", config.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "JellyfinAudio")
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
                throw e
            }
            response.use { response ->
                onReachable?.invoke(true)
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("Subsonic HTTP ${response.code}")
                val root = JSONObject(text).optJSONObject("subsonic-response")
                    ?: error("Not a Subsonic response")
                if (root.optString("status") != "ok") {
                    error(root.optJSONObject("error")?.optString("message") ?: "Subsonic error")
                }
                root
            }
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

internal fun <T> pageOf(items: List<T>, offset: Int, limit: Int): app.myspoti.api.Page<T> {
    val start = offset.coerceAtLeast(0).coerceAtMost(items.size)
    val end = (start + limit.coerceAtLeast(0)).coerceAtMost(items.size)
    return app.myspoti.api.Page(
        items = items.subList(start, end),
        offset = start,
        total = items.size,
        nextOffset = end.takeIf { it < items.size },
    )
}

internal fun <T> emptyPage(): app.myspoti.api.Page<T> =
    app.myspoti.api.Page(emptyList(), 0, 0, null)
