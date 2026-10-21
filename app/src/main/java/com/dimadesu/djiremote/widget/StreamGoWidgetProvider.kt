package com.dimadesu.djiremote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.dimadesu.djiremote.MainActivity
import com.dimadesu.djiremote.R
import com.dimadesu.djiremote.dji.DjiDeviceStorage
import com.dimadesu.djiremote.dji.DjiModel
import com.dimadesu.djiremote.dji.DjiRepository
import com.dimadesu.djiremote.dji.SettingsDjiDeviceState
import java.util.UUID

class StreamGoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        when (action) {
            ACTION_TOGGLE_STREAM -> {
                val profileIdStr = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return
                val profileId = try { UUID.fromString(profileIdStr) } catch (e: Exception) { return }

                DjiRepository.initialize(context)
                val devices = DjiRepository.devices.value
                val device = devices.firstOrNull { it.id == profileId } ?: return

                if (!device.isStarted) {
                    DjiModel.startStreaming(context, device)
                } else {
                    DjiModel.stopStreaming(device)
                }
                updateAllWidgets(context)
            }
            ACTION_UPDATE_WIDGET -> {
                updateAllWidgets(context)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_STREAM = "com.dimadesu.djiremote.widget.ACTION_TOGGLE_STREAM"
        const val ACTION_UPDATE_WIDGET = "com.dimadesu.djiremote.widget.ACTION_UPDATE_WIDGET"
        const val EXTRA_PROFILE_ID = "extra_profile_id"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, StreamGoWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (widgetId in allWidgetIds) {
                updateAppWidget(context, appWidgetManager, widgetId)
            }
        }

        private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_streamgo)

            // Open app intent
            val appIntent = Intent(context, MainActivity::class.java)
            val appPendingIntent = PendingIntent.getActivity(
                context,
                0,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_app_link, appPendingIntent)

            // Load profiles
            DjiRepository.initialize(context)
            val devices = DjiRepository.devices.value

            views.removeAllViews(R.id.widget_profiles_container)

            if (devices.isEmpty()) {
                val emptyViews = RemoteViews(context.packageName, R.layout.widget_streamgo)
                views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_empty_text, View.GONE)

                // Show up to 4 profiles directly inside the widget
                for (device in devices.take(4)) {
                    val itemView = RemoteViews(context.packageName, R.layout.widget_profile_item)
                    
                    val displayName = if (!device.customCameraName.isNullOrBlank()) {
                        "${device.name} (${device.customCameraName})"
                    } else {
                        device.name
                    }
                    itemView.setTextViewText(R.id.item_profile_name, displayName)

                    val statusText = when (device.state) {
                        SettingsDjiDeviceState.IDLE -> "Bereit"
                        SettingsDjiDeviceState.STREAMING -> "🔴 LIVE"
                        SettingsDjiDeviceState.RECONNECTING -> "Neu verbinden..."
                        SettingsDjiDeviceState.WIFI_SETUP_FAILED -> "WLAN Fehler"
                        else -> "Verbindet..."
                    }
                    val batteryStr = device.batteryPercentage?.let { " • 🔋$it%" } ?: ""
                    itemView.setTextViewText(R.id.item_profile_status, "$statusText$batteryStr")

                    // Toggle Button action
                    val isStreaming = device.state == SettingsDjiDeviceState.STREAMING
                    val isStarted = device.isStarted

                    val buttonText = when {
                        isStreaming -> "⏹ Stop"
                        isStarted -> "⏳..."
                        else -> "▶ Start"
                    }
                    itemView.setTextViewText(R.id.item_action_button, buttonText)

                    val toggleIntent = Intent(context, StreamGoWidgetProvider::class.java).apply {
                        action = ACTION_TOGGLE_STREAM
                        putExtra(EXTRA_PROFILE_ID, device.id.toString())
                    }
                    val togglePendingIntent = PendingIntent.getBroadcast(
                        context,
                        device.id.hashCode(),
                        toggleIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    itemView.setOnClickPendingIntent(R.id.item_action_button, togglePendingIntent)

                    views.addView(R.id.widget_profiles_container, itemView)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
