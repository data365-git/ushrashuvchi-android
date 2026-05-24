package com.example.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object WidgetKeys {
    val IS_RECORDING    = booleanPreferencesKey("w_recording")
    val IS_PAUSED       = booleanPreferencesKey("w_paused")
    val RECORD_SECONDS  = longPreferencesKey("w_seconds")
    val SOURCE          = stringPreferencesKey("w_source")
    val HAS_API_KEY     = booleanPreferencesKey("w_has_key")
    val LAST_TITLE      = stringPreferencesKey("w_last_title")
    val LAST_DURATION   = stringPreferencesKey("w_last_dur")
    val LAST_STATUS     = stringPreferencesKey("w_last_status")
    val LAST_MEETING_ID = intPreferencesKey("w_last_id")
    val IS_PROCESSING   = booleanPreferencesKey("w_processing")
}
