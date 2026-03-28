package com.hazel.android.ui.screens.converter

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hazel.android.R
import com.hazel.android.converter.ConverterViewModel
import com.hazel.android.download.LogLevel

@Composable
fun ConverterScreen(
    converterViewModel: ConverterViewModel = viewModel()
) {
    val state by converterViewModel.state.collectAsState()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { converterViewModel.selectFile(context, it) }
    }

    val logListState = rememberLazyListState()
    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) logListState.animateScrollToItem(state.logs.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // Header
        Text(
            text = "Converter",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Video to Audio",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // File Picker
        OutlinedButton(
            onClick = { filePickerLauncher.launch(arrayOf("video/*")) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = !state.isConverting
        ) {
            Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = state.inputFileName.ifBlank { "Choose a video file" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quality Options
        Text(
            text = "Output Quality",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))

        val qualities = listOf(
            Triple("320kbps", "MP3 · High Quality", "Best MP3, most compatible"),
            Triple("AAC 256k", "AAC · Premium", "Better quality per size"),
            Triple("FLAC", "FLAC · Lossless", "Perfect copy, large file"),
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            qualities.forEach { (id, title, subtitle) ->
                val isSelected = state.selectedQuality == id
                Surface(
                    onClick = { if (!state.isConverting) converterViewModel.setQuality(id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.AudioFile, null, modifier = Modifier.size(20.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                title, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                        if (isSelected) {
                            Icon(Icons.Filled.Check, null, Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Convert Button
        Button(
            onClick = { converterViewModel.convert(context) },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            enabled = state.inputFileUri != null && !state.isConverting,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (state.isConverting) {
                CircularProgressIndicator(Modifier.size(18.dp), MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Converting...", style = MaterialTheme.typography.titleMedium)
            } else {
                Icon(Icons.Filled.Transform, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Convert", style = MaterialTheme.typography.titleMedium)
            }
        }

        // ── Progress + Logs ──
        AnimatedVisibility(
            visible = state.isConverting || state.isComplete || state.error != null,
            enter = fadeIn(tween(300)) + slideInVertically(tween(300))
        ) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                if (state.isConverting) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Step logs with shimmer on active entry
                LazyColumn(
                    state = logListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(state.logs) { index, entry ->
                        val isLast = index == state.logs.lastIndex && entry.durationMs == null
                        ConverterLogRow(entry, isActive = isLast)
                    }
                }

                // Completion row — tick + filename + path + Open/Next buttons
                if (state.isComplete) {
                    Spacer(Modifier.height(8.dp))
                    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.small_right_tick),
                                    contentDescription = null,
                                    tint = com.hazel.android.ui.theme.SuccessGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    state.outputFileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                state.outputPath,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Open folder — opens converted output folder in file manager
                                Button(
                                    onClick = {
                                        val folder = com.hazel.android.util.StoragePaths.finalConverted
                                        openConverterFolderInFileManager(context, folder)
                                    },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Open", style = MaterialTheme.typography.labelMedium)
                                }
                                // Next conversion
                                Button(
                                    onClick = { converterViewModel.resetState() },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Text("Next", style = MaterialTheme.typography.labelMedium)
                                }
                        }
                        }
                    }
                }

                // Error card
                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Conversion Failed", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(4.dp))
                            Text(state.error ?: "", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Log row with custom SVG icons and shimmer on active entry.
 */
@Composable
private fun ConverterLogRow(log: com.hazel.android.download.LogEntry, isActive: Boolean) {
    val shimmerBrush = if (isActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
        val translateAnim by infiniteTransition.animateFloat(
            initialValue = -300f, targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = RepeatMode.Restart
            ), label = "shimmerTranslate"
        )
        androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            ),
            start = androidx.compose.ui.geometry.Offset(translateAnim, 0f),
            end = androidx.compose.ui.geometry.Offset(translateAnim + 400f, 0f)
        )
    } else null

    val iconRes = when (log.level) {
        LogLevel.SUCCESS -> R.drawable.small_right_tick
        LogLevel.ERROR -> R.drawable.small_error_x
        LogLevel.WARN -> R.drawable.small_warn
        LogLevel.INFO -> R.drawable.small_chevron
    }
    val tintColor = when (log.level) {
        LogLevel.SUCCESS -> com.hazel.android.ui.theme.SuccessGreen
        LogLevel.ERROR -> com.hazel.android.ui.theme.ErrorRed
        LogLevel.WARN -> com.hazel.android.ui.theme.WarningAmber
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    }

    val durationStr = log.durationMs?.let { " (${it}ms)" } ?: ""

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))

        if (shimmerBrush != null) {
            Text(
                log.message + durationStr,
                style = TextStyle(brush = shimmerBrush, fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            Text(
                log.message,
                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                color = when (log.level) {
                    LogLevel.SUCCESS -> com.hazel.android.ui.theme.SuccessGreen
                    LogLevel.ERROR -> com.hazel.android.ui.theme.ErrorRed
                    LogLevel.WARN -> com.hazel.android.ui.theme.WarningAmber
                    LogLevel.INFO -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                },
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // Duration badge
        if (log.durationMs != null) {
            Text(
                "%.2fs".format(log.durationMs / 1000.0),
                fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

/**
 * Opens a folder in the system's default file manager.
 */
private fun openConverterFolderInFileManager(context: android.content.Context, folder: java.io.File) {
    if (!folder.exists()) folder.mkdirs()
    try {
        val relativePath = folder.absolutePath.substringAfter("/storage/emulated/0/")
        val encodedPath = relativePath.replace("/", "%2F")
        val uri = android.net.Uri.parse(
            "content://com.android.externalstorage.documents/document/primary:$encodedPath"
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "vnd.android.document/directory")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", folder
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("file://${folder.absolutePath}")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Open folder"))
            } catch (_: Exception) {
                android.widget.Toast.makeText(
                    context, "Path: ${folder.absolutePath}", android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
