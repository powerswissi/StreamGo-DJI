package com.dimadesu.djiremote.ui.dji

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.djiremote.dji.*
import com.dimadesu.djiremote.preset.ConnectionPreset
import com.dimadesu.djiremote.preset.PresetRepository
import com.dimadesu.djiremote.settings.AppSettingsRepository
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DjiDeviceSettingsScreen(
    device: SettingsDjiDevice,
    onBack: () -> Unit = {},
    onOpenScanner: () -> Unit = {},
    onOpenPresets: () -> Unit = {}
) {
    val context = LocalContext.current

    // Observe the live device from the repository so UI recomposes on state/isStarted/lastError changes
    val allDevices by DjiRepository.devices.collectAsState()
    val liveDevice = allDevices.firstOrNull { it.id == device.id } ?: device

    var name by remember { mutableStateOf(liveDevice.name) }
    var customCameraName by remember { mutableStateOf(liveDevice.customCameraName ?: "") }
    var ssid by remember { mutableStateOf(liveDevice.wifiSsid) }
    var password by remember { mutableStateOf(liveDevice.wifiPassword) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var rtmpUrl by remember { mutableStateOf(liveDevice.rtmpUrl) }
    var resolution by remember { mutableStateOf(liveDevice.resolution) }
    var bitrate by remember { mutableIntStateOf(liveDevice.bitrate) }
    var imageStabilization by remember { mutableStateOf(liveDevice.imageStabilization) }

    var expandedResolution by remember { mutableStateOf(false) }
    var expandedBitrate by remember { mutableStateOf(false) }
    var expandedImageStab by remember { mutableStateOf(false) }
    var expandedFps by remember { mutableStateOf(false) }
    var expandedWifiDropdown by remember { mutableStateOf(false) }

    // Presets observation & state
    val presets by PresetRepository.presets.collectAsState()
    var expandedPresetDropdown by remember { mutableStateOf(false) }

    // Own Hotspot observation & dialog state
    val appSettings by AppSettingsRepository.settings.collectAsState()
    var showOwnHotspotDialog by remember { mutableStateOf(false) }
    var hotspotDialogSsid by remember(appSettings.ownHotspotSsid) { mutableStateOf(appSettings.ownHotspotSsid) }
    var hotspotDialogPassword by remember(appSettings.ownHotspotPassword) { mutableStateOf(appSettings.ownHotspotPassword) }
    var isHotspotDialogPwdVisible by remember { mutableStateOf(false) }

    // WiFi scanner observation
    val wifiScanResults by WifiScanner.scanResults.collectAsState()
    val isWifiScanning by WifiScanner.isScanning.collectAsState()
    val wifiScanError by WifiScanner.lastScanError.collectAsState()

    val hasWifiPermissions = WifiScanner.hasPermissions(context)
    val wifiPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.any { it }
        if (granted) {
            WifiScanner.startScan(context)
        }
    }

    // Automatically trigger WiFi scan on entering this screen
    LaunchedEffect(Unit) {
        if (hasWifiPermissions) {
            WifiScanner.startScan(context)
        }
    }

    val resolutionOptions = listOf("1080p", "720p", "480p")
    val bitrateOptions = listOf(
        20_000_000,
        16_000_000,
        12_000_000,
        10_000_000,
        8_000_000,
        6_000_000,
        4_000_000,
        2_000_000
    )

    fun imageStabToString(value: SettingsDjiDeviceImageStabilization): String {
        return when (value) {
            SettingsDjiDeviceImageStabilization.OFF -> "Off"
            SettingsDjiDeviceImageStabilization.ROCK_STEADY -> "RockSteady"
            SettingsDjiDeviceImageStabilization.ROCK_STEADY_PLUS -> "RockSteady+"
            SettingsDjiDeviceImageStabilization.HORIZON_BALANCING -> "HorizonBalancing"
            SettingsDjiDeviceImageStabilization.HORIZON_STEADY -> "HorizonSteady"
        }
    }

    fun stringToImageStab(value: String): SettingsDjiDeviceImageStabilization {
        return when (value) {
            "RockSteady" -> SettingsDjiDeviceImageStabilization.ROCK_STEADY
            "RockSteady+" -> SettingsDjiDeviceImageStabilization.ROCK_STEADY_PLUS
            "HorizonBalancing" -> SettingsDjiDeviceImageStabilization.HORIZON_BALANCING
            "HorizonSteady" -> SettingsDjiDeviceImageStabilization.HORIZON_STEADY
            else -> SettingsDjiDeviceImageStabilization.OFF
        }
    }

    val imageStabOptions = listOf("Off", "RockSteady", "RockSteady+", "HorizonBalancing", "HorizonSteady")

    // Save config to repository whenever user leaves the screen
    DisposableEffect(Unit) {
        onDispose {
            val latest = DjiRepository.devices.value.firstOrNull { it.id == device.id } ?: return@onDispose
            DjiRepository.updateDevice(
                latest.copy(
                    name = name,
                    wifiSsid = ssid,
                    wifiPassword = password,
                    rtmpUrl = rtmpUrl,
                    resolution = resolution,
                    bitrate = bitrate,
                    imageStabilization = imageStabilization
                )
            )
        }
    }

    if (showOwnHotspotDialog) {
        AlertDialog(
            onDismissRequest = { showOwnHotspotDialog = false },
            title = { Text("📱 Eigener Hotspot einrichten", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Da Android den eigenen Handy-Hotspot nicht im WLAN-Scan anzeigt, kannst du ihn hier einmalig hinterlegen und jederzeit mit 1 Klick auswählen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = hotspotDialogSsid,
                        onValueChange = { hotspotDialogSsid = it },
                        label = { Text("Hotspot Name (SSID)") },
                        placeholder = { Text("z. B. iPhone / Galaxy Hotspot") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = hotspotDialogPassword,
                        onValueChange = { hotspotDialogPassword = it },
                        label = { Text("Hotspot Passwort") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (isHotspotDialogPwdVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isHotspotDialogPwdVisible = !isHotspotDialogPwdVisible }) {
                                Icon(
                                    imageVector = if (isHotspotDialogPwdVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedSsid = hotspotDialogSsid.trim()
                        if (trimmedSsid.isNotBlank()) {
                            AppSettingsRepository.updateSettings(
                                appSettings.copy(
                                    ownHotspotSsid = trimmedSsid,
                                    ownHotspotPassword = hotspotDialogPassword
                                )
                            )
                            ssid = trimmedSsid
                            password = hotspotDialogPassword
                            liveDevice.wifiSsid = ssid
                            liveDevice.wifiPassword = password
                            DjiRepository.updateDevice(liveDevice)
                            showOwnHotspotDialog = false
                            Toast.makeText(context, "Eigener Hotspot gespeichert & ausgewählt!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = hotspotDialogSsid.isNotBlank()
                ) {
                    Text("Speichern & Auswählen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOwnHotspotDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Name section
        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !liveDevice.isStarted
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Device section
        Text("DJI Kamera", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onOpenScanner,
            modifier = Modifier.fillMaxWidth(),
            enabled = !liveDevice.isStarted
        ) {
            Text(liveDevice.bluetoothPeripheralName ?: "Kamera über Bluetooth auswählen")
        }
        if (liveDevice.bluetoothPeripheralAddress != null) {
            Text(
                text = "${liveDevice.bluetoothPeripheralAddress} (${liveDevice.model})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = customCameraName,
                onValueChange = { 
                    customCameraName = it
                    liveDevice.customCameraName = it.ifBlank { null }
                    DjiRepository.updateDevice(liveDevice)
                },
                label = { Text("Eigener Kamera-Name (z.B. Helm-Cam)") },
                placeholder = { Text("z.B. Helm-Cam, Brust-Cam, Cam 1") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !liveDevice.isStarted
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // WiFi section with presets and scanner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("WiFi", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // Compact Preset Dropdown Button
                if (presets.isNotEmpty()) {
                    Box {
                        FilledTonalButton(
                            onClick = { if (!liveDevice.isStarted) expandedPresetDropdown = true },
                            enabled = !liveDevice.isStarted,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("⚡ Vorlage ▼", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        DropdownMenu(
                            expanded = expandedPresetDropdown,
                            onDismissRequest = { expandedPresetDropdown = false }
                        ) {
                            presets.forEach { item ->
                                DropdownMenuItem(
                                    text = {
                                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(
                                                text = "📶 ${item.wifiSsid.ifEmpty { "–" }} • 🎥 ${item.rtmpUrl.ifEmpty { "–" }}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        ssid = item.wifiSsid
                                        if (item.wifiPassword.isNotEmpty()) {
                                            password = item.wifiPassword
                                        }
                                        if (item.rtmpUrl.isNotEmpty()) {
                                            rtmpUrl = item.rtmpUrl
                                        }
                                        liveDevice.wifiSsid = ssid
                                        liveDevice.wifiPassword = password
                                        liveDevice.rtmpUrl = rtmpUrl
                                        DjiRepository.updateDevice(liveDevice)
                                        expandedPresetDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = {
                        if (hasWifiPermissions) {
                            WifiScanner.startScan(context)
                        } else {
                            wifiPermissionsLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_WIFI_STATE,
                                    Manifest.permission.CHANGE_WIFI_STATE
                                )
                            )
                        }
                    },
                    enabled = !isWifiScanning && !liveDevice.isStarted,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    if (isWifiScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scanne...", fontSize = 12.sp)
                    } else {
                        Text("Scannen ⟳", fontSize = 12.sp)
                    }
                }
            }
        }

        if (wifiScanError != null) {
            Text(
                text = wifiScanError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Quick selection for Own Hotspot
        val isOwnHotspotConfigured = appSettings.ownHotspotSsid.isNotBlank()
        val isOwnHotspotActive = isOwnHotspotConfigured && ssid == appSettings.ownHotspotSsid
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !liveDevice.isStarted) {
                    if (isOwnHotspotConfigured) {
                        ssid = appSettings.ownHotspotSsid
                        if (appSettings.ownHotspotPassword.isNotEmpty()) {
                            password = appSettings.ownHotspotPassword
                        }
                        liveDevice.wifiSsid = ssid
                        liveDevice.wifiPassword = password
                        DjiRepository.updateDevice(liveDevice)
                        Toast.makeText(context, "Eigener Hotspot '${appSettings.ownHotspotSsid}' ausgewählt!", Toast.LENGTH_SHORT).show()
                    } else {
                        hotspotDialogSsid = ssid
                        hotspotDialogPassword = password
                        showOwnHotspotDialog = true
                    }
                },
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(
                width = if (isOwnHotspotActive) 2.dp else 1.dp,
                color = if (isOwnHotspotActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            ),
            colors = CardDefaults.outlinedCardColors(
                containerColor = if (isOwnHotspotActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isOwnHotspotConfigured) {
                            "📱 Eigener Hotspot: ${appSettings.ownHotspotSsid}"
                        } else {
                            "📱 Eigener Hotspot auswählen / einrichten"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isOwnHotspotActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isOwnHotspotConfigured) {
                            if (isOwnHotspotActive) "✓ Ausgewählt • Tippen zum Übernehmen" else "Tippen um eigenen Handy-Hotspot zu nutzen"
                        } else {
                            "Einmalig Name & Passwort speichern (wird von Android nicht gescannt)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = {
                        hotspotDialogSsid = appSettings.ownHotspotSsid.ifBlank { ssid }
                        hotspotDialogPassword = appSettings.ownHotspotPassword.ifBlank { password }
                        showOwnHotspotDialog = true
                    },
                    enabled = !liveDevice.isStarted,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(if (isOwnHotspotConfigured) "Ändern ✎" else "Einrichten", fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Dropdown / Selection for discovered WiFi networks
        ExposedDropdownMenuBox(
            expanded = expandedWifiDropdown,
            onExpandedChange = { if (!liveDevice.isStarted) expandedWifiDropdown = !expandedWifiDropdown }
        ) {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !liveDevice.isStarted),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "WLAN / Hotspot auswählen (${wifiScanResults.size} im Scan)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Gescannte Netzwerke oder eigenen Hotspot wählen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedWifiDropdown)
                }
            }

            ExposedDropdownMenu(
                expanded = expandedWifiDropdown,
                onDismissRequest = { expandedWifiDropdown = false }
            ) {
                // Always show "Eigener Hotspot" as top option in the dropdown
                DropdownMenuItem(
                    text = {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = if (isOwnHotspotConfigured) "📱 Eigener Hotspot (${appSettings.ownHotspotSsid})" else "📱 Eigener Hotspot (Einrichten...)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isOwnHotspotConfigured) "Eigenen Smartphone-Hotspot verwenden" else "Hotspot-Zugangsdaten hinterlegen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        expandedWifiDropdown = false
                        if (isOwnHotspotConfigured) {
                            ssid = appSettings.ownHotspotSsid
                            if (appSettings.ownHotspotPassword.isNotEmpty()) {
                                password = appSettings.ownHotspotPassword
                            }
                            liveDevice.wifiSsid = ssid
                            liveDevice.wifiPassword = password
                            DjiRepository.updateDevice(liveDevice)
                        } else {
                            hotspotDialogSsid = ssid
                            hotspotDialogPassword = password
                            showOwnHotspotDialog = true
                        }
                    }
                )

                if (wifiScanResults.isNotEmpty()) {
                    HorizontalDivider()
                }

                wifiScanResults.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(item.ssid, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${item.rssi} dBm", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(
                                    text = "Signal: ${item.signalPercentage}% (${item.level}/4) • ${item.band} • ${item.security}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            ssid = item.ssid
                            expandedWifiDropdown = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = ssid,
            onValueChange = { ssid = it },
            label = { Text("SSID") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !liveDevice.isStarted
        )

        // SSID Scan verification indicator
        if (ssid.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            val isFound = WifiScanner.isSsidFound(ssid)
            if (isOwnHotspotActive) {
                Text(
                    text = "✓ 📱 Eigener Hotspot aktiv ausgewählt (${appSettings.ownHotspotSsid})",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Medium
                )
            } else if (isFound) {
                val scanItem = WifiScanner.getScanItem(ssid)
                Text(
                    text = "✓ Im WiFi-Scan gefunden (Signal: ${scanItem?.rssi} dBm, ${scanItem?.band}, ${scanItem?.security})",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = "ℹ️ Nicht im Scan gefunden (Normal bei Handy-Hotspot oder versteckter SSID)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !liveDevice.isStarted,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (isPasswordVisible) "Passwort verbergen" else "Passwort anzeigen"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "The DJI device will connect to and stream RTMP over this WiFi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // RTMP section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("RTMP Stream-Ziel", style = MaterialTheme.typography.titleMedium)
            FilledTonalButton(
                onClick = {
                    rtmpUrl = "rtmp://10.42.0.1:1935/publish/live"
                    liveDevice.rtmpUrl = rtmpUrl
                    DjiRepository.updateDevice(liveDevice)
                },
                enabled = !liveDevice.isStarted,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("⚡ Belabox 1-Click", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = rtmpUrl,
            onValueChange = { 
                rtmpUrl = it
                liveDevice.rtmpUrl = it
                DjiRepository.updateDevice(liveDevice)
            },
            label = { Text("RTMP URL") },
            placeholder = { Text("rtmp://10.42.0.1:1935/publish/live") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !liveDevice.isStarted
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Settings section
        Text("Settings", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        // Resolution dropdown
        ExposedDropdownMenuBox(
            expanded = expandedResolution,
            onExpandedChange = { expandedResolution = !expandedResolution }
        ) {
            TextField(
                value = resolution,
                onValueChange = {},
                readOnly = true,
                label = { Text("Resolution") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedResolution) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !liveDevice.isStarted),
                enabled = !liveDevice.isStarted
            )
            ExposedDropdownMenu(
                expanded = expandedResolution,
                onDismissRequest = { expandedResolution = false }
            ) {
                resolutionOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            resolution = option
                            expandedResolution = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bitrate dropdown
        ExposedDropdownMenuBox(
            expanded = expandedBitrate,
            onExpandedChange = { expandedBitrate = !expandedBitrate }
        ) {
            TextField(
                value = "${bitrate / 1_000_000} Mbps",
                onValueChange = {},
                readOnly = true,
                label = { Text("Bitrate") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBitrate) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !liveDevice.isStarted),
                enabled = !liveDevice.isStarted
            )
            ExposedDropdownMenu(
                expanded = expandedBitrate,
                onDismissRequest = { expandedBitrate = false }
            ) {
                bitrateOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text("${option / 1_000_000} Mbps") },
                        onClick = {
                            bitrate = option
                            expandedBitrate = false
                        }
                    )
                }
            }
        }

        // Image Stabilization dropdown
        if (liveDevice.model.hasImageStabilization()) {
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = expandedImageStab,
                onExpandedChange = { expandedImageStab = !expandedImageStab }
            ) {
                TextField(
                    value = imageStabToString(imageStabilization),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Image stabilization") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedImageStab) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !liveDevice.isStarted),
                    enabled = !liveDevice.isStarted
                )
                ExposedDropdownMenu(
                    expanded = expandedImageStab,
                    onDismissRequest = { expandedImageStab = false }
                ) {
                    imageStabOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                imageStabilization = stringToImageStab(option)
                                expandedImageStab = false
                            }
                        )
                    }
                }
            }
        }

        // FPS picker
        if (liveDevice.model == SettingsDjiDeviceModel.OSMO_POCKET_3 ||
            liveDevice.model == SettingsDjiDeviceModel.OSMO_POCKET_4) {
            val fpsOptions = listOf(25, 30)
            var fps by remember { mutableIntStateOf(liveDevice.fps) }
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = expandedFps,
                onExpandedChange = { expandedFps = !expandedFps }
            ) {
                TextField(
                    value = "$fps",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("FPS") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFps) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !liveDevice.isStarted),
                    enabled = !liveDevice.isStarted
                )
                ExposedDropdownMenu(
                    expanded = expandedFps,
                    onDismissRequest = { expandedFps = false }
                ) {
                    fpsOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text("$option") },
                            onClick = {
                                fps = option
                                expandedFps = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "High bitrates may be unstable.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Status section
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (liveDevice.state) {
                    SettingsDjiDeviceState.IDLE -> "Not started"
                    SettingsDjiDeviceState.DISCOVERING -> "Discovering"
                    SettingsDjiDeviceState.CONNECTING -> "Connecting"
                    SettingsDjiDeviceState.PAIRING -> "Pairing"
                    SettingsDjiDeviceState.STOPPING_STREAM -> "Stopping stream"
                    SettingsDjiDeviceState.PREPARING_STREAM -> "Preparing to stream"
                    SettingsDjiDeviceState.SETTING_UP_WIFI -> "Setting up WiFi"
                    SettingsDjiDeviceState.WIFI_SETUP_FAILED -> "WiFi setup failed"
                    SettingsDjiDeviceState.CONFIGURING -> "Configuring"
                    SettingsDjiDeviceState.STARTING_STREAM -> "Starting stream"
                    SettingsDjiDeviceState.STREAMING -> "Streaming"
                    SettingsDjiDeviceState.RECONNECTING -> "Verbinde neu... (Versuch ${liveDevice.reconnectAttempt})"
                    else -> "Unknown"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = when (liveDevice.state) {
                    SettingsDjiDeviceState.STREAMING -> MaterialTheme.colorScheme.primary
                    SettingsDjiDeviceState.WIFI_SETUP_FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }

        // Prominent Last Error Display (Requirement 8: Never swallow errors)
        if (!liveDevice.lastError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "⚠️ Letzter Verbindungsfehler:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = liveDevice.lastError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Start/Stop button section
        if (!liveDevice.isStarted) {
            Button(
                onClick = {
                    liveDevice.name = name
                    liveDevice.customCameraName = customCameraName.ifBlank { null }
                    liveDevice.wifiSsid = ssid
                    liveDevice.wifiPassword = password
                    liveDevice.rtmpUrl = rtmpUrl
                    liveDevice.resolution = resolution
                    liveDevice.bitrate = bitrate
                    liveDevice.imageStabilization = imageStabilization
                    DjiRepository.updateDevice(liveDevice)

                    DjiModel.startStreaming(context, liveDevice)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start live stream")
            }
        } else {
            val buttonText = if (liveDevice.state == SettingsDjiDeviceState.RECONNECTING) {
                "Verbinde neu... (Versuch ${liveDevice.reconnectAttempt})"
            } else {
                "Stop live stream"
            }
            Button(
                onClick = {
                    DjiModel.stopStreaming(liveDevice)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(buttonText)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
    }
}
