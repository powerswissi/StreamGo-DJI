package com.dimadesu.djiremote.dji

import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG = "DjiDevice"

/**
 * File logger wrapper for persistent and in-memory logging.
 * Kept for backwards compatibility with any legacy call sites.
 */
object DjiFileLogger {
    fun init(context: Context) {
    }

    fun log(msg: String) {
    }

    fun getPath(): String? = null

    /** Returns the log file if it exists and is non-empty, or null. */
    fun getFile(): File? = null
}

// UUIDs from the iOS implementation (16-bit) expanded to 128-bit base
val FFF3_UUID: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
val FFF4_UUID: UUID = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb")
val FFF5_UUID: UUID = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb")
val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

// Transaction ids / targets / types ported from the iOS implementation
private const val PAIR_TRANSACTION_ID = 0x8092
private const val STOP_STREAMING_TRANSACTION_ID = 0xEAC8
private const val PREPARING_TO_LIVESTREAM_TRANSACTION_ID = 0x8C12
private const val SETUP_WIFI_TRANSACTION_ID = 0x8C19
private const val START_STREAMING_TRANSACTION_ID = 0x8C2C
private const val CONFIGURE_TRANSACTION_ID = 0x8C2D

private const val PAIR_TARGET = 0x0702
private const val STOP_STREAMING_TARGET = 0x0802
private const val PREPARING_TO_LIVESTREAM_TARGET = 0x0802
private const val SETUP_WIFI_TARGET = 0x0702
private const val CONFIGURE_TARGET = 0x0102
private const val START_STREAMING_TARGET = 0x0802

private const val PAIR_TYPE = 0x450740
private const val STOP_STREAMING_TYPE = 0x8E0240
private const val PREPARING_TO_LIVESTREAM_TYPE = 0xE10240
private const val SETUP_WIFI_TYPE = 0x470740
private const val CONFIGURE_TYPE = 0x8E0240
private const val START_STREAMING_TYPE = 0x780840

private const val PAIR_PIN_CODE = "mbln"

enum class DjiDeviceState {
    IDLE,
    DISCOVERING,
    CONNECTING,
    CHECKING_IF_PAIRED,
    PAIRING,
    CLEANING_UP,
    PREPARING_STREAM,
    SETTING_UP_WIFI,
    WIFI_SETUP_FAILED,
    CONFIGURING,
    STARTING_STREAM,
    STREAMING,
    STOPPING_STREAM,
    RECONNECTING
}

interface DjiDeviceDelegate {
    fun djiDeviceStreamingState(device: DjiDevice, state: DjiDeviceState, error: String? = null)
}

class DjiDevice(private val context: Context) {
    private var bluetoothGatt: BluetoothGatt? = null
    private var fff5Characteristic: BluetoothGattCharacteristic? = null
    private var fff4Characteristic: BluetoothGattCharacteristic? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Write queue
    private val writeChunkSize = 20 // Fixed 20-byte chunks like working Android reference
    private val writeQueue: ArrayDeque<ByteArray> = ArrayDeque()
    private var isWriting: Boolean = false
    private val writeIntervalMs: Long = 30L // pacing between writes for NO_RESPONSE
    // Invalidates already scheduled write runnables after reset/failure/disconnect
    private var writeGeneration: Int = 0
    private var writeRetryCount: Int = 0

    // RX reassembly buffer for multi-chunk message reassembly
    private var rxBuffer = ByteArray(0)
    private var lastRxChunkAt: Long = 0L

    // Descriptor write queue
    private val descriptorWriteQueue: ArrayDeque<BluetoothGattDescriptor> = ArrayDeque()
    private var isWritingDescriptor: Boolean = false

    // State machine fields
    private var wifiSsid: String? = null
    private var wifiPassword: String? = null
    private var rtmpUrl: String? = null
    private var resolution: SettingsDjiDeviceResolution? = null
    private var fps: Int = 30
    private var bitrateKbps: Int = 4000
    private var imageStabilization: SettingsDjiDeviceImageStabilization? = null
    private var deviceAddress: String? = null
    private var model: SettingsDjiDeviceModel = SettingsDjiDeviceModel.UNKNOWN
    private var batteryPercentage: Int? = null
    private var cameraTemperature: Int? = null
    private var liveBitrateKbps: Int? = null

    private var userInitiatedStop: Boolean = false
    private var reconnectAttempts: Int = 0
    private var reconnectRunnable: Runnable? = null
    private var connectRunnable: Runnable? = null
    private var lastGattReleaseAt: Long = 0L
    private var seenActiveStreamInCurrentSession: Boolean = false
    private companion object {
        const val MAX_RECONNECT_ATTEMPTS = 15
        // disconnect() is asynchronous: give the camera time to see the disconnect before close()
        const val GATT_CLOSE_DELAY_MS = 1000L
        // Minimum pause between releasing a GATT connection and opening a new one to the same camera
        const val RECONNECT_GUARD_MS = 2000L
        const val MAX_WRITE_RETRIES = 40
        const val RX_FRAGMENT_TIMEOUT_MS = 500L
        // Broadcast / livestream status telemetry type (bytes [8..10] = 80 EE 03 -> LE24 = 0x03EE80, or 40 EE 03 -> 0x03EE40)
        const val BROADCAST_STATUS_TYPE_1 = 0x03EE40
        const val BROADCAST_STATUS_TYPE_2 = 0x03EE80
    }

    // Error diagnostics tracking
    private var state: DjiDeviceState = DjiDeviceState.IDLE
    private var previousState: DjiDeviceState = DjiDeviceState.IDLE
    private var lastError: String? = null
    private var lastGattStatus: Int? = null
    private var lastTxSummary: String? = null
    private var lastRxSummary: String? = null

    var delegate: DjiDeviceDelegate? = null

    // Timers
    private var startStreamingRunnable: Runnable? = null
    private var stopStreamingRunnable: Runnable? = null
    private var streamingHeartbeatRunnable: Runnable? = null

    private fun djiLog(msg: String) {
        android.util.Log.d(TAG, msg)
    }

    private fun djiLogInfo(msg: String) {
        android.util.Log.i(TAG, msg)
    }

    private fun djiLogWarn(msg: String) {
        android.util.Log.w(TAG, msg)
    }

    private fun djiLogError(msg: String, tr: Throwable? = null) {
        if (tr != null) {
            android.util.Log.e(TAG, msg, tr)
        } else {
            android.util.Log.e(TAG, msg)
        }
    }

