package com.hazel.android.gallery

/** A gallery-dl run that did not finish, sorted by what the user can do about it. */
class GalleryDlException(
    val kind: Kind,
    /** gallery-dl's own last error line, for logs. Not shown as is. */
    val detail: String,
    cause: Throwable? = null
) : Exception(detail, cause) {

    enum class Kind {
        /** The site wants a sign-in, or the saved one ran out. */
        LOGIN_REQUIRED,
        /** No part of gallery-dl reads this kind of link. */
        UNSUPPORTED,
        /** The post is gone, private, or holds nothing to save. */
        NOT_FOUND,
        /** The connection or the proxy failed. */
        NETWORK,
        /** gallery-dl could not be set up on the device. */
        SETUP,
        OTHER
    }

    companion object {
        /** Sorts gallery-dl's last error line into a [Kind]. */
        fun classify(line: String): Kind {
            val text = line.lowercase()
            return when {
                LOGIN_SIGNS.any { it in text } -> Kind.LOGIN_REQUIRED
                "unsupported url" in text || "no suitable extractor" in text -> Kind.UNSUPPORTED
                NOT_FOUND_SIGNS.any { it in text } -> Kind.NOT_FOUND
                NETWORK_SIGNS.any { it in text } -> Kind.NETWORK
                else -> Kind.OTHER
            }
        }

        private val LOGIN_SIGNS = listOf(
            "login required", "authorizationerror", "authentication", "401 unauthorized",
            "redirect to login", "cookies", "log in", "login"
        )
        private val NOT_FOUND_SIGNS = listOf("404", "not found", "notfounderror", "does not exist", "private")
        private val NETWORK_SIGNS = listOf(
            "connectionerror", "connection", "timed out", "timeout", "proxy", "name resolution",
            "ssl", "network is unreachable"
        )
    }
}
