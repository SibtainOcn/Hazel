package com.hazel.android.ui.screens.more

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import com.hazel.android.ui.components.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.download.formatFileSize
import com.hazel.android.ui.theme.InfoBlue
import com.hazel.android.ui.theme.SuccessGreen
import com.hazel.android.ui.theme.WarningAmber
import com.hazel.android.util.TempCategory
import com.hazel.android.util.TempStorage
import kotlinx.coroutines.launch

/**
 * Shows what the app is holding on disk beyond the user's downloads, and clears it.
 *
 * Every working location the app writes to is listed, so space cannot go missing into a
 * directory the user has no way to see or empty. Downloaded media is never listed here,
 * because by this point it has been moved out of the app's own storage.
 *
 * Each category has its own colour, used for its icon and for its share of the bar in the
 * summary card, so the bar reads as a key to the list below it. Rows that hold something
 * carry a delete mark in their size chip, which is what tells the user a row can be tapped;
 * an empty row has a plain chip and cannot be tapped.
 *
 * The title bar, the summary and Clear everything stay put; the categories scroll
 * between them, so the main action is always in reach however long the list runs at a
 * large font.
 *
 * [sample] replaces what is read from disk, for layout tests and previews only; the
 * screen then shows those sizes and never reads storage until something is cleared.
 */
@Composable
fun StorageCleanupScreen(onBack: () -> Unit, sample: List<TempCategory>? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var categories by remember { mutableStateOf(sample ?: emptyList()) }
    var confirming by remember { mutableStateOf<TempCategory?>(null) }
    var confirmingAll by remember { mutableStateOf(false) }

    suspend fun refresh() {
        categories = TempStorage.categories(context)
    }

    LaunchedEffect(Unit) { if (sample == null) refresh() }

    val total = categories.sumOf { it.bytes }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = stringResource(R.string.cleanup_back))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.cleanup_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
            SummaryCard(categories = categories, total = total)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {

            // One group of separate tiles: the ends of the group are fully rounded, the joins
            // between tiles only slightly, so the list reads as one set of distinct buttons.
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                categories.forEachIndexed { index, category ->
                    CategoryTile(
                        category = category,
                        first = index == 0,
                        last = index == categories.lastIndex,
                        onClick = { confirming = category }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
            ClearEverythingButton(enabled = total > 0, onClick = { confirmingAll = true })
        }
    }

    confirming?.let { category ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            icon = {
                Icon(
                    categoryIcon(category.id),
                    contentDescription = null,
                    tint = categoryColor(category.id)
                )
            },
            title = { Text(stringResource(R.string.cleanup_confirm_category_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (category.safeToClear) {
                        stringResource(
                            R.string.cleanup_confirm_category_safe,
                            formatFileSize(category.bytes)
                        )
                    } else {
                        stringResource(
                            R.string.cleanup_confirm_category_unsafe,
                            formatFileSize(category.bytes)
                        )
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = category.id
                    confirming = null
                    scope.launch {
                        TempStorage.clear(context, id)
                        refresh()
                    }
                }) { Text(stringResource(R.string.cleanup_confirm_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.cleanup_confirm_cancel)) }
            }
        )
    }

    if (confirmingAll) {
        AlertDialog(
            onDismissRequest = { confirmingAll = false },
            icon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
            title = { Text(stringResource(R.string.cleanup_confirm_all_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(
                        R.string.cleanup_confirm_all_body,
                        formatFileSize(total).ifBlank { "0 KB" }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ids = categories.map { it.id }
                    confirmingAll = false
                    scope.launch {
                        ids.forEach { TempStorage.clear(context, it) }
                        refresh()
                    }
                }) { Text(stringResource(R.string.cleanup_confirm_all_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingAll = false }) { Text(stringResource(R.string.cleanup_confirm_all_cancel)) }
            }
        )
    }
}

/**
 * The total, what it is, and a bar split by category in the colours the rows use.
 *
 * The bar is left out while nothing is held, since an empty track says nothing the
 * "0 KB" above it does not.
 */
@Composable
private fun SummaryCard(categories: List<TempCategory>, total: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(horizontal = 22.dp, vertical = 22.dp)
        ) {
            Text(
                formatFileSize(total).ifBlank { "0 KB" },
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                stringResource(R.string.cleanup_header_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (total > 0) {
                Spacer(modifier = Modifier.height(18.dp))
                UsageBar(categories = categories, total = total)
            }
        }
    }
}

/**
 * Each category's share of [total] as a rounded segment. A category that holds anything gets
 * at least a sliver, so a few kilobytes beside a hundred megabytes still shows up.
 */
@Composable
private fun UsageBar(categories: List<TempCategory>, total: Long) {
    val held = categories.filter { it.bytes > 0 }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        held.forEach { category ->
            // Keyed by category, so clearing one does not hand its animation to the next.
            key(category.id) {
                val share by animateFloatAsState(
                    targetValue = (category.bytes.toFloat() / total).coerceAtLeast(0.03f),
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "share_${category.id}"
                )
                Box(
                    modifier = Modifier
                        .weight(share)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(categoryColor(category.id))
                )
            }
        }
    }
}

/**
 * One category as a tile: its icon on a coloured shape with the size under it, then the name
 * and what it is. A tile that holds something has a delete mark in its size chip, which is
 * what tells the user it can be tapped; an empty one has a plain grey chip.
 *
 * At large font sizes the icon and chip sit in a line above the text instead, so the text
 * keeps the full width of the tile and a long word is never broken to fit beside them.
 *
 * Pressing it rounds every corner out fully, the Material 3 Expressive press response, so
 * the tile visibly answers the finger before the dialog opens.
 */
@Composable
private fun CategoryTile(
    category: TempCategory,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit
) {
    val hasFiles = category.bytes > 0
    val accent = categoryColor(category.id)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val compact = LocalDensity.current.fontScale < 1.3f

    val outer = 24.dp
    val inner = 6.dp
    val spec = spring<Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    val top by animateDpAsState(if (first || pressed) outer else inner, spec, label = "top")
    val bottom by animateDpAsState(if (last || pressed) outer else inner, spec, label = "bottom")
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)

    val container by animateColorAsState(
        if (hasFiles) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "container"
    )

    val tile = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(container)
        .clickable(
            enabled = hasFiles,
            interactionSource = interaction,
            indication = androidx.compose.material3.ripple(),
            role = Role.Button,
            onClickLabel = stringResource(R.string.cleanup_confirm_clear),
            onClick = onClick
        )
        .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)

    if (compact) {
        Row(modifier = tile, verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier.width(88.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CategoryBadge(category.id, accent, hasFiles)
                Spacer(modifier = Modifier.height(6.dp))
                SizeChip(bytes = category.bytes, accent = accent)
            }
            Spacer(modifier = Modifier.width(10.dp))
            CategoryText(category, modifier = Modifier.weight(1f))
        }
    } else {
        Column(modifier = tile) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(category.id, accent, hasFiles)
                Spacer(modifier = Modifier.width(10.dp))
                SizeChip(
                    bytes = category.bytes,
                    accent = accent,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            CategoryText(category)
        }
    }
}

