package com.calltheitguy.monitor.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.math.max

object NetworkEngine {

    sealed class CheckResult {
        data class Up(
            val latencyMs: Long,
            val detail: String,
        ) : CheckResult()

        data class Down(
            val latencyMs: Long,
            val reason: String,
        ) : CheckResult()
    }

    data class SslStatus(
        val isValid: Boolean,
        val daysRemaining: Long,
        val notAfterEpochMillis: Long?,
        val issuer: String?,
        val subject: String?,
        val error: String?,
    )

    suspend fun checkReachability(
        host: String,
        fallbackPort: Int = 443,
        timeoutMs: Int = 3000,
    ): CheckResult = withContext(Dispatchers.IO) {
        val normalizedHost = normalizeHost(host)
        val startedAt = System.nanoTime()

        try {
            val address = InetAddress.getByName(normalizedHost)
            val reachable = address.isReachable(timeoutMs)
            val latency = elapsedMillis(startedAt)

            if (reachable) {
                return@withContext CheckResult.Up(
                    latencyMs = latency,
                    detail = "Reachable via InetAddress: ${address.hostAddress}",
                )
            }
        } catch (_: Exception) {
            // Android ICMP-style reachability is inconsistent without privileges.
            // Fall through to TCP fallback.
        }

        return@withContext checkTcpSocket(
            host = normalizedHost,
            port = fallbackPort,
            timeoutMs = timeoutMs,
            startedAt = startedAt,
            fallbackDetail = "Reachability fallback via TCP port $fallbackPort",
        )
    }

    suspend fun checkTcpSocket(
        host: String,
        port: Int,
        timeoutMs: Int = 3000,
    ): CheckResult = withContext(Dispatchers.IO) {
        checkTcpSocket(
            host = normalizeHost(host),
            port = port,
            timeoutMs = timeoutMs,
            startedAt = System.nanoTime(),
            fallbackDetail = null,
        )
    }

    suspend fun checkSslCertificate(
        domain: String,
        port: Int = 443,
        timeoutMs: Int = 5000,
    ): SslStatus = withContext(Dispatchers.IO) {
        val normalizedDomain = normalizeHost(domain)

        try {
            val sslSocketFactory = createDefaultSslSocketFactory()
            val socket = sslSocketFactory.createSocket() as SSLSocket

            socket.use { sslSocket ->
                sslSocket.soTimeout = timeoutMs
                sslSocket.connect(
                    InetSocketAddress(normalizedDomain, port),
                    timeoutMs,
                )
                sslSocket.startHandshake()

                val certificate = sslSocket.session.peerCertificates
                    .filterIsInstance<X509Certificate>()
                    .firstOrNull()
                    ?: return@withContext SslStatus(
                        isValid = false,
                        daysRemaining = -1,
                        notAfterEpochMillis = null,
                        issuer = null,
                        subject = null,
                        error = "No X509 certificate returned.",
                    )

                certificate.checkValidity()

                val notAfter = certificate.notAfter.toInstant()
                val now = Instant.now()
                val daysRemaining = ChronoUnit.DAYS.between(now, notAfter)

                return@withContext SslStatus(
                    isValid = daysRemaining >= 0,
                    daysRemaining = daysRemaining,
                    notAfterEpochMillis = certificate.notAfter.time,
                    issuer = certificate.issuerX500Principal?.name,
                    subject = certificate.subjectX500Principal?.name,
                    error = null,
                )
            }
        } catch (exception: Exception) {
            return@withContext SslStatus(
                isValid = false,
                daysRemaining = -1,
                notAfterEpochMillis = null,
                issuer = null,
                subject = null,
                error = exception.message ?: exception.javaClass.simpleName,
            )
        }
    }

    suspend fun checkDomainValidity(
        domain: String,
        timeoutMs: Long = 4000L,
    ): CheckResult = withContext(Dispatchers.IO) {
        val normalizedDomain = normalizeHost(domain)
        val startedAt = System.nanoTime()

        try {
            val client = OkHttpClient.Builder()
                .dns(Dns.SYSTEM)
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()

            val addresses = client.dns.lookup(normalizedDomain)
            val latency = elapsedMillis(startedAt)

            if (addresses.isEmpty()) {
                CheckResult.Down(
                    latencyMs = latency,
                    reason = "DNS lookup returned no records.",
                )
            } else {
                CheckResult.Up(
                    latencyMs = latency,
                    detail = "DNS resolved: ${addresses.joinToString { it.hostAddress ?: it.hostName }}",
                )
            }
        } catch (exception: Exception) {
            CheckResult.Down(
                latencyMs = elapsedMillis(startedAt),
                reason = exception.message ?: "DNS resolution failed.",
            )
        }
    }

    suspend fun checkHttpHead(
        url: String,
        timeoutMs: Long = 5000L,
    ): CheckResult = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val normalizedUrl = when {
            url.startsWith("http://", ignoreCase = true) -> url
            url.startsWith("https://", ignoreCase = true) -> url
            else -> "https://$url"
        }

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val request = Request.Builder()
                .url(normalizedUrl)
                .head()
                .build()

            client.newCall(request).execute().use { response ->
                val latency = elapsedMillis(startedAt)
                if (response.isSuccessful || response.isRedirect) {
                    CheckResult.Up(
                        latencyMs = latency,
                        detail = "HTTP ${response.code}",
                    )
                } else {
                    CheckResult.Down(
                        latencyMs = latency,
                        reason = "HTTP ${response.code}",
                    )
                }
            }
        } catch (exception: Exception) {
            CheckResult.Down(
                latencyMs = elapsedMillis(startedAt),
                reason = exception.message ?: "HTTP request failed.",
            )
        }
    }

    private fun checkTcpSocket(
        host: String,
        port: Int,
        timeoutMs: Int,
        startedAt: Long,
        fallbackDetail: String?,
    ): CheckResult {
        if (port !in 1..65535) {
            return CheckResult.Down(
                latencyMs = elapsedMillis(startedAt),
                reason = "Invalid port: $port",
            )
        }

        return try {
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(host, port),
                    timeoutMs,
                )
            }

            CheckResult.Up(
                latencyMs = elapsedMillis(startedAt),
                detail = fallbackDetail ?: "TCP socket connected on port $port.",
            )
        } catch (exception: Exception) {
            CheckResult.Down(
                latencyMs = elapsedMillis(startedAt),
                reason = exception.message ?: "TCP socket failed on port $port.",
            )
        }
    }

    private fun createDefaultSslSocketFactory(): SSLSocketFactory {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, null, null)
        return sslContext.socketFactory
    }

    private fun normalizeHost(value: String): String {
        return value
            .trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore(":")
            .trim()
    }

    private fun elapsedMillis(startedAt: Long): Long {
        return max(0L, (System.nanoTime() - startedAt) / 1_000_000L)
    }
}
