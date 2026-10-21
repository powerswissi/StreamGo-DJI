package com.dimadesu.djiremote.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.djiremote.dji.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    onNavigateToProfiles: () -> Unit
) {
    val context = LocalContext.current
    val devices by DjiRepository.devices.collectAsState()
    val scanResults by WifiScanner.scanResults.collectAsState()
    val isScanningWifi by WifiScanner.isScanning.collectAsState()

    // Periodically update stopwatch and trigger wifi scan
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        WifiScanner.startScan(context)
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    // Active or first device
    val activeDevice = devices.firstOrNull { it.isStarted }
    val displayDevice = activeDevice ?: devices.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Live Dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Echtzeit-Telemetrie & Verbindungsstatus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (activeDevice != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFD32F2F)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Text(
                            "LIVE",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (displayDevice == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Keine Profile vorhanden.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onNavigateToProfiles) {
                        Text("Zu den Profilen")
                    }
                }
            }
        } else {
            // Main Live Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (displayDevice.state) {
                        SettingsDjiDeviceState.STREAMING -> Color(0xFF1B5E20)
                        SettingsDjiDeviceState.RECONNECTING -> Color(0xFFE65100)
                        SettingsDjiDeviceState.WIFI_SETUP_FAILED -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val statusText = when (displayDevice.state) {
                        SettingsDjiDeviceState.IDLE -> "Bereit (Standby)"
                        SettingsDjiDeviceState.DISCOVERING -> "Sucht DJI Kamera..."
                        SettingsDjiDeviceState.CONNECTING -> "Verbindet Bluetooth..."
                        SettingsDjiDeviceState.PAIRING -> "Bluetooth-Kopplung..."
                        SettingsDjiDeviceState.STOPPING_STREAM -> "Stoppt Live-Stream..."
                        SettingsDjiDeviceState.PREPARING_STREAM -> "Bereitet Stream vor..."
                        SettingsDjiDeviceState.SETTING_UP_WIFI -> "WLAN-Konfiguration..."
                        SettingsDjiDeviceState.WIFI_SETUP_FAILED -> "WLAN-Setup fehlgeschlagen"
                        SettingsDjiDeviceState.CONFIGURING -> "Kamera-Setup..."
                        SettingsDjiDeviceState.STARTING_STREAM -> "Initialisiere RTMP..."
                        SettingsDjiDeviceState.STREAMING -> "STREAMING AKTIV"
                        SettingsDjiDeviceState.RECONNECTING -> "Signal verloren - Reconnect (#${displayDevice.reconnectAttempt})..."
                        else -> "Unbekannt"
                    }

                    val titleColor = when (displayDevice.state) {
                        SettingsDjiDeviceState.STREAMING, SettingsDjiDeviceState.RECONNECTING -> Color.White
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = titleColor
                        )

                        // Camera Battery Badge
                        displayDevice.batteryPercentage?.let { batt ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (batt <= 20) Color(0xFFD32F2F) else Color(0xFF388E3C)
                            ) {
                                Text(
                                    text = "🔋 $batt%",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stream Time Stopwatch
                    val streamTimeFormatted = displayDevice.streamingStartedAt?.let { start ->
                        val durationMs = (now - start).coerceAtLeast(0)
                        val totalSeconds = durationMs / 1000
                        val hours = totalSeconds / 3600
                        val minutes = (totalSeconds % 3600) / 60
                        val seconds = totalSeconds % 60
                        if (hours > 0) {
                            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                        } else {
                            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                        }
                    } ?: "00:00"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "STREAM-LAUFZEIT",
                                style = MaterialTheme.typography.labelSmall,
                                color = titleColor.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = streamTimeFormatted,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = titleColor
                            )
                        }

                        // Start / Stop Quick Button
                        if (displayDevice.isStarted) {
                            Button(
                                onClick = { DjiModel.stopStreaming(displayDevice) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("■ STOP", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        } else {
                            Button(
                                onClick = { DjiModel.startStreaming(context, displayDevice) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("▶ START", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Camera Hardware & Profile Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Kamera-Hardware & Profil",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val camDisplay = displayDevice.customCameraName?.takeIf { it.isNotBlank() }
                        ?.let { "$it (${displayDevice.model})" }
                        ?: "${displayDevice.bluetoothPeripheralName ?: "Nicht gekoppelt"} (${displayDevice.model})"

                    DashboardInfoRow(label = "Aktives Profil", value = displayDevice.name)
                    DashboardInfoRow(label = "Kamera", value = camDisplay)
                    DashboardInfoRow(label = "Bluetooth MAC", value = displayDevice.bluetoothPeripheralAddress ?: "Keine")
                    DashboardInfoRow(
                        label = "Stream-Format",
                        value = "${displayDevice.resolution} @ ${displayDevice.fps}fps (${displayDevice.bitrate / 1_000_000} Mbps)"
                    )
                    DashboardInfoRow(label = "Stabilisierung", value = displayDevice.imageStabilization.name)
                }
            }

            // Wi-Fi Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "WLAN & Netzwerk",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = { WifiScanner.startScan(context) },
                            enabled = !isScanningWifi
                        ) {
                            Text(if (isScanningWifi) "Scannt..." else "🔄 Scan")
                        }
                    }

                    val targetSsid = displayDevice.wifiSsid
                    val scanMatch = scanResults.firstOrNull { it.ssid == targetSsid }

                    DashboardInfoRow(label = "Ziel-SSID", value = if (targetSsid.isNotBlank()) targetSsid else "Keine konfiguriert")
                    
                    if (scanMatch != null) {
                        DashboardInfoRow(
                            label = "Signal / Band",
                            value = "${scanMatch.rssi} dBm (${scanMatch.signalPercentage}%) • ${scanMatch.band}"
                        )
                        DashboardInfoRow(label = "Sicherheit", value = scanMatch.security)
                    } else if (targetSsid.isNotBlank()) {
                        DashboardInfoRow(
                            label = "WLAN-Status",
                            value = "⚠️ SSID nicht in Reichweite gefunden"
                        )
                    }

                    DashboardInfoRow(
                        label = "RTMP Ziel",
                        value = if (displayDevice.rtmpUrl.isNotBlank()) displayDevice.rtmpUrl else "Keine Ziel-URL hinterlegt"
                    )
                }
            }

            // Last Error Banner (if present)
            displayDevice.lastError?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Letzte Meldung:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        displayDevice.lastErrorTimestamp?.let { ts ->
                            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Zeitstempel: $time",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
