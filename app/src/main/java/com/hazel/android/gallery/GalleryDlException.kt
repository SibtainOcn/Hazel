package com.hazel.android.gallery

import androidx.annotation.StringRes
import com.hazel.android.R

/** A gallery-dl run that did not finish, sorted by what the user can do about it. */
class GalleryDlException(
    val kind: Kind,
    /** gallery-dl's own last error line, for logs. Not shown as is. */
    val detail: String,
    cause: Throwable? = null,
    /** The end of the run's redacted log, for the log sheet; blank when there is none. */
    val log: String = ""
) : Exception(detail, cause) {

    enum class Kind(@param:StringRes val message: Int) {
        /** The site wants a sign-in, or the saved one ran out. */
        LOGIN_REQUIRED(R.string.gallery_error_login),
        /** The site turned the request away although a sign-in for it is saved. */
        SIGN_IN_REFUSED(R.string.gallery_error_sign_in_refused),
        /** No part of gallery-dl reads this kind of link. */
        UNSUPPORTED(R.string.gallery_error_unsupported),
        /** The post is gone, private, or holds nothing to save. */
        NOT_FOUND(R.string.gallery_error_not_found),
        /** The connection or the proxy failed. */
        NETWORK(R.string.gallery_error_network),
        /** gallery-dl could not be set up on the device. */
        SETUP(R.string.gallery_error_setup),
        OTHER(R.string.gallery_failed)
    }

    companion object {
        /** Sorts an error gallery-dl wrote into its `-j` listing, by its class first. */
        fun classifyError(name: String, message: String): Kind = when (name) {
            "AuthRequired", "AuthorizationError", "AuthenticationError" -> Kind.LOGIN_REQUIRED
            "NotFoundError" -> Kind.NOT_FOUND
            "NoExtractorError" -> Kind.UNSUPPORTED
            else -> classify("$name $message")
        }

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

        /**
         * [kind] in the light of whether a sign-in for the site is saved. Without one, anything
         * not plainly gone or unsupported asks for one; with one, a refusal means it stopped working.
         */
        fun withSignIn(kind: Kind, text: String, signedIn: Boolean): Kind = when {
            !signedIn -> if (kind == Kind.OTHER) Kind.LOGIN_REQUIRED else kind
            kind == Kind.LOGIN_REQUIRED -> Kind.SIGN_IN_REFUSED
            kind == Kind.OTHER && REFUSED_SIGNS.any { it in text.lowercase() } -> Kind.SIGN_IN_REFUSED
            else -> kind
        }

        // Instagram sends a session it turns away (flagged, or too many requests) to its home or challenge page.
        private val REFUSED_SIGNS = listOf(
            "400 bad request", "403 forbidden", "redirect to home page", "redirect to challenge page"
        )
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
