package com.dimadesu.djiremote.preset

import java.util.UUID

data class ConnectionPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val rtmpUrl: String = ""
)
