// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * HTTPS for a self-hosted server: the system trust store first, then trust-on-first-use.
 * The first certificate seen for a host is pinned; a later different certificate is refused
 * (the user can reset the pin in Settings). HTTP never goes through this.
 */
object SubsonicTls {
    private const val PREFS = "subsonic_tls"

    fun client(context: Context, config: SubsonicConfig): OkHttpClient {
        if (!config.baseUrl.startsWith("https://", ignoreCase = true)) {
            return OkHttpClient.Builder().build()
        }
        val host = runCatching { config.baseUrl.toHttpUrl().host }.getOrNull()
            ?: return OkHttpClient.Builder().build()
        return runCatching {
            val trust = TofuTrustManager(context.applicationContext, host)
            val ssl = SSLContext.getInstance("TLS")
            ssl.init(null, arrayOf(trust), null)
            OkHttpClient.Builder().sslSocketFactory(ssl.socketFactory, trust).build()
        }.getOrElse { OkHttpClient.Builder().build() }
    }

    /** Forget every pinned certificate; the next connection pins the current one. */
    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }

    internal fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

internal class TofuTrustManager(
    private val context: Context,
    private val host: String,
) : X509TrustManager {
    private val delegate = defaultTrustManager()

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        delegate.checkClientTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        try {
            delegate.checkServerTrusted(chain, authType)
            return
        } catch (_: CertificateException) {
            // Self-signed or unknown CA: pin it on first use.
        }
        val cert = chain.firstOrNull() ?: throw CertificateException("empty certificate chain")
        val fingerprint = certificateFingerprint(cert.encoded)
        val prefs = SubsonicTls.prefs(context)
        val known = prefs.getString(host, null)
        if (known == null) {
            prefs.edit().putString(host, fingerprint).apply()
            return
        }
        if (!known.equals(fingerprint, ignoreCase = true)) {
            throw CertificateException("the certificate of $host changed; reset the server trust in Settings")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
}

internal fun certificateFingerprint(encoded: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(encoded)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun defaultTrustManager(): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
}
