package com.hazel.android.ui.screens.more

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Badge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.AdvancedSettings
import com.hazel.android.download.AdvancedSettingsStore
import com.hazel.android.ui.screens.download.TextInputDialog
import kotlinx.coroutines.launch

/**
 * Engine options for sources the defaults do not reach: YouTube's player clients and
 * proof-of-origin tokens, a pause between requests, and raw yt-dlp arguments.
 *
 * Everything here is off or empty until it is set, so the screen changes nothing for anyone
 * who does not need it.
 */
@Composable
fun AdvancedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by SettingsRepository.getAdvancedSettings(context)
        .collectAsState(initial = AdvancedSettingsStore.current)

    var dialog by remember { mutableStateOf(AdvancedDialog.NONE) }
    val close = { dialog = AdvancedDialog.NONE }
    fun save(change: (AdvancedSettings) -> AdvancedSettings) {
        scope.launch { SettingsRepository.setAdvancedSettings(context, change(settings)) }
    }

    val notSet = stringResource(R.string.advanced_not_set)

    SettingsScreen(
        title = stringResource(R.string.advanced_title),
        description = stringResource(R.string.advanced_description),
        onBack = onBack
    ) {
        SettingsSection(
            title = stringResource(R.string.advanced_section_youtube),
            rows = listOf<@Composable () -> Unit>(
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.advanced_player_clients),
                        value = settings.playerClients.joinToString(", ")
                            .ifBlank { stringResource(R.string.advanced_automatic) },
                        onClick = { dialog = AdvancedDialog.PLAYER_CLIENTS }
                    )
                },
                {
                    val valid = settings.validPoTokens().size
                    val entered = settings.poTokens.split(',', '\n').count { it.isNotBlank() }
                    ValueSettingRow(
                        icon = Icons.Filled.Key,
                        title = stringResource(R.string.advanced_po_tokens),
                        value = when {
                            entered == 0 -> notSet
                            valid == entered -> stringResource(R.string.advanced_po_tokens_set, valid)
                            else -> stringResource(R.string.advanced_po_tokens_invalid, entered - valid)
                        },
                        onClick = { dialog = AdvancedDialog.PO_TOKENS }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Badge,
                        title = stringResource(R.string.advanced_visitor_data),
                        value = settings.visitorData.ifBlank { notSet },
                        onClick = { dialog = AdvancedDialog.VISITOR_DATA }
                    )
                },
                {
                    SwitchSettingRow(
                        icon = Icons.Filled.Translate,
                        title = stringResource(R.string.advanced_metadata_language),
                        summary = stringResource(R.string.advanced_metadata_language_summary),
                        checked = settings.metadataInAppLanguage,
                        onCheckedChange = { on -> save { it.copy(metadataInAppLanguage = on) } }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Code,
                        title = stringResource(R.string.advanced_youtube_args),
                        value = settings.youtubeExtraArgs.ifBlank { notSet },
                        onClick = { dialog = AdvancedDialog.YOUTUBE_ARGS }
                    )
                }
            )
        )

        SettingsSection(
            title = stringResource(R.string.advanced_section_engine),
            rows = listOf<@Composable () -> Unit>(
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Timer,
                        title = stringResource(R.string.advanced_sleep_requests),
                        value = if (settings.sleepRequestsSeconds > 0) {
                            stringResource(R.string.advanced_sleep_seconds, settings.sleepRequestsSeconds)
                        } else {
                            stringResource(R.string.advanced_sleep_off)
                        },
                        onClick = { dialog = AdvancedDialog.SLEEP }
                    )
                },
                {
                    SwitchSettingRow(
                        icon = Icons.Filled.Security,
                        title = stringResource(R.string.advanced_no_check_certificates),
                        summary = stringResource(R.string.advanced_no_check_certificates_summary),
                        checked = settings.noCheckCertificates,
                        onCheckedChange = { on -> save { it.copy(noCheckCertificates = on) } }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Terminal,
                        title = stringResource(R.string.advanced_extra_args),
                        value = settings.downloadExtraArgs.ifBlank { notSet },
                        onClick = { dialog = AdvancedDialog.EXTRA_ARGS }
                    )
                }
            )
        )

        FlatSettingButton(
            icon = Icons.Filled.RestartAlt,
            text = stringResource(R.string.advanced_reset),
            onClick = { dialog = AdvancedDialog.RESET }
        )
    }

    when (dialog) {
        AdvancedDialog.NONE -> Unit

        AdvancedDialog.PLAYER_CLIENTS -> MultiChoiceDialog(
            title = stringResource(R.string.advanced_player_clients),
            choices = (listOf("default") + AdvancedSettings.PLAYER_CLIENTS).map { it to it },
            selected = settings.playerClients.toSet(),
            onConfirm = { ticked ->
                // Kept in the order they are listed, which is the order yt-dlp tries them.
                val ordered = (listOf("default") + AdvancedSettings.PLAYER_CLIENTS).filter { it in ticked }
                save { it.copy(playerClients = ordered) }
                close()
            },
            onDismiss = close
        )

        AdvancedDialog.PO_TOKENS -> TextInputDialog(
            title = stringResource(R.string.advanced_po_tokens),
            hint = stringResource(R.string.advanced_po_tokens_hint),
            value = settings.poTokens,
            onConfirm = { text ->
                save { it.copy(poTokens = text) }
                close()
            },
            onDismiss = close
        )

        AdvancedDialog.VISITOR_DATA -> TextInputDialog(
            title = stringResource(R.string.advanced_visitor_data),
            hint = stringResource(R.string.advanced_visitor_data_hint),
            value = settings.visitorData,
            onConfirm = { text ->
                save { it.copy(visitorData = text) }
                close()
            },
            onDismiss = close
        )

        AdvancedDialog.YOUTUBE_ARGS -> TextInputDialog(
            title = stringResource(R.string.advanced_youtube_args),
            hint = stringResource(R.string.advanced_youtube_args_hint),
            value = settings.youtubeExtraArgs,
            onConfirm = { text ->
                save { it.copy(youtubeExtraArgs = text) }
                close()
            },
            onDismiss = close
        )

        AdvancedDialog.SLEEP -> SingleChoiceDialog(
            title = stringResource(R.string.advanced_sleep_requests),
            choices = SLEEP_STEPS.map { seconds ->
                seconds to if (seconds == 0) stringResource(R.string.advanced_sleep_off)
                else stringResource(R.string.advanced_sleep_seconds, seconds)
            },
            selected = settings.sleepRequestsSeconds,
            onSelect = { seconds ->
                save { it.copy(sleepRequestsSeconds = seconds) }
                close()
            },
            onDismiss = close
        )

        AdvancedDialog.EXTRA_ARGS -> TextInputDialog(
            title = stringResource(R.string.advanced_extra_args),
            hint = stringResource(R.string.advanced_extra_args_hint),
            value = settings.downloadExtraArgs,
            onConfirm = { text ->
                save { it.copy(downloadExtraArgs = text) }
                close()
            },
            onDismiss = close
        )

        AdvancedDialog.RESET -> ConfirmSettingDialog(
            title = stringResource(R.string.advanced_reset_title),
            body = stringResource(R.string.advanced_reset_body),
            confirm = stringResource(R.string.format_sheet_reset),
            onConfirm = {
                scope.launch { SettingsRepository.setAdvancedSettings(context, AdvancedSettings()) }
                close()
            },
            onDismiss = close
        )
    }
}

private enum class AdvancedDialog {
    NONE, PLAYER_CLIENTS, PO_TOKENS, VISITOR_DATA, YOUTUBE_ARGS, SLEEP, EXTRA_ARGS, RESET
}

/** The pauses offered between requests, in seconds. */
private val SLEEP_STEPS = listOf(0, 1, 2, 5, 10)
