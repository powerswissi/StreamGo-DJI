package com.dimadesu.djiremote.dji

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WifiScanItem(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val level: Int, // 0..4
    val frequencyMhz: Int,
    val capabilities: String
) {
    val band: String
        get() = when (frequencyMhz) {
            in 2400..2500 -> "2.4 GHz"
            in 4900..5900 -> "5 GHz"
            in 5925..7125 -> "6 GHz"
            else -> "$frequencyMhz MHz"
        }

    val signalPercentage: Int
        get() = ((rssi + 100) * 2).coerceIn(0, 100)

    val security: String
        get() = when {
            capabilities.contains("WPA3", ignoreCase = true) || capabilities.contains("SAE", ignoreCase = true) -> "WPA3"
            capabilities.contains("WPA2", ignoreCase = true) -> "WPA2"
            capabilities.contains("WPA", ignoreCase = true) -> "WPA"
            capabilities.contains("WEP", ignoreCase = true) -> "WEP"
            else -> "Offen"
        }
}

/**
 * Manages WiFi scanning via Android WifiManager.
 * Emits discovered networks with SSID and signal strength, and validates SSID availability.
 */
object WifiScanner {
    private const val TAG = "WifiScanner"

    private val _scanResults = MutableStateFlow<List<WifiScanItem>>(emptyList())
    val scanResults: StateFlow<List<WifiScanItem>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _lastScanError = MutableStateFlow<String?>(null)
    val lastScanError: StateFlow<String?> = _lastScanError.asStateFlow()

    private var receiverRegistered = false
    private var wifiReceiver: BroadcastReceiver? = null

    fun hasPermissions(context: Context): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val wifiState = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_WIFI_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val granted = (fineLocation || coarseLocation) && wifiState
        return granted
    }

    @SuppressLint("MissingPermission")
    fun startScan(context: Context) {
        val appCtx = context.applicationContext
        _lastScanError.value = null

        val wifiManager = appCtx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager == null) {
            val err = "WifiManager auf diesem Gerät nicht verfügbar!"
            _lastScanError.value = err
            return
        }

        if (!wifiManager.isWifiEnabled) {
            val err = "WLAN ist auf dem Gerät deaktiviert. Bitte WLAN in den Einstellungen einschalten."
            _lastScanError.value = err
            _isScanning.value = false
            return
        }

        // Fetch any existing/cached scan results immediately
        try {
            updateFromScanResults(wifiManager.scanResults)
        } catch (e: Exception) {
        }

        // Register receiver for fresh scan results
        registerReceiverIfNeeded(appCtx, wifiManager)

        _isScanning.value = true
        try {
            @Suppress("DEPRECATION")
            val success = wifiManager.startScan()
            if (!success) {
                _isScanning.value = false
            }
        } catch (e: Exception) {
            _lastScanError.value = "Scan-Fehler: ${e.message}"
            _isScanning.value = false
        }
    }

    private fun registerReceiverIfNeeded(context: Context, wifiManager: WifiManager) {
        if (receiverRegistered) return

        wifiReceiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    _isScanning.value = false
                    try {
                        updateFromScanResults(wifiManager.scanResults)
                    } catch (e: Exception) {
                    }
                }
            }
        }

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        ContextCompat.registerReceiver(
            context,
            wifiReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    private fun updateFromScanResults(rawResults: List<ScanResult>?) {
        if (rawResults == null || rawResults.isEmpty()) {
            return
        }

        // Group by SSID, filter out empty SSIDs, and keep the result with the best RSSI
        val itemsBySsid = mutableMapOf<String, WifiScanItem>()

        for (result in rawResults) {
            val ssid = getSsid(result)
            if (ssid.isBlank() || ssid == "<unknown ssid>") continue

            val level = WifiManager.calculateSignalLevel(result.level, 5)
            val item = WifiScanItem(
                ssid = ssid,
                bssid = result.BSSID ?: "",
                rssi = result.level,
                level = level,
                frequencyMhz = result.frequency,
                capabilities = result.capabilities ?: ""
            )

            val existing = itemsBySsid[ssid]
            if (existing == null || result.level > existing.rssi) {
                itemsBySsid[ssid] = item
            }
        }

        // Sort descending by signal strength
        val sortedList = itemsBySsid.values.sortedByDescending { it.rssi }
        _scanResults.value = sortedList
    }

    @Suppress("DEPRECATION")
    private fun getSsid(result: ScanResult): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.wifiSsid?.toString()?.trim('"') ?: result.SSID ?: ""
        } else {
            result.SSID ?: ""
        }
    }

    /**
     * Checks whether a specific SSID was found in the recent WiFi scan.
     */
    fun isSsidFound(ssid: String): Boolean {
        val trimmed = ssid.trim()
        if (trimmed.isEmpty()) return false
        return _scanResults.value.any { it.ssid.equals(trimmed, ignoreCase = false) }
    }

    /**
     * Retrieves the WifiScanItem for an SSID, or null if not found.
     */
    fun getScanItem(ssid: String): WifiScanItem? {
        val trimmed = ssid.trim()
        return _scanResults.value.firstOrNull { it.ssid.equals(trimmed, ignoreCase = false) }
    }

    /**
     * Returns a list of all currently discovered SSIDs.
     */
    fun getAvailableSsids(): List<String> {
        return _scanResults.value.map { it.ssid }
    }
}
