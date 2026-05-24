package com.example.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

object WidgetStateManager {
    suspend fun push(
        context: Context,
        isRecording: Boolean = false,
        isPaused: Boolean = false,
        recordSeconds: Long = 0L,
        source: String = "OFFLINE_MEET",
        hasApiKey: Boolean = true,
        lastTitle: String = "",
        lastDuration: String = "",
        lastStatus: String = "",
        lastMeetingId: Int = -1,
        isProcessing: Boolean = false
    ) {
        try {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(MeetingWidget::class.java)
            ids.forEach { id ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().also {
                        it[WidgetKeys.IS_RECORDING]    = isRecording
                        it[WidgetKeys.IS_PAUSED]       = isPaused
                        it[WidgetKeys.RECORD_SECONDS]  = recordSeconds
                        it[WidgetKeys.SOURCE]          = source
                        it[WidgetKeys.HAS_API_KEY]     = hasApiKey
                        it[WidgetKeys.LAST_TITLE]      = lastTitle
                        it[WidgetKeys.LAST_DURATION]   = lastDuration
                        it[WidgetKeys.LAST_STATUS]     = lastStatus
                        it[WidgetKeys.LAST_MEETING_ID] = lastMeetingId
                        it[WidgetKeys.IS_PROCESSING]   = isProcessing
                    }
                }
                MeetingWidget().update(context, id)
            }
        } catch (_: Exception) {
            // No widgets placed — safe to ignore
        }
    }
}
