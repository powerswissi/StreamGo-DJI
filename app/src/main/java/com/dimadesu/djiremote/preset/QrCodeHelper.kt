package com.dimadesu.djiremote.preset

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.io.InputStream

data class QrParsedResult(
    val name: String = "",
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val rtmpUrl: String = "",
    val rawText: String = "",
    val type: QrType = QrType.UNKNOWN
) {
    enum class QrType {
        WIFI_CONFIG, STREAMGO_PRESET, RTMP_URL, UNKNOWN
    }
}

object QrCodeHelper {
    private val gson = Gson()

    /**
     * Erzeugt eine Bitmap eines QR-Codes für einen beliebigen String (offline, rein zxing)
     */
    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parst den Inhalt eines QR-Codes (WIFI: Standard, StreamGo JSON oder RTMP URL)
     */
    fun parseQrContent(raw: String): QrParsedResult {
        val trimmed = raw.trim()

        // 1. Android/iOS Standard WLAN QR-Code Format: WIFI:S:MySSID;T:WPA;P:MyPassword;;
        if (trimmed.startsWith("WIFI:", ignoreCase = true)) {
            val ssid = extractWifiField(trimmed, "S")
            val pass = extractWifiField(trimmed, "P")
            return QrParsedResult(
                name = if (ssid.isNotEmpty()) "WLAN $ssid" else "Neues WLAN",
                wifiSsid = ssid,
                wifiPassword = pass,
                rawText = trimmed,
                type = QrParsedResult.QrType.WIFI_CONFIG
            )
        }

        // 2. StreamGo JSON Preset
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val preset = gson.fromJson(trimmed, ConnectionPreset::class.java)
                if (preset != null && (preset.name.isNotEmpty() || preset.wifiSsid.isNotEmpty() || preset.rtmpUrl.isNotEmpty())) {
                    return QrParsedResult(
                        name = preset.name.ifEmpty { "QR Preset" },
                        wifiSsid = preset.wifiSsid,
                        wifiPassword = preset.wifiPassword,
                        rtmpUrl = preset.rtmpUrl,
                        rawText = trimmed,
                        type = QrParsedResult.QrType.STREAMGO_PRESET
                    )
                }
            } catch (e: Exception) {
                // Not valid json
            }
        }

        // 3. RTMP / SRT URL
        if (trimmed.startsWith("rtmp://", ignoreCase = true) ||
            trimmed.startsWith("rtmps://", ignoreCase = true) ||
            trimmed.startsWith("srt://", ignoreCase = true)) {
            return QrParsedResult(
                name = "Stream URL",
                rtmpUrl = trimmed,
                rawText = trimmed,
                type = QrParsedResult.QrType.RTMP_URL
            )
        }

        return QrParsedResult(
            name = "Gescanntes Preset",
            rawText = trimmed,
            type = QrParsedResult.QrType.UNKNOWN
        )
    }

    private fun extractWifiField(qrText: String, fieldKey: String): String {
        // Sucht nach KEY:VALUE;
        val pattern = "(?i);?$fieldKey:([^;]*);".toRegex()
        val match = pattern.find(qrText)
        return match?.groupValues?.get(1) ?: ""
    }

    /**
     * Startet den Google Play Services Barcode Scanner (benötigt KEINE Camera-Permission in Manifest)
     */
    fun startLiveScanner(
        context: Context,
        onSuccess: (QrParsedResult) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        try {
            val scanner = GmsBarcodeScanning.getClient(context)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val raw = barcode.rawValue
                    if (!raw.isNullOrBlank()) {
                        val parsed = parseQrContent(raw)
                        onSuccess(parsed)
                    } else {
                        onError("QR-Code enthielt keine lesbaren Daten")
                    }
                }
                .addOnCanceledListener {
                    // Vom Nutzer abgebrochen
                }
                .addOnFailureListener { e ->
                    onError(e.localizedMessage ?: "Scanner-Fehler")
                }
        } catch (e: Exception) {
            onError("Scanner konnte nicht gestartet werden: ${e.localizedMessage}")
        }
    }

    /**
     * Liest einen QR-Code aus einer Bild-Datei / Galerie-URI aus
     */
    fun decodeFromImageUri(context: Context, uri: Uri): QrParsedResult? {
        return try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            } ?: return null
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = MultiFormatReader().decode(binaryBitmap)
            result.text?.let { parseQrContent(it) }
        } catch (e: Exception) {
            null
        }
    }
}
