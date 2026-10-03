package com.hazel.android.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * For a list or scrolling column inside a bottom sheet; goes before its scroll modifier.
 *
 * A fling that reaches the end of the list stops there. Passed on, what was left of it
 * stretched the layers above and set the sheet itself moving, which then sprang back. A drag
 * still reaches the sheet, so pulling down from the top of a list closes it as usual.
 */
fun Modifier.keepFlingInSheet(): Modifier = nestedScroll(KeepFlingInside)

private val KeepFlingInside = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.SideEffect) available else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}
