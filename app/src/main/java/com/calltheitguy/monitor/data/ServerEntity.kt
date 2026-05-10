package com.calltheitguy.monitor.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val nickname: String,
    val hostOrIp: String,
    val checkPing: Boolean = true,
    val checkSocketPort: Int? = null,
    val checkSsl: Boolean = false,
    val checkDomain: Boolean = false,
    val intervalMinutes: Int = 15,
    val isOnline: Boolean = true,
    val lastChecked: Long = 0L,
    val pingOnline: Boolean? = null,
    val socketOnline: Boolean? = null,
    val sslValid: Boolean? = null,
    val domainValid: Boolean? = null,
    val pingLatencyMs: Long? = null,
    val socketLatencyMs: Long? = null,
    val sslDaysRemaining: Long? = null,
    val lastStatusMessage: String = "Pending check...",
    val enabled: Boolean = true,
)
