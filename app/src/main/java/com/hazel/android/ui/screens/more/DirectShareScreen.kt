package com.hazel.android.ui.screens.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import kotlinx.coroutines.launch

/**
 * What the direct share target does, since it does it without asking.
 *
 * The ordinary share target opens the sheet and every choice is made there. The direct one
 * makes none, so the choices have to exist somewhere, and this is that somewhere. Only the
 * two that change what file lands on the device are here; container, naming and destination
 * are already settings of their own and are shared with every other download.
 */
@Composable
fun DirectShareScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isVideo by SettingsRepository.getQuickIsVideo(context).collectAsState(initial = true)
    val maxHeight by SettingsRepository.getQuickMaxHeight(context).collectAsState(initial = 0)

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
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.direct_share_back)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Hazel Instant",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            "Sharing a link to Hazel Instant starts the download straight away, with no " +
                    "sheet and no questions. These are the answers it uses.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = Modifier.padding(bottom = 20.dp)
        )

        SettingGroup(title = stringResource(R.string.direct_share_save_as)) {
            ChoiceRow(
                label = stringResource(R.string.direct_share_video),
                description = "Picture and sound, muxed into one file.",
                selected = isVideo,
                onSelect = { scope.launch { SettingsRepository.setQuickIsVideo(context, true) } }
            )
            ChoiceRow(
                label = stringResource(R.string.direct_share_audio_only),
                description = "Sound alone, at the best the source offers.",
                selected = !isVideo,
                onSelect = { scope.launch { SettingsRepository.setQuickIsVideo(context, false) } }
            )
        }

        if (isVideo) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Quality limit",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "A ceiling, not an exact size. Sources do not all offer the same heights, " +
                        "so the tallest that fits under this is taken.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                HEIGHT_CHOICES.forEach { (height, label) ->
                    ChoiceRow(
                        label = label,
                        description = null,
                        selected = maxHeight == height,
                        onSelect = {
                            scope.launch { SettingsRepository.setQuickMaxHeight(context, height) }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(12.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        content()
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    description: String?,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }
    }
}

/** Height ceilings offered, coarsest first. 0 means whatever the source calls best. */
private val HEIGHT_CHOICES = listOf(
    0 to "Best available",
    2160 to "Up to 4K",
    1440 to "Up to 1440p",
    1080 to "Up to 1080p",
    720 to "Up to 720p",
    480 to "Up to 480p",
    360 to "Up to 360p"
)
