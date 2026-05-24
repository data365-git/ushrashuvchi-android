package com.example.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MeetingWidget()
}

class HeroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MeetingWidget()
}

class WidgetActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SOURCE_CHANGE = "com.example.widget.ACTION_SOURCE_CHANGE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SOURCE_CHANGE) {
            val src = intent.getStringExtra("source") ?: return
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val ids = GlanceAppWidgetManager(context).getGlanceIds(MeetingWidget::class.java)
                    ids.forEach { id ->
                        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                            prefs.toMutablePreferences().also { it[WidgetKeys.SOURCE] = src }
                        }
                        MeetingWidget().update(context, id)
                    }
                } catch (_: Exception) {}
                context.getSharedPreferences("ushrashuvchi_prefs", Context.MODE_PRIVATE)
                    .edit().putString("meeting_audio_source", src).apply()
            }
        }
    }
}
