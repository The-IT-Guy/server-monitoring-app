package com.calltheitguy.monitor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private val DialogBackground = Color(0xFF101720)
private val DialogSurface = Color(0xFF121B26)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecondary = Color(0xFF8FA3B8)
private val BorderColor = Color(0xFF263241)
private val AccentBlue = Color(0xFF4DA3FF)
private val GoodColor = Color(0xFF2AD39B)
private val AmberColor = Color(0xFFFFC857)


data class AddServerRequest(
    val nickname: String,
    val hostOrIp: String,
    val checkPing: Boolean,
    val socketPort: Int?,
    val checkSsl: Boolean,
    val checkDomain: Boolean,
    val intervalMinutes: Int,
)

private data class PollingOption(
    val label: String,
    val valueMinutes: Int,
    val helper: String,
)

private val PollingOptions = listOf(
    PollingOption("5 min", 5, "Uses chained one-time WorkManager runs. Android may defer execution under battery policy."),
    PollingOption("15 min", 15, "Recommended background polling interval."),
    PollingOption("1 hour", 60, "Lower-noise monitoring for stable systems."),
    PollingOption("Manual Only", 0, "No recurring schedule. Use Check Now."),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddServerDialog(
    onDismiss: () -> Unit,
    onAddServer: (AddServerRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nickname by rememberSaveable { mutableStateOf("") }
    var hostOrIp by rememberSaveable { mutableStateOf("") }
    var checkPing by rememberSaveable { mutableStateOf(true) }
    var checkSocket by rememberSaveable { mutableStateOf(false) }
    var socketPortText by rememberSaveable { mutableStateOf("443") }
    var checkSsl by rememberSaveable { mutableStateOf(false) }
    var checkDomain by rememberSaveable { mutableStateOf(false) }
    var intervalMinutes by rememberSaveable { mutableIntStateOf(15) }

    val socketPort by remember(socketPortText) {
        derivedStateOf { socketPortText.trim().toIntOrNull() }
    }
    val hasAnyCheck by remember(checkPing, checkSocket, checkSsl, checkDomain) {
        derivedStateOf { checkPing || checkSocket || checkSsl || checkDomain }
    }
    val isSocketPortValid by remember(checkSocket, socketPort) {
        derivedStateOf { !checkSocket || (socketPort != null && socketPort in 1..65535) }
    }
    val isFormValid by remember(hostOrIp, hasAnyCheck, isSocketPortValid) {
        derivedStateOf { hostOrIp.trim().isNotBlank() && hasAnyCheck && isSocketPortValid }
    }
    val selectedPollingOption = PollingOptions.firstOrNull { it.valueMinutes == intervalMinutes } ?: PollingOptions[1]

    AlertDialog(
        modifier = modifier.fillMaxWidth().widthIn(max = 560.dp),
        onDismissRequest = onDismiss,
        containerColor = DialogBackground,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = "Add Server",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = nickname,
                    onValueChange = { nickname = it },
                    singleLine = true,
                    label = { Text("Nickname") },
                    placeholder = { Text("Production VPS") },
                    supportingText = { Text("Used on the dashboard and notification alerts.") },
                )

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = hostOrIp,
                    onValueChange = { hostOrIp = it },
                    singleLine = true,
                    label = { Text("Host / IP / Domain") },
                    placeholder = { Text("server.example.com or 192.168.1.10") },
                    supportingText = { Text("Enter a host, IP, or domain. Do not include https://") },
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = DialogSurface,
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 0.dp,
                    border = BorderStroke(1.dp, BorderColor),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Checks",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                        )

                        CheckToggleRow(
                            checked = checkPing,
                            onCheckedChange = { checkPing = it },
                            title = "ICMP Ping / Reachability",
                            subtitle = "Android-safe reachability check with TCP fallback.",
                            icon = { Icon(Icons.Default.NetworkPing, contentDescription = null, tint = AccentBlue) },
                        )

                        CheckToggleRow(
                            checked = checkSocket,
                            onCheckedChange = { checkSocket = it },
                            title = "TCP Socket Check",
                            subtitle = "Checks a custom port such as 22, 80, 443, or 3389.",
                            icon = { Icon(Icons.Default.SettingsEthernet, contentDescription = null, tint = AccentBlue) },
                        )

                        if (checkSocket) {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = socketPortText,
                                onValueChange = { value ->
                                    socketPortText = value.filter { it.isDigit() }.take(5)
                                },
                                singleLine = true,
                                label = { Text("Port Number") },
                                placeholder = { Text("443") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = socketPortText.isNotBlank() && !isSocketPortValid,
                                supportingText = {
                                    Text(if (isSocketPortValid) "Valid range: 1 to 65535." else "Enter a valid port from 1 to 65535.")
                                },
                            )
                        }

                        CheckToggleRow(
                            checked = checkSsl,
                            onCheckedChange = { checkSsl = it },
                            title = "SSL Certificate Validity",
                            subtitle = "Checks certificate validity and expiration on port 443.",
                            icon = { Icon(Icons.Default.Security, contentDescription = null, tint = GoodColor) },
                        )

                        CheckToggleRow(
                            checked = checkDomain,
                            onCheckedChange = { checkDomain = it },
                            title = "Domain / DNS Validity",
                            subtitle = "Resolves DNS records and reports lookup failures.",
                            icon = { Icon(Icons.Default.Dns, contentDescription = null, tint = AmberColor) },
                        )
                    }
                }

                PollingIntervalSelector(
                    selectedOption = selectedPollingOption,
                    onSelected = { intervalMinutes = it.valueMinutes },
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (checkPing) PreviewChip("Ping")
                    if (checkSocket && socketPort != null) PreviewChip("TCP:$socketPort")
                    if (checkSsl) PreviewChip("SSL:443")
                    if (checkDomain) PreviewChip("DNS")
                }

                if (!hasAnyCheck) {
                    Text(
                        text = "Select at least one check type.",
                        color = AmberColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = isFormValid,
                onClick = {
                    onAddServer(
                        AddServerRequest(
                            nickname = nickname,
                            hostOrIp = hostOrIp,
                            checkPing = checkPing,
                            socketPort = if (checkSocket) socketPort else null,
                            checkSsl = checkSsl,
                            checkDomain = checkDomain,
                            intervalMinutes = intervalMinutes,
                        ),
                    )
                },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun CheckToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        icon()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = title, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            Text(text = subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun PollingIntervalSelector(
    selectedOption: PollingOption,
    onSelected: (PollingOption) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DialogSurface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, BorderColor),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Polling Interval",
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = selectedOption.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Interval") },
                    trailingIcon = {
                        IconButton(onClick = { expanded = true }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Open interval menu",
                            )
                        }
                    },
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = Color(0xFF172536),
                ) {
                    PollingOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(text = option.label, color = TextPrimary)
                                    Text(
                                        text = option.helper,
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            },
                            onClick = {
                                expanded = false
                                onSelected(option)
                            },
                        )
                    }
                }
            }
            Text(
                text = selectedOption.helper,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PreviewChip(label: String) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = Color(0xFF172536),
            disabledLabelColor = AccentBlue,
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = false,
            borderColor = AccentBlue.copy(alpha = 0.45f),
            disabledBorderColor = AccentBlue.copy(alpha = 0.45f),
        ),
    )
}
