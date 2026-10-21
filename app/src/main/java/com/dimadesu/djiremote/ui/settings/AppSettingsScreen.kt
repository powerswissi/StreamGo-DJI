package com.dimadesu.djiremote.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimadesu.djiremote.dji.DjiDeviceStorage
import com.dimadesu.djiremote.dji.DjiRepository
import com.dimadesu.djiremote.preset.PresetRepository
import com.dimadesu.djiremote.settings.AppSettingsRepository
import com.dimadesu.djiremote.settings.AppThemeMode
import kotlin.math.roundToInt

@Composable
fun AppSettingsScreen(
    onBack: () -> Unit = {},
    onOpenPresets: () -> Unit = {}
) {
    val context = LocalContext.current
    val devices by DjiRepository.devices.collectAsState()
    val appSettings by AppSettingsRepository.settings.collectAsState()
    val presets by PresetRepository.presets.collectAsState()

    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonText by remember { mutableStateOf("") }
    var restoreError by remember { mutableStateOf<String?>(null) }

    // Dialog for Restore
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = {
                showRestoreDialog = false
                restoreJsonText = ""
                restoreError = null
            },
            title = { Text("Profile wiederherstellen") },
            text = {
                Column {
                    Text(
                        "Füge den exportierten Backup-Text (JSON) hier ein. Deine bestehenden Profile werden ersetzt:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restoreJsonText,
                        onValueChange = {
                            restoreJsonText = it
                            restoreError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        placeholder = { Text("JSON Backup-Code einfügen...") },
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    restoreError?.let { err ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val imported = DjiDeviceStorage.importFromJson(restoreJsonText.trim())
                        if (imported != null && imported.isNotEmpty()) {
                            DjiRepository.replaceAllDevices(imported)
                            Toast.makeText(context, "${imported.size} Profil(e) erfolgreich wiederhergestellt!", Toast.LENGTH_SHORT).show()
                            showRestoreDialog = false
                            restoreJsonText = ""
                        } else {
                            restoreError = "Ungültiges Format oder leeres Backup!"
                        }
                    }
                ) {
                    Text("Wiederherstellen")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestoreDialog = false
                    restoreJsonText = ""
                    restoreError = null
                }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with Back Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text("←", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "Einstellungen",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "StreamGo DJI Konfiguration & Anzeige",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section 1: Display & Design
        SettingsCard(title = "🎨 ERSCHEINUNGSBILD & DISPLAY") {
            // Theme Mode
            Text("Design-Modus", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = appSettings.themeMode == AppThemeMode.DARK,
                    onClick = { AppSettingsRepository.updateSettings(appSettings.copy(themeMode = AppThemeMode.DARK)) },
                    label = { Text("Dunkel") }
                )
                FilterChip(
                    selected = appSettings.themeMode == AppThemeMode.LIGHT,
                    onClick = { AppSettingsRepository.updateSettings(appSettings.copy(themeMode = AppThemeMode.LIGHT)) },
                    label = { Text("Hell") }
                )
                FilterChip(
                    selected = appSettings.themeMode == AppThemeMode.SYSTEM,
                    onClick = { AppSettingsRepository.updateSettings(appSettings.copy(themeMode = AppThemeMode.SYSTEM)) },
                    label = { Text("System") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Keep Screen On
            SettingsToggleRow(
                title = "Bildschirm immer an",
                subtitle = "Verhindert automatisches Abschalten/Sperren während die App geöffnet ist",
                checked = appSettings.keepScreenOn,
                onCheckedChange = { AppSettingsRepository.updateSettings(appSettings.copy(keepScreenOn = it)) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Outdoor Max Brightness
            SettingsToggleRow(
                title = "Outdoor-Modus (Sonne)",
                subtitle = "Erzwingt 100% Display-Helligkeit für optimale Ablesbarkeit im Sonnenlicht",
                checked = appSettings.outdoorMaxBrightness,
                onCheckedChange = { AppSettingsRepository.updateSettings(appSettings.copy(outdoorMaxBrightness = it)) }
            )
        }

        // Section 2: Akku-Warnung (Frei einstellbar)
        SettingsCard(title = "🔋 KAMERA AKKU-WARNUNG") {
            SettingsToggleRow(
                title = "Warnung bei schwachem Akku",
                subtitle = "Alarmiert rechtzeitig, bevor der DJI-Kameraakku im Stream ausgeht",
                checked = appSettings.batteryWarningEnabled,
                onCheckedChange = { AppSettingsRepository.updateSettings(appSettings.copy(batteryWarningEnabled = it)) }
            )

            if (appSettings.batteryWarningEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Warnschwelle:", style = MaterialTheme.typography.bodyMedium)
                    Text("${appSettings.batteryWarningThreshold}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = appSettings.batteryWarningThreshold.toFloat(),
                    onValueChange = { AppSettingsRepository.updateSettings(appSettings.copy(batteryWarningThreshold = it.roundToInt())) },
                    valueRange = 10f..40f,
                    steps = 5
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsToggleRow(
                    title = "Vibrations-Alarm",
                    subtitle = "Vibriert bei Erreichen der Warnschwelle",
                    checked = appSettings.batteryWarningVibration,
                    onCheckedChange = { AppSettingsRepository.updateSettings(appSettings.copy(batteryWarningVibration = it)) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsToggleRow(
                    title = "Signalton (Audio-Beep)",
                    subtitle = "Gibt einen kurzen Hinweiston aus",
                    checked = appSettings.batteryWarningSound,
                    onCheckedChange = { AppSettingsRepository.updateSettings(appSettings.copy(batteryWarningSound = it)) }
                )
            }
        }

        // Section 3: Feedback & Reconnect
        SettingsCard(title = "🔄 VERBINDUNG & RECONNECT") {
            SettingsToggleRow(
                title = "Automatischer Reconnect",
                subtitle = "Verbindet bei Funk- oder Signalabriss automatisch neu",
                checked = appSettings.autoReconnectEnabled,
                onCheckedChange = { AppSettingsRepository.updateSettings(appSettings.copy(autoReconnectEnabled = it)) }
            )

            if (appSettings.autoReconnectEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Max. Reconnect-Versuche:", style = MaterialTheme.typography.bodyMedium)
                    Text("${appSettings.maxReconnectAttempts}x", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = appSettings.maxReconnectAttempts.toFloat(),
                    onValueChange = { AppSettingsRepository.updateSettings(appSettings.copy(maxReconnectAttempts = it.roundToInt())) },
                    valueRange = 3f..30f,
                    steps = 26
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggleRow(
                title = "Haptisches Feedback",
                subtitle = "Vibration bei Start, Stopp & Verbindungsereignissen",
                checked = appSettings.hapticFeedbackEnabled,
                onCheckedChange = { AppSettingsRepository.updateSettings(appSettings.copy(hapticFeedbackEnabled = it)) }
            )
        }

        // Section: Presets
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenPresets() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text("⚡", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Verbindungs-Vorlagen (Presets)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${presets.size} Vorlage(n) • WLAN, QR-Scan & RTMP",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text("➔", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }

        // Section 4: Backup & Restore
        SettingsCard(title = "📦 PROFILE BACKUP & RESTORE") {
            Text(
                "Sichere alle ${devices.size} Kamera-Profile & RTMP-URLs als Text oder stelle ein bestehendes Backup wieder her.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val json = DjiDeviceStorage.exportToJson(devices)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("StreamGo DJI Profile Backup", json)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Backup in Zwischenablage kopiert!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("📋 Exportieren")
                }

                OutlinedButton(
                    onClick = { showRestoreDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("📥 Wiederherstellen")
                }
            }
        }

        // Section 5: App-Info & GitHub
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("SG", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Column {
                        Text("StreamGo DJI by Swissi", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Version 1.0.2 • 100% Vibe Coding", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/powerswissi/StreamGo-DJI"))
                            context.startActivity(intent)
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("GitHub Repository (powerswissi/StreamGo-DJI)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Text("↗", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
