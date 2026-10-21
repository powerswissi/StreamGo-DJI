package com.dimadesu.djiremote.ui.preset

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.djiremote.dji.WifiScanner
import com.dimadesu.djiremote.preset.ConnectionPreset
import com.dimadesu.djiremote.preset.PresetRepository

@Composable
fun SavePresetDialog(
    initialSsid: String = "",
    initialPassword: String = "",
    initialRtmpUrl: String = "",
    onDismiss: () -> Unit,
    onSave: (ConnectionPreset) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf(initialSsid) }
    var password by remember { mutableStateOf(initialPassword) }
    var rtmpUrl by remember { mutableStateOf(initialRtmpUrl) }

    var expandedWifiDropdown by remember { mutableStateOf(false) }
    val wifiScanResults by WifiScanner.scanResults.collectAsState()
    val isWifiScanning by WifiScanner.isScanning.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Als Vorlage speichern", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Speichere diese Zugangsdaten als Preset, um sie später schnell für beliebige Kameras zu verwenden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset-Name *") },
                    placeholder = { Text("z. B. Handy Hotspot, Heimstudio") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // WiFi SSID with optional scan chooser
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("WLAN-Name (SSID)", style = MaterialTheme.typography.labelMedium)
                        if (wifiScanResults.isNotEmpty()) {
                            TextButton(
                                onClick = { expandedWifiDropdown = !expandedWifiDropdown },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Aus Scan wählen (${wifiScanResults.size})", fontSize = 11.sp)
                            }
                        }
                    }

                    if (expandedWifiDropdown && wifiScanResults.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            LazyColumn {
                                items(wifiScanResults) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                ssid = item.ssid
                                                expandedWifiDropdown = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(item.ssid, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        Text("${item.signalPercentage}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    OutlinedTextField(
                        value = ssid,
                        onValueChange = { ssid = it },
                        label = { Text("SSID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("WLAN-Passwort") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                OutlinedTextField(
                    value = rtmpUrl,
                    onValueChange = { rtmpUrl = it },
                    label = { Text("RTMP Stream-Ziel (optional)") },
                    placeholder = { Text("rtmp://...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val newPreset = ConnectionPreset(
                            name = name.trim(),
                            wifiSsid = ssid.trim(),
                            wifiPassword = password,
                            rtmpUrl = rtmpUrl.trim()
                        )
                        PresetRepository.addPreset(newPreset)
                        onSave(newPreset)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
fun EditPresetDialog(
    preset: ConnectionPreset,
    onDismiss: () -> Unit,
    onSave: (ConnectionPreset) -> Unit
) {
    var name by remember { mutableStateOf(preset.name) }
    var ssid by remember { mutableStateOf(preset.wifiSsid) }
    var password by remember { mutableStateOf(preset.wifiPassword) }
    var rtmpUrl by remember { mutableStateOf(preset.rtmpUrl) }

    var expandedWifiDropdown by remember { mutableStateOf(false) }
    val wifiScanResults by WifiScanner.scanResults.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vorlage bearbeiten", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset-Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("WLAN-Name (SSID)", style = MaterialTheme.typography.labelMedium)
                        if (wifiScanResults.isNotEmpty()) {
                            TextButton(
                                onClick = { expandedWifiDropdown = !expandedWifiDropdown },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Aus Scan wählen", fontSize = 11.sp)
                            }
                        }
                    }

                    if (expandedWifiDropdown && wifiScanResults.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            LazyColumn {
                                items(wifiScanResults) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                ssid = item.ssid
                                                expandedWifiDropdown = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(item.ssid, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        Text("${item.signalPercentage}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    OutlinedTextField(
                        value = ssid,
                        onValueChange = { ssid = it },
                        label = { Text("SSID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("WLAN-Passwort") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                OutlinedTextField(
                    value = rtmpUrl,
                    onValueChange = { rtmpUrl = it },
                    label = { Text("RTMP Stream-Ziel") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val updated = preset.copy(
                            name = name.trim(),
                            wifiSsid = ssid.trim(),
                            wifiPassword = password,
                            rtmpUrl = rtmpUrl.trim()
                        )
                        PresetRepository.updatePreset(updated)
                        onSave(updated)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
fun ManagePresetsDialog(
    onDismiss: () -> Unit,
    onSelectPreset: ((ConnectionPreset) -> Unit)? = null
) {
    val presets by PresetRepository.presets.collectAsState()
    val context = LocalContext.current
    var editingPreset by remember { mutableStateOf<ConnectionPreset?>(null) }
    var showCreateNew by remember { mutableStateOf(false) }
    var presetToDelete by remember { mutableStateOf<ConnectionPreset?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = {
                showImportDialog = false
                importJsonText = ""
                importError = null
            },
            title = { Text("Vorlagen importieren") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Füge exportierte Presets als JSON-Code ein:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = {
                            importJsonText = it
                            importError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        placeholder = { Text("[{\"name\":\"Hotspot\",...}]") },
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    if (importError != null) {
                        Text(importError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = PresetRepository.importFromJson(importJsonText)
                        if (success) {
                            showImportDialog = false
                            importJsonText = ""
                            Toast.makeText(context, "Vorlagen erfolgreich importiert!", Toast.LENGTH_SHORT).show()
                        } else {
                            importError = "Ungültiges JSON-Format."
                        }
                    },
                    enabled = importJsonText.isNotBlank()
                ) {
                    Text("Importieren")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (editingPreset != null) {
        EditPresetDialog(
            preset = editingPreset!!,
            onDismiss = { editingPreset = null },
            onSave = { editingPreset = null }
        )
    }

    if (showCreateNew) {
        SavePresetDialog(
            onDismiss = { showCreateNew = false },
            onSave = { showCreateNew = false }
        )
    }

    if (presetToDelete != null) {
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = { Text("Preset löschen?") },
            text = { Text("Möchtest du das Preset '${presetToDelete?.name}' wirklich löschen?") },
            confirmButton = {
                Button(
                    onClick = {
                        presetToDelete?.let { PresetRepository.deletePreset(it.id) }
                        presetToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Vorlagen (Presets)", fontWeight = FontWeight.Bold)
                FilledTonalButton(
                    onClick = { showCreateNew = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Neu", fontSize = 12.sp)
                }
            }
        },
        text = {
            if (presets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Noch keine Presets gespeichert.\nTippe auf '+ Neu', um eine Vorlage anzulegen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(presets) { preset ->
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (onSelectPreset != null) {
                                        onSelectPreset(preset)
                                        onDismiss()
                                    }
                                },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "📶 WLAN: ${preset.wifiSsid.ifEmpty { "–" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (preset.rtmpUrl.isNotEmpty()) {
                                        Text(
                                            text = "🎥 RTMP: ${preset.rtmpUrl.take(28)}...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { editingPreset = preset },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Bearbeiten", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { presetToDelete = preset },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            val json = PresetRepository.exportToJson()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("StreamGo Presets Backup", json)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Vorlagen in Zwischenablage kopiert!", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("📋 Export", fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = { showImportDialog = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("📥 Import", fontSize = 12.sp)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Schließen")
                }
            }
        }
    )
}
