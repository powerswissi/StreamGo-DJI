package com.dimadesu.djiremote.ui.dji

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.djiremote.dji.DjiModel
import com.dimadesu.djiremote.dji.DjiRepository
import com.dimadesu.djiremote.dji.SettingsDjiDevice
import com.dimadesu.djiremote.dji.SettingsDjiDeviceState

@Composable
fun DjiDevicesSettingsScreen(onOpenDevice: (SettingsDjiDevice) -> Unit) {
    val context = LocalContext.current
    val devices by DjiRepository.devices.collectAsState()
    var profileToDelete by remember { mutableStateOf<SettingsDjiDevice?>(null) }

    // Bestätigungsdialog vor dem Löschen
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
                Text(
                    text = "${devices.size} Profil(e) gespeichert",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { onOpenDevice(device) },
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header Row: Profil-Name & Action Buttons (Start/Stop + Löschen)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!device.customCameraName.isNullOrBlank()) {
                                        Text(
                                            text = "📷 ${device.customCameraName} (${device.model})",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else if (device.bluetoothPeripheralName != null) {
                                        Text(
                                            text = "📷 ${device.bluetoothPeripheralName} (${device.model})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // 1-CLICK START / STOP BUTTON
                                val isStarted = device.isStarted
                                val isStreaming = device.state == SettingsDjiDeviceState.STREAMING

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
                                            isStarted -> Color(0xFFFFA000) // Orange beim Verbinden/Vorbereiten
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = when {
                                            isStreaming -> "⏹ Stop"
                                            isStarted -> "⏳ Läuft..."
                                            else -> "▶ Start"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // EINZELNER LÖSCHEN-BUTTON FÜR DIESES SPEZIFISCHE PROFIL
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

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(10.dp))

                            // Status & Detail Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Status: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = when (device.state) {
                                            SettingsDjiDeviceState.IDLE -> "Bereit (Inaktiv)"
                                            SettingsDjiDeviceState.DISCOVERING -> "Sucht Kamera..."
                                            SettingsDjiDeviceState.CONNECTING -> "Verbindet BLE..."
                                            SettingsDjiDeviceState.PAIRING -> "Kopplung..."
                                            SettingsDjiDeviceState.STOPPING_STREAM -> "Stoppt..."
                                            SettingsDjiDeviceState.PREPARING_STREAM -> "Bereitet vor..."
                                            SettingsDjiDeviceState.SETTING_UP_WIFI -> "WLAN-Setup..."
                                            SettingsDjiDeviceState.WIFI_SETUP_FAILED -> "WLAN fehlgeschlagen"
                                            SettingsDjiDeviceState.CONFIGURING -> "Konfiguriert..."
                                            SettingsDjiDeviceState.STARTING_STREAM -> "Startet Stream..."
                                            SettingsDjiDeviceState.STREAMING -> "🔴 LIVE STREAM"
                                            SettingsDjiDeviceState.RECONNECTING -> "Neu verbinden (Versuch ${device.reconnectAttempt})..."
                                            else -> device.state.name
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = when (device.state) {
                                            SettingsDjiDeviceState.STREAMING -> Color(0xFF2E7D32)
                                            SettingsDjiDeviceState.WIFI_SETUP_FAILED -> MaterialTheme.colorScheme.error
                                            SettingsDjiDeviceState.RECONNECTING -> Color(0xFFE65100)
                                            SettingsDjiDeviceState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                                }

                                if (device.batteryPercentage != null) {
                                    Text(
                                        text = "🔋 ${device.batteryPercentage}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (device.batteryPercentage!! <= 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Ziel-Infos anzeigen (WLAN / Auflösung)
                            if (device.wifiSsid.isNotBlank() || device.rtmpUrl.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (device.wifiSsid.isNotBlank()) {
                                        Text(
                                            text = "📶 ${device.wifiSsid}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${device.resolution} • ${device.bitrate / 1_000_000}M",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Show last error if present
                            if (!device.lastError.isNullOrBlank()) {
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
