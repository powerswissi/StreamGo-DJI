package com.dimadesu.djiremote.dji

import android.content.Context

/**
 * Stub – Logging wurde entfernt. Alle Aufrufe sind No-Ops.
 */
object AppLogger {
    fun init(context: Context) {}
    fun d(tag: String, msg: String) {}
    fun v(tag: String, msg: String) {}
    fun i(tag: String, msg: String) {}
    fun w(tag: String, msg: String) {}
    fun e(tag: String, msg: String, tr: Throwable? = null) {}
}
