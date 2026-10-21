package com.dimadesu.djiremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.dimadesu.djiremote.dji.DjiRepository
import com.dimadesu.djiremote.dji.SettingsDjiDevice
import com.dimadesu.djiremote.ui.dashboard.DashboardScreen
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
                var currentTab by remember { mutableStateOf("profiles") }
                var screen by remember { mutableStateOf("main") } // "main", "device", "device-scanner"
                var selectedDevice by remember { mutableStateOf<SettingsDjiDevice?>(null) }

                BackHandler(enabled = screen != "main" || currentTab != "profiles") {
                    if (screen == "device-scanner") {
                        screen = "device"
                    } else if (screen == "device") {
                        screen = "main"
                    } else if (currentTab != "profiles") {
                        currentTab = "profiles"
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        // Show bottom nav bar only when at the top-level main screens
                        if (screen == "main") {
                            NavigationBar {
                                val devices by DjiRepository.devices.collectAsState()
                                val isAnyStreaming = devices.any { it.isStarted }

                                NavigationBarItem(
                                    selected = currentTab == "profiles",
                                    onClick = { currentTab = "profiles" },
                                    icon = { Text("📹") },
                                    label = { Text("Profile") }
                                )

                                NavigationBarItem(
                                    selected = currentTab == "dashboard",
                                    onClick = { currentTab = "dashboard" },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (isAnyStreaming) {
                                                    Badge { Text("LIVE") }
                                                }
                                            }
                                        ) {
                                            Text("📊")
                                        }
                                    },
                                    label = { Text("Dashboard") }
                                )

                                NavigationBarItem(
                                    selected = currentTab == "settings",
                                    onClick = { currentTab = "settings" },
                                    icon = { Text("⚙️") },
                                    label = { Text("Einstellungen") }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        when (screen) {
                            "main" -> {
                                when (currentTab) {
                                    "profiles" -> {
                                        DjiDevicesSettingsScreen(onOpenDevice = {
                                            selectedDevice = it
                                            screen = "device"
                                        })
                                    }
                                    "dashboard" -> {
                                        DashboardScreen(onNavigateToProfiles = {
                                            currentTab = "profiles"
                                        })
                                    }
                                    "settings" -> {
                                        AppSettingsScreen()
                                    }
                                }
                            }
                            "device" -> {
                                selectedDevice?.let { d ->
                                    DjiDeviceSettingsScreen(
                                        device = d,
                                        onBack = { screen = "main" },
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
                        }
                    }
                }
            }
        }
    }
}