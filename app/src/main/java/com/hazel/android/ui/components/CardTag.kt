package com.hazel.android.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A small label drawn over artwork: duration, size, state. */
@Composable
fun CardTag(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = Color.Black.copy(alpha = 0.6f),
    foreground: Color = Color.White
) {
    Surface(shape = RoundedCornerShape(4.dp), color = background, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
            // A tag is always one line; one wider than its room is cut short, never wrapped
            // into a tall column of letters.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** A moment as the lists show it, in the device's own language: "30 Sept 2026, 21:22". */
fun formatDateTime(millis: Long): String =
    runCatching {
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
    }.getOrDefault("")
