package com.hazel.android.ui.layout

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * The layout tests' screenshots. By default none is written for a test that passes, and a
 * test that fails writes the screen as it stood when it failed, so a red run shows what went
 * wrong without hundreds of images on every run. With -PlayoutScreenshots=all every one is
 * written, to look through in every language and font size after changing a layout. The
 * build passes the mode (see testOptions in app/build.gradle.kts).
 *
 * Goes inside the compose rule in a test's RuleChain, so a failure is captured while the
 * screen is still there.
 */
class LayoutShots : TestWatcher() {

    /** Writes [path] on a run that keeps every screenshot; otherwise does nothing. */
    fun take(path: String) {
        if (ALL) capture(path)
    }

    override fun failed(e: Throwable, description: Description) {
        if (ALL) return
        val test = description.className.substringAfterLast('.') + "_" + description.methodName
        // A crash can leave nothing on screen to draw; the failure itself is already reported.
        runCatching { capture("$DIR/failed/${test.replace(UNSAFE, "_")}.png") }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(path: String) = captureScreenRoboImage(path)

    private companion object {
        const val DIR = "build/outputs/roborazzi"
        val ALL = System.getProperty("hazel.layout.screenshots") == "all"
        val UNSAFE = Regex("[^A-Za-z0-9._-]+")
    }
}
