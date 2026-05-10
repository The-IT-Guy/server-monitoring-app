package com.calltheitguy.monitor.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.calltheitguy.monitor.data.MonitorDatabase
import com.calltheitguy.monitor.data.ServerEntity
import com.calltheitguy.monitor.engine.NetworkEngine
import java.util.concurrent.TimeUnit

class MonitorWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val serverId = inputData.getInt(KEY_SERVER_ID, INVALID_SERVER_ID)
        if (serverId == INVALID_SERVER_ID) return Result.failure()

        val database = MonitorDatabase.getInstance(applicationContext)
        val dao = database.serverDao()
        val server = dao.getById(serverId) ?: return Result.success()

        if (!server.enabled) return Result.success()

        val previousOnline = server.isOnline
        val checkResult = executeConfiguredChecks(server)

        val updatedServer = server.copy(
            isOnline = checkResult.overallOnline,
            lastChecked = System.currentTimeMillis(),
            pingOnline = checkResult.pingOnline,
            socketOnline = checkResult.socketOnline,
            sslValid = checkResult.sslValid,
            domainValid = checkResult.domainValid,
            pingLatencyMs = checkResult.pingLatencyMs,
            socketLatencyMs = checkResult.socketLatencyMs,
            sslDaysRemaining = checkResult.sslDaysRemaining,
            lastStatusMessage = checkResult.statusMessage,
        )

        dao.update(updatedServer)

        if (previousOnline && !checkResult.overallOnline) {
            NotificationHelper.notifyOutage(
                context = applicationContext,
                serverLabel = server.nickname,
                reason = checkResult.statusMessage,
            )
        }

        if (!previousOnline && checkResult.overallOnline) {
            NotificationHelper.notifyRecovery(
                context = applicationContext,
                serverLabel = server.nickname,
                detail = checkResult.statusMessage,
            )
        }

        if (server.checkSsl && checkResult.sslDaysRemaining != null && checkResult.sslDaysRemaining <= SSL_WARNING_DAYS) {
            NotificationHelper.notifySslWarning(
                context = applicationContext,
                serverLabel = server.nickname,
                daysRemaining = checkResult.sslDaysRemaining,
            )
        }

        MonitorScheduler.scheduleNext(
            context = applicationContext,
            serverId = server.id,
            intervalMinutes = server.intervalMinutes,
        )

        return Result.success()
    }

    private suspend fun executeConfiguredChecks(server: ServerEntity): ServerCheckResult {
        val messages = mutableListOf<String>()
        var pingOnline: Boolean? = null
        var socketOnline: Boolean? = null
        var sslValid: Boolean? = null
        var domainValid: Boolean? = null
        var pingLatencyMs: Long? = null
        var socketLatencyMs: Long? = null
        var sslDaysRemaining: Long? = null

        if (server.checkPing) {
            val fallbackPort = server.checkSocketPort ?: DEFAULT_SSL_PORT
            when (val result = NetworkEngine.checkReachability(server.hostOrIp, fallbackPort, DEFAULT_REACHABILITY_TIMEOUT_MS)) {
                is NetworkEngine.CheckResult.Up -> {
                    pingOnline = true
                    pingLatencyMs = result.latencyMs
                    messages.add("Ping: UP (${result.latencyMs}ms)")
                }
                is NetworkEngine.CheckResult.Down -> {
                    pingOnline = false
                    pingLatencyMs = result.latencyMs
                    messages.add("Ping: DOWN (${result.reason})")
                }
            }
        }

        if (server.checkSocketPort != null) {
            when (val result = NetworkEngine.checkTcpSocket(server.hostOrIp, server.checkSocketPort, DEFAULT_SOCKET_TIMEOUT_MS)) {
                is NetworkEngine.CheckResult.Up -> {
                    socketOnline = true
                    socketLatencyMs = result.latencyMs
                    messages.add("TCP ${server.checkSocketPort}: UP (${result.latencyMs}ms)")
                }
                is NetworkEngine.CheckResult.Down -> {
                    socketOnline = false
                    socketLatencyMs = result.latencyMs
                    messages.add("TCP ${server.checkSocketPort}: DOWN (${result.reason})")
                }
            }
        }

        if (server.checkSsl) {
            val sslStatus = NetworkEngine.checkSslCertificate(server.hostOrIp, DEFAULT_SSL_PORT, DEFAULT_SSL_TIMEOUT_MS)
            sslValid = sslStatus.isValid && sslStatus.daysRemaining >= 0L
            sslDaysRemaining = sslStatus.daysRemaining
            val sslMessage = if (sslValid) {
                "SSL: VALID (${sslStatus.daysRemaining}d remaining)"
            } else {
                "SSL: INVALID (${sslStatus.error ?: "expired"})"
            }
            messages.add(sslMessage)
        }

        if (server.checkDomain) {
            when (val result = NetworkEngine.checkDomainValidity(server.hostOrIp, DEFAULT_DNS_TIMEOUT_MS)) {
                is NetworkEngine.CheckResult.Up -> {
                    domainValid = true
                    messages.add("DNS: VALID")
                }
                is NetworkEngine.CheckResult.Down -> {
                    domainValid = false
                    messages.add("DNS: INVALID (${result.reason})")
                }
            }
        }

        val activeResults = listOfNotNull(pingOnline, socketOnline, sslValid, domainValid)
        val overallOnline = activeResults.isNotEmpty() && activeResults.all { it }

        return ServerCheckResult(
            overallOnline = overallOnline,
            pingOnline = pingOnline,
            socketOnline = socketOnline,
            sslValid = sslValid,
            domainValid = domainValid,
            pingLatencyMs = pingLatencyMs,
            socketLatencyMs = socketLatencyMs,
            sslDaysRemaining = sslDaysRemaining,
            statusMessage = if (messages.isEmpty()) "No active checks configured." else messages.joinToString(" | "),
        )
    }

    private data class ServerCheckResult(
        val overallOnline: Boolean,
        val pingOnline: Boolean?,
        val socketOnline: Boolean?,
        val sslValid: Boolean?,
        val domainValid: Boolean?,
        val pingLatencyMs: Long?,
        val socketLatencyMs: Long?,
        val sslDaysRemaining: Long?,
        val statusMessage: String,
    )

    companion object {
        const val KEY_SERVER_ID = "server_id"
        private const val INVALID_SERVER_ID = -1
        private const val DEFAULT_SSL_PORT = 443
        private const val DEFAULT_REACHABILITY_TIMEOUT_MS = 3000
        private const val DEFAULT_SOCKET_TIMEOUT_MS = 3000
        private const val DEFAULT_SSL_TIMEOUT_MS = 5000
        private const val DEFAULT_DNS_TIMEOUT_MS = 4000L
        private const val SSL_WARNING_DAYS = 14L
    }
}

