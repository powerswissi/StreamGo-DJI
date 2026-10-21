package com.dimadesu.djiremote.ui.dji

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.djiremote.dji.DjiBleScanner
import com.dimadesu.djiremote.dji.SettingsDjiDeviceModel

private const val TAG = "DjiBleScannerScreen"

@Composable
fun DjiBleScannerScreen(
    onSelect: (String, String, SettingsDjiDeviceModel) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val discoveredState by DjiBleScanner.discovered.collectAsState()
    val scanError by DjiBleScanner.scanError.collectAsState(initial = null)
    val isBtEnabled = DjiBleScanner.isBluetoothEnabled(context)
    var showAllDevices by remember { mutableStateOf(false) }

    val hasPermissions = DjiBleScanner.hasPermissions(context)

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { perms ->
            val granted = perms.values.all { it }
            if (granted) {
                DjiBleScanner.startScanning(context, !showAllDevices)
            }
        }
    )

    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            DjiBleScanner.startScanning(context, !showAllDevices)
        }
    }


    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "DJI-Kamera auswählen",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (!hasPermissions) {
            Text("Für die Suche nach DJI-Geräten werden Bluetooth- und Standortberechtigungen benötigt.")
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissionsLauncher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                } else {
                    permissionsLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                }
            }) {
                Text("Berechtigungen erteilen")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { onBack() }) { Text("Zurück") }
            return@Column
        }

        if (!isBtEnabled) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    text = "⚠️ Bluetooth ist deaktiviert. Bitte Bluetooth einschalten.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        scanError?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    text = "⚠️ $err",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Alle BLE-Geräte anzeigen (ohne DJI-Filter)")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = showAllDevices,
                onCheckedChange = {
                    showAllDevices = it
                    DjiBleScanner.startScanning(context, !it)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (discoveredState.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Suche nach DJI-Kameras...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Kamera einschalten & Bluetooth aktivieren",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(discoveredState) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelect(device.address, device.name, device.model) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Modell: ${device.model}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = device.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (device.rssi != 0) {
                                Text(
                                    text = "${device.rssi} dBm",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { DjiBleScanner.startScanning(context, !showAllDevices) }) {
                Text("Neu scannen ⟳")
            }
            OutlinedButton(onClick = {
                DjiBleScanner.stopScanning()
                onBack()
            }) {
                Text("Zurück")
            }
        }
    }
}
