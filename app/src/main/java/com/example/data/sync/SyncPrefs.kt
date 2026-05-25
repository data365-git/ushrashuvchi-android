package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences

class SyncPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    var deviceId: String?
        get() = prefs.getString("device_id", null)
        set(value) { prefs.edit().putString("device_id", value).apply() }

    var jwtToken: String?
        get() = prefs.getString("jwt_token", null)
        set(value) { prefs.edit().putString("jwt_token", value).apply() }

    var cloudSyncEnabled: Boolean
        get() = prefs.getBoolean("cloud_sync_enabled", false)
        set(value) { prefs.edit().putBoolean("cloud_sync_enabled", value).apply() }

    fun authHeader(): String? = jwtToken?.let { "Bearer $it" }
}
