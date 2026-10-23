package com.hazel.android.ui.screens.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/** One message for the pill toast; each show is a new instance, so a repeat still shows. */
class StatusMessage(val text: String, val ok: Boolean)

/** What a screen's pill toast is showing, if anything. */
@Stable
class StatusToastState {
    var current by mutableStateOf<StatusMessage?>(null)
        internal set

    fun show(text: String, ok: Boolean = true) {
        current = StatusMessage(text, ok)
    }
}

@Composable
fun rememberStatusToastState(): StatusToastState = remember { StatusToastState() }

/**
 * The pill toast with a tick or an error mark, shown for [FEEDBACK_MS] and then faded out.
 * Place it where the screen wants it; it takes no room while hidden.
 */
@Composable
fun StatusToastHost(state: StatusToastState, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(StatusMessage("", true)) }
    LaunchedEffect(state.current) {
        val current = state.current ?: return@LaunchedEffect
        shown = current
        delay(FEEDBACK_MS)
        state.current = null
    }
    AnimatedVisibility(
        visible = state.current != null,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = modifier
    ) {
        FeedbackToast(
            shown.text,
            icon = if (shown.ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            iconTint = if (shown.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}
