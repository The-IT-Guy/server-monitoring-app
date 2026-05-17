# The IT Guy Monitor

An Android app for monitoring servers and network endpoints from your phone. Add any host, IP, or domain — the app checks reachability, TCP port availability, SSL certificate validity, and DNS resolution on a configurable schedule and notifies you the moment something goes wrong.

No accounts. No cloud. No backend. Runs entirely on-device using WorkManager and a local Room database.

---

## Features

- **Four independent check types per server:**
  - ICMP ping / reachability (with TCP fallback for Android's network restrictions)
  - TCP socket check (custom port, latency measured)
  - SSL certificate validity (days remaining, issuer info)
  - DNS / domain resolution
- **Configurable polling intervals:** 5 minutes, 15 minutes, 1 hour, or manual-only
- **Background monitoring** via WorkManager — checks run even when the app is closed, resume automatically after reboot
- **Push notifications** for outages, recoveries, and SSL certificates expiring within 14 days
- **Fleet dashboard** — at-a-glance summary of total / up / down / pending / disabled server counts
- **Per-server status cards** showing latency, port numbers, SSL days remaining, last checked timestamp
- **Enable/disable servers** without deleting them
- **Dark theme** with navy/blue UI throughout

---

## Tech Stack

| Component          | Library / Tool                            |
|--------------------|-------------------------------------------|
| Language           | Kotlin 2.0.21                             |
| UI                 | Jetpack Compose + Material 3              |
| Architecture       | ViewModel + StateFlow (MVVM)              |
| Background work    | WorkManager 2.10.0 (CoroutineWorker)      |
| Local database     | Room 2.6.1 (SQLite)                       |
| Networking         | OkHttp 4.12.0 + raw `Socket` + `SSLSocket`|
| Min SDK            | 26 (Android 8.0)                          |
| Target SDK         | 35                                        |
| Build              | Gradle 9.0, AGP 8.7.3, Java 17           |

---

## How It Works

### Adding a Server

Tap **Add Server** and fill in:
- **Nickname** (optional — defaults to host/IP if blank)
- **Host / IP / Domain** — any resolvable address
- **Checks to enable** — mix and match ping, TCP socket (with port), SSL, and DNS
- **Polling interval** — how often WorkManager should run the checks

The app immediately runs a first check when you add a server, then schedules recurring checks at your chosen interval.

### Check Logic

Each check runs independently and reports its own result:

| Check | Method | How It Works |
|-------|--------|--------------|
| Ping / Reachability | `InetAddress.isReachable` → TCP fallback | Android prevents raw ICMP without root; falls back to a TCP connection on port 443 if ICMP fails |
| TCP Socket | `Socket.connect()` | Connects to the specified port, measures latency |
| SSL Certificate | `SSLSocket` TLS handshake | Reads the X.509 certificate, computes days until expiry |
| DNS / Domain | OkHttp DNS lookup | Resolves the hostname via system DNS |

A server is considered **online** only when all enabled checks pass. Any single check failure marks the server offline.

### Notifications

| Event | Trigger |
|-------|---------|
| Outage | Server was online → at least one check now fails |
| Recovery | Server was offline → all checks now pass |
| SSL Warning | SSL check enabled and certificate expires in ≤ 14 days |

Notifications are stable-ID'd per server so they don't stack. Tapping a notification opens the app.

---

## Building

### Requirements

- Android Studio Meerkat or later
- JDK 17
- Android SDK with API 35

### Steps

```bash
git clone https://github.com/The-IT-Guy/server-monitoring-app.git
cd server-monitoring-app

# Build debug APK
./gradlew assembleDebug

# Install on connected device or emulator
./gradlew installDebug
```

---

## Project Structure

```
app/src/main/
├── AndroidManifest.xml
└── java/com/calltheitguy/monitor/
    ├── MainActivity.kt                   # Entry point, notification permission, theme
    ├── data/
    │   ├── ServerEntity.kt               # Room entity: all server config + last result
    │   ├── ServerDao.kt                  # Room DAO: CRUD + observe as Flow
    │   ├── MonitorDatabase.kt            # Room database singleton
    │   └── ServerRepository.kt           # Thin repo layer over DAO
    ├── engine/
    │   └── NetworkEngine.kt              # Stateless network checks (ping/TCP/SSL/DNS)
    ├── service/
    │   ├── MonitorWorker.kt              # CoroutineWorker: runs checks, notifies, reschedules
    │   └── NotificationHelper.kt         # Notification channel, outage/recovery/SSL alerts
    ├── ui/
    │   ├── AddServerDialog.kt            # Server add form (nickname, host, checks, interval)
    │   ├── MonitorDashboard.kt           # Main screen: fleet summary + server list
    │   └── ServerItem.kt                 # Per-server card with status badges and actions
    └── viewmodel/
        └── MonitorViewModel.kt           # UI state, server CRUD, scheduling coordination
```

---

## Permissions

| Permission                           | Reason |
|--------------------------------------|--------|
| `INTERNET`                           | All network checks |
| `ACCESS_NETWORK_STATE`               | WorkManager network connectivity requirement |
| `POST_NOTIFICATIONS`                 | Outage, recovery, and SSL warning alerts |
| `FOREGROUND_SERVICE`                 | WorkManager foreground service (Android 12+) |
| `FOREGROUND_SERVICE_REMOTE_MESSAGING`| Required classification for network foreground service |

---

## WorkManager Scheduling

The app uses a chained `OneTimeWorkRequest` pattern rather than `PeriodicWorkRequest`. After each check completes, `MonitorWorker` schedules its own next run with `setInitialDelay`. This allows the interval to be configured per-server and changed at any time without cancelling and re-enqueuing a periodic chain.

- Minimum delay between checks: 5 minutes (enforced by `MonitorScheduler`)
- Network constraint: `NetworkType.CONNECTED` — checks are deferred if offline
- Backoff policy: exponential, 30s base
- Work names: `"scheduled_monitor_{id}"` and `"manual_monitor_{id}"` — unique, replacing any prior enqueue

Setting a server's interval to **Manual Only** (0) skips the `scheduleNext` call entirely — checks only run when you tap "Check Now".

---

## Roadmap

- [ ] Dark/light theme toggle
- [ ] Per-server notification control (mute specific servers)
- [ ] HTTP/HTTPS response code check
- [ ] Export server list for backup / migration
- [ ] Tablet layout / landscape optimization
- [ ] Widget for home screen status overview