/** The category's icon on its scalloped, tinted badge. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryBadge(id: String, accent: Color, hasFiles: Boolean) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(accent.copy(alpha = if (hasFiles) 0.18f else 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            categoryIcon(id),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (hasFiles) accent else accent.copy(alpha = 0.5f)
        )
    }
}

/** The category's name and what it is. */
@Composable
private fun CategoryText(category: TempCategory, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            stringResource(category.labelRes),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium.copy(hyphens = Hyphens.Auto)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            stringResource(category.descriptionRes),
            style = MaterialTheme.typography.bodySmall.copy(hyphens = Hyphens.Auto),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The size as a pill, with a delete mark in it while there is something to clear. Never
 * wider than its slot: a size too long for it ends in an ellipsis rather than wrapping.
 */
@Composable
private fun SizeChip(bytes: Long, accent: Color, modifier: Modifier = Modifier) {
    val hasFiles = bytes > 0
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (hasFiles) accent.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.padding(
                start = if (hasFiles) 5.dp else 8.dp,
                end = 7.dp,
                top = 2.dp,
                bottom = 2.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasFiles) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = accent
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
            Text(
                formatFileSize(bytes).ifBlank { "0 KB" },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (hasFiles) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The pinned destructive action, compact, with the expressive press-to-squircle shape morph. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClearEverythingButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        contentPadding = ButtonDefaults.contentPaddingFor(48.dp)
    ) {
        Icon(
            Icons.Filled.DeleteSweep,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(48.dp))
        )
        Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(48.dp)))
        Text(
            stringResource(R.string.cleanup_action_clear_everything),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/** A distinct hue per category, shared by its icon and its segment of the usage bar. */
@Composable
private fun categoryColor(id: String): Color = when (id) {
    "ytdlp_cache" -> MaterialTheme.colorScheme.primary
    TempStorage.LINK_READS -> InfoBlue
    "partial_downloads" -> WarningAmber
    "converted" -> SuccessGreen
    else -> OtherCacheViolet
}

private val OtherCacheViolet = Color(0xFFB388FF)

private fun categoryIcon(id: String): ImageVector = when (id) {
    "ytdlp_cache" -> Icons.Outlined.Link
    TempStorage.LINK_READS -> Icons.Outlined.History
    "partial_downloads" -> Icons.Outlined.Downloading
    "converted" -> Icons.Outlined.GraphicEq
    "other_cache" -> Icons.Outlined.Image
    else -> Icons.Outlined.FolderOpen
}
