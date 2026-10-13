package com.hazel.android.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hazel.android.R
import com.hazel.android.download.extractor.WebSearch
import com.hazel.android.ui.components.EqualWidthActions

/**
 * Who answers Web search: DuckDuckGo, which needs nothing set up, or a SearXNG server the
 * person runs or trusts. Choosing SearXNG asks for its address, and Save stays off until
 * the address is one.
 *
 * [server] is the saved address, blank for DuckDuckGo; [onSave] gets the new one, blank
 * again for DuckDuckGo.
 */
@Composable
internal fun WebSearchDialog(server: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var useServer by rememberSaveable { mutableStateOf(server.isNotBlank()) }
    var address by rememberSaveable { mutableStateOf(server) }
    val normalized = WebSearch.normalizeServer(address)
    val canSave = !useServer || normalized != null

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(top = 24.dp, bottom = 16.dp)
            ) {
                Text(
                    stringResource(R.string.fetch_settings_web_search),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                )
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ChoiceRow(
                        label = stringResource(R.string.web_search_duckduckgo),
                        description = stringResource(R.string.web_search_duckduckgo_summary),
                        chosen = !useServer,
                        role = Role.RadioButton,
                        shape = groupShape(0, 2),
                        onClick = { useServer = false }
                    )
                    ChoiceRow(
                        label = stringResource(R.string.web_search_searxng),
                        description = stringResource(R.string.web_search_searxng_summary),
                        chosen = useServer,
                        role = Role.RadioButton,
                        shape = groupShape(1, 2),
                        onClick = { useServer = true }
                    )
                }

                if (useServer) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AddressField(
                        value = address,
                        onValueChange = { address = it },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    if (address.isNotBlank() && normalized == null) {
                        Text(
                            stringResource(R.string.web_search_server_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 28.dp, end = 24.dp, top = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                EqualWidthActions(modifier = Modifier.padding(horizontal = 16.dp)) {
                    DialogButton(stringResource(R.string.download_cancel), filled = false, onClick = onDismiss)
                    DialogButton(
                        stringResource(R.string.options_text_input_save),
                        filled = true,
                        enabled = canSave,
                        onClick = { onSave(if (useServer) normalized.orEmpty() else "") }
                    )
                }
            }
        }
    }
}

/** What Web search is set to, for a settings row: DuckDuckGo, or the SearXNG server's host. */
@Composable
internal fun webSearchLabel(server: String): String =
    if (server.isBlank()) stringResource(R.string.web_search_duckduckgo)
    else runCatching { java.net.URI(server).host }.getOrNull() ?: server

/** The server's address: a flat tonal field, with its hint until something is typed. */
@Composable
private fun AddressField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.web_search_server_hint)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = modifier.fillMaxWidth()
    )
}
