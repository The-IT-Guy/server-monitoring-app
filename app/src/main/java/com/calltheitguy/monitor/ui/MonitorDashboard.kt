package com.calltheitguy.monitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.calltheitguy.monitor.viewmodel.MonitorViewModel

private val DashboardBackground = Color(0xFF0B0F14)
private val PanelColor = Color(0xFF101720)
private val PanelAltColor = Color(0xFF121B26)
private val BorderColor = Color(0xFF263241)
private val GoodColor = Color(0xFF2AD39B)
private val BadColor = Color(0xFFFF5C5C)
private val UnknownColor = Color(0xFF8FA3B8)
private val AccentBlue = Color(0xFF4DA3FF)
private val AmberColor = Color(0xFFFFC857)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorDashboard(
    viewModel: MonitorViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.rescheduleEnabledServers()
    }

    if (showAddDialog) {
        AddServerDialog(
            onDismiss = { showAddDialog = false },
            onAddServer = { request ->
                viewModel.addServer(
                    nickname = request.nickname,
                    hostOrIp = request.hostOrIp,
                    checkPing = request.checkPing,
                    checkSocketPort = request.socketPort,
                    checkSsl = request.checkSsl,
                    checkDomain = request.checkDomain,
                    intervalMinutes = request.intervalMinutes,
                )
                showAddDialog = false
            },
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(DashboardBackground),
        containerColor = DashboardBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "The IT Guy Monitor",
                            color = Color(0xFFE6EDF3),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Standalone polling dashboard",
                            color = Color(0xFF8FA3B8),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = { viewModel.checkAllNow() },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color(0xFF172536),
                            contentColor = AccentBlue,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Check all servers now",
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DashboardBackground,
                    titleContentColor = Color(0xFFE6EDF3),
                    actionIconContentColor = AccentBlue,
                ),
                windowInsets = WindowInsets.statusBars,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
                onClick = { showAddDialog = true },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                    )
                },
                text = { Text("Add Server") },
                containerColor = AccentBlue,
                contentColor = Color(0xFF001D35),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(DashboardBackground).padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 128.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DashboardSummary(
                    totalCount = uiState.totalCount,
                    onlineCount = uiState.onlineCount,
                    offlineCount = uiState.offlineCount,
                    pendingCount = uiState.pendingCount,
                    disabledCount = uiState.disabledCount,
                )
            }

            if (uiState.servers.isEmpty()) {
                item {
                    EmptyDashboardState(onAddClick = { showAddDialog = true })
                }
            } else {
                items(
                    items = uiState.servers,
                    key = { server -> server.id },
                ) { server ->
                    ServerItem(
                        server = server,
                        onCheckNow = { viewModel.checkNow(server) },
                        onEnabledChanged = { enabled -> viewModel.setEnabled(server, enabled) },
                        onDelete = { viewModel.deleteServer(server) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardSummary(
    totalCount: Int,
    onlineCount: Int,
    offlineCount: Int,
    pendingCount: Int,
    disabledCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = PanelColor,
            contentColor = Color(0xFFE6EDF3),
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(BorderColor),
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
                Column {
                    Text(
                        text = "Fleet Status",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFE6EDF3),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Direct polling, Room-backed local storage",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8FA3B8),
                    )
                }
                StatusDot(
                    color = when {
                        totalCount == 0 -> UnknownColor
                        offlineCount > 0 -> BadColor
                        pendingCount > 0 -> AmberColor
                        else -> GoodColor
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SummaryTile("Total", totalCount.toString(), AccentBlue, Modifier.weight(1f))
                SummaryTile("Up", onlineCount.toString(), GoodColor, Modifier.weight(1f))
                SummaryTile("Down", offlineCount.toString(), BadColor, Modifier.weight(1f))
                SummaryTile("Pending", pendingCount.toString(), AmberColor, Modifier.weight(1f))
                SummaryTile("Off", disabledCount.toString(), UnknownColor, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(min = 72.dp),
        color = PanelAltColor,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                color = color,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )

            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmptyDashboardState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = PanelColor,
            contentColor = Color(0xFFE6EDF3),
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(BorderColor),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(58.dp).clip(CircleShape).background(Color(0xFF172536)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(30.dp),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "No servers configured",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFE6EDF3),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Add a server, domain, custom port, SSL check, or DNS check to start direct monitoring.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8FA3B8),
                    textAlign = TextAlign.Center,
                )
            }
            Button(onClick = onAddClick) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Add First Server")
            }
        }
    }
}

@Composable
private fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(14.dp).clip(CircleShape).background(color),
    )
}
