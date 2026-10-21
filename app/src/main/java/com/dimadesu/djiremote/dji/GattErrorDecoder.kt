package com.dimadesu.djiremote.dji

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback

/**
 * Utility to decode Bluetooth, GATT, and DJI protocol status codes into human-readable descriptions.
 */
object GattErrorDecoder {

    /**
     * Translates BluetoothGatt status codes into readable English/German descriptions.
     * Covers standard Android constants and common HCI/AOSP internal error codes.
     */
    fun decodeGattStatus(status: Int): String {
        return when (status) {
            BluetoothGatt.GATT_SUCCESS -> "GATT_SUCCESS (0x00): Operation succeeded"
            BluetoothGatt.GATT_READ_NOT_PERMITTED -> "GATT_READ_NOT_PERMITTED (0x02): Read operation not allowed"
            BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "GATT_WRITE_NOT_PERMITTED (0x03): Write operation not allowed"
            BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION -> "GATT_INSUFFICIENT_AUTHENTICATION (0x05): Device requires pairing / authentication"
            BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED -> "GATT_REQUEST_NOT_SUPPORTED (0x06): GATT request is not supported"
            BluetoothGatt.GATT_INVALID_OFFSET -> "GATT_INVALID_OFFSET (0x07): Invalid offset"
            BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> "GATT_INSUFFICIENT_ENCRYPTION (0x0F): Link encryption is required"
            BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> "GATT_INVALID_ATTRIBUTE_LENGTH (0x0D): Invalid attribute value length"
            BluetoothGatt.GATT_CONNECTION_CONGESTED -> "GATT_CONNECTION_CONGESTED (0x8F): Remote device or controller is congested"
            BluetoothGatt.GATT_FAILURE -> "GATT_FAILURE (0x0101/257): Generic GATT failure"

            // AOSP internal error codes (often returned in onConnectionStateChange or onDescriptorWrite)
            0x01 -> "GATT_INVALID_HANDLE (0x01): Invalid attribute handle"
            0x08 -> "GATT_INSUFFICIENT_AUTHORIZATION / HCI_TIMEOUT (0x08): Connection supervision timeout or insufficient authorization"
            0x13 -> "HCI_CONN_TERMINATE_PEER_USER (0x13): Remote DJI device closed/terminated the connection"
            0x16 -> "HCI_CONN_TERMINATE_LOCAL_HOST (0x16): Connection terminated by local Android host"
            0x22 -> "HCI_LMP_RESPONSE_TIMEOUT (0x22): Link manager protocol response timeout"
            0x3E -> "HCI_CONN_FAILED_ESTABLISHMENT (0x3E): BLE connection could not be established"
            0x80 -> "GATT_NO_RESOURCES / GATT_INTERNAL_ERROR (0x80 / 128): No resources or internal BLE stack error"
            0x81 -> "GATT_INTERNAL_ERROR (0x81 / 129): BLE internal stack error"
            0x82 -> "GATT_WRONG_STATE (0x82 / 130): Operation attempted in wrong state"
            0x83 -> "GATT_DB_FULL (0x83 / 131): GATT database full"
            0x84 -> "GATT_BUSY (0x84 / 132): Controller busy"
            0x85 -> "GATT_ERROR (0x85 / 133): Common Android BLE error (connection timeout, peer unreachable, or packet collision)"
            0x86 -> "GATT_CMD_STARTED (0x86 / 134): Command already started"
            0x87 -> "GATT_ILLEGAL_PARAMETER (0x87 / 135): Illegal parameter"
            0x88 -> "GATT_PENDING (0x88 / 136): Operation pending"
            0x89 -> "GATT_AUTH_FAIL (0x89 / 137): Authentication failed"
            0x8A -> "GATT_MORE (0x8A / 138): More data expected"
            0x8B -> "GATT_INVALID_CFG (0x8B / 139): Invalid configuration"
            0x8C -> "GATT_SERVICE_STARTED (0x8C / 140): Service started"
            0x8D -> "GATT_ENCRYPTED_NO_MITM (0x8D / 141): Encrypted link without MITM protection"
            0x8E -> "GATT_NOT_ENCRYPTED (0x8E / 142): Link not encrypted"
            0xFD -> "GATT_CCC_CFG_ERR (0xFD / 253): Client Characteristic Configuration Descriptor improperly configured"
            0xFE -> "GATT_PRC_IN_PROGRESS (0xFE / 254): Procedure already in progress"
            0xFF -> "GATT_OUT_OF_RANGE (0xFF / 255): Attribute value out of range"
            else -> "UNKNOWN_GATT_STATUS (0x${status.toString(16).uppercase()} / $status)"
        }
    }

