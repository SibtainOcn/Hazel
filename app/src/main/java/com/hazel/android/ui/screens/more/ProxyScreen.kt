package com.hazel.android.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.AdvancedSettingsStore
import com.hazel.android.download.NetworkProxy
import com.hazel.android.ui.components.FlatChip
import kotlinx.coroutines.launch

/**
 * Sets the proxy: its kind, server and, if it needs one, a login. Saving turns the proxy on;
 * removing it forgets the address and connects directly again. The switch on the Advanced
 * screen turns a saved proxy off and on without touching it.
 *
 * Nothing is saved that some part of the app could not use, so a typo can never leave some
 * connections going out directly while others go through the proxy.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProxyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by remember(context) { SettingsRepository.getAdvancedSettings(context) }
        .collectAsState(initial = AdvancedSettingsStore.current)
    // The fields start from what was saved when the screen opened.
    val saved = remember { NetworkProxy.parse(AdvancedSettingsStore.current.proxy) }

    var scheme by rememberSaveable { mutableStateOf(saved?.scheme ?: NetworkProxy.SCHEMES.first()) }
    var host by rememberSaveable { mutableStateOf(saved?.host?.removeSurrounding("[", "]").orEmpty()) }
    var port by rememberSaveable { mutableStateOf(saved?.port?.toString().orEmpty()) }
    var user by rememberSaveable { mutableStateOf(saved?.user.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(saved?.password.orEmpty()) }
    var showPassword by remember { mutableStateOf(false) }
    // Mistakes are pointed out once Save has been tried, not while the first field is typed.
    var tried by remember { mutableStateOf(false) }

    val portOk = port.trim().toIntOrNull()?.let { it in 1..65535 } == true
    val built = NetworkProxy.build(scheme, host, port, user, password)
    val hostError = tried && (host.isBlank() || (portOk && built == null))
    val portError = tried && !portOk

    fun saveProxy() {
        tried = true
        val address = built ?: return
        scope.launch {
            SettingsRepository.setAdvancedSettings(context, settings.copy(proxy = address, proxyEnabled = true))
            onBack()
        }
    }

    val plain = KeyboardOptions(
        capitalization = KeyboardCapitalization.None,
        autoCorrectEnabled = false,
        keyboardType = KeyboardType.Uri,
        imeAction = ImeAction.Next
    )

    SettingsScreen(
        title = stringResource(R.string.advanced_proxy),
        description = stringResource(R.string.advanced_proxy_hint),
        onBack = onBack
    ) {
        SettingsSection(
            title = stringResource(R.string.proxy_section_server),
            rows = listOf<@Composable () -> Unit>({
                Column(modifier = Modifier.padding(16.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NetworkProxy.SCHEMES.forEach { kind ->
                            // A check as well as the accent, so the choice shows with any accent colour.
                            FlatChip(
                                label = schemeName(kind),
                                selected = kind == scheme,
                                onClick = { scheme = kind },
                                leading = if (kind == scheme) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text(stringResource(R.string.proxy_host)) },
                        singleLine = true,
                        isError = hostError,
                        supportingText = if (hostError) {
                            { Text(stringResource(R.string.proxy_host_invalid)) }
                        } else null,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = plain,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { text -> port = text.filter { it.isDigit() }.take(5) },
                        label = { Text(stringResource(R.string.proxy_port)) },
                        singleLine = true,
                        isError = portError,
                        supportingText = if (portError) {
                            { Text(stringResource(R.string.proxy_port_invalid)) }
                        } else null,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = plain.copy(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            })
        )

        SettingsSection(
            title = stringResource(R.string.proxy_section_login),
            rows = listOf<@Composable () -> Unit>({
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text(stringResource(R.string.proxy_user)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = plain.copy(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.proxy_password)) },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = stringResource(
                                        if (showPassword) R.string.proxy_hide_password else R.string.proxy_show_password
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = plain.copy(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { saveProxy() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            })
        )

        Button(
            onClick = ::saveProxy,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(stringResource(R.string.options_text_input_save), fontWeight = FontWeight.SemiBold)
        }

        if (saved != null) {
            Spacer(modifier = Modifier.height(8.dp))
            FlatSettingButton(
                icon = Icons.Filled.Delete,
                text = stringResource(R.string.proxy_remove),
                onClick = {
                    scope.launch {
                        SettingsRepository.setAdvancedSettings(context, settings.copy(proxy = "", proxyEnabled = false))
                        onBack()
                    }
                }
            )
        }
    }
}

/** A kind of proxy as people write it. Not translated: these are the names of protocols. */
private fun schemeName(scheme: String): String = when (scheme) {
    "socks5h" -> "SOCKS5h"
    else -> scheme.uppercase(java.util.Locale.ROOT)
}
