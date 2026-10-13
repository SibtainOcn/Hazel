package com.hazel.android.ui.layout

import androidx.test.espresso.AppNotIdleException
import androidx.test.espresso.IdlingPolicies
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.TimeUnit

/**
 * Fails a layout test quickly, and says why, when its screen never settles.
 *
 * Compose waits for a screen to stop changing before a test reads it. A screen that never
 * stops, from an endless animation, a measure or composition loop, or a text field inside a
 * Dialog (which Robolectric never lets settle), used to hold each case for a minute before
 * Espresso gave up, so one such screen across every language and font size stalled a run for
 * half an hour. This cuts the wait to [SECONDS] seconds and turns the failure into a message
 * naming the likely causes.
 *
 * Goes outside the compose rule in a test's RuleChain, so the timeout is in place before the
 * screen is drawn and the failure is caught on its way out.
 */
class SettleGuard : TestRule {

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            IdlingPolicies.setMasterPolicyTimeout(SECONDS, TimeUnit.SECONDS)
            IdlingPolicies.setIdlingResourceTimeout(SECONDS, TimeUnit.SECONDS)
            try {
                base.evaluate()
            } catch (e: AppNotIdleException) {
                throw AssertionError(
                    "${description.methodName}: the screen never settled within $SECONDS s. Something keeps " +
                        "redrawing it: an endless animation, a layout or composition loop, or a text field " +
                        "inside a Dialog, which Robolectric cannot settle.",
                    e
                )
            } finally {
                IdlingPolicies.setMasterPolicyTimeout(DEFAULT_SECONDS, TimeUnit.SECONDS)
                IdlingPolicies.setIdlingResourceTimeout(DEFAULT_SECONDS, TimeUnit.SECONDS)
            }
        }
    }

    private companion object {
        const val SECONDS = 10L
        const val DEFAULT_SECONDS = 60L
    }
}
