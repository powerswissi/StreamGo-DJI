package com.dimadesu.djiremote.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.djiremote.dji.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun DashboardScreen(
    onNavigateToProfiles: () -> Unit
) {
    val context = LocalContext.current
    val devices by DjiRepository.devices.collectAsState()
    val scanResults by WifiScanner.scanResults.collectAsState()
    val isScanningWifi by WifiScanner.isScanning.collectAsState()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        WifiScanner.startScan(context)
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    val activeDevices = devices.filter { it.isStarted }
    val idleDevices = devices.filter { !it.isStarted }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Dashboard Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Live Dashboard",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (activeDevices.isNotEmpty()) {
                            "${activeDevices.size} Kamera(s) aktiv / im Einsatz"
                        } else {
                            "Keine Kameras aktiv (Standby)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (activeDevices.isNotEmpty()) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (activeDevices.isNotEmpty()) FontWeight.Bold else FontWeight.Normal
                    )
                }

                IconButton(
                    onClick = { WifiScanner.startScan(context) },
                    enabled = !isScanningWifi
                ) {
                    Text(if (isScanningWifi) "⏳" else "🔄", fontSize = 18.sp)
                }
            }
        }

        if (devices.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Noch keine Kamera-Profile angelegt.")
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onNavigateToProfiles) {
                            Text("Profil erstellen")
                        }
                    }
                }
            }
        } else {
            // Section 1: ACTIVE / RUNNING CAMERAS
            if (activeDevices.isNotEmpty()) {
                item {
                    Text(
                        "AKTIVE KAMERAS (${activeDevices.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                }

                items(activeDevices, key = { it.id }) { device ->
                    ActiveCameraCard(
                        device = device,
                        now = now,
                        scanResults = scanResults,
                        onStop = { DjiModel.stopStreaming(device) }
                    )
                }
            }

            // Section 2: STANDBY / INACTIVE CAMERAS
            if (idleDevices.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        if (activeDevices.isEmpty()) "ALLE KAMERAS (STANDBY)" else "INAKTIVE KAMERAS (${idleDevices.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(idleDevices, key = { it.id }) { device ->
                    IdleCameraCard(
                        device = device,
                        scanResults = scanResults,
                        onStart = { DjiModel.startStreaming(context, device) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveCameraCard(
    device: SettingsDjiDevice,
    now: Long,
    scanResults: List<WifiScanItem>,
    onStop: () -> Unit
) {
    val isStreaming = device.state == SettingsDjiDeviceState.STREAMING
    val isReconnecting = device.state == SettingsDjiDeviceState.RECONNECTING

    val cardBorderColor by animateColorAsState(
        targetValue = when {
            isStreaming -> Color(0xFF2E7D32)
            isReconnecting -> Color(0xFFE65100)
            else -> MaterialTheme.colorScheme.primary
        },
        label = "borderColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, cardBorderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Camera Name + Live Tag + Battery
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                    val displayName = device.customCameraName?.takeIf { it.isNotBlank() } ?: device.name
                    Text(
                        text = "📷 $displayName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "(${device.model})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    device.batteryPercentage?.let { batt ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (batt <= 20) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                        ) {
                            Text(
                                "🔋 $batt%",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isStreaming) Color(0xFFD32F2F) else Color(0xFFE65100)
                    ) {
                        Text(
                            text = if (isStreaming) "● LIVE" else "RECONNECT",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // State & Stopwatch
            val statusLabel = when (device.state) {
                SettingsDjiDeviceState.DISCOVERING -> "Sucht DJI Kamera..."
                SettingsDjiDeviceState.CONNECTING -> "Verbindet Bluetooth..."
                SettingsDjiDeviceState.PAIRING -> "Bluetooth-Kopplung..."
                SettingsDjiDeviceState.PREPARING_STREAM -> "Initialisiert Stream..."
                SettingsDjiDeviceState.SETTING_UP_WIFI -> "WLAN wird aufgebaut..."
                SettingsDjiDeviceState.CONFIGURING -> "Kamera-Einstellungen..."
                SettingsDjiDeviceState.STARTING_STREAM -> "Startet RTMP..."
                SettingsDjiDeviceState.STREAMING -> "Live Stream aktiv"
                SettingsDjiDeviceState.RECONNECTING -> "Signal-Verlust! Reconnect #${device.reconnectAttempt}..."
                SettingsDjiDeviceState.STOPPING_STREAM -> "Stream wird beendet..."
                else -> "In Bearbeitung..."
            }

            val stopwatch = device.streamingStartedAt?.let { start ->
                val totalSec = ((now - start).coerceAtLeast(0)) / 1000
                val h = totalSec / 3600
                val m = (totalSec % 3600) / 60
                val s = totalSec % 60
                if (h > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
                else String.format(Locale.getDefault(), "%02d:%02d", m, s)
            } ?: "--:--"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isStreaming) Color(0xFF2E7D32) else Color(0xFFE65100)
                    )
                    Text(
                        "${device.resolution} @ ${device.fps}fps • ${device.bitrate / 1_000_000} Mbps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("LAUFZEIT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stopwatch,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Wi-Fi details for this camera
            val wifiItem = scanResults.firstOrNull { it.ssid == device.wifiSsid }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "WLAN: ${device.wifiSsid.ifBlank { "Kein WLAN hinterlegt" }}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    if (wifiItem != null) {
                        Text(
                            "${wifiItem.band} • ${wifiItem.rssi} dBm (${wifiItem.signalPercentage}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (device.wifiSsid.isNotBlank()) {
                        Text(
                            "Eigener Hotspot / Manuell",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("■ STOPP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun IdleCameraCard(
    device: SettingsDjiDevice,
    scanResults: List<WifiScanItem>,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val displayName = device.customCameraName?.takeIf { it.isNotBlank() } ?: device.name
                    Text(
                        text = "📷 $displayName",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Profil: ${device.name} • ${device.model}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onStart,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("▶ Start", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // WLAN & RTMP Info
            val wifiItem = scanResults.firstOrNull { it.ssid == device.wifiSsid }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (device.wifiSsid.isNotBlank()) "WLAN: ${device.wifiSsid}" else "Kein WLAN hinterlegt",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (wifiItem != null) {
                    Text(
                        "Empfang: ${wifiItem.signalPercentage}% (${wifiItem.band})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
