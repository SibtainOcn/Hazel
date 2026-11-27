package com.hazel.android.download.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackgroundPlaybackTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun lendsOnePlayerAtATimeAndStopsTheServiceOnReturn() {
        val first = ExoPlayer.Builder(context).build()
        val second = ExoPlayer.Builder(context).build()
        val app = shadowOf(context as android.app.Application)
        try {
            BackgroundPlayback.attach(context, first)
            val lent = BackgroundPlayback.session
            assertNotNull(lent)
            assertSame(first, lent!!.player)
            assertEquals(PlaybackService::class.java.name, app.nextStartedService?.component?.className)

            BackgroundPlayback.attach(context, first)
            assertSame(lent, BackgroundPlayback.session)

            // Another player replaces the first, whose session is let go.
            BackgroundPlayback.attach(context, second)
            assertSame(second, BackgroundPlayback.session?.player)

            // A stale player never takes the current one back.
            BackgroundPlayback.detach(context, first)
            assertSame(second, BackgroundPlayback.session?.player)

            BackgroundPlayback.detach(context, second)
            assertNull(BackgroundPlayback.session)
            assertEquals(PlaybackService::class.java.name, app.nextStoppedService?.component?.className)
        } finally {
            BackgroundPlayback.detach(context, first)
            BackgroundPlayback.detach(context, second)
            first.release()
            second.release()
        }
    }

    /** Android 14 refuses a media foreground service without these, and only at run time. */
    @Test
    fun manifestDeclaresTheMediaService() {
        val manifest = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
            .map(::File).first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(manifest)
        val ns = "http://schemas.android.com/apk/res/android"
        val permissions = doc.getElementsByTagName("uses-permission").let { list ->
            (0 until list.length).map { (list.item(it) as org.w3c.dom.Element).getAttributeNS(ns, "name") }
        }
        assertTrue("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" in permissions)
        assertTrue("android.permission.WAKE_LOCK" in permissions)
        val services = doc.getElementsByTagName("service")
        val service = (0 until services.length).map { services.item(it) as org.w3c.dom.Element }
            .single { it.getAttributeNS(ns, "name") == ".download.playback.PlaybackService" }
        assertEquals("mediaPlayback", service.getAttributeNS(ns, "foregroundServiceType"))
        assertEquals("true", service.getAttributeNS(ns, "exported"))
        val action = service.getElementsByTagName("action").item(0) as org.w3c.dom.Element
        assertEquals("androidx.media3.session.MediaSessionService", action.getAttributeNS(ns, "name"))

        // Hazel stays in the system's "Open with" list for audio and video files.
        val activities = doc.getElementsByTagName("activity")
        val player = (0 until activities.length).map { activities.item(it) as org.w3c.dom.Element }
            .single { it.getAttributeNS(ns, "name") == ".ui.player.PlayerActivity" }
        val data = player.getElementsByTagName("data").let { list ->
            (0 until list.length).map { (list.item(it) as org.w3c.dom.Element) }
        }
        val types = data.map { it.getAttributeNS(ns, "mimeType") }
        assertTrue("audio/*" in types && "video/*" in types)
        assertTrue("content" in data.map { it.getAttributeNS(ns, "scheme") })
        val viewAction = player.getElementsByTagName("action").item(0) as org.w3c.dom.Element
        assertEquals("android.intent.action.VIEW", viewAction.getAttributeNS(ns, "name"))
    }
}
