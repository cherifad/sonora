// Subsonic/OpenSubsonic backend for Myspoti. GPL-3.0.
package app.myspoti.subsonic

import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/** The password every test server is configured with. */
internal const val TEST_PASSWORD = "s3cret"

/** A Subsonic envelope with [fields] (already JSON, key + value) inside `subsonic-response`. */
internal fun okEnvelope(vararg fields: String): String =
    "{\"subsonic-response\":{\"status\":\"ok\",\"version\":\"1.16.1\",\"type\":\"navidrome\"" +
        (if (fields.isEmpty()) "" else "," + fields.joinToString(",")) + "}}"

/** An HTTP client answering with [handler]'s body (200) instead of reaching a server. */
internal fun fakeHttp(handler: (HttpUrl) -> String): OkHttpClient =
    fakeHttpStatus { url -> 200 to handler(url) }

/** An HTTP client answering [code] + [body] from [handler]. */
internal fun fakeHttpStatus(handler: (HttpUrl) -> Pair<Int, String>): OkHttpClient =
    OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val (code, body) = handler(request.url)
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

/** An HTTP client whose connections are refused, like a server out of reach. */
internal fun offlineHttp(): OkHttpClient =
    OkHttpClient.Builder().addInterceptor { throw IOException("connection refused") }.build()

/** A client against [handler]'s answers. */
internal fun testClient(
    config: SubsonicConfig = SubsonicConfig("music.example", "me", TEST_PASSWORD),
    handler: (HttpUrl) -> String,
): SubsonicClient = SubsonicClient(config, fakeHttp(handler))
