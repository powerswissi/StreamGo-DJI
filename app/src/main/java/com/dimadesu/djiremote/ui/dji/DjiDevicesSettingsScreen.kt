package com.dimadesu.djiremote.ui.dji

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.djiremote.dji.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun DjiDevicesSettingsScreen(
    onOpenDevice: (SettingsDjiDevice) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val devices by DjiRepository.devices.collectAsState()
    val scanResults by WifiScanner.scanResults.collectAsState()
    var profileToDelete by remember { mutableStateOf<SettingsDjiDevice?>(null) }

    // Live Stopwatch ticker
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        WifiScanner.startScan(context)
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    // Delete Confirmation Dialog
    profileToDelete?.let { device ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text("Profil löschen?") },
            text = { Text("Möchtest du das Profil '${device.name}' wirklich unwiderruflich löschen?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (device.isStarted) {
                            DjiModel.stopStreaming(device)
                        }
                        DjiRepository.removeDevice(device.id)
                        profileToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Löschen", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // App Header with Settings Button and + New Profile
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "StreamGo DJI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "by Swissi",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                val activeCount = devices.count { it.isStarted }
                Text(
                    text = if (activeCount > 0) "$activeCount aktive Verbindung(en) • ${devices.size} Profil(e)" else "${devices.size} Profil(e) gespeichert",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (activeCount > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (activeCount > 0) FontWeight.Bold else FontWeight.Normal
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenSettings) {
                    Text("⚙️", fontSize = 20.sp)
                }
                Button(
                    onClick = {
                        val d = SettingsDjiDevice(name = "Profil ${devices.size + 1}")
                        DjiRepository.addDevice(d)
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("+ Neu")
                }
            }
        }

        if (devices.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Keine Profile vorhanden",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tippe auf '+ Neu', um ein Kamera-Profil anzulegen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(devices, key = { it.id }) { device ->
                    val isStarted = device.isStarted
                    val isStreaming = device.state == SettingsDjiDeviceState.STREAMING
                    val isReconnecting = device.state == SettingsDjiDeviceState.RECONNECTING

                    val cardBorderModifier = if (isStarted) {
                        Modifier.border(
                            2.dp,
                            if (isStreaming) Color(0xFF2E7D32) else Color(0xFFE65100),
                            RoundedCornerShape(16.dp)
                        )
                    } else {
                        Modifier
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .then(cardBorderModifier)
                            .clickable { onOpenDevice(device) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isStarted) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isStarted) 4.dp else 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header Row: Profil-Name + Live/Akku Badge + Start/Stop + Löschen
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = device.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isStreaming) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFD32F2F)
                                            ) {
                                                Text(
                                                    "LIVE",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                        // Batterie-Badge prominent direkt im Header!
                                        if (device.batteryPercentage != null) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (device.batteryPercentage!! <= 20) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                                            ) {
                                                Text(
                                                    "🔋 ${device.batteryPercentage}%",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // 1-Click START / STOP BUTTON
                                    Button(
                                        onClick = {
                                            if (!isStarted) {
                                                DjiModel.startStreaming(context, device)
                                            } else {
                                                DjiModel.stopStreaming(device)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = when {
                                                isStreaming -> Color(0xFFD32F2F) // Rot bei aktivem Stream
                                                isStarted -> Color(0xFFE65100) // Orange beim Vorbereiten / Reconnect
                                                else -> MaterialTheme.colorScheme.primary
                                            }
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                isStreaming -> "■ Stop"
                                                isStarted -> "Läuft…"
                                                else -> "▶ Start"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(2.dp))

                                    // Löschen-Button
                                    IconButton(
                                        onClick = { profileToDelete = device },
                                        enabled = !isStarted
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Profil löschen",
                                            tint = if (!isStarted) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else Color.Gray
                                        )
                                    }
                                }
                            }

                            // Kamera-Zeile: volle Breite, einzeilig
                            val camName = device.customCameraName?.takeIf { it.isNotBlank() }
                                ?: device.bluetoothPeripheralName
                                ?: "Kamera nicht gekoppelt"
                            Text(
                                text = "📷 $camName · ${device.model.name.replace('_', ' ')}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (!device.customCameraName.isNullOrBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Active Telemetry & Status Row
                            if (isStarted) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(10.dp))

                                val streamSec = device.streamingStartedAt?.let { start ->
                                    ((now - start).coerceAtLeast(0)) / 1000
                                } ?: 0L

                                val stopwatch = if (streamSec > 0) {
                                    val h = streamSec / 3600
                                    val m = (streamSec % 3600) / 60
                                    val s = streamSec % 60
                                    if (h > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
                                    else String.format(Locale.getDefault(), "%02d:%02d", m, s)
                                } else "--:--"

                                // Echte, von der Kamera gemeldete Encoder-Bitrate (kbit/s); Fallback: eingestellte Bitrate
                                val reportedKbps = device.liveBitrateKbps
                                val effectiveKbps = reportedKbps ?: (device.bitrate / 1000)
                                val rateText = if (isStreaming) {
                                    String.format(Locale.getDefault(), "↑ %.1f Mbit/s", effectiveKbps / 1000.0)
                                } else null

                                // Geschätztes Datenvolumen (Bitrate x Laufzeit) - nur Näherung, keine Messung am Netzwerk
                                val streamedMb = if (isStreaming && streamSec > 0) {
                                    val mb = (effectiveKbps * 1000.0 / 8.0) * streamSec / (1024.0 * 1024.0)
                                    if (mb >= 1000.0) String.format(Locale.getDefault(), "≈ %.2f GB", mb / 1024.0)
                                    else String.format(Locale.getDefault(), "≈ %.1f MB", mb)
                                } else null

                                val statusLabel = when (device.state) {
                                    SettingsDjiDeviceState.DISCOVERING -> "Sucht DJI Kamera..."
                                    SettingsDjiDeviceState.CONNECTING -> "Verbindet BLE..."
                                    SettingsDjiDeviceState.PAIRING -> "Kopplung..."
                                    SettingsDjiDeviceState.PREPARING_STREAM -> "Initialisiert Stream..."
                                    SettingsDjiDeviceState.SETTING_UP_WIFI -> "WLAN wird aufgebaut..."
                                    SettingsDjiDeviceState.WIFI_SETUP_FAILED -> "WLAN fehlgeschlagen"
                                    SettingsDjiDeviceState.CONFIGURING -> "Kamera-Einstellungen..."
                                    SettingsDjiDeviceState.STARTING_STREAM -> "Startet RTMP..."
                                    SettingsDjiDeviceState.STREAMING -> "Stream aktiv"
                                    SettingsDjiDeviceState.RECONNECTING -> "Signal-Verlust! Reconnect #${device.reconnectAttempt}..."
                                    SettingsDjiDeviceState.STOPPING_STREAM -> "Stoppt..."
                                    else -> device.state.name
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isStreaming -> Color(0xFF2E7D32)
                                            isReconnecting -> Color(0xFFE65100)
                                            device.state == SettingsDjiDeviceState.WIFI_SETUP_FAILED -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                                    Text(
                                        text = stopwatch,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Kennzahlen gruppiert: Upload | Datenmenge
                                if (isStreaming) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (rateText != null) {
                                            Text(
                                                text = rateText,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2E7D32)
                                            )
                                        }
                                        if (streamedMb != null) {
                                            Text(
                                                text = "📦 $streamedMb",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(8.dp))

                            // WLAN & Stream Config Row
                            val wifiItem = scanResults.firstOrNull { it.ssid == device.wifiSsid }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = buildString {
                                        append(if (device.wifiSsid.isNotBlank()) "📶 ${device.wifiSsid}" else "Kein WLAN hinterlegt")
                                        if (wifiItem != null) append(" · ${wifiItem.signalPercentage}% · ${wifiItem.band}")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = "${device.resolution} · ${device.fps}fps · ${device.bitrate / 1_000_000}M",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Show last error if present (nur wenn nicht erfolgreich live gestreamt wird)
                            if (!device.lastError.isNullOrBlank() && !isStreaming) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "⚠️ ${device.lastError}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
