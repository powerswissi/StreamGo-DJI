package com.dimadesu.djiremote.ui.preset

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.djiremote.dji.WifiScanner
import com.dimadesu.djiremote.preset.ConnectionPreset
import com.dimadesu.djiremote.preset.PresetRepository
import com.dimadesu.djiremote.preset.QrCodeHelper
import com.dimadesu.djiremote.preset.QrParsedResult
import com.google.gson.Gson

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val presets by PresetRepository.presets.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<ConnectionPreset?>(null) }
    var presetToDelete by remember { mutableStateOf<ConnectionPreset?>(null) }
    var showQrDialogForPreset by remember { mutableStateOf<ConnectionPreset?>(null) }

    // Pre-fill states when scanned from QR or WiFi
    var prefillSsid by remember { mutableStateOf("") }
    var prefillPassword by remember { mutableStateOf("") }
    var prefillRtmp by remember { mutableStateOf("") }

    // WiFi Scanner observation
    val wifiScanResults by WifiScanner.scanResults.collectAsState()
    val isWifiScanning by WifiScanner.isScanning.collectAsState()
    var showWifiScanList by remember { mutableStateOf(false) }

    // Gallery QR Picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val parsed = QrCodeHelper.decodeFromImageUri(context, uri)
            if (parsed != null) {
                prefillSsid = parsed.wifiSsid
                prefillPassword = parsed.wifiPassword
                prefillRtmp = parsed.rtmpUrl
                showCreateDialog = true
                Toast.makeText(context, "QR-Code aus Bild erkannt: ${parsed.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Kein gültiger QR-Code im Bild gefunden", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Live Scanner handler
    fun startQrScanner() {
        QrCodeHelper.startLiveScanner(
            context = context,
            onSuccess = { parsed ->
                prefillSsid = parsed.wifiSsid
                prefillPassword = parsed.wifiPassword
                prefillRtmp = parsed.rtmpUrl
                showCreateDialog = true
                val notice = when (parsed.type) {
                    QrParsedResult.QrType.WIFI_CONFIG -> "WLAN QR-Code erkannt (${parsed.wifiSsid})!"
                    QrParsedResult.QrType.RTMP_URL -> "RTMP Stream-URL erkannt!"
                    QrParsedResult.QrType.STREAMGO_PRESET -> "StreamGo Vorlage erkannt!"
                    else -> "QR-Code Daten übernommen!"
                }
                Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
            },
            onError = { err ->
                Toast.makeText(context, "Scanner: $err", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog for Creating or Pre-filled QR
    if (showCreateDialog) {
        SavePresetDialog(
            initialSsid = prefillSsid,
            initialPassword = prefillPassword,
            initialRtmpUrl = prefillRtmp,
            onDismiss = {
                showCreateDialog = false
                prefillSsid = ""
                prefillPassword = ""
                prefillRtmp = ""
            },
            onSave = {
                showCreateDialog = false
                prefillSsid = ""
                prefillPassword = ""
                prefillRtmp = ""
                Toast.makeText(context, "Vorlage gespeichert!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (editingPreset != null) {
        EditPresetDialog(
            preset = editingPreset!!,
            onDismiss = { editingPreset = null },
            onSave = {
                editingPreset = null
                Toast.makeText(context, "Vorlage aktualisiert!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (presetToDelete != null) {
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = { Text("Vorlage löschen?") },
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

    // QR-Code Display Dialog
    if (showQrDialogForPreset != null) {
        val p = showQrDialogForPreset!!
        val qrContent = if (p.wifiSsid.isNotEmpty() && p.rtmpUrl.isEmpty()) {
            // Standard WLAN QR-Code: kann jedes Smartphone direkt für WLAN nutzen
            "WIFI:S:${p.wifiSsid};T:WPA;P:${p.wifiPassword};;"
        } else {
            // StreamGo Full Preset JSON
            Gson().toJson(p)
        }
        val qrBitmap = remember(qrContent) { QrCodeHelper.generateQrBitmap(qrContent, 600) }

        AlertDialog(
            onDismissRequest = { showQrDialogForPreset = null },
            title = {
                Text("QR-Code: ${p.name}", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (qrBitmap != null) {
                        Surface(
                            modifier = Modifier.size(240.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White
                        ) {
                            Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Text(
                        text = if (p.wifiSsid.isNotEmpty() && p.rtmpUrl.isEmpty()) {
                            "Standard WLAN-QR-Code:\nKann von jedem Smartphone mit der Kamera gescannt werden, um sich direkt mit '${p.wifiSsid}' zu verbinden."
                        } else {
                            "StreamGo Preset-QR:\nEnthält WLAN & RTMP. Kann direkt in StreamGo auf einem anderen Handy gescannt werden."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialogForPreset = null }) {
                    Text("Schließen")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Verbindungs-Vorlagen", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("${presets.size} Preset(s) gespeichert", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = {
                            prefillSsid = ""
                            prefillPassword = ""
                            prefillRtmp = ""
                            showCreateDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Neu")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Action Card: QR-Code Scannen
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📷", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("QR-Code Schnell-Import", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Scanne WLAN-Router, Handy-Hotspots oder RTMP-Links", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { startQrScanner() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("📷 QR scannen", fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("🖼️ Aus Bild", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Action Card: Live WLAN Scanner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📶", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("WLAN in Reichweite", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        if (wifiScanResults.isNotEmpty()) "${wifiScanResults.size} Netzwerke gefunden" else "Suche nach WLANs in der Nähe",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            TextButton(
                                onClick = {
                                    WifiScanner.startScan(context)
                                    showWifiScanList = true
                                },
                                enabled = !isWifiScanning
                            ) {
                                if (isWifiScanning) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Scanne...")
                                } else {
                                    Text("Scannen ⟳")
                                }
                            }
                        }

                        if (wifiScanResults.isNotEmpty()) {
                            TextButton(
                                onClick = { showWifiScanList = !showWifiScanList },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    if (showWifiScanList) "▲ Gefundene WLANs einklappen" else "▼ Gefundene WLANs anzeigen (${wifiScanResults.size})",
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (showWifiScanList && wifiScanResults.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                LazyColumn {
                                    items(wifiScanResults) { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    prefillSsid = item.ssid
                                                    prefillPassword = ""
                                                    prefillRtmp = ""
                                                    showCreateDialog = true
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(item.ssid, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                Text("${item.signalPercentage}% • ${item.band} • ${item.security}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text("+ Als Preset", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Presets List Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "GESPEICHERTE VORLAGEN",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                val json = PresetRepository.exportToJson()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("StreamGo Presets Backup", json)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Alle Vorlagen als JSON kopiert!", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("📋 Export", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (presets.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Noch keine Vorlagen hinterlegt.\nScanne einen QR-Code oder tippe auf '+ Neu'.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(presets) { preset ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // QR Code Share Button
                                    IconButton(
                                        onClick = { showQrDialogForPreset = preset },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "QR anzeigen", modifier = Modifier.size(16.dp))
                                    }

                                    // Edit
                                    IconButton(
                                        onClick = { editingPreset = preset },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Bearbeiten", modifier = Modifier.size(16.dp))
                                    }

                                    // Delete
                                    IconButton(
                                        onClick = { presetToDelete = preset },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            // Details
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("📶 ", fontSize = 12.sp)
                                        Text(
                                            preset.wifiSsid.ifEmpty { "Kein WLAN" },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (preset.wifiPassword.isNotEmpty()) {
                                            Text(" (🔒)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }

                                if (preset.rtmpUrl.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("🎥 ", fontSize = 12.sp)
                                            Text(
                                                preset.rtmpUrl.take(24) + "...",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
