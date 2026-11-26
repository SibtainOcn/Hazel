package com.hazel.android.ui.screens.download

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.ui.components.AlertDialog
import com.hazel.android.ui.components.pressScale

/**
 * Which engine the search screen talks to, as one connected pair: Media (yt-dlp, video and
 * audio, results on Home) or Gallery (gallery-dl, posts and profiles, results on the Gallery tab).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaGallerySwitch(gallery: Boolean, onGallery: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        ToggleButton(
            checked = !gallery,
            onCheckedChange = { if (gallery) onGallery(false) },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.RadioButton }
        ) {
            Text(stringResource(R.string.search_mode_media), fontWeight = FontWeight.Bold, maxLines = 1)
        }
        ToggleButton(
            checked = gallery,
            onCheckedChange = { if (!gallery) onGallery(true) },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.RadioButton }
        ) {
            Text(stringResource(R.string.nav_gallery), fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** The search screen's "i": a seven-sided cookie that opens the guide to what each side finds. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GuideButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = MaterialShapes.Cookie7Sided.toShape(),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        interactionSource = interaction,
        modifier = Modifier.pressScale(interaction, pressed = 0.9f).size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "i",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** What Media and Gallery each find, what profile links and pasted links do, and when to sign in. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchGuideDialog(onDismiss: () -> Unit) {
    val parts = listOf(
        R.string.search_guide_media_title to R.string.search_guide_media_body,
        R.string.search_guide_gallery_title to R.string.search_guide_gallery_body,
        R.string.search_guide_profiles_title to R.string.search_guide_profiles_body,
        R.string.search_guide_links_title to R.string.search_guide_links_body,
        R.string.search_guide_sign_in_title to R.string.search_guide_sign_in_body
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_guide_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                parts.forEachIndexed { index, (title, body) ->
                    Row {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(MaterialShapes.Cookie7Sided.toShape())
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.search_guide_ok), fontWeight = FontWeight.Bold)
            }
        }
    )
}
