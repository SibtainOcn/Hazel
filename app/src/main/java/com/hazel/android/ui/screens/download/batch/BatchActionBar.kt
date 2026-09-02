package com.hazel.android.ui.screens.download.batch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.download.DownloadOptions

/**
 * The bar along the bottom of the batch sheet.
 *
 * Everything here changes a whole set of links at once, and each button opens a sheet or a
 * dialog of its own rather than expanding in place. Laying the settings out inline is what
 * made the sheet long enough to bury the list it is about, so they are kept one tap away
 * and the list keeps the height.
 *
 * Every setting has a button of its own rather than several of them sharing an overflow
 * menu: an overflow hides exactly the settings whose current value is worth seeing, and a
 * badge on a hidden button says nothing. The row scrolls sideways when they do not all fit,
 * which costs less than a menu that has to be opened before it can be read.
 *
 * What a button carries follows what it says. The first four each stand for a value the
 * button itself shows, so an icon is enough. The rest are the adjust-download options, and
 * they are named the same way the single download sheet names them, since an unlabelled
 * icon for "SponsorBlock" or "Filename template" is a guess rather than a control.
 *
 * The buttons act on the ticked links, or on all of them when nothing is ticked, which is
 * why nothing here says how many it covers: the count above the list already does.
 */
@Composable
fun BatchActionBar(
    isVideo: Boolean,
    qualityLabel: String,
    containerLabel: String,
    options: DownloadOptions,
    onDownloadType: () -> Unit,
    onQuality: () -> Unit,
    onSaveDir: () -> Unit,
    onContainer: () -> Unit,
    onThumbnail: () -> Unit,
    onChapters: () -> Unit,
    onSubtitles: () -> Unit,
    onSponsorBlock: () -> Unit,
    onFilename: () -> Unit,
    /** Only the sets holding a source with several soundtracks have this to offer. */
    showAudioLanguage: Boolean = false,
    audioLanguageLabel: String = "",
    onAudioLanguage: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = batchRaisedColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // The adjust-download options sit above the rest. They are named rather than
            // drawn, so they are wider than the buttons under them, and they wrap onto as
            // many lines as they need: a row that scrolls sideways hides the ones past the
            // edge, and a setting nobody can see is a setting nobody will use.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BarChip(
                    label = stringResource(R.string.batch_bar_thumbnail),
                    icon = Icons.Filled.Image,
                    selected = options.embedThumbnail,
                    onClick = onThumbnail
                )
                BarChip(
                    label = stringResource(R.string.batch_bar_chapters),
                    icon = Icons.Filled.Book,
                    badge = options.chapterBadge(isVideo),
                    onClick = onChapters
                )
                // Subtitles are left out of an audio download: there is nothing for
                // yt-dlp to attach them to, which is how the single sheet treats it too.
                if (isVideo) {
                    BarChip(
                        label = stringResource(R.string.batch_bar_subtitles),
                        icon = Icons.Filled.ClosedCaption,
                        badge = options.subtitleBadge,
                        onClick = onSubtitles
                    )
                }
                if (showAudioLanguage) {
                    BarChip(
                        label = audioLanguageLabel,
                        icon = Icons.Filled.Translate,
                        onClick = onAudioLanguage
                    )
                }
                BarChip(
                    label = stringResource(R.string.batch_bar_sponsorblock),
                    icon = Icons.Filled.Paid,
                    badge = options.sponsorBlockFilters.size,
                    onClick = onSponsorBlock
                )
                BarChip(
                    label = stringResource(R.string.batch_bar_filename_template),
                    icon = Icons.Filled.Edit,
                    onClick = onFilename
                )
            }

            // Ranged along the start rather than spread across the width: spreading them
            // put wide gaps between buttons that belong together and left the row reading
            // as a set of unrelated controls.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BarButton(
                    icon = if (isVideo) Icons.Filled.Videocam else Icons.Filled.MusicNote,
                    description = stringResource(R.string.batch_bar_download_type),
                    onClick = onDownloadType
                )
                BarButton(
                    icon = Icons.Filled.HighQuality,
                    description = stringResource(R.string.batch_bar_quality, qualityLabel),
                    onClick = onQuality
                )
                BarButton(
                    icon = Icons.Filled.Folder,
                    description = stringResource(R.string.batch_bar_save_location),
                    onClick = onSaveDir
                )
                // Carries its value rather than an icon: a container is a name, and a
                // picture of one would have to be learned before it said anything.
                BarButton(
                    text = containerLabel,
                    description = stringResource(R.string.batch_bar_container),
                    onClick = onContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarButton(
    description: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    text: String? = null
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = description,
                modifier = Modifier.size(24.dp),
                tint = tint
            )
        } else if (text != null) {
            Text(
                text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
        }
    }
}

/**
 * One adjust-download option, drawn the way the single download sheet draws it so the same
 * setting looks the same wherever it is reached from.
 */
@Composable
private fun BarChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    selected: Boolean = false,
    badge: Int = 0
) {
    val chip = @Composable {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    if (selected) Icons.Filled.Check else icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                selectedLabelColor = MaterialTheme.colorScheme.primary,
                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
            )
        )
    }

    if (badge > 0) {
        BadgedBox(badge = { Badge { Text("$badge") } }) { chip() }
    } else {
        chip()
    }
}