    fun startLiveStream(
        address: String,
        wifiSsid: String,
        wifiPassword: String,
        rtmpUrl: String,
        resolution: SettingsDjiDeviceResolution,
        fps: Int,
        bitrateKbps: Int,
        imageStabilization: SettingsDjiDeviceImageStabilization,
        model: SettingsDjiDeviceModel,
        isReconnectAttempt: Boolean = false
    ) {
        lastError = null
        lastGattStatus = null
        lastTxSummary = null
        lastRxSummary = null
        userInitiatedStop = false
        // Fix: reconnectAttempts NICHT bei jedem automatischen Reconnect-Versuch zurücksetzen,
        // sonst greift der Backoff nie und es wird alle 2s ohne Ende neu verbunden
        // (Ursache für "Livestream gestartet bleibt hängen" + BLE-Stack blockiert -> App-Neustart nötig).
        if (!isReconnectAttempt) {
            reconnectAttempts = 0
        }
        cancelReconnect()

        DjiConnectionService.startIfNeeded(context)

        djiLogInfo("=================== START LIVE STREAM ===================")
        djiLogInfo("  Ziel-Adresse: $address")
        djiLogInfo("  Kamera-Modell: $model")
        djiLogInfo("  Ziel-WLAN: '$wifiSsid'")
        djiLogInfo("  RTMP-URL: $rtmpUrl")
        djiLogInfo("  Auflösung: $resolution, FPS: $fps, Bitrate: ${bitrateKbps}kbps, Stabilisierung: $imageStabilization")

        // Pre-check WiFi status
        if (WifiScanner.isSsidFound(wifiSsid)) {
            val item = WifiScanner.getScanItem(wifiSsid)
            djiLogInfo("  ✓ WLAN '$wifiSsid' im Android WiFi-Scan bestätigt (Signal: ${item?.rssi} dBm, Band: ${item?.band})")
        } else {
            val available = WifiScanner.getAvailableSsids()
            djiLogWarn("  ⚠️ Hinweis: WLAN '$wifiSsid' wurde im Android WiFi-Scan bisher nicht gefunden (evtl. versteckt). Verfügbar: [${available.joinToString(", ")}]")
        }

        // configure
        this.wifiSsid = wifiSsid
        this.wifiPassword = wifiPassword
        this.rtmpUrl = rtmpUrl
        this.resolution = resolution
        this.fps = fps
        this.bitrateKbps = bitrateKbps
        this.imageStabilization = imageStabilization
        this.deviceAddress = address
        this.model = model

        reset(keepError = false)
        startStartStreamingTimer()
        setState(DjiDeviceState.DISCOVERING)
        djiLogInfo("Verbinde mit DJI-Kamera über BLE: $address...")
        // Never open a new GATT connection right after releasing the previous one:
        // the camera only accepts one BLE connection and may still consider the old one alive.
        val sinceRelease = SystemClock.elapsedRealtime() - lastGattReleaseAt
        val wait = (RECONNECT_GUARD_MS - sinceRelease).coerceAtLeast(0L)
        if (wait > 0L) {
            val runnable = Runnable {
                connectRunnable = null
                connectToAddress(address)
            }
            connectRunnable = runnable
            mainHandler.postDelayed(runnable, wait)
        } else {
            connectToAddress(address)
        }
    }

    fun stopLiveStream() {
        if (state == DjiDeviceState.IDLE) return
        // No active BLE link (e.g. waiting for reconnect or still connecting): nothing to send, just tear down.
        if (bluetoothGatt == null || fff5Characteristic == null) {
            userInitiatedStop = true
            cancelReconnect()
            reset(keepError = false)
            return
        }
        userInitiatedStop = true
        cancelReconnect()
        djiLogInfo("stopLiveStream() aufgerufen in Zustand: $state")
        stopStartStreamingTimer()
        startStopStreamingTimer()
        sendStopStream()
        setState(DjiDeviceState.STOPPING_STREAM)
    }

    fun getBatteryPercentage(): Int? = batteryPercentage
    fun getCameraTemperature(): Int? = cameraTemperature
    fun getLiveBitrateKbps(): Int? = liveBitrateKbps
    fun getLastError(): String? = lastError
    fun getReconnectAttempts(): Int = reconnectAttempts

    /**
     * Clean up or reset connections.
     * CRITICAL RULE (Goal 4 & 8): Errors must never vanish via reset().
     * If keepError is true, lastError and diagnostic tracking are preserved.
     */
    private fun reset(keepError: Boolean = true) {
        djiLog("reset() aufgerufen (keepError=$keepError, vorheriger Zustand=$state, lastError=$lastError)")
        stopStartStreamingTimer()
        stopStopStreamingTimer()
        stopStreamingHeartbeatTimer()
        cancelPendingConnect()

        releaseGatt()
        batteryPercentage = null
        cameraTemperature = null
        liveBitrateKbps = null
        seenActiveStreamInCurrentSession = false
        rxBuffer = ByteArray(0)
        clearWriteState()

        if (!keepError) {
            lastError = null
        }
        setState(DjiDeviceState.IDLE, error = if (keepError) lastError else null)
    }

    private fun cancelPendingConnect() {
        connectRunnable?.let { mainHandler.removeCallbacks(it) }
        connectRunnable = null
    }

    /** Invalidates all queued/scheduled writes (stale runnables check [writeGeneration]). */
    private fun clearWriteState() {
        writeGeneration++
        writeRetryCount = 0
        writeQueue.clear()
        descriptorWriteQueue.clear()
        isWriting = false
        isWritingDescriptor = false
    }

    /**
     * Releases the current GATT connection gracefully. The reference is dropped immediately
     * (so late callbacks of this connection are ignored), disconnect() is issued right away and
     * close() is deferred so the camera reliably notices the disconnect.
     */
    private fun releaseGatt() {
        val gatt = bluetoothGatt
        bluetoothGatt = null
        fff5Characteristic = null
        fff4Characteristic = null
        if (gatt == null) return
        lastGattReleaseAt = SystemClock.elapsedRealtime()
        try {
            gatt.disconnect()
        } catch (e: Exception) {
            djiLogWarn("Ausnahme bei BluetoothGatt.disconnect(): ${e.message}")
        }
        mainHandler.postDelayed({
            try {
                gatt.close()
            } catch (e: Exception) {
                djiLogWarn("Ausnahme beim Schließen von BluetoothGatt: ${e.message}")
            }
        }, GATT_CLOSE_DELAY_MS)
    }

