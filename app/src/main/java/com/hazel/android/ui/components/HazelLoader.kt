package com.hazel.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Reusable M3 loading indicator with morphing polygon shapes.
 * Uses the official Material3 [LoadingIndicator].
 * Appears centered with a smooth fade — no slide animation.
 *
 * @param visible controls visibility with fade enter/exit
 * @param modifier optional modifier for the outer container
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HazelLoader(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator(
                modifier = Modifier.size(56.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
