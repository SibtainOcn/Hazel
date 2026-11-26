package com.hazel.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieImportTest {

    @Test
    fun jsonExportNetscapeWithSpacesAndJunk() {
        val json = """[{"domain":".x.com","hostOnly":false,"httpOnly":true,"name":"auth_token","path":"/",
            "secure":true,"expirationDate":1790000000.5,"value":"abc"}]"""
        assertEquals(
            listOf("#HttpOnly_.x.com\tTRUE\t/\tTRUE\t1790000000\tauth_token\tabc"),
            CookieImport.toNetscapeLines(json)
        )
        assertEquals(
            listOf(".instagram.com\tTRUE\t/\tTRUE\t0\tsessionid\tv 1"),
            CookieImport.toNetscapeLines("# Netscape HTTP Cookie File\n.instagram.com TRUE / TRUE 0 sessionid v 1")
        )
        assertTrue(CookieImport.toNetscapeLines("hello world\nnot a cookie").isEmpty())
    }
}
