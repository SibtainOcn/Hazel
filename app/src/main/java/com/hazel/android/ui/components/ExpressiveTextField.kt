package com.hazel.android.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * The app's text field: a tinted tile with round corners and a thin rim, so it reads as a box
 * on any dialog, sheet or glass. The rim takes the accent while typing and the error colour
 * on an error; there is no underline.
 */
@Composable
fun ExpressiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    shape: Shape = FieldShape
) {
    val source = remember { MutableInteractionSource() }
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.rim(source, isError, shape),
        enabled = enabled,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        shape = shape,
        colors = expressiveFieldColors(),
        interactionSource = source
    )
}

/** The same field over a [TextFieldValue], for callers that move the cursor themselves. */
@Composable
fun ExpressiveTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    shape: Shape = FieldShape
) {
    val source = remember { MutableInteractionSource() }
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.rim(source, false, shape),
        textStyle = textStyle,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        shape = shape,
        colors = expressiveFieldColors(),
        interactionSource = source
    )
}

private val FieldShape = RoundedCornerShape(14.dp)

@Composable
private fun Modifier.rim(source: MutableInteractionSource, isError: Boolean, shape: Shape): Modifier {
    val focused by source.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    val color = when {
        isError -> scheme.error
        focused -> scheme.primary
        else -> scheme.onSurface.copy(alpha = 0.12f)
    }
    return border(1.dp, color, shape)
}

@Composable
private fun expressiveFieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    val tile = scheme.onSurface.copy(alpha = 0.06f)
    return TextFieldDefaults.colors(
        focusedContainerColor = tile,
        unfocusedContainerColor = tile,
        disabledContainerColor = scheme.onSurface.copy(alpha = 0.03f),
        errorContainerColor = tile,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        focusedLabelColor = scheme.primary,
        unfocusedPlaceholderColor = scheme.onSurface.copy(alpha = 0.5f),
        focusedPlaceholderColor = scheme.onSurface.copy(alpha = 0.5f),
        cursorColor = scheme.primary
    )
}
