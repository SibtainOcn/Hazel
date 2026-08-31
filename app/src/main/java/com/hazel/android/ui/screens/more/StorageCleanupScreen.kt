package com.hazel.android.ui.screens.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.download.formatFileSize
import com.hazel.android.util.TempCategory
import com.hazel.android.util.TempStorage
import kotlinx.coroutines.launch

/**
 * Shows what the app is holding on disk beyond the user's downloads, and clears it.
 *
 * Every working location the app writes to is listed, so space cannot go missing into a
 * directory the user has no way to see or empty. Downloaded media is never listed here,
 * because by this point it has been moved out of the app's own storage.
 */
@Composable
fun StorageCleanupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var categories by remember { mutableStateOf<List<TempCategory>>(emptyList()) }
    var confirming by remember { mutableStateOf<TempCategory?>(null) }
    var confirmingAll by remember { mutableStateOf(false) }

    suspend fun refresh() {
        categories = TempStorage.categories(context)
    }

    LaunchedEffect(Unit) { refresh() }

    val total = categories.sumOf { it.bytes }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cleanup_back))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.cleanup_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    formatFileSize(total).ifBlank { "0 KB" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.cleanup_header_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            categories.forEachIndexed { index, category ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = category.bytes > 0) { confirming = category }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(category.label, fontWeight = FontWeight.Medium)
                        Text(
                            category.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        formatFileSize(category.bytes).ifBlank { "0 KB" },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (category.bytes > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            onClick = { confirmingAll = true },
            enabled = total > 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(
                alpha = if (total > 0) 1f else 0.4f
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    stringResource(R.string.cleanup_action_clear_everything),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    confirming?.let { category ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(R.string.cleanup_confirm_category_title, category.label.lowercase()), fontWeight = FontWeight.Bold) },
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
            title = { Text(stringResource(R.string.cleanup_confirm_all_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(
                        R.string.cleanup_confirm_all_body,
                        formatFileSize(total)
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
