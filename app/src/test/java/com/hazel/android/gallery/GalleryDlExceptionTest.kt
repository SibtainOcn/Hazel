package com.hazel.android.gallery

import com.hazel.android.gallery.GalleryDlException.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryDlExceptionTest {

    private val instagram400 = "HttpError: '400 Bad Request' for 'https://www.instagram.com/api/v1/media/1/info/'"

    @Test
    fun refusalWithSavedSignInAsksToSignInAgain() {
        val kind = GalleryDlException.classifyError("HttpError", instagram400)
        assertEquals(Kind.SIGN_IN_REFUSED, GalleryDlException.withSignIn(kind, instagram400, signedIn = true))
        assertEquals(Kind.LOGIN_REQUIRED, GalleryDlException.withSignIn(kind, instagram400, signedIn = false))
        assertEquals(Kind.NOT_FOUND, GalleryDlException.withSignIn(Kind.NOT_FOUND, "NotFoundError: post", signedIn = true))
        val home = "HTTP redirect to home page (https://www.instagram.com/)"
        assertEquals(Kind.SIGN_IN_REFUSED, GalleryDlException.withSignIn(GalleryDlException.classifyError("AbortExtraction", home), home, signedIn = true))
    }
}
