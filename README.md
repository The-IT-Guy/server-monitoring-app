# The IT Guy Monitor

An Android app for monitoring servers, domains, and services directly from your phone. Runs periodic background checks and sends push notifications when something goes down or comes back online.

## Features

- **ICMP Ping / Reachability** — Android-safe reachability check with automatic TCP fallback
- **TCP Socket Check** — Verify any custom port (SSH, HTTP, RDP, etc.)
- **SSL Certificate Validity** — Checks cert validity and alerts when expiration is within 14 days
- **DNS / Domain Check** — Confirms DNS resolution is working for a host or domain
- **Background Polling** — Powered by WorkManager; runs at 5 min, 15 min, or 1 hour intervals
- **Push Notifications** — Alerts on outage and recovery events
- **Local Storage** — All server data stored on-device via Room (no cloud dependency)
- **Fleet Dashboard** — Summary view showing total, up, down, pending, and disabled counts
- **Dark / Light Theme** — Follows system theme

## Tech Stack

| Layer | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Architecture | ViewModel + StateFlow |
| Database | Room 2.6 |
| Background Work | WorkManager 2.10 |
| Networking | OkHttp 4.12 |
| Build | Kotlin + KSP, Gradle (Kotlin DSL) |

## Requirements

- Android 8.0+ (API 26)
- Target SDK 35

## Getting Started

1. Clone the repo
2. Open in Android Studio
3. Build and run on a device or emulator
4. Tap **Add Server**, enter a host or IP, select your checks, and set a polling interval

## Check Types

| Check | What it does |
|---|---|
| Ping | `InetAddress.isReachable` with TCP fallback to port 443 (or the configured socket port) |
| TCP Socket | Raw socket connect to any port 1–65535 |
| SSL | TLS handshake on port 443, reads cert expiry |
| DNS | OkHttp DNS lookup — confirms the domain resolves |

## Notifications

- **Outage** — fired when a previously online server fails all active checks
- **Recovery** — fired when a server comes back online
- **SSL Warning** — fired when a cert has 14 or fewer days remaining

## Package

`com.calltheitguy.monitor`