object MonitorScheduler {

    private const val MANUAL_ONLY_INTERVAL_MINUTES = 0

    fun scheduleNext(
        context: Context,
        serverId: Int,
        intervalMinutes: Int,
    ) {
        if (serverId <= 0 || intervalMinutes == MANUAL_ONLY_INTERVAL_MINUTES) return

        val safeDelayMinutes = intervalMinutes.toLong().coerceAtLeast(5L)
        val request = buildOneTimeRequest(serverId)
            .setInitialDelay(safeDelayMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            uniqueWorkName(serverId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun runNow(
        context: Context,
        serverId: Int,
    ) {
        if (serverId <= 0) return

        val request = buildOneTimeRequest(serverId).build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            manualWorkName(serverId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(
        context: Context,
        serverId: Int,
    ) {
        if (serverId <= 0) return
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(uniqueWorkName(serverId))
        workManager.cancelUniqueWork(manualWorkName(serverId))
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(TAG_MONITOR_WORK)
    }

    private fun buildOneTimeRequest(serverId: Int): OneTimeWorkRequest.Builder {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        return OneTimeWorkRequestBuilder<MonitorWorker>()
            .setInputData(workDataOf(MonitorWorker.KEY_SERVER_ID to serverId))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .addTag(TAG_MONITOR_WORK)
            .addTag("server_$serverId")
    }

    private fun uniqueWorkName(serverId: Int): String = "scheduled_monitor_$serverId"

    private fun manualWorkName(serverId: Int): String = "manual_monitor_$serverId"

    private const val TAG_MONITOR_WORK = "monitor_work"
}
