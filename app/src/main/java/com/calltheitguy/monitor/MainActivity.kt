package com.calltheitguy.monitor

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.calltheitguy.monitor.service.NotificationHelper
import com.calltheitguy.monitor.ui.MonitorDashboard
import com.calltheitguy.monitor.viewmodel.MonitorViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MonitorViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        NotificationHelper.ensureNotificationChannel(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureNotificationChannel(this)
        requestNotificationPermissionIfNeeded()

        setContent {
            MonitorAppTheme {
                MonitorDashboard(viewModel = viewModel)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun MonitorAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val darkColors = darkColorScheme(
        primary = Color(0xFF4DA3FF),
        onPrimary = Color(0xFF001D35),
        primaryContainer = Color(0xFF103A5C),
        onPrimaryContainer = Color(0xFFD3E9FF),
        secondary = Color(0xFF8FB8D8),
        onSecondary = Color(0xFF102A3D),
        secondaryContainer = Color(0xFF203A4E),
        onSecondaryContainer = Color(0xFFD7E9F7),
        tertiary = Color(0xFFB7C8E8),
        onTertiary = Color(0xFF202A44),
        tertiaryContainer = Color(0xFF333D5A),
        onTertiaryContainer = Color(0xFFE0E7FF),
        background = Color(0xFF0B0F14),
        onBackground = Color(0xFFE6EDF3),
        surface = Color(0xFF101720),
        onSurface = Color(0xFFE6EDF3),
        surfaceVariant = Color(0xFF182230),
        onSurfaceVariant = Color(0xFFB8C4D0),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF3B0000),
        errorContainer = Color(0xFF5A1111),
        onErrorContainer = Color(0xFFFFD7D7),
        outline = Color(0xFF314050),
        outlineVariant = Color(0xFF223040),
        scrim = Color(0xCC000000),
    )

    val lightColors = lightColorScheme(
        primary = Color(0xFF0067A8),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD3E9FF),
        onPrimaryContainer = Color(0xFF001D35),
        secondary = Color(0xFF466176),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFCBE6FF),
        onSecondaryContainer = Color(0xFF001E31),
        tertiary = Color(0xFF575E7D),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFDDE3FF),
        onTertiaryContainer = Color(0xFF141B36),
        background = Color(0xFFF7FAFD),
        onBackground = Color(0xFF111820),
        surface = Color.White,
        onSurface = Color(0xFF111820),
        surfaceVariant = Color(0xFFE3EAF2),
        onSurfaceVariant = Color(0xFF3E4A56),
        error = Color(0xFFB3261E),
        onError = Color.White,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        outline = Color(0xFF72808E),
        outlineVariant = Color(0xFFC4D0DC),
        scrim = Color(0x99000000),
    )

    MaterialTheme(
        colorScheme = if (darkTheme) darkColors else lightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
