// Subsonic/OpenSubsonic backend for Myspoti. GPL-3.0 (see the repository LICENSE and AGENTS.md).
package app.myspoti.subsonic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class SubsonicConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
)

/**
 * A very small Subsonic client: JSON only, plain password auth (`p`), one call per endpoint.
 * The spike reads the server settings from SharedPreferences (see Graph.init).
 */
class SubsonicClient(val config: SubsonicConfig) {

    private val http = OkHttpClient.Builder().build()
    private val base: HttpUrl = config.baseUrl.trim().trimEnd('/').toHttpUrl()

    private fun endpoint(method: String, params: Map<String, String>): HttpUrl {
        val builder = base.newBuilder()
            .addPathSegments("rest/$method.view")
            .addQueryParameter("u", config.username)
            .addQueryParameter("p", config.password)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "MyspotiSubsonic")
            .addQueryParameter("f", "json")
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    suspend fun call(method: String, params: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(endpoint(method, params)).build()
            http.newCall(request).execute().use { response ->
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

    fun streamUrl(id: String): String = endpoint("stream", mapOf("id" to id)).toString()

    fun coverUrl(coverArt: String?, size: Int = 300): String? {
        val id = coverArt?.takeIf { it.isNotBlank() } ?: return null
        return endpoint("getCoverArt", mapOf("id" to id, "size" to size.toString())).toString()
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
