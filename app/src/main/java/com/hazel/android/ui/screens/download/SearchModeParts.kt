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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
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
import com.hazel.android.ui.components.SoftIconButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.hazel.android.ui.components.pressScale

/**
 * Which engine the search screen talks to, as one connected pair (the picked one keeps its
 * tight inner corner, as the Queue tabs do): Media (yt-dlp, video and
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
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes().let { ToggleButtonShapes(it.shape, it.pressedShape, it.shape) },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.RadioButton }
        ) {
            Text(stringResource(R.string.search_mode_media), fontWeight = FontWeight.Bold, maxLines = 1)
        }
        ToggleButton(
            checked = gallery,
            onCheckedChange = { if (!gallery) onGallery(true) },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes().let { ToggleButtonShapes(it.shape, it.pressedShape, it.shape) },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { role = Role.RadioButton }
        ) {
            Text(stringResource(R.string.nav_gallery), fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** A header button on a seven-sided cookie, as the search screen's "i" and three dots are. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CookieButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = MaterialShapes.Cookie7Sided.toShape(),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        interactionSource = interaction,
        modifier = modifier.pressScale(interaction, pressed = 0.9f).size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** The search screen's "i", which opens the guide to what each side finds. */
@Composable
internal fun GuideButton(onClick: () -> Unit) {
    CookieButton(onClick = onClick) {
        Text("i", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/**
 * What Media and Gallery each find, what profile links and pasted links do, and when to sign
 * in, as a screen of its own over whatever opened it. Back, by button or gesture, returns there.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchGuideScreen(onBack: () -> Unit) {
    val parts = listOf(
        R.string.search_guide_media_title to R.string.search_guide_media_body,
        R.string.search_guide_gallery_title to R.string.search_guide_gallery_body,
        R.string.search_guide_profiles_title to R.string.search_guide_profiles_body,
        R.string.search_guide_links_title to R.string.search_guide_links_body,
        R.string.search_guide_sign_in_title to R.string.search_guide_sign_in_body
    )
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SoftIconButton(onClick = onBack, corner = 22.dp, modifier = Modifier.padding(start = 8.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = stringResource(R.string.search_back),
                            modifier = Modifier.size(20.dp).padding(start = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.search_guide_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        modifier = Modifier.weight(1f).padding(end = 16.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    parts.forEachIndexed { index, (title, body) ->
                        val outer = 24.dp
                        val inner = 6.dp
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = if (index == 0) outer else inner,
                                topEnd = if (index == 0) outer else inner,
                                bottomStart = if (index == parts.lastIndex) outer else inner,
                                bottomEnd = if (index == parts.lastIndex) outer else inner
                            ),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(16.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
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
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        stringResource(body),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
