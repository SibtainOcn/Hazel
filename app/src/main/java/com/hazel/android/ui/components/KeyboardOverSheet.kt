package com.hazel.android.ui.components

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Where the keyboard and the field being typed in are, for a sheet the keyboard opens over.
 *
 * The sheet stays put while the keyboard covers only what is below the field. When the
 * keyboard would cover the field itself (a tall keyboard, landscape, a large font, a short
 * sheet), the sheet is lifted by the overlap and no more, so the field sits just above it.
 */
@Stable
class SheetKeyboard {
    /** The keyboard's top edge in the sheet's window, or [NONE] while it is closed. */
    internal var keyboardTop by mutableIntStateOf(NONE)

    /** The focused field's bottom edge as it would be with the sheet not lifted, or [NONE]. */
    internal var fieldBottom by mutableIntStateOf(NONE)

    /** The field [fieldBottom] belongs to, so a field losing focus clears only its own. */
    internal var owner: Any? = null

    /** The lift last applied, to read a field's position back as if it were not lifted. */
    internal var applied = 0

    internal fun lift(gap: Int): Int = keyboardLift(fieldBottom, keyboardTop, gap)

    internal companion object {
        const val NONE = Int.MIN_VALUE
    }
}

/**
 * How far to lift a sheet so a field whose bottom edge is at [fieldBottom] sits [gap] above a
 * keyboard whose top edge is at [keyboardTop], both in window pixels with the sheet not
 * lifted. Zero while the field is clear of the keyboard, or either is unknown.
 */
internal fun keyboardLift(fieldBottom: Int, keyboardTop: Int, gap: Int): Int =
    if (keyboardTop == SheetKeyboard.NONE || fieldBottom == SheetKeyboard.NONE) 0
    else (fieldBottom.toLong() + gap - keyboardTop).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

@Composable
fun rememberSheetKeyboard(): SheetKeyboard = remember { SheetKeyboard() }

/**
 * Lifts the sheet by however much of the focused field the keyboard would cover: nothing at
 * all while the field is clear of it. Given as the sheet's own modifier, so the whole sheet
 * moves as one; it is a move only, nothing inside the sheet is laid out again.
 */
fun Modifier.liftedOverKeyboard(keyboard: SheetKeyboard): Modifier = offset {
    val lift = keyboard.lift(FIELD_GAP.roundToPx())
    keyboard.applied = lift
    IntOffset(0, -lift)
}

/** Marks a field the sheet keeps above the keyboard while it is being typed in. */
@Composable
fun Modifier.keptAboveKeyboard(keyboard: SheetKeyboard): Modifier {
    val spot = remember { FieldSpot() }
    return this
        .onGloballyPositioned { coordinates ->
            spot.bottom = coordinates.boundsInWindow().bottom.toInt() + keyboard.applied
            if (keyboard.owner === spot) keyboard.fieldBottom = spot.bottom
        }
        .onFocusChanged { focus ->
            if (focus.hasFocus) {
                keyboard.owner = spot
                keyboard.fieldBottom = spot.bottom
            } else if (keyboard.owner === spot) {
                keyboard.owner = null
                keyboard.fieldBottom = SheetKeyboard.NONE
            }
        }
}

private class FieldSpot {
    var bottom = SheetKeyboard.NONE
}

/** Room left between a field and the keyboard when the sheet has to be lifted. */
private val FIELD_GAP = 12.dp

/**
 * Lets the keyboard open over a bottom sheet rather than lift it whole.
 *
 * A modal sheet pads itself by the keyboard's height, so typing in a field near its top
 * pushed the whole sheet up and shrank it. Called once inside the sheet's content, this
 * hides the keyboard's room from the sheet's own window: the sheet stays where it is and the
 * keyboard is drawn over its lower part. Where the keyboard is goes to [keyboard], so a
 * field it would cover can still be lifted clear of it with [liftedOverKeyboard].
 */
@Composable
fun KeyboardOverSheet(keyboard: SheetKeyboard) {
    val view = LocalView.current
    DisposableEffect(view) {
        // The sheet's window, and the view holding the sheet in it: insets reach the sheet
        // through that view, so that is where the keyboard is taken out.
        val window = (view as? DialogWindowProvider)?.window
        val host = view.parent as? View
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Older versions resize the window itself for the keyboard.
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        if (host != null) {
            fun note(insets: WindowInsetsCompat) {
                val height = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                keyboard.keyboardTop =
                    if (height > 0) host.rootView.height - height else SheetKeyboard.NONE
            }
            ViewCompat.setOnApplyWindowInsetsListener(host) { _, insets ->
                note(insets)
                insets.withoutKeyboard()
            }
            ViewCompat.setWindowInsetsAnimationCallback(
                host,
                object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    override fun onProgress(
                        insets: WindowInsetsCompat,
                        runningAnimations: MutableList<WindowInsetsAnimationCompat>
                    ): WindowInsetsCompat {
                        note(insets)
                        return insets.withoutKeyboard()
                    }
                }
            )
            ViewCompat.requestApplyInsets(host)
        }
        onDispose {
            if (host != null) {
                ViewCompat.setOnApplyWindowInsetsListener(host, null)
                ViewCompat.setWindowInsetsAnimationCallback(host, null)
            }
            keyboard.keyboardTop = SheetKeyboard.NONE
        }
    }
}

private fun WindowInsetsCompat.withoutKeyboard(): WindowInsetsCompat =
    WindowInsetsCompat.Builder(this)
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
        .build()
