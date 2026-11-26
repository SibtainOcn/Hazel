package com.hazel.android.ui.screens.gallery

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.AccountState
import com.hazel.android.data.SettingsRepository
import com.hazel.android.gallery.GalleryProfiles
import com.hazel.android.gallery.ProfileSection
import com.hazel.android.gallery.ProfileSite
import com.hazel.android.ui.components.SheetHeaderLayout
import com.hazel.android.ui.screens.cookies.StatePill
import com.hazel.android.ui.screens.download.ToggleRow
import com.hazel.android.ui.theme.sheetBlackColor
import com.hazel.android.ui.theme.sheetRaisedColor
import kotlinx.coroutines.launch

/**
 * Which sections of a profile to fetch, at most [GalleryProfiles.MAX_SECTIONS] at a time: a
 * third switched on drops the first. One sheet for every door to it: a pasted profile link
 * (with [onFetch]), the three dots menu and Image settings (without; the choice saves as made).
 * [signedIn] marks the sections only signed-in accounts can see.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileSectionsSheet(
    site: ProfileSite,
    title: String,
    chosen: List<ProfileSection>,
    onChosen: (List<ProfileSection>) -> Unit,
    signedIn: Boolean,
    onDismiss: () -> Unit,
    onTune: (() -> Unit)? = null,
    onSignIn: (() -> Unit)? = null,
    onFetch: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: (() -> Unit) -> Unit = { then ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBlackColor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            SheetHeaderLayout {
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.profile_sections_hint, site.label, GalleryProfiles.MAX_SECTIONS),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onTune != null) {
                    FilledTonalIconButton(
                        onClick = onTune,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.more_image_settings))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            val sections = ProfileSection.of(site)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                sections.forEachIndexed { index, section ->
                    SectionRow(
                        section = section,
                        selected = section in chosen,
                        signedIn = signedIn,
                        first = index == 0,
                        last = index == sections.lastIndex,
                        onClick = { onChosen(GalleryProfiles.toggle(chosen, section)) }
                    )
                }
            }

            if (site == ProfileSite.X) {
                Spacer(modifier = Modifier.height(12.dp))
                XSwitches()
            }

            Spacer(modifier = Modifier.height(20.dp))
            val needsSignIn = !signedIn && chosen.any { it.signIn }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (onFetch != null && needsSignIn && onSignIn != null) {
                    FilledTonalButton(
                        onClick = onSignIn,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight)
                    ) {
                        Text(stringResource(R.string.no_results_sign_in), fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                Button(
                    onClick = { close(onFetch ?: onDismiss) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight)
                ) {
                    Text(
                        stringResource(if (onFetch != null) R.string.profile_fetch else R.string.profile_done),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** One section: a connected list row, round only at the ends of the list, tinted when on. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionRow(
    section: ProfileSection,
    selected: Boolean,
    signedIn: Boolean,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit
) {
    val outer = 22.dp
    val inner = 6.dp
    val shape = RoundedCornerShape(
        topStart = if (first) outer else inner,
        topEnd = if (first) outer else inner,
        bottomStart = if (last) outer else inner,
        bottomEnd = if (last) outer else inner
    )
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        if (selected) scheme.primary.copy(alpha = 0.14f) else sheetRaisedColor,
        label = "sectionFill"
    )
    Surface(onClick = onClick, shape = shape, color = fill, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(MaterialShapes.Cookie7Sided.toShape())
                    .background(if (selected) scheme.primary else scheme.onSurface.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                stringResource(section.label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) scheme.primary else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (section.signIn) {
                Spacer(modifier = Modifier.width(8.dp))
                StatePill(if (signedIn) AccountState.ACTIVE else AccountState.SIGNED_OUT)
            }
        }
    }
}

/** X's two switches, saved as they change: they shape what every X section includes. */
@Composable
private fun XSwitches() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val skipReposts by remember { SettingsRepository.getGallerySkipReposts(context) }.collectAsState(initial = true)
    val quotes by remember { SettingsRepository.getGalleryXQuotes(context) }.collectAsState(initial = false)
    Surface(shape = RoundedCornerShape(22.dp), color = sheetRaisedColor, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            ToggleRow(
                label = stringResource(R.string.profile_x_retweets),
                checked = !skipReposts,
                onCheckedChange = { on -> scope.launch { SettingsRepository.setGallerySkipReposts(context, !on) } }
            )
            ToggleRow(
                label = stringResource(R.string.profile_x_quotes),
                checked = quotes,
                onCheckedChange = { on -> scope.launch { SettingsRepository.setGalleryXQuotes(context, on) } }
            )
        }
    }
}

/**
 * The sheet as a setting: the saved sections of [site], saved as they change. Opened from the
 * three dots menu and from Image settings.
 */
@Composable
fun SavedProfileSectionsSheet(site: ProfileSite, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chosen by remember(site) { SettingsRepository.getGalleryProfileSections(context, site) }
        .collectAsState(initial = ProfileSection.defaults(site))
    val signedIn by remember(site) { com.hazel.android.data.CookieRepository.signsIn(context, listOf(site.home)) }
        .collectAsState(initial = false)
    ProfileSectionsSheet(
        site = site,
        title = stringResource(R.string.profile_sections_title, site.label),
        chosen = chosen,
        onChosen = { sections -> scope.launch { SettingsRepository.setGalleryProfileSections(context, site, sections) } },
        signedIn = signedIn,
        onDismiss = onDismiss
    )
}