    /**
     * Translates BluetoothProfile connection state constants into readable descriptions.
     */
    fun decodeConnectionState(state: Int): String {
        return when (state) {
            BluetoothProfile.STATE_DISCONNECTED -> "STATE_DISCONNECTED (0)"
            BluetoothProfile.STATE_CONNECTING -> "STATE_CONNECTING (1)"
            BluetoothProfile.STATE_CONNECTED -> "STATE_CONNECTED (2)"
            BluetoothProfile.STATE_DISCONNECTING -> "STATE_DISCONNECTING (3)"
            else -> "UNKNOWN_CONNECTION_STATE ($state)"
        }
    }

    /**
     * Translates BluetoothDevice bond state constants into readable descriptions.
     */
    fun decodeBondState(bondState: Int): String {
        return when (bondState) {
            BluetoothDevice.BOND_NONE -> "BOND_NONE (10 - Unbonded)"
            BluetoothDevice.BOND_BONDING -> "BOND_BONDING (11 - Bonding in progress)"
            BluetoothDevice.BOND_BONDED -> "BOND_BONDED (12 - Bonded / Paired with OS)"
            else -> "UNKNOWN_BOND_STATE ($bondState)"
        }
    }

    /**
     * Translates BluetoothLeScanner scan error codes into readable descriptions.
     */
    fun decodeBleScanError(errorCode: Int): String {
        return when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "SCAN_FAILED_ALREADY_STARTED (1): Scan already in progress"
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED (2): App registration with BLE stack failed"
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "SCAN_FAILED_INTERNAL_ERROR (3): Internal BLE stack error"
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "SCAN_FAILED_FEATURE_UNSUPPORTED (4): BLE scan mode or feature not supported on this device"
            5 -> "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES (5): Out of BLE hardware scanner resources"
            6 -> "SCAN_FAILED_SCANNING_TOO_FREQUENTLY (6): BLE scanning throttled by Android (scanned too frequently)"
            else -> "UNKNOWN_SCAN_ERROR ($errorCode)"
        }
    }

    /**
     * Translates DJI Transaction IDs into readable command names.
     */
    fun decodeDjiTransactionId(id: Int): String {
        return when (id) {
            0x8092 -> "PAIR (0x8092)"
            0xEAC8 -> "STOP_STREAM / CONFIRM_START (0xEAC8)"
            0x8C12 -> "PREPARING_LIVESTREAM (0x8C12)"
            0x8C19 -> "SETUP_WIFI (0x8C19)"
            0x8C2C -> "START_STREAMING (0x8C2C)"
            0x8C2D -> "CONFIGURE (0x8C2D)"
            else -> "CMD_0x${id.toString(16).uppercase()}"
        }
    }

    /**
     * Translates DJI Target IDs into readable module names.
     */
    fun decodeDjiTarget(target: Int): String {
        return when (target) {
            0x0702 -> "WIFI/PAIR_MODULE (0x0702)"
            0x0802 -> "STREAMING_MODULE (0x0802)"
            0x0102 -> "CAMERA_CONFIG_MODULE (0x0102)"
            else -> "TARGET_0x${target.toString(16).uppercase()}"
        }
    }

    /**
     * Decodes the response payload for DJI WiFi Setup (0x8C19).
     */
    fun decodeWifiSetupResponse(payload: ByteArray): String {
        if (payload.isEmpty()) return "Leere Antwort von DJI (0 Bytes)"
        if (payload.contentEquals(byteArrayOf(0x00, 0x00))) {
            return "✓ ERFOLG (00 00): DJI hat das WLAN akzeptiert und verbindet sich"
        }
        val hex = payload.joinToString(" ") { "%02X".format(it) }
        return when {
            payload.size >= 1 && payload[0] == 0x01.toByte() -> "FEHLER ($hex): Falsches WLAN-Passwort oder Authentifizierungsfehler"
            payload.size >= 1 && payload[0] == 0x02.toByte() -> "FEHLER ($hex): WLAN nicht gefunden oder SSID nicht erreichbar"
            payload.size >= 1 && payload[0] == 0x03.toByte() -> "FEHLER ($hex): Verbindung zum WLAN-Access-Point fehlgeschlagen (Timeout/IP-Bezug)"
            payload.size >= 1 && payload[0] == 0x04.toByte() -> "FEHLER ($hex): WLAN-Frequenz/Kanal von DJI nicht unterstützt (z. B. 5GHz DFS)"
            else -> "FEHLER ($hex): DJI lehnte WiFi-Setup ab"
        }
    }
}
