package com.dimadesu.djiremote.dji

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.util.UUID

private const val TAG = "DjiBleScanner"

data class DiscoveredDjiDevice(
    val id: String,
    val address: String,
    val name: String,
    val model: SettingsDjiDeviceModel,
    val rssi: Int = 0
)

object DjiBleScanner {
    private val _discovered = MutableStateFlow<List<DiscoveredDjiDevice>>(emptyList())
    val discovered: StateFlow<List<DiscoveredDjiDevice>> = _discovered

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()
    private var scanner: BluetoothLeScanner? = null
    private val handler = Handler(Looper.getMainLooper())
    private var callback: ScanCallback? = null
    private var filterOnlyDji: Boolean = true
    private val foundDevices = mutableListOf<DiscoveredDjiDevice>()

    fun hasPermissions(context: Context): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            val connectGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            val locationGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            scanGranted && connectGranted && locationGranted
        } else {
            val locationGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            locationGranted
        }
        return result
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        return adapter.isEnabled
    }

    @SuppressLint("MissingPermission")
    fun startScanning(context: Context, filterOnlyDji: Boolean = true) {
        if (_isScanning.value) {
            stopScanning(clearResults = false)
        }
        this.filterOnlyDji = filterOnlyDji
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            val err = "Bluetooth-Adapter ist nicht verfügbar (null)!"
            _scanError.value = err
            return
        }
        if (!adapter.isEnabled) {
            val err = "Bluetooth ist auf diesem Gerät deaktiviert! Bitte aktivieren."
            _scanError.value = err
            return
        }
        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            val err = "BluetoothLeScanner ist null (wird BLE unterstützt?)"
            _scanError.value = err
            return
        }

        // Clear previous results when starting a fresh scan
        synchronized(foundDevices) {
            foundDevices.clear()
            _discovered.value = emptyList()
        }
        _scanError.value = null

        callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    val scanRecord = result.scanRecord
                    val address = device.address ?: return
                    val name = device.name ?: address
                    val rssi = result.rssi

                    val isDji = isDjiAdvertisement(scanRecord)
                    if (this@DjiBleScanner.filterOnlyDji && !isDji) {
                        return
                    }

                    // Detect model
                    val model = getModelFromScanRecord(scanRecord)

                    val id = UUID.nameUUIDFromBytes(address.toByteArray(StandardCharsets.UTF_8)).toString()
                    synchronized(foundDevices) {
                        val existingIdx = foundDevices.indexOfFirst { it.id == id }
                        if (existingIdx == -1) {
                            foundDevices.add(DiscoveredDjiDevice(id, address, name, model, rssi))
                            _discovered.value = foundDevices.toList()
                        } else if (rssi != 0 && foundDevices[existingIdx].rssi != rssi) {
                            foundDevices[existingIdx] = foundDevices[existingIdx].copy(rssi = rssi)
                            _discovered.value = foundDevices.toList()
                        }
                    }
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                val decoded = GattErrorDecoder.decodeBleScanError(errorCode)
                val errMsg = "BLE-Scan fehlgeschlagen: $decoded"
                _scanError.value = errMsg
                _isScanning.value = false
            }
        }

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner?.startScan(null, settings, callback)
            _isScanning.value = true
        } catch (e: Exception) {
            val err = "Fehler beim Aufruf von startScan(): ${e.message}"
            _scanError.value = err
            _isScanning.value = false
            return
        }

        // stop active BLE radio scan after 30s automatically, but keep discovered devices visible
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopScanning(clearResults = false) }, 30_000)
    }

    @SuppressLint("MissingPermission")
    fun stopScanning(clearResults: Boolean = false) {
        handler.removeCallbacksAndMessages(null)
        if (_isScanning.value) {
            try {
                scanner?.stopScan(callback)
            } catch (e: Exception) {
            }
        }
        callback = null
        scanner = null
        _isScanning.value = false
        if (clearResults) {
            _scanError.value = null
            synchronized(foundDevices) {
                foundDevices.clear()
                _discovered.value = emptyList()
            }
        }
    }

    private fun isDjiAdvertisement(record: ScanRecord?): Boolean {
        if (record == null) return false

        // DJI manufacturer ID is 0x08AA (2218 in decimal)
        // The manufacturer ID is stored as little-endian in the key
        // DJI Technology Co Ltd = 0x08AA (2218), Xtra Ltd = 0xF7AA (63402)
        val djiManufacturerId = 2218
        val xtraManufacturerId = 63402

        // Check if either manufacturer ID exists
        for (mfgId in listOf(djiManufacturerId, xtraManufacturerId)) {
            val data = record.getManufacturerSpecificData(mfgId)
            if (data != null) {
                return true
            }
        }

        return false
    }

    fun getModelFromScanRecord(record: ScanRecord?): SettingsDjiDeviceModel {
        if (record == null) return SettingsDjiDeviceModel.UNKNOWN

        // Check both DJI and Xtra manufacturer IDs
        val djiManufacturerId = 2218
        val xtraManufacturerId = 63402
        var data: ByteArray? = null
        for (mfgId in listOf(djiManufacturerId, xtraManufacturerId)) {
            data = record.getManufacturerSpecificData(mfgId)
            if (data != null) break
        }

        if (data != null && data.size >= 2) {
            // Model is in bytes 0-1 of manufacturer data (little-endian)
            val modelId = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
            val model = when (modelId) {
                0x0010 -> SettingsDjiDeviceModel.OSMO_ACTION_2
                0x0012 -> SettingsDjiDeviceModel.OSMO_ACTION_3
                0x0014 -> SettingsDjiDeviceModel.OSMO_ACTION_4
                0x0015 -> SettingsDjiDeviceModel.OSMO_ACTION_5_PRO
                0x0017 -> SettingsDjiDeviceModel.OSMO_360
                0x0018 -> SettingsDjiDeviceModel.OSMO_ACTION_6
                0x0020 -> SettingsDjiDeviceModel.OSMO_POCKET_3
                0x0021 -> SettingsDjiDeviceModel.OSMO_POCKET_4
                else -> SettingsDjiDeviceModel.UNKNOWN
            }
            return model
        }

        return SettingsDjiDeviceModel.UNKNOWN
    }
}
