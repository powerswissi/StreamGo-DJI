package com.dimadesu.djiremote.dji

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

private const val TAG = "DjiModel"

// Manages DjiDevice instances keyed by profile UUID, matching Moblin's approach.
// Each SettingsDjiDevice profile gets its own DjiDevice instance.
object DjiModel : DjiDeviceDelegate {
    private val deviceWrappers = mutableMapOf<java.util.UUID, DjiDevice>()
    private val scope: CoroutineScope = MainScope()

    fun startStreaming(context: Context, settings: SettingsDjiDevice) {

        val address = settings.bluetoothPeripheralAddress
        if (address == null) {
            val err = "Stream kann nicht gestartet werden: Keine Bluetooth-Adresse ausgewählt!"
            recordError(settings, err)
            return
        }
        if (settings.wifiSsid.isBlank()) {
            val err = "Stream kann nicht gestartet werden: WLAN-SSID ist leer!"
            recordError(settings, err)
            return
        }
        if (settings.rtmpUrl.isBlank()) {
            val err = "Stream kann nicht gestartet werden: RTMP-URL ist leer!"
            recordError(settings, err)
            return
        }

        // Check if SSID was detected in recent WiFi scan
        if (!WifiScanner.isSsidFound(settings.wifiSsid)) {
            val available = WifiScanner.getAvailableSsids()
            val list = if (available.isEmpty()) "Keine" else available.joinToString(", ")
        }

        val device = deviceWrappers.getOrPut(settings.id) {
            DjiDevice(context).also { it.delegate = this }
        }

        val res = when (settings.resolution) {
            "1080p" -> SettingsDjiDeviceResolution.r1080p
            "720p" -> SettingsDjiDeviceResolution.r720p
            "480p" -> SettingsDjiDeviceResolution.r480p
            else -> SettingsDjiDeviceResolution.r1080p
        }
        val imageStab = settings.imageStabilization

        // Reset error on fresh start
        scope.launch(Dispatchers.Main) {
            DjiRepository.updateDevice(
                settings.copy(
                    isStarted = true,
                    state = SettingsDjiDeviceState.PREPARING_STREAM,
                    lastError = null,
                    lastErrorTimestamp = null
                )
            )
        }

        device.startLiveStream(
            address = address,
            wifiSsid = settings.wifiSsid,
            wifiPassword = settings.wifiPassword,
            rtmpUrl = settings.rtmpUrl,
            resolution = res,
            fps = settings.fps,
            bitrateKbps = settings.bitrate / 1000,
            imageStabilization = imageStab,
            model = settings.model
        )

        DjiConnectionService.startIfNeeded(context)
    }

    private fun recordError(settings: SettingsDjiDevice, errorMsg: String) {
        scope.launch(Dispatchers.Main) {
            val latest = DjiRepository.devices.value.firstOrNull { it.id == settings.id } ?: settings
            DjiRepository.updateDevice(
                latest.copy(
                    isStarted = false,
                    state = SettingsDjiDeviceState.IDLE,
                    lastError = errorMsg,
                    lastErrorTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun stopStreaming(settings: SettingsDjiDevice) {
        val device = deviceWrappers[settings.id] ?: return
        device.stopLiveStream()
        scope.launch(Dispatchers.Main) {
            val latest = DjiRepository.devices.value.firstOrNull { it.id == settings.id } ?: settings
            DjiRepository.updateDevice(
                latest.copy(
                    isStarted = false,
                    state = SettingsDjiDeviceState.IDLE,
                    streamingStartedAt = null
                )
            )
        }
    }

    // Find which profile owns a DjiDevice instance via === identity, matching Moblin
    private fun getSettingsForDevice(djiDevice: DjiDevice): SettingsDjiDevice? {
        val profileId = deviceWrappers.entries.firstOrNull { it.value === djiDevice }?.key
            ?: return null
        return DjiRepository.devices.value.firstOrNull { it.id == profileId }
    }

    override fun djiDeviceStreamingState(device: DjiDevice, state: DjiDeviceState, error: String?) {
        scope.launch(Dispatchers.Main) {
            val existing = getSettingsForDevice(device) ?: return@launch
            val newState = when (state) {
                DjiDeviceState.IDLE -> SettingsDjiDeviceState.IDLE
                DjiDeviceState.DISCOVERING -> SettingsDjiDeviceState.DISCOVERING
                DjiDeviceState.CONNECTING -> SettingsDjiDeviceState.CONNECTING
                DjiDeviceState.CHECKING_IF_PAIRED -> SettingsDjiDeviceState.PAIRING
                DjiDeviceState.PAIRING -> SettingsDjiDeviceState.PAIRING
                DjiDeviceState.CLEANING_UP -> SettingsDjiDeviceState.STOPPING_STREAM
                DjiDeviceState.STOPPING_STREAM -> SettingsDjiDeviceState.STOPPING_STREAM
                DjiDeviceState.PREPARING_STREAM -> SettingsDjiDeviceState.PREPARING_STREAM
                DjiDeviceState.SETTING_UP_WIFI -> SettingsDjiDeviceState.SETTING_UP_WIFI
                DjiDeviceState.WIFI_SETUP_FAILED -> SettingsDjiDeviceState.WIFI_SETUP_FAILED
                DjiDeviceState.CONFIGURING -> SettingsDjiDeviceState.CONFIGURING
                DjiDeviceState.STARTING_STREAM -> SettingsDjiDeviceState.STARTING_STREAM
                DjiDeviceState.STREAMING -> SettingsDjiDeviceState.STREAMING
                DjiDeviceState.RECONNECTING -> SettingsDjiDeviceState.RECONNECTING
            }

            var updated = existing.copy(
                state = newState,
                batteryPercentage = device.getBatteryPercentage(),
                cameraTemperature = device.getCameraTemperature(),
                liveBitrateKbps = device.getLiveBitrateKbps()
            )

            if (error != null) {
                updated = updated.copy(
                    lastError = error,
                    lastErrorTimestamp = System.currentTimeMillis()
                )
            } else if (newState == SettingsDjiDeviceState.STREAMING) {
                // Wenn der Stream erfolgreich aktiv läuft, alten Fehler verwerfen!
                updated = updated.copy(
                    lastError = null,
                    lastErrorTimestamp = null
                )
            }

            when (state) {
                DjiDeviceState.IDLE, DjiDeviceState.WIFI_SETUP_FAILED -> {
                    updated = updated.copy(isStarted = false, reconnectAttempt = 0, streamingStartedAt = null)
                }
                DjiDeviceState.RECONNECTING -> {
                    updated = updated.copy(isStarted = true, reconnectAttempt = device.getReconnectAttempts())
                }
                DjiDeviceState.STREAMING -> {
                    val startedAt = existing.streamingStartedAt ?: System.currentTimeMillis()
                    updated = updated.copy(isStarted = true, reconnectAttempt = 0, streamingStartedAt = startedAt)
                }
                else -> {
                    updated = updated.copy(isStarted = true, reconnectAttempt = 0)
                }
            }
            DjiRepository.updateDevice(updated)
        }
    }
}
