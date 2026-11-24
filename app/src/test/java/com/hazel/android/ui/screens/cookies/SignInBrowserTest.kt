package com.hazel.android.ui.screens.cookies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInBrowserTest {

    private val webView =
        "Mozilla/5.0 (Linux; Android 17; SM-A556E Build/CP2A; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/142.0.7444.60 Mobile Safari/537.36"

    @Test
    fun `mobile agent drops the WebView marks and the device model`() {
        val ua = SignInBrowser.mobileUserAgent(webView)
        assertFalse("; wv" in ua)
        assertFalse("Version/4.0" in ua)
        assertFalse("SM-A556E" in ua)
        assertTrue("Chrome/142.0.0.0 Mobile Safari/537.36" in ua)
    }

    @Test
    fun `desktop agent follows the WebView's Chrome version`() {
        val ua = SignInBrowser.desktopUserAgent(webView)
        assertTrue(ua.startsWith("Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
        assertTrue("Chrome/142.0.0.0 Safari/537.36" in ua)
        assertFalse("Mobile" in ua)
    }

    @Test
    fun `an unreadable agent still gives a Chrome agent`() {
        assertTrue("Chrome/" in SignInBrowser.mobileUserAgent(""))
        assertTrue("Chrome/" in SignInBrowser.desktopUserAgent("odd"))
    }

    @Test
    fun `a bare Instagram home opens the login form`() {
        val login = "https://www.instagram.com/accounts/login/"
        assertEquals(login, SignInBrowser.startUrl("https://www.instagram.com"))
        assertEquals(login, SignInBrowser.startUrl("https://instagram.com/"))
        assertEquals(login, SignInBrowser.startUrl("https://m.instagram.com"))
    }

    @Test
    fun `other addresses open as given`() {
        listOf(
            "https://www.youtube.com",
            "https://www.instagram.com/reel/abc/",
            "https://www.instagram.com/?hl=en",
            "not a url"
        ).forEach { assertEquals(it, SignInBrowser.startUrl(it)) }
    }

    @Test
    fun `app links stay out of the page`() {
        assertTrue(SignInBrowser.opensInPage("https://www.instagram.com/accounts/login/"))
        assertTrue(SignInBrowser.opensInPage("about:blank"))
        assertFalse(SignInBrowser.opensInPage("intent://instagram.com/#Intent;package=com.instagram.android;end"))
        assertFalse(SignInBrowser.opensInPage("instagram://user?username=x"))
        assertFalse(SignInBrowser.opensInPage(null))
    }
}