    private fun cancelReconnect() {
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            djiLog("Reconnect-Versuche erschöpft ($reconnectAttempts/$MAX_RECONNECT_ATTEMPTS). Gebe auf.")
            lastError = "Verbindung zur Kamera nach $reconnectAttempts Versuchen nicht möglich"
            setState(DjiDeviceState.IDLE, error = lastError)
            return
        }

        val delay = when (reconnectAttempts) {
            0 -> 2000L
            1 -> 5000L
            2 -> 10000L
            else -> 15000L
        }
        reconnectAttempts++
        djiLog("Reconnect geplant in ${delay}ms (Versuch $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)")
        setState(DjiDeviceState.RECONNECTING)
        reconnectRunnable = Runnable {
            djiLog("Starte Reconnect (Versuch $reconnectAttempts)")
            val addr = deviceAddress
            val ssid = wifiSsid
            val pwd = wifiPassword
            val rtmp = rtmpUrl
            val res = resolution
            val fps = this.fps
            val bitrate = this.bitrateKbps
            val stab = imageStabilization
            val model = model
            if (!addr.isNullOrEmpty() && !ssid.isNullOrEmpty() && !rtmp.isNullOrEmpty() && res != null) {
                startLiveStream(addr, ssid, pwd ?: "", rtmp, res, fps, bitrate, stab ?: SettingsDjiDeviceImageStabilization.OFF, model, isReconnectAttempt = true)
            } else {
                djiLogWarn("Reconnect übersprungen: Parameter unvollständig")
            }
        }
        mainHandler.postDelayed(reconnectRunnable!!, delay)
    }

    /**
     * Comprehensive Error Handling (Goal 4):
     * Records cause, timestamp, current state, previous state, GATT status,
     * last TX, last RX, exception, and WiFi information.
     */
    fun failWithError(reason: String, details: String? = null, exception: Throwable? = null) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val gattDesc = lastGattStatus?.let { "$it (${GattErrorDecoder.decodeGattStatus(it)})" } ?: "N/A"
        val wifiInfo = "SSID='$wifiSsid' (im Scan gefunden: ${wifiSsid?.let { WifiScanner.isSsidFound(it) } ?: false})"

        val fullDiagnostic = buildString {
            append("Ursache: $reason\n")
            if (!details.isNullOrBlank()) append("Details: $details\n")
            append("Zeitstempel: $timestamp\n")
            append("Zustand: $state (vorher: $previousState)\n")
            append("GATT-Status: $gattDesc\n")
            append("Letztes TX: ${lastTxSummary ?: "Keines"}\n")
            append("Letztes RX: ${lastRxSummary ?: "Keines"}\n")
            append("WLAN: $wifiInfo")
            if (exception != null) append("\nException: ${exception.javaClass.simpleName}: ${exception.message}")
        }

        val shortSummary = if (!details.isNullOrBlank()) {
            if (details.contains(reason)) details else "$reason: $details"
        } else {
            reason
        }
        lastError = shortSummary
        djiLogError("❌ FEHLER AUFGETRETEN:\n$fullDiagnostic", exception)

        // Stop start timer
        stopStartStreamingTimer()

        // Transition to failure state
        if (state == DjiDeviceState.SETTING_UP_WIFI || state == DjiDeviceState.PREPARING_STREAM) {
            setState(DjiDeviceState.WIFI_SETUP_FAILED, error = shortSummary)
        } else {
            setState(DjiDeviceState.IDLE, error = shortSummary)
        }

        // Clean up GATT without wiping the error
        cancelPendingConnect()
        releaseGatt()
        clearWriteState()
    }

    private fun startStartStreamingTimer() {
        stopStartStreamingTimer()
        startStreamingRunnable = Runnable { startStreamingTimerExpired() }
        mainHandler.postDelayed(startStreamingRunnable!!, 60_000)
    }

    private fun stopStartStreamingTimer() {
        startStreamingRunnable?.let { mainHandler.removeCallbacks(it) }
        startStreamingRunnable = null
    }

    private fun startStreamingTimerExpired() {
        val msg = "Stream-Start Timeout nach 60 Sekunden abgelaufen! Kamera reagiert nicht mehr im Zustand: $state"
        failWithError("Timeout beim Starten", msg)
    }

    private fun startStopStreamingTimer() {
        stopStopStreamingTimer()
        stopStreamingRunnable = Runnable { stopStreamingTimerExpired() }
        mainHandler.postDelayed(stopStreamingRunnable!!, 10_000)
    }

    private fun startStreamingHeartbeatTimer() {
        stopStreamingHeartbeatTimer()
        streamingHeartbeatRunnable = Runnable {
            if (state == DjiDeviceState.STREAMING) {
                djiLogWarn("⚠️ Stream-Heartbeat Timeout: Signal zur DJI-Kamera verloren! Starte automatischen Reconnect...")
                failWithError("Signal zur Kamera verloren", "Keine Telemetrie mehr empfangen während STREAMING")
                scheduleReconnect()
            }
        }
        mainHandler.postDelayed(streamingHeartbeatRunnable!!, 8000)
    }

    private fun stopStreamingHeartbeatTimer() {
        streamingHeartbeatRunnable?.let { mainHandler.removeCallbacks(it) }
        streamingHeartbeatRunnable = null
    }

    private fun stopStopStreamingTimer() {
        stopStreamingRunnable?.let { mainHandler.removeCallbacks(it) }
        stopStreamingRunnable = null
    }

    private fun stopStreamingTimerExpired() {
        djiLogWarn("⚠️ Stop-Stream Timeout nach 10 Sekunden abgelaufen. Erzwinge Trennung.")
        reset(keepError = true)
    }

    private fun setState(newState: DjiDeviceState, error: String? = null) {
        if (newState == state && error == null) return
        previousState = state
        djiLogInfo("Status-Wechsel: $state -> $newState ${if (error != null) "[Fehler: $error]" else ""}")
        state = newState
        delegate?.djiDeviceStreamingState(this, state, error ?: lastError)
    }

    fun getState(): DjiDeviceState = state

    @Suppress("DEPRECATION")
    fun connectToAddress(address: String) {
        djiLogInfo("connectToAddress(): $address")
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            failWithError("Bluetooth-Adapter nicht verfügbar", "BluetoothAdapter.getDefaultAdapter() ist null!")
            return
        }
        if (!adapter.isEnabled) {
            failWithError("Bluetooth ist deaktiviert", "Bitte Bluetooth auf dem Smartphone aktivieren!")
            return
        }

        val device: BluetoothDevice
        try {
            device = adapter.getRemoteDevice(address)
        } catch (e: Exception) {
            failWithError("Ungültige Bluetooth-Adresse", "Konnte RemoteDevice für '$address' nicht laden: ${e.message}", e)
            return
        }

        val bondStateDesc = GattErrorDecoder.decodeBondState(device.bondState)
        djiLogInfo("RemoteDevice geladen: Name='${device.name}', Adresse='${device.address}', Bond-Status: $bondStateDesc")

        // Connect directly via LE transport
        djiLogInfo("Rufe connectGatt() auf: autoConnect=false, transport=TRANSPORT_LE...")
        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (bluetoothGatt == null) {
                failWithError("connectGatt fehlgeschlagen", "device.connectGatt() gab null zurück!")
                return
            }
            setState(DjiDeviceState.CONNECTING)
            djiLogInfo("connectGatt() erfolgreich initiiert. Warte auf onConnectionStateChange...")
        } catch (e: SecurityException) {
            failWithError("Fehlende Bluetooth-Berechtigung", "SecurityException bei connectGatt: ${e.message}", e)
        } catch (e: Exception) {
            failWithError("Fehler bei connectGatt()", e.message, e)
        }
    }

    private fun sendPairingMessage() {
        djiLogInfo("========== SENDE PAIRING-NACHRICHT AN DJI ==========")
        djiLogInfo("  PIN-Code: '$PAIR_PIN_CODE', Target: ${GattErrorDecoder.decodeDjiTarget(PAIR_TARGET)}, ID: ${GattErrorDecoder.decodeDjiTransactionId(PAIR_TRANSACTION_ID)}")

        val pairPayload = DjiPairMessagePayload(PAIR_PIN_CODE).encode()
        val msg = DjiMessage(PAIR_TARGET, PAIR_TRANSACTION_ID, PAIR_TYPE, pairPayload)
        writeMessage(msg)
    }

    @Suppress("DEPRECATION")
    private fun writeNextDescriptor() {
        if (isWritingDescriptor) return
        val descriptor = descriptorWriteQueue.removeFirstOrNull()
        if (descriptor == null) {
            djiLog("writeNextDescriptor: Warteschlange ist leer")
            return
        }

        val gatt = bluetoothGatt
        if (gatt == null) {
            failWithError("GATT-Fehler", "writeNextDescriptor: bluetoothGatt ist null!")
            return
        }

        djiLog("Schreibe Descriptor für Characteristic ${descriptor.characteristic.uuid} (noch ${descriptorWriteQueue.size} in Warteschlange)...")
        isWritingDescriptor = true

        val descriptorValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE // 0x01 00

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = gatt.writeDescriptor(descriptor, descriptorValue)
            djiLog("  Descriptor-Write initiiert (Android 13+ API): result=$result, Wert=0x0100 (NOTIFY)")
        } else {
            descriptor.value = descriptorValue
            val result = gatt.writeDescriptor(descriptor)
            djiLog("  Descriptor-Write initiiert (Legacy API): result=$result, Wert=0x0100 (NOTIFY)")
        }
    }

    fun disconnect() {
        userInitiatedStop = true
        cancelReconnect()
        djiLogInfo("disconnect() explizit aufgerufen.")
        cancelPendingConnect()
        releaseGatt()
        clearWriteState()
        setState(DjiDeviceState.IDLE)
    }

    /**
     * Thin BluetoothGattCallback: GATT callbacks arrive on Binder threads. Everything is moved to the
     * main thread (where timers, the write loop and the state machine live) and events of connections
     * that were already released are dropped.
     */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            mainHandler.post {
                if (gatt === bluetoothGatt) gattEvents.onConnectionStateChange(gatt, status, newState)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            mainHandler.post {
                if (gatt === bluetoothGatt) gattEvents.onMtuChanged(gatt, mtu, status)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            mainHandler.post {
                if (gatt === bluetoothGatt) gattEvents.onDescriptorWrite(gatt, descriptor, status)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            mainHandler.post {
                if (gatt === bluetoothGatt) gattEvents.onServicesDiscovered(gatt, status)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            val copy = value.copyOf()
            mainHandler.post {
                if (gatt === bluetoothGatt) gattEvents.processIncomingData(characteristic, copy)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val copy = characteristic.value?.copyOf() ?: return
            mainHandler.post {
                if (gatt === bluetoothGatt) gattEvents.processIncomingData(characteristic, copy)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            mainHandler.post {
                if (gatt === bluetoothGatt) gattEvents.onCharacteristicWrite(gatt, characteristic, status)
            }
        }
    }

    private val gattEvents = object {
        fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            lastGattStatus = status
            val decodedStatus = GattErrorDecoder.decodeGattStatus(status)
            val decodedNewState = GattErrorDecoder.decodeConnectionState(newState)
            djiLogInfo("onConnectionStateChange: Neuer State: $decodedNewState, Status: $decodedStatus")

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    djiLogInfo("✓ Erfolgreich mit DJI-Kamera verbunden! Fordere MTU (128 Bytes) an...")
                    try {
                        val mtuRequested = gatt.requestMtu(128)
                        djiLog("gatt.requestMtu(128) aufgerufen: result=$mtuRequested")
                    } catch (e: Exception) {
                        djiLogWarn("MTU-Anfrage fehlgeschlagen (${e.message}), starte Service Discovery direkt...")
                        gatt.discoverServices()
                    }
                } else {
                    failWithError("Verbindung fehlgeschlagen", "GATT Status $status: $decodedStatus")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                cancelReconnect()
                val isGatt133 = status == 133 || status == 0x85
                val isTimeout = status == 8 || status == 0x08 || status == 0x22
                
                val userFriendlyReason = when {
                    status == BluetoothGatt.GATT_SUCCESS -> 
                        "Kamera getrennt."
                    isGatt133 || isTimeout -> 
                        "Kamera nicht erreichbar. Ist deine DJI eingeschaltet und Bluetooth aktiv?"
                    status == 0x13 -> 
                        "Kamera hat die Verbindung beendet (evtl. ausgeschaltet oder Akku leer)."
                    else -> 
                        "Verbindung unterbrochen. Ist deine DJI in Reichweite und eingeschaltet?"
                }
                
                djiLogWarn("❌ $userFriendlyReason (GATT Status $status, Zustand: $state)")

                if (state != DjiDeviceState.IDLE && state != DjiDeviceState.STOPPING_STREAM) {
                    failWithError("Kamera getrennt", userFriendlyReason)
                } else {
                    reset(keepError = false)
                }

                if (!userInitiatedStop) {
                    scheduleReconnect()
                }
            } else {
                djiLog("Anderer Connection State: $decodedNewState (Status: $decodedStatus)")
            }
        }

        fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            lastGattStatus = status
            val decodedStatus = GattErrorDecoder.decodeGattStatus(status)
            djiLogInfo("onMtuChanged: Ausgehandelte MTU=$mtu Bytes, Status=$decodedStatus")
            djiLogInfo("Starte Service Discovery (gatt.discoverServices())...")
            try {
                val discoveryStarted = gatt.discoverServices()
                djiLog("discoverServices() aufgerufen: result=$discoveryStarted")
            } catch (e: Exception) {
                djiLogError("Fehler beim Starten von discoverServices()", e)
            }
        }

        fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            lastGattStatus = status
            val decodedStatus = GattErrorDecoder.decodeGattStatus(status)
            djiLog("onDescriptorWrite: Descriptor=${descriptor.uuid}, Characteristic=${descriptor.characteristic.uuid}, Status=$decodedStatus, State=$state")
            isWritingDescriptor = false

            if (status == BluetoothGatt.GATT_SUCCESS) {
                djiLog("✓ Descriptor erfolgreich geschrieben für ${descriptor.characteristic.uuid}")
                if (descriptor.characteristic.uuid == FFF4_UUID && descriptorWriteQueue.isEmpty()) {
                    if (state != DjiDeviceState.CONNECTING) {
                        djiLogWarn("FFF4 Notification aktiviert, aber Gerät ist in Zustand '$state' (nicht CONNECTING), ignoriere")
                        return
                    }
                    djiLogInfo("✓ FFF4 Benachrichtigungen aktiviert in CONNECTING-Zustand. Starte Pairing-Prüfung...")
                    setState(DjiDeviceState.CHECKING_IF_PAIRED)
                    sendPairingMessage()
                } else if (!descriptorWriteQueue.isEmpty()) {
                    writeNextDescriptor()
                }
            } else {
                djiLogError("⚠️ Descriptor-Write fehlgeschlagen: $decodedStatus")

                if (!descriptorWriteQueue.isEmpty()) {
                    writeNextDescriptor()
                } else if (state == DjiDeviceState.CONNECTING) {
                    djiLogWarn("⚠️ Alle Descriptor-Writes abgearbeitet oder fehlgeschlagen. Versuche Pairing trotzdem...")
                    setState(DjiDeviceState.CHECKING_IF_PAIRED)
                    sendPairingMessage()
                }
            }
        }

        fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            lastGattStatus = status
            val decodedStatus = GattErrorDecoder.decodeGattStatus(status)
            djiLogInfo("onServicesDiscovered: Status=$decodedStatus")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                failWithError("Service Discovery fehlgeschlagen", "Status $status: $decodedStatus")
                return
            }

            val serviceList = gatt.services
            djiLogInfo("Service Discovery erfolgreich: ${serviceList.size} Services gefunden:")
            for (s in serviceList) {
                djiLog("  - Service: ${s.uuid}")
            }

            for (service in serviceList) {
                val fff5 = service.getCharacteristic(FFF5_UUID)
                val fff4 = service.getCharacteristic(FFF4_UUID)
                val fff3 = service.getCharacteristic(FFF3_UUID)

                if (fff5 != null || fff4 != null || fff3 != null) {
                    djiLogInfo("✓ DJI-spezifische Characteristics gefunden in Service ${service.uuid}:")

                    if (fff3 != null) {
                        djiLog("  -> FFF3 Characteristic gefunden (RX-Kanal)")
                    }

                    if (fff5 != null) {
                        fff5Characteristic = fff5
                        val props = fff5.properties
                        val noResp = (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                        val write = (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                        djiLogInfo("  -> FFF5 Characteristic gefunden (TX-Kanal! Props: 0x${props.toString(16)}, WRITE_NO_RESP=$noResp, WRITE=$write)")
                    }

                    if (fff4 != null) {
                        fff4Characteristic = fff4
                        val props = fff4.properties
                        val notify = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                        val indicate = (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                        djiLogInfo("  -> FFF4 Characteristic gefunden (RX/Notify-Kanal! Props: 0x${props.toString(16)}, NOTIFY=$notify, INDICATE=$indicate)")
                    }

                    val fff4Descriptor = fff4?.getDescriptor(CCCD_UUID)

                    for (c in service.characteristics) {
                        if (c.uuid == FFF5_UUID) {
                            djiLog("    Überspringe Notifications für FFF5 (TX-Schreibkanal)")
                            continue
                        }
                        djiLog("    Aktiviere Notification für: ${c.uuid}")
                        try {
                            gatt.setCharacteristicNotification(c, true)
                        } catch (e: Exception) {
                            djiLogWarn("    Konnte Notification nicht lokal setzen: ${e.message}")
                        }

                        if (c.uuid != FFF4_UUID) {
                            val descriptor = c.getDescriptor(CCCD_UUID)
                            if (descriptor != null) {
                                descriptorWriteQueue.add(descriptor)
                            }
                        }
                    }

                    if (fff4Descriptor != null) {
                        djiLog("    Reihe FFF4-Descriptor als letzten in die Warteschlange ein")
                        descriptorWriteQueue.add(fff4Descriptor)
                    }
                    break
                }
            }

            if (fff5Characteristic == null) {
                failWithError("DJI Characteristic fehlt", "FFF5 Characteristic (TX-Kanal) wurde in keinem Service gefunden!")
                return
            }

            djiLogInfo("Starte Descriptor-Schreibvorgänge (${descriptorWriteQueue.size} in Warteschlange)...")
            if (descriptorWriteQueue.isEmpty()) {
                djiLogWarn("Keine Descriptoren zu schreiben. Gehe direkt zum Pairing-Schritt.")
                setState(DjiDeviceState.CHECKING_IF_PAIRED)
                sendPairingMessage()
            } else {
                writeNextDescriptor()
            }
        }

        fun processIncomingData(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (value.isEmpty()) return

            val hex = value.joinToString(" ") { "%02X".format(it) }
            djiLog("RX: ${characteristic.uuid} (${value.size} Bytes): $hex")

            // Drop a stale partial message (lost continuation chunk) before starting over
            val now = SystemClock.elapsedRealtime()
            if (rxBuffer.isNotEmpty() && now - lastRxChunkAt > RX_FRAGMENT_TIMEOUT_MS) {
                djiLogWarn("RX: Verwerfe veraltetes Teilpaket (${rxBuffer.size} Bytes)")
                rxBuffer = ByteArray(0)
            }
            lastRxChunkAt = now

            if (rxBuffer.isEmpty() && value[0] != 0x55.toByte()) {
                djiLogWarn("RX: Verwerfe Chunk ohne 0x55 Header (kein Teilpaket offen)")
                return
            }

            rxBuffer += value

            while (rxBuffer.size >= 2) {
                if (rxBuffer[0] != 0x55.toByte()) {
                    rxBuffer = rxBuffer.copyOfRange(1, rxBuffer.size)
                    continue
                }

                val messageLength = rxBuffer[1].toInt() and 0xFF
                if (messageLength < 11) {
                    rxBuffer = rxBuffer.copyOfRange(1, rxBuffer.size)
                    continue
                }

                if (rxBuffer.size < messageLength) {
                    djiLog("RX: Fragment ${rxBuffer.size}/$messageLength Bytes, warte auf weiteres Datenpaket...")
                    break
                }

                val completeMessage = rxBuffer.copyOfRange(0, messageLength)
                rxBuffer = rxBuffer.copyOfRange(messageLength, rxBuffer.size)

                try {
                    val message = DjiMessage.fromBytes(completeMessage)
                    val cmdName = GattErrorDecoder.decodeDjiTransactionId(message.id)
                    val targetName = GattErrorDecoder.decodeDjiTarget(message.target)
                    val payloadHex = message.payload.joinToString(" ") { "%02X".format(it) }
                    lastRxSummary = "Cmd=$cmdName, Target=$targetName, Payload=[$payloadHex]"
                    djiLogInfo("RX decodiert: $lastRxSummary")
                    handleDecodedMessage(message)
                } catch (e: Exception) {
                    djiLogError("RX: Beschädigtes DJI-Paket verworfen: ${e.message}", e)
                }
            }
        }

        fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            lastGattStatus = status
            val decodedStatus = GattErrorDecoder.decodeGattStatus(status)
            djiLog("onCharacteristicWrite: Characteristic=${characteristic.uuid}, Status=$decodedStatus")
        }
    }

    private fun handleDecodedMessage(message: DjiMessage) {
        val payloadHex = message.payload.joinToString(" ") { "%02X".format(it) }
        val cmdName = GattErrorDecoder.decodeDjiTransactionId(message.id)
        djiLogInfo("RX-Verarbeitung: Status=$state, Cmd=$cmdName, Payload=[$payloadHex]")

        when (state) {
            DjiDeviceState.CHECKING_IF_PAIRED -> processCheckingIfPaired(message)
            DjiDeviceState.PAIRING -> processPairing()
            DjiDeviceState.CLEANING_UP -> processCleaningUp(message)
            DjiDeviceState.PREPARING_STREAM -> processPreparingStream(message)
            DjiDeviceState.SETTING_UP_WIFI -> processSettingUpWifi(message)
            DjiDeviceState.CONFIGURING -> processConfiguring(message)
            DjiDeviceState.STARTING_STREAM -> processStartingStream(message)
            DjiDeviceState.STREAMING -> processStreaming(message)
            DjiDeviceState.STOPPING_STREAM -> processStoppingStream(message)
            DjiDeviceState.CONNECTING -> {
                djiLog("Nachricht in CONNECTING-Zustand ignoriert")
            }
            else -> {
                djiLog("Nachricht in Zustand $state ignoriert")
            }
        }
    }

    fun writeMessage(message: DjiMessage) {
        val bytes = message.encode()
        val cmdName = GattErrorDecoder.decodeDjiTransactionId(message.id)
        val targetName = GattErrorDecoder.decodeDjiTarget(message.target)
        val hex = bytes.joinToString(" ") { "%02X".format(it) }
        lastTxSummary = "Cmd=$cmdName -> Target=$targetName, Type=0x${message.type.toString(16)}, ${bytes.size}B"
        djiLogInfo("TX: $lastTxSummary: $hex")

        enqueueWrite(bytes)
    }

    private fun enqueueWrite(value: ByteArray) {
        var offset = 0
        var chunkCount = 0
        while (offset < value.size) {
            val end = minOf(offset + writeChunkSize, value.size)
            val chunk = value.copyOfRange(offset, end)
            writeQueue.addLast(chunk)
            chunkCount++
            offset = end
        }
        djiLog("  In Schreib-Warteschlange eingereiht: $chunkCount Chunks (ChunkSize=$writeChunkSize Bytes)")
        startWriteLoopIfNeeded()
    }

    private fun startWriteLoopIfNeeded() {
        if (isWriting) return
        isWriting = true
        val gen = writeGeneration
        mainHandler.post { writeNextChunk(gen) }
    }

    @Suppress("DEPRECATION")
    private fun writeNextChunk(gen: Int) {
        // Stale runnable from before a reset/failure/disconnect
        if (gen != writeGeneration) return
        val chunk = writeQueue.removeFirstOrNull()
        if (chunk == null) {
            djiLog("writeNextChunk: Warteschlange leer, Schreib-Schleife beendet.")
            isWriting = false
            return
        }
        val char = fff5Characteristic ?: run {
            failWithError("TX-Fehler", "writeNextChunk: fff5Characteristic ist null!")
            writeQueue.clear()
            isWriting = false
            return
        }
        val hex = chunk.joinToString(" ") { "%02X".format(it) }
        djiLog("writeNextChunk: Sende ${chunk.size} Bytes: $hex")

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt?.writeCharacteristic(
                char,
                chunk,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ) ?: BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION
        } else {
            char.value = chunk
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            if (bluetoothGatt?.writeCharacteristic(char) == true) {
                BluetoothStatusCodes.SUCCESS
            } else {
                BluetoothGatt.GATT_FAILURE
            }
        }

        if (result != BluetoothStatusCodes.SUCCESS) {
            djiLogWarn("  writeCharacteristic gab Code $result zurück")
            // Never drop a chunk: a missing chunk corrupts the whole message and the camera never answers.
            writeRetryCount++
            if (writeRetryCount > MAX_WRITE_RETRIES) {
                failWithError("TX-Fehler", "writeCharacteristic wiederholt fehlgeschlagen (Code $result)")
                return
            }
            writeQueue.addFirst(chunk)
        } else {
            writeRetryCount = 0
        }

        mainHandler.postDelayed({ writeNextChunk(gen) }, writeIntervalMs)
    }

    private fun sendStopStream() {
        djiLogInfo("Sende STOP_STREAM (Vorbereitungs-Reset)...")
        val payload = DjiStopStreamingMessagePayload().encode()
        val message = DjiMessage(STOP_STREAMING_TARGET, STOP_STREAMING_TRANSACTION_ID, STOP_STREAMING_TYPE, payload)
        writeMessage(message)
    }

    private fun processCheckingIfPaired(response: DjiMessage) {
        if (response.id != PAIR_TRANSACTION_ID) {
            djiLog("processCheckingIfPaired: Ignoriere Nachricht mit ID=0x${response.id.toString(16)} (Erwarte PAIR_TRANSACTION_ID=0x${PAIR_TRANSACTION_ID.toString(16)})")
            return
        }

        val payloadHex = response.payload.joinToString(" ") { "%02X".format(it) }
        djiLogInfo("Pairing-Antwort erhalten: Payload=[$payloadHex]")

        if (response.payload.contentEquals(byteArrayOf(0, 1))) {
            djiLogInfo("✓ DJI-Kamera meldet: Erfolgreich gepaart (00 01)!")
            processPairing()
        } else {
            djiLogWarn("⚠️ DJI-Kamera noch nicht gepaart (Payload=[$payloadHex]). Gehe in PAIRING-Zustand...")
            setState(DjiDeviceState.PAIRING)
        }
    }

    private fun processPairing() {
        sendStopStream()
        setState(DjiDeviceState.CLEANING_UP)
    }

    private fun processCleaningUp(response: DjiMessage) {
        if (response.id != STOP_STREAMING_TRANSACTION_ID) return
        djiLogInfo("✓ Stop-Stream bestätigt. Sende PREPARING_TO_LIVESTREAM...")
        val payload = DjiPreparingToLivestreamMessagePayload().encode()
        val message = DjiMessage(PREPARING_TO_LIVESTREAM_TARGET, PREPARING_TO_LIVESTREAM_TRANSACTION_ID, PREPARING_TO_LIVESTREAM_TYPE, payload)
        writeMessage(message)
        setState(DjiDeviceState.PREPARING_STREAM)
    }

    private fun processPreparingStream(response: DjiMessage) {
        if (response.id != PREPARING_TO_LIVESTREAM_TRANSACTION_ID) return

        val ssid = wifiSsid
        val pwd = wifiPassword
        if (ssid.isNullOrEmpty()) {
            failWithError("WiFi-Konfigurationsfehler", "WLAN-SSID ist leer!")
            return
        }
        if (pwd == null) {
            failWithError("WiFi-Konfigurationsfehler", "WLAN-Passwort ist null!")
            return
        }

        // GOAL 7: WiFi-Validierung vor dem Senden
        // Prüfen, ob SSID im Scan vorhanden ist.
        // Wenn ja: RSSI/Band loggen.
        // Wenn nein: WARNUNG + sichtbare SSIDs loggen.
        // ABER: Nicht blockieren (versteckte SSIDs & Throttling erlauben).
        val isSsidDiscovered = WifiScanner.isSsidFound(ssid)
        if (isSsidDiscovered) {
            val scanItem = WifiScanner.getScanItem(ssid)
            djiLogInfo("✓ Ziel-WLAN '$ssid' im WiFi-Scan bestätigt (Signal: ${scanItem?.rssi} dBm, Band: ${scanItem?.band}, Schutz: ${scanItem?.security})")
        } else {
            val available = WifiScanner.getAvailableSsids()
            val availableStr = if (available.isEmpty()) "Keine WLANs im Scan" else available.joinToString(", ")
            djiLogWarn("⚠️ WARNUNG: Ziel-WLAN '$ssid' wurde im aktuellen WiFi-Scan nicht gefunden (möglicherweise versteckt oder Scan gedrosselt). Verfügbare SSIDs: [$availableStr]. Fahre mit Konfiguration fort...")
        }

        djiLogInfo("Sende SETUP_WIFI an DJI-Kamera...")
        val payload = DjiSetupWifiMessagePayload(ssid, pwd).encode()
        val message = DjiMessage(SETUP_WIFI_TARGET, SETUP_WIFI_TRANSACTION_ID, SETUP_WIFI_TYPE, payload)
        writeMessage(message)
        setState(DjiDeviceState.SETTING_UP_WIFI)
    }

    private fun processSettingUpWifi(response: DjiMessage) {
        if (response.id != SETUP_WIFI_TRANSACTION_ID) return

        val payloadHex = response.payload.joinToString(" ") { "%02X".format(it) }
        val decoded = GattErrorDecoder.decodeWifiSetupResponse(response.payload)
        djiLogInfo("WiFi-Setup Antwort von DJI erhalten: [$payloadHex] -> $decoded")

        if (!response.payload.contentEquals(byteArrayOf(0x00, 0x00))) {
            djiLogError("❌ WiFi-Setup bei DJI FEHLGESCHLAGEN: $decoded")
            failWithError("DJI WiFi-Setup fehlgeschlagen", decoded)
            return
        }

        djiLogInfo("✓ WiFi-Setup erfolgreich bestätigt (00 00)! Fahre mit Kamera-Konfiguration fort...")

        when (model) {
            SettingsDjiDeviceModel.OSMO_ACTION_2, SettingsDjiDeviceModel.OSMO_ACTION_3 -> {
                sendStartStreaming()
            }
            SettingsDjiDeviceModel.OSMO_ACTION_4, SettingsDjiDeviceModel.OSMO_ACTION_6 -> {
                val imageStab = imageStabilization ?: SettingsDjiDeviceImageStabilization.OFF
                val payload = DjiConfigureMessagePayload(imageStab, oa5 = false).encode()
                val message = DjiMessage(CONFIGURE_TARGET, CONFIGURE_TRANSACTION_ID, CONFIGURE_TYPE, payload)
                writeMessage(message)
                setState(DjiDeviceState.CONFIGURING)
            }
            SettingsDjiDeviceModel.OSMO_ACTION_5_PRO, SettingsDjiDeviceModel.OSMO_360 -> {
                val imageStab = imageStabilization ?: SettingsDjiDeviceImageStabilization.OFF
                val payload = DjiConfigureMessagePayload(imageStab, oa5 = true).encode()
                val message = DjiMessage(CONFIGURE_TARGET, CONFIGURE_TRANSACTION_ID, CONFIGURE_TYPE, payload)
                writeMessage(message)
                setState(DjiDeviceState.CONFIGURING)
            }
            SettingsDjiDeviceModel.OSMO_POCKET_3, SettingsDjiDeviceModel.OSMO_POCKET_4, SettingsDjiDeviceModel.UNKNOWN -> {
                sendStartStreaming()
            }
        }
    }

    private fun processConfiguring(response: DjiMessage) {
        if (response.id != CONFIGURE_TRANSACTION_ID) return
        djiLogInfo("✓ Konfiguration bestätigt (0x8C2D). Sende START_STREAMING...")
        sendStartStreaming()
    }

    private fun sendStartStreaming() {
        val rtmp = rtmpUrl
        if (rtmp.isNullOrEmpty()) {
            failWithError("Stream-Konfigurationsfehler", "RTMP-URL ist leer!")
            return
        }
        val res = resolution ?: SettingsDjiDeviceResolution.r1080p
        val bitrateKbps = this.bitrateKbps

        djiLogInfo("Sende START_STREAMING: RTMP='$rtmp', Auflösung=$res, FPS=$fps, Bitrate=${bitrateKbps}kbps")

        when (model) {
            SettingsDjiDeviceModel.OSMO_POCKET_4 -> {
                val payload = DjiStartStreamingMessagePayloadPocket4(rtmp, res, fps, bitrateKbps).encode()
                val message = DjiMessage(START_STREAMING_TARGET, START_STREAMING_TRANSACTION_ID, START_STREAMING_TYPE, payload)
                writeMessage(message)
            }
            else -> {
                val oa5 = model.hasNewProtocol()
                val payload = DjiStartStreamingMessagePayload(rtmp, res, fps, bitrateKbps, oa5).encode()
                val message = DjiMessage(START_STREAMING_TARGET, START_STREAMING_TRANSACTION_ID, START_STREAMING_TYPE, payload)
                writeMessage(message)
            }
        }

        if (model.hasNewProtocol()) {
            djiLogInfo("Neues Protokoll erkannt ($model): Sende sofort CONFIRM_START_STREAMING...")
            val confirmPayload = DjiConfirmStartStreamingMessagePayload().encode()
            val confirmMsg = DjiMessage(STOP_STREAMING_TARGET, STOP_STREAMING_TRANSACTION_ID, STOP_STREAMING_TYPE, confirmPayload)
            writeMessage(confirmMsg)
        }

        setState(DjiDeviceState.STARTING_STREAM)
    }

    private fun processStartingStream(response: DjiMessage) {
        if (response.id != START_STREAMING_TRANSACTION_ID) return
        // Check if the camera rejected the start-streaming command with a non-zero status code
        if (response.payload.isNotEmpty() && response.payload[0] != 0x00.toByte()) {
            val hex = response.payload.joinToString(" ") { "%02X".format(it) }
            failWithError("Stream-Start von Kamera abgelehnt", "Fehlercode in Antwort auf 0x8C2C: [$hex]")
            return
        }
        djiLogInfo("🎉 START_STREAMING bestätigt (0x8C2C)! Live-Streaming ist jetzt AKTIV (STREAMING).")
        reconnectAttempts = 0
        seenActiveStreamInCurrentSession = false
        setState(DjiDeviceState.STREAMING)
        stopStartStreamingTimer()
        startStreamingHeartbeatTimer()
    }

    private fun processStreaming(message: DjiMessage) {
        // Detect if the camera stopped streaming on its own or was stopped manually on the camera UI
        if (message.id == STOP_STREAMING_TRANSACTION_ID) {
            djiLogInfo("Kamera hat STOP_STREAMING (0xEAC8) gemeldet. Beende Stream-Zustand.")
            userInitiatedStop = true
            cancelReconnect()
            reset(keepError = false)
            return
        }

        when (message.type) {
            0x020D00 -> {
                if (message.payload.size >= 21) {
                    val battery = message.payload[20].toInt() and 0xFF
                    batteryPercentage = battery
                    djiLog("Batteriestatus empfangen: $battery%")
                }
                if (message.payload.size >= 23) {
                    // Byte 21 und 22: Temperatur in 0.1°C (z. B. 0x0186 = 390 -> 39°C)
                    val rawTemp = ((message.payload[22].toInt() and 0xFF) shl 8) or (message.payload[21].toInt() and 0xFF)
                    val tempC = if (rawTemp in 100..900) rawTemp / 10 else rawTemp
                    if (tempC in 15..95) {
                        cameraTemperature = tempC
                        djiLog("Kamera-Temperatur empfangen: ${tempC}°C")
                    }
                }
                // Telemetrie sofort via Delegate an DjiModel weiterleiten!
                delegate?.djiDeviceStreamingState(this, state, null)
            }
            BROADCAST_STATUS_TYPE_1, BROADCAST_STATUS_TYPE_2 -> {
                // In DJI broadcast telemetry (target 0x0802, type 0x03EE80 or 0x03EE40):
                // payload[0] = stream state (0x02 = streaming, 0x03 = stopped/idle, 0x00 = initializing)
                // payload[1] = stream flag (0x08 = live streaming active, 0x01/0x00 = idle/stopped)
                if (message.payload.isNotEmpty()) {
                    val streamState = message.payload[0].toInt() and 0xFF
                    val streamFlag = if (message.payload.size >= 2) message.payload[1].toInt() and 0xFF else 0
                    djiLog("Broadcast-Status Telemetrie: State=0x${streamState.toString(16)}, Flag=0x${streamFlag.toString(16)}")

                    // payload[6..7] (little endian) schwankt um die eingestellte Bitrate in kbit/s
                    // (z. B. 0x2054 = 8276 bei 8 Mbit/s) -> tatsächlich von der Kamera gemeldete Encoder-Bitrate
                    if (streamState == 0x02 && streamFlag == 0x08 && message.payload.size >= 8) {
                        val kbps = ((message.payload[7].toInt() and 0xFF) shl 8) or (message.payload[6].toInt() and 0xFF)
                        if (kbps in 100..60000) {
                            liveBitrateKbps = kbps
                        }
                    }

                    if (streamFlag == 0x08 && streamState == 0x02) {
                        seenActiveStreamInCurrentSession = true
                        startStreamingHeartbeatTimer() // Refresh watchdog timer while stream is active
                    } else if (seenActiveStreamInCurrentSession && (streamFlag != 0x08 || streamState == 0x03)) {
                        djiLogInfo("DJI Broadcast meldet Stream-Ende (State=0x${streamState.toString(16)}, Flag=0x${streamFlag.toString(16)}). Beende Zustand.")
                        userInitiatedStop = true
                        cancelReconnect()
                        reset(keepError = false)
                    }
                }
            }
            else -> {
                djiLog("Streaming-Statusnachricht empfangen: Type=0x${message.type.toString(16)}")
            }
        }
    }

    private fun processStoppingStream(response: DjiMessage) {
        if (response.id != STOP_STREAMING_TRANSACTION_ID) return
        djiLogInfo("✓ STOP_STREAMING bestätigt durch DJI-Kamera. Setze Gerät zurück.")
        reset(keepError = false)
    }
}
