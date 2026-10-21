package com.dimadesu.djiremote.settings

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    DARK, LIGHT, SYSTEM
}

data class AppSettingsState(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val keepScreenOn: Boolean = false,
    val outdoorMaxBrightness: Boolean = false,
    // Haptik & Vibration
    val hapticFeedbackEnabled: Boolean = true,
    // Akku-Warnung
    val batteryWarningEnabled: Boolean = true,
    val batteryWarningThreshold: Int = 20, // 10% - 50%
    val batteryWarningSound: Boolean = true,
    val batteryWarningVibration: Boolean = true,
    // Auto-Reconnect
    val autoReconnectEnabled: Boolean = true,
    val maxReconnectAttempts: Int = 15 // 3 - 30 Versuche
)

object AppSettingsRepository {
    private const val PREFS_NAME = "streamgo_app_settings"
    private var prefs: SharedPreferences? = null

    private val _settings = MutableStateFlow(AppSettingsState())
    val settings: StateFlow<AppSettingsState> = _settings.asStateFlow()

    private var toneGenerator: ToneGenerator? = null

    fun initialize(context: Context) {
        if (prefs != null) return
        val appCtx = context.applicationContext
        val sp = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        val themeStr = sp.getString("theme_mode", AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name
        val themeMode = try { AppThemeMode.valueOf(themeStr) } catch (e: Exception) { AppThemeMode.DARK }

        _settings.value = AppSettingsState(
            themeMode = themeMode,
            keepScreenOn = sp.getBoolean("keep_screen_on", false),
            outdoorMaxBrightness = sp.getBoolean("outdoor_max_brightness", false),
            hapticFeedbackEnabled = sp.getBoolean("haptic_feedback_enabled", true),
            batteryWarningEnabled = sp.getBoolean("battery_warning_enabled", true),
            batteryWarningThreshold = sp.getInt("battery_warning_threshold", 20),
            batteryWarningSound = sp.getBoolean("battery_warning_sound", true),
            batteryWarningVibration = sp.getBoolean("battery_warning_vibration", true),
            autoReconnectEnabled = sp.getBoolean("auto_reconnect_enabled", true),
            maxReconnectAttempts = sp.getInt("max_reconnect_attempts", 15)
        )

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (e: Exception) {
            // Ignore if audio is unavailable
        }
    }

    fun updateSettings(newSettings: AppSettingsState) {
        _settings.value = newSettings
        prefs?.edit()?.apply {
            putString("theme_mode", newSettings.themeMode.name)
            putBoolean("keep_screen_on", newSettings.keepScreenOn)
            putBoolean("outdoor_max_brightness", newSettings.outdoorMaxBrightness)
            putBoolean("haptic_feedback_enabled", newSettings.hapticFeedbackEnabled)
            putBoolean("battery_warning_enabled", newSettings.batteryWarningEnabled)
            putInt("battery_warning_threshold", newSettings.batteryWarningThreshold)
            putBoolean("battery_warning_sound", newSettings.batteryWarningSound)
            putBoolean("battery_warning_vibration", newSettings.batteryWarningVibration)
            putBoolean("auto_reconnect_enabled", newSettings.autoReconnectEnabled)
            putInt("max_reconnect_attempts", newSettings.maxReconnectAttempts)
            apply()
        }
    }

    // Feedback Utilities
    fun triggerVibration(context: Context, pattern: LongArray = longArrayOf(0, 150)) {
        if (!_settings.value.hapticFeedbackEnabled) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(pattern, -1)
                    }
                }
            }
        } catch (e: Exception) {
            // Best effort
        }
    }

    fun triggerBatteryAlarm(context: Context) {
        val s = _settings.value
        if (!s.batteryWarningEnabled) return

        if (s.batteryWarningVibration) {
            // 2 deutliche Impulse
            triggerVibration(context, longArrayOf(0, 250, 150, 250))
        }

        if (s.batteryWarningSound) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
            } catch (e: Exception) {
            }
        }
    }
}
