package com.dimadesu.djiremote.preset

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SuppressLint("StaticFieldLeak")
object PresetRepository {
    private const val PREFS_NAME = "streamgo_presets"
    private const val KEY_PRESETS = "presets_list"
    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    private val _presets = MutableStateFlow<List<ConnectionPreset>>(emptyList())
    val presets: StateFlow<List<ConnectionPreset>> = _presets.asStateFlow()

    fun initialize(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        val json = sp.getString(KEY_PRESETS, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<ConnectionPreset>>() {}.type
                val loaded: List<ConnectionPreset>? = gson.fromJson(json, type)
                _presets.value = loaded ?: emptyList()
            } catch (e: Exception) {
                _presets.value = emptyList()
            }
        }
    }

    private fun saveToStorage() {
        prefs?.let { sp ->
            val json = gson.toJson(_presets.value)
            sp.edit().putString(KEY_PRESETS, json).apply()
        }
    }

    fun addPreset(preset: ConnectionPreset) {
        _presets.value = _presets.value + preset
        saveToStorage()
    }

    fun updatePreset(preset: ConnectionPreset) {
        _presets.value = _presets.value.map { if (it.id == preset.id) preset else it }
        saveToStorage()
    }

    fun deletePreset(id: String) {
        _presets.value = _presets.value.filter { it.id != id }
        saveToStorage()
    }

    fun exportToJson(): String {
        return gson.toJson(_presets.value)
    }

    fun importFromJson(json: String): Boolean {
        return try {
            val type = object : TypeToken<List<ConnectionPreset>>() {}.type
            val imported: List<ConnectionPreset>? = gson.fromJson(json, type)
            if (!imported.isNullOrEmpty()) {
                _presets.value = imported
                saveToStorage()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
