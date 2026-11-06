// Subsonic/OpenSubsonic backend for Sonora. GPL-3.0.
package com.adlen.sonora.subsonic

import java.io.IOException

/**
 * A failed request the UI can explain. Every error the client raises is one of these, so the
 * setup screen no longer shows a raw exception message for a wrong password or a dead host.
 */
sealed interface SubsonicFailure {
    /** Short, user-facing text; never contains the password or the token. */
    val userMessage: String
}

/**
 * A server answer that is not `ok`: auth, missing data, protocol mismatch or a server-side
 * error. It stays an [IllegalStateException] so existing callers that only catch that keep
 * working while new ones can switch on the subtype.
 */
sealed class SubsonicException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause), SubsonicFailure {

    class Auth(message: String) : SubsonicException(message) {
        override val userMessage: String
            get() = message.orEmpty().ifBlank { "Wrong username or password." }
    }

    class NotFound(message: String) : SubsonicException(message) {
        override val userMessage: String
            get() = message.orEmpty().ifBlank { "The server could not find that item." }
    }

    /**
     * [fromHttp] distinguishes an HTTP status from a Subsonic `error.code`: the first is mapped
     * to a sentence, the second already carries the server's own message.
     */
    class Server(val code: Int, message: String, val fromHttp: Boolean = false) : SubsonicException(message) {
        override val userMessage: String
            get() = when {
                !fromHttp -> message.orEmpty().ifBlank { "The server refused the request." }
                code == 401 || code == 403 -> "The server refused the credentials."
                code == 429 -> "The server is rate-limiting requests. Try again in a minute."
                code in 500..599 -> "The server has an internal error ($code)."
                else -> "The server answered with an error ($code)."
            }
    }

    class Protocol(message: String) : SubsonicException(message) {
        override val userMessage = "This address does not look like a Subsonic server."
    }

    class Unsupported(val feature: String) : SubsonicException("This server does not support $feature.") {
        override val userMessage = "This server does not support $feature."
    }
}

/**
 * The server could not be reached at all. It stays an [IOException] because the offline mode
 * and the player already treat a refused connection as "the server is away".
 */
class SubsonicNetworkException(message: String, cause: Throwable) :
    IOException(message, cause), SubsonicFailure {
    override val userMessage = "Could not reach the server. Check the address and the network."
}

/** Subsonic `error.code` → typed failure. Codes are the ones in the Subsonic API docs. */
internal fun subsonicFailure(code: Int, message: String): SubsonicException = when (code) {
    40, 41, 42, 43, 44, 50 -> SubsonicException.Auth(message)
    70 -> SubsonicException.NotFound(message)
    10, 20, 30 -> SubsonicException.Protocol(message)
    else -> SubsonicException.Server(code, message)
}
