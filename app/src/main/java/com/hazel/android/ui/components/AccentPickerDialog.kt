package com.hazel.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.ui.screens.more.SingleChoiceDialog
import com.hazel.android.ui.theme.AccentColors

/** Accent colour picker, in the same choice dialog the other settings use. */
@Composable
fun AccentPickerDialog(
    accentName: String,
    onAccentChanged: (String) -> Unit,
    onDismiss: () -> Unit
) {
    SingleChoiceDialog(
        title = stringResource(R.string.accent_picker_title),
        choices = AccentColors.map { it.name to it.name },
        selected = accentName,
        onSelect = {
            onAccentChanged(it)
            onDismiss()
        },
        onDismiss = onDismiss,
        leading = { name ->
            val accent = AccentColors.find { it.name == name } ?: AccentColors.first()
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accent.dark)
            )
        }
    )
}
