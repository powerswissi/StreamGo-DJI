package com.dimadesu.djiremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.dimadesu.djiremote.dji.DjiRepository
import com.dimadesu.djiremote.dji.SettingsDjiDevice
import com.dimadesu.djiremote.ui.dji.DjiBleScannerScreen
import com.dimadesu.djiremote.ui.dji.DjiDeviceSettingsScreen
import com.dimadesu.djiremote.ui.dji.DjiDevicesSettingsScreen
import com.dimadesu.djiremote.ui.settings.AppSettingsScreen
import com.dimadesu.djiremote.ui.theme.DJIRemoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize repository with context
        DjiRepository.initialize(this)

        enableEdgeToEdge()
        setContent {
            DJIRemoteTheme {
                var screen by remember { mutableStateOf("devices") } // "devices", "device", "device-scanner", "settings"
                var selectedDevice by remember { mutableStateOf<SettingsDjiDevice?>(null) }

                BackHandler(enabled = screen != "devices") {
                    screen = when (screen) {
                        "device" -> "devices"
                        "device-scanner" -> "device"
                        "settings" -> "devices"
                        else -> "devices"
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        when (screen) {
                            "devices" -> {
                                DjiDevicesSettingsScreen(
                                    onOpenDevice = {
                                        selectedDevice = it
                                        screen = "device"
                                    },
                                    onOpenSettings = {
                                        screen = "settings"
                                    }
                                )
                            }
                            "device" -> {
                                selectedDevice?.let { d ->
                                    DjiDeviceSettingsScreen(
                                        device = d,
                                        onBack = { screen = "devices" },
                                        onOpenScanner = {
                                            screen = "device-scanner"
                                        }
                                    )
                                }
                            }
                            "device-scanner" -> {
                                DjiBleScannerScreen(
                                    onSelect = { address, name, model ->
                                        selectedDevice?.let { d ->
                                            val latest = DjiRepository.devices.value
                                                .firstOrNull { it.id == d.id } ?: d
                                            DjiRepository.updateDevice(latest.copy(
                                                bluetoothPeripheralAddress = address,
                                                bluetoothPeripheralName = name,
                                                model = model
                                            ))
                                        }
                                        screen = "device"
                                    },
                                    onBack = { screen = "device" }
                                )
                            }
                            "settings" -> {
                                AppSettingsScreen(onBack = { screen = "devices" })
                            }
                        }
                    }
                }
            }
        }
    }
}
