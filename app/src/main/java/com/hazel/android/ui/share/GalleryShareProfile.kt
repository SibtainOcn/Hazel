package com.hazel.android.ui.share

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hazel.android.HazelApp
import com.hazel.android.R
import com.hazel.android.data.CookieRepository
import com.hazel.android.data.SettingsRepository
import com.hazel.android.gallery.GalleryDlEngine
import com.hazel.android.gallery.GalleryDlException
import com.hazel.android.gallery.GalleryPost
import com.hazel.android.gallery.GalleryProfile
import com.hazel.android.gallery.ProfileSection
import com.hazel.android.ui.screens.cookies.CookieWebViewActivity
import com.hazel.android.ui.screens.gallery.ProfileSectionsSheet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The profile sections sheet over the app a profile was shared from, as the Gallery tab asks
 * it: it starts on the saved sections, and what Fetch takes is saved for next time.
 */
@Composable
internal fun ProfileAskSheet(
    profile: GalleryProfile,
    onFetch: (List<ProfileSection>) -> Unit,
    onTune: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var picked by remember(profile) { mutableStateOf<List<ProfileSection>?>(null) }
    val saved by remember(profile.site) { SettingsRepository.getGalleryProfileSections(context, profile.site) }
        .collectAsState(initial = null)
    val signedIn by remember(profile) { CookieRepository.signsIn(context, listOf(profile.home)) }
        .collectAsState(initial = false)
    // Signing in comes back to the sheet, which then shows the sign-in as done.
    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val chosen = picked ?: saved ?: return
    ProfileSectionsSheet(
        site = profile.site,
        title = stringResource(R.string.profile_fetch_from, profile.user),
        chosen = chosen,
        onChosen = { picked = it },
        signedIn = signedIn,
        onDismiss = onDismiss,
        onTune = onTune,
        onSignIn = { signInLauncher.launch(CookieWebViewActivity.intent(context, profile.home)) },
        onFetch = {
            val app = context.applicationContext
            HazelApp.instance.applicationScope.launch { SettingsRepository.setGalleryProfileSections(app, profile.site, chosen) }
            onFetch(chosen)
        }
    )
}

/** The sections to read for [profile]: asked for when the setting says so, else the saved ones. */
internal suspend fun profileSectionsToAsk(context: Context, profile: GalleryProfile): List<ProfileSection>? =
    if (SettingsRepository.getGalleryProfileAsk(context).first()) null
    else SettingsRepository.getGalleryProfileSections(context, profile.site).first()

/**
 * Each chosen section of [profile] as one post of its own link, with its files numbered as
 * gallery-dl counts them there, read in one run. A section that fails is left out while another
 * answers; when none does, the first failure is thrown.
 */
internal suspend fun readProfileSections(
    context: Context,
    profile: GalleryProfile,
    sections: List<ProfileSection>
): List<GalleryPost> {
    val chosen = sections.sortedBy { it.ordinal }
    val links = chosen.map { it.link(profile.user) }
    val limit = SettingsRepository.getGalleryPostsPerSearch(context).first()
    val read = arrayOfNulls<GalleryPost>(links.size)
    var failure: Throwable? = null
    GalleryDlEngine.listEach(context, links, postLimit = limit) { url, result ->
        val at = links.indexOf(url)
        result.onSuccess { listing ->
            if (listing.items.isNotEmpty()) {
                read[at] = GalleryPost(key = url, source = url, items = listing.items, section = chosen[at])
            }
        }.onFailure { if (failure == null) failure = it }
    }
    val posts = read.filterNotNull()
    if (posts.isEmpty()) {
        throw failure ?: GalleryDlException(GalleryDlException.Kind.NOT_FOUND, "no files")
    }
    return posts
}
