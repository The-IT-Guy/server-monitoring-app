package com.calltheitguy.monitor.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calltheitguy.monitor.data.MonitorDatabase
import com.calltheitguy.monitor.data.ServerEntity
import com.calltheitguy.monitor.data.ServerRepository
import com.calltheitguy.monitor.service.MonitorScheduler
import com.calltheitguy.monitor.service.NotificationHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MonitorViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = ServerRepository(
        MonitorDatabase.getInstance(appContext).serverDao(),
    )

    val uiState: StateFlow<MonitorUiState> = repository.observeServers()
        .map { servers ->
            MonitorUiState(
                servers = servers,
                totalCount = servers.size,
                onlineCount = servers.count { server -> server.enabled && server.isOnline && server.lastChecked > 0L },
                offlineCount = servers.count { server -> server.enabled && !server.isOnline && server.lastChecked > 0L },
                pendingCount = servers.count { server -> server.lastChecked == 0L },
                disabledCount = servers.count { server -> !server.enabled },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = MonitorUiState(),
        )

    init {
        NotificationHelper.ensureNotificationChannel(appContext)
        rescheduleEnabledServers()
    }

    fun addServer(
        nickname: String,
        hostOrIp: String,
        checkPing: Boolean,
        checkSocketPort: Int?,
        checkSsl: Boolean,
        checkDomain: Boolean,
        intervalMinutes: Int,
    ) {
        val cleanNickname = nickname.trim().ifBlank { hostOrIp.trim() }
        val cleanHost = hostOrIp.trim()

        if (cleanHost.isBlank()) return
        if (!checkPing && checkSocketPort == null && !checkSsl && !checkDomain) return
        if (checkSocketPort != null && checkSocketPort !in MIN_PORT..MAX_PORT) return

        viewModelScope.launch {
            val server = ServerEntity(
                nickname = cleanNickname,
                hostOrIp = cleanHost,
                checkPing = checkPing,
                checkSocketPort = checkSocketPort,
                checkSsl = checkSsl,
                checkDomain = checkDomain,
                intervalMinutes = normalizeInterval(intervalMinutes),
                isOnline = true,
                lastChecked = 0L,
                lastStatusMessage = "Pending check...",
                enabled = true,
            )

            val id = repository.addServer(server)
            MonitorScheduler.runNow(appContext, id)
            MonitorScheduler.scheduleNext(appContext, id, server.intervalMinutes)
        }
    }

    fun updateServer(server: ServerEntity) {
        if (server.hostOrIp.isBlank()) return
        if (!server.checkPing && server.checkSocketPort == null && !server.checkSsl && !server.checkDomain) return
        if (server.checkSocketPort != null && server.checkSocketPort !in MIN_PORT..MAX_PORT) return

        viewModelScope.launch {
            val normalizedServer = server.copy(intervalMinutes = normalizeInterval(server.intervalMinutes))
            repository.updateServer(normalizedServer)

            if (normalizedServer.enabled) {
                MonitorScheduler.scheduleNext(appContext, normalizedServer.id, normalizedServer.intervalMinutes)
            } else {
                MonitorScheduler.cancel(appContext, normalizedServer.id)
            }
        }
    }

    fun setEnabled(
        server: ServerEntity,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            val updatedServer = server.copy(enabled = enabled)
            repository.updateServer(updatedServer)

            if (enabled) {
                MonitorScheduler.runNow(appContext, updatedServer.id)
                MonitorScheduler.scheduleNext(appContext, updatedServer.id, updatedServer.intervalMinutes)
            } else {
                MonitorScheduler.cancel(appContext, updatedServer.id)
            }
        }
    }

    fun deleteServer(server: ServerEntity) {
        viewModelScope.launch {
            MonitorScheduler.cancel(appContext, server.id)
            NotificationHelper.cancelServerNotifications(appContext, server.nickname)
            repository.deleteServer(server)
        }
    }

    fun checkNow(server: ServerEntity) {
        if (server.id <= 0 || !server.enabled) return
        MonitorScheduler.runNow(appContext, server.id)
    }

    fun checkAllNow() {
        viewModelScope.launch {
            repository.getEnabled().forEach { server ->
                MonitorScheduler.runNow(appContext, server.id)
            }
        }
    }

    fun rescheduleEnabledServers() {
        viewModelScope.launch {
            repository.getEnabled().forEach { server ->
                MonitorScheduler.scheduleNext(appContext, server.id, server.intervalMinutes)
            }
        }
    }

    fun cancelAllScheduledChecks() {
        MonitorScheduler.cancelAll(appContext)
    }

    private fun normalizeInterval(intervalMinutes: Int): Int {
        return when (intervalMinutes) {
            0, 5, 15, 60 -> intervalMinutes
            else -> 15
        }
    }

    companion object {
        private const val MIN_PORT = 1
        private const val MAX_PORT = 65535
    }
}

data class MonitorUiState(
    val servers: List<ServerEntity> = emptyList(),
    val totalCount: Int = 0,
    val onlineCount: Int = 0,
    val offlineCount: Int = 0,
    val pendingCount: Int = 0,
    val disabledCount: Int = 0,
) {
    val hasServers: Boolean
        get() = servers.isNotEmpty()

    val allHealthy: Boolean
        get() = servers.isNotEmpty() && offlineCount == 0 && pendingCount == 0
}
