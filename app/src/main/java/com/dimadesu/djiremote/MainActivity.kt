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
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import com.dimadesu.djiremote.settings.AppSettingsRepository
import com.dimadesu.djiremote.settings.AppThemeMode
import com.dimadesu.djiremote.dji.DjiRepository
import com.dimadesu.djiremote.dji.SettingsDjiDevice
import com.dimadesu.djiremote.ui.dji.DjiBleScannerScreen
import com.dimadesu.djiremote.ui.dji.DjiDeviceSettingsScreen
import com.dimadesu.djiremote.ui.dji.DjiDevicesSettingsScreen
import com.dimadesu.djiremote.ui.preset.PresetsScreen
import com.dimadesu.djiremote.ui.settings.AppSettingsScreen
import com.dimadesu.djiremote.ui.theme.DJIRemoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize repository with context
        DjiRepository.initialize(this)
        AppSettingsRepository.initialize(this)
        com.dimadesu.djiremote.preset.PresetRepository.initialize(this)

        enableEdgeToEdge()
        setContent {
            val appSettings by AppSettingsRepository.settings.collectAsState()

            // Keep screen on
            DisposableEffect(appSettings.keepScreenOn) {
                if (appSettings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            // Outdoor max brightness
            DisposableEffect(appSettings.outdoorMaxBrightness) {
                val layoutParams = window.attributes
                if (appSettings.outdoorMaxBrightness) {
                    layoutParams.screenBrightness = 1.0f
                } else {
                    layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                window.attributes = layoutParams
                onDispose {
                    val lp = window.attributes
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.attributes = lp
                }
            }

            val darkTheme = when (appSettings.themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            DJIRemoteTheme(darkTheme = darkTheme) {
                var screen by remember { mutableStateOf("devices") } // "devices", "device", "device-scanner", "settings", "presets"
                var selectedDevice by remember { mutableStateOf<SettingsDjiDevice?>(null) }

                BackHandler(enabled = screen != "devices") {
                    screen = when (screen) {
                        "device" -> "devices"
                        "device-scanner" -> "device"
                        "settings" -> "devices"
                        "presets" -> "devices"
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
                                    },
                                    onOpenPresets = {
                                        screen = "presets"
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
                                        },
                                        onOpenPresets = {
                                            screen = "presets"
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
                                AppSettingsScreen(
                                    onBack = { screen = "devices" },
                                    onOpenPresets = { screen = "presets" }
                                )
                            }
                            "presets" -> {
                                PresetsScreen(onBack = { screen = "devices" })
                            }
                        }
                    }
                }
            }
        }
    }
}
