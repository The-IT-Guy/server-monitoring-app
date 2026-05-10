package com.calltheitguy.monitor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calltheitguy.monitor.data.ServerEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardBackground = Color(0xFF101720)
private val CardBackgroundMuted = Color(0xFF0F151D)
private val BorderColor = Color(0xFF263241)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecondary = Color(0xFF8FA3B8)
private val AccentBlue = Color(0xFF4DA3FF)
private val GoodColor = Color(0xFF2AD39B)
private val BadColor = Color(0xFFFF5C5C)
private val AmberColor = Color(0xFFFFC857)
private val UnknownColor = Color(0xFF8FA3B8)
private val ChipSurface = Color(0xFF172536)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerItem(
    server: ServerEntity,
    onCheckNow: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete server?") },
            text = { Text("This removes ${server.nickname} from local monitoring.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF101720),
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (server.enabled) CardBackground else CardBackgroundMuted,
            contentColor = TextPrimary,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                !server.enabled -> BorderColor
                server.lastChecked == 0L -> AmberColor.copy(alpha = 0.55f)
                server.isOnline -> GoodColor.copy(alpha = 0.55f)
                else -> BadColor.copy(alpha = 0.65f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusIndicator(server)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = server.nickname,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = server.hostOrIp,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Switch(
                    checked = server.enabled,
                    onCheckedChange = onEnabledChanged,
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OverallStatusBadge(server)

                if (server.checkPing) {
                    PingBadge(server)
                }

                if (server.checkSocketPort != null) {
                    SocketBadge(server)
                }

                if (server.checkSsl) {
                    SslBadge(server)
                }

                if (server.checkDomain) {
                    DnsBadge(server)
                }

                IntervalBadge(server.intervalMinutes)
            }

            Text(
                text = server.lastStatusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (server.enabled && server.lastChecked > 0L && !server.isOnline) BadColor else TextSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Last checked: ${formatTimestamp(server.lastChecked)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = BadColor),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete server",
                        )
                    }
                    FilledTonalButton(
                        onClick = onCheckNow,
                        enabled = server.enabled,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF172536),
                            contentColor = AccentBlue,
                            disabledContainerColor = Color(0xFF111820),
                            disabledContentColor = TextSecondary.copy(alpha = 0.45f),
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check Now")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(server: ServerEntity) {
    val color = when {
        !server.enabled -> UnknownColor.copy(alpha = 0.45f)
        server.lastChecked == 0L -> AmberColor
        server.isOnline -> GoodColor
        else -> BadColor
    }

    Box(
        modifier = Modifier.size(16.dp).clip(CircleShape).background(color),
    )
}

@Composable
private fun OverallStatusBadge(server: ServerEntity) {
    val label = when {
        !server.enabled -> "Disabled"
        server.lastChecked == 0L -> "Pending"
        server.isOnline -> "Online"
        else -> "Down"
    }
    val color = when {
        !server.enabled -> UnknownColor
        server.lastChecked == 0L -> AmberColor
        server.isOnline -> GoodColor
        else -> BadColor
    }
    BadgeChip(text = label, color = color)
}

@Composable
private fun PingBadge(server: ServerEntity) {
    val label = when (server.pingOnline) {
        true -> "Ping UP${server.pingLatencyMs?.let { " ${it}ms" } ?: ""}"
        false -> "Ping DOWN"
        null -> "Ping pending"
    }
    val color = when (server.pingOnline) {
        true -> GoodColor
        false -> BadColor
        null -> AmberColor
    }
    BadgeChip(
        text = label,
        color = color,
        icon = { Icon(Icons.Default.NetworkPing, contentDescription = null, modifier = Modifier.size(16.dp)) },
    )
}

@Composable
private fun SocketBadge(server: ServerEntity) {
    val port = server.checkSocketPort ?: return
    val label = when (server.socketOnline) {
        true -> "TCP:$port UP${server.socketLatencyMs?.let { " ${it}ms" } ?: ""}"
        false -> "TCP:$port DOWN"
        null -> "TCP:$port pending"
    }
    val color = when (server.socketOnline) {
        true -> GoodColor
        false -> BadColor
        null -> AmberColor
    }
    BadgeChip(
        text = label,
        color = color,
        icon = { Icon(Icons.Default.SettingsEthernet, contentDescription = null, modifier = Modifier.size(16.dp)) },
    )
}

@Composable
private fun SslBadge(server: ServerEntity) {
    val days = server.sslDaysRemaining
    val label = when {
        server.sslValid == null -> "SSL pending"
        server.sslValid == false -> "SSL invalid"
        days == null -> "SSL valid"
        days < 0L -> "SSL expired"
        days == 0L -> "SSL today"
        days == 1L -> "SSL 1d"
        else -> "SSL ${days}d"
    }
    val color = when {
        server.sslValid == false -> BadColor
        days == null -> AmberColor
        days < 0L -> BadColor
        days <= 7L -> BadColor
        days <= 14L -> AmberColor
        else -> GoodColor
    }
    BadgeChip(
        text = label,
        color = color,
        icon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp)) },
    )
}

@Composable
private fun DnsBadge(server: ServerEntity) {
    val label = when (server.domainValid) {
        true -> "DNS valid"
        false -> "DNS invalid"
        null -> "DNS pending"
    }
    val color = when (server.domainValid) {
        true -> GoodColor
        false -> BadColor
        null -> AmberColor
    }
    BadgeChip(
        text = label,
        color = color,
        icon = { Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(16.dp)) },
    )
}

@Composable
private fun IntervalBadge(intervalMinutes: Int) {
    val label = when (intervalMinutes) {
        0 -> "Manual"
        5 -> "5 min"
        15 -> "15 min"
        60 -> "1 hour"
        else -> "$intervalMinutes min"
    }
    BadgeChip(
        text = label,
        color = AccentBlue,
        icon = { Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp)) },
    )
}

@Composable
private fun BadgeChip(
    text: String,
    color: Color,
    icon: (@Composable () -> Unit)? = null,
) {
    AssistChip(
        modifier = Modifier.defaultMinSize(minHeight = 32.dp),
        onClick = {},
        enabled = false,
        leadingIcon = icon,
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = color.copy(alpha = 0.14f),
            disabledLabelColor = color,
            disabledLeadingIconContentColor = color,
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = false,
            borderColor = color.copy(alpha = 0.38f),
            disabledBorderColor = color.copy(alpha = 0.38f),
        ),
    )
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "Never"
    return try {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
    } catch (_: Exception) {
        "Unknown"
    }
}
