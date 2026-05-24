package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentWidth
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.audio.RecordingService

// ── Design tokens ───────────────────────────────────────────────────────────
private val White       = Color(0xFFFFFFFF)
private val SlateText   = Color(0xFF94A3B8)
private val MutedText   = Color(0xFF475569)
private val AccentBlue  = Color(0xFF3B82F6)
private val ErrorRed    = Color(0xFFEF4444)
private val AmberColor  = Color(0xFFF59E0B)
private val Divider     = Color(0x2E94A3B8)

// Static waveform bar heights in dp (for 24 bars; take first N for fewer bars)
private val WAVE = listOf(8, 14, 28, 22, 16, 32, 10, 20, 30, 18, 24, 12,
                           28, 20, 8, 16, 26, 14, 22, 18, 12, 30, 16, 8)

private fun formatTimer(s: Long) =
    if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%02d:%02d".format(s / 60, s % 60)

private fun sourceLabel(src: String) = when (src) {
    "CALL"        -> "Call"
    "ONLINE_MEET" -> "Online"
    "VOICE_NOTE"  -> "Note"
    else          -> "Offline"
}

// ── Widget class ────────────────────────────────────────────────────────────
class MeetingWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs       = currentState<Preferences>()
            val isRecording = prefs[WidgetKeys.IS_RECORDING]    ?: false
            val isPaused    = prefs[WidgetKeys.IS_PAUSED]       ?: false
            val seconds     = prefs[WidgetKeys.RECORD_SECONDS]  ?: 0L
            val source      = prefs[WidgetKeys.SOURCE]          ?: "OFFLINE_MEET"
            val hasApiKey   = prefs[WidgetKeys.HAS_API_KEY]     ?: true
            val lastTitle   = prefs[WidgetKeys.LAST_TITLE]      ?: ""
            val lastDur     = prefs[WidgetKeys.LAST_DURATION]   ?: ""
            val lastStatus  = prefs[WidgetKeys.LAST_STATUS]     ?: ""
            val lastId      = prefs[WidgetKeys.LAST_MEETING_ID] ?: -1
            val processing  = prefs[WidgetKeys.IS_PROCESSING]   ?: false
            val isHero      = LocalSize.current.width >= 200.dp

            when {
                isRecording || isPaused ->
                    if (isHero) HeroRecording(isPaused, seconds, source)
                    else        CompactRecording(isPaused, seconds)
                else ->
                    if (isHero) HeroIdle(source, hasApiKey, lastTitle, lastDur, lastStatus, lastId, processing)
                    else        CompactIdle(hasApiKey, source)
            }
        }
    }
}

// ── Compact Idle ─────────────────────────────────────────────────────────────
@Composable
private fun CompactIdle(hasApiKey: Boolean, source: String) {
    val ctx = LocalContext.current
    val tapIntent = if (hasApiKey) {
        Intent(ctx, MainActivity::class.java).apply {
            action = "com.example.ACTION_QUICK_RECORD"
            putExtra("source", source)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    } else {
        Intent(ctx, MainActivity::class.java).apply {
            action = "com.example.ACTION_OPEN_SETTINGS"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .clickable(actionStartActivity(tapIntent))
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(72.dp)
                    .background(ImageProvider(R.drawable.widget_btn_blue)),
                contentAlignment = Alignment.Center
            ) {
                Text("🎙", style = TextStyle(fontSize = 28.sp))
            }
            Spacer(GlanceModifier.height(8.dp))
            Text(
                "Tap to record",
                style = TextStyle(
                    color = ColorProvider(White),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            )
            Text(
                if (!hasApiKey) "⚠ Set API key" else "meeting",
                style = TextStyle(
                    color = ColorProvider(if (!hasApiKey) AmberColor else SlateText),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

// ── Hero Idle ─────────────────────────────────────────────────────────────────
@Composable
private fun HeroIdle(
    source: String,
    hasApiKey: Boolean,
    lastTitle: String,
    lastDur: String,
    lastStatus: String,
    lastId: Int,
    isProcessing: Boolean
) {
    val ctx = LocalContext.current
    val srcList   = listOf("OFFLINE_MEET", "CALL", "ONLINE_MEET", "VOICE_NOTE")
    val srcIcons  = listOf("🎙", "📞", "💻", "📝")

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── LEFT: record button + source picker ──────────────────────
            Column(
                modifier = GlanceModifier.wrapContentWidth().fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tapIntent = Intent(ctx, MainActivity::class.java).apply {
                    action = if (hasApiKey) "com.example.ACTION_QUICK_RECORD" else "com.example.ACTION_OPEN_SETTINGS"
                    if (hasApiKey) putExtra("source", source)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                Box(
                    modifier = GlanceModifier
                        .size(64.dp)
                        .background(ImageProvider(R.drawable.widget_btn_blue))
                        .clickable(actionStartActivity(tapIntent)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎙", style = TextStyle(fontSize = 26.sp))
                }
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    "Tap to record",
                    style = TextStyle(color = ColorProvider(White), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                )
                Text(
                    "Source: ${sourceLabel(source)} ▾",
                    style = TextStyle(color = ColorProvider(SlateText), fontSize = 11.sp)
                )
                Spacer(GlanceModifier.height(6.dp))
                // Source chips
                Row {
                    srcList.forEachIndexed { i, s ->
                        val isSelected = s == source
                        val changeIntent = Intent(ctx, WidgetActionReceiver::class.java).apply {
                            action = WidgetActionReceiver.ACTION_SOURCE_CHANGE
                            putExtra("source", s)
                        }
                        Box(
                            modifier = GlanceModifier
                                .size(28.dp)
                                .background(
                                    ImageProvider(
                                        if (isSelected) R.drawable.widget_btn_blue
                                        else R.drawable.widget_btn_dark
                                    )
                                )
                                .clickable(actionSendBroadcast(changeIntent)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(srcIcons[i], style = TextStyle(fontSize = 12.sp))
                        }
                        if (i < 3) Spacer(GlanceModifier.width(4.dp))
                    }
                }
            }

            Spacer(GlanceModifier.width(12.dp))

            // ── RIGHT: last meeting peek ─────────────────────────────────
            if (lastTitle.isNotBlank()) {
                val meetingIntent = Intent(ctx, MainActivity::class.java).apply {
                    action = "com.example.ACTION_OPEN_MEETING"
                    putExtra("meetingId", lastId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight()
                        .background(ImageProvider(R.drawable.widget_bg_surface))
                        .cornerRadius(12.dp)
                        .padding(10.dp)
                        .clickable(actionStartActivity(meetingIntent))
                ) {
                    Text(
                        "TODAY'S LAST MEETING",
                        style = TextStyle(color = ColorProvider(SlateText), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(Divider)) {}
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        lastTitle,
                        style = TextStyle(color = ColorProvider(White), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    if (isProcessing) {
                        Text(
                            "Generating summary…",
                            style = TextStyle(color = ColorProvider(AccentBlue), fontSize = 11.sp)
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Box(modifier = GlanceModifier.fillMaxWidth().height(2.dp).background(AccentBlue)) {}
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(lastDur, style = TextStyle(color = ColorProvider(SlateText), fontSize = 11.sp))
                            Text(" · ", style = TextStyle(color = ColorProvider(MutedText), fontSize = 11.sp))
                            val (pillBg, pillTxt) = when (lastStatus) {
                                "COMPLETED"  -> Color(0xFF166534) to "AI ready"
                                "PROCESSING" -> Color(0xFF1E3A8A) to "Processing"
                                "FAILED"     -> Color(0xFF9F1239) to "Failed · Retry"
                                else         -> Color(0xFF374151) to "Recorded"
                            }
                            Box(
                                modifier = GlanceModifier
                                    .background(pillBg)
                                    .cornerRadius(4.dp)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(pillTxt, style = TextStyle(color = ColorProvider(White), fontSize = 10.sp))
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No meetings yet",
                        style = TextStyle(color = ColorProvider(MutedText), fontSize = 12.sp, textAlign = TextAlign.Center)
                    )
                }
            }
        }
    }
}

// ── Compact Recording ─────────────────────────────────────────────────────────
@Composable
private fun CompactRecording(isPaused: Boolean, seconds: Long) {
    val ctx = LocalContext.current
    val stopIntent = Intent(ctx, RecordingService::class.java).apply { action = RecordingService.ACTION_STOP }
    val toggleIntent = Intent(ctx, RecordingService::class.java).apply {
        action = if (isPaused) RecordingService.ACTION_RESUME else RecordingService.ACTION_PAUSE
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(if (isPaused) R.drawable.widget_bg else R.drawable.widget_bg_recording))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .background(if (isPaused) AmberColor else ErrorRed)
                    .cornerRadius(4.dp)
            ) {}
            Spacer(GlanceModifier.width(6.dp))
            Text(
                if (isPaused) "Paused" else "Recording",
                style = TextStyle(
                    color = ColorProvider(if (isPaused) AmberColor else ErrorRed),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        WaveBars(count = 8, frozen = isPaused)
        Spacer(GlanceModifier.height(6.dp))
        Text(
            formatTimer(seconds),
            style = TextStyle(color = ColorProvider(White), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(GlanceModifier.height(8.dp))
        Row(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = GlanceModifier
                    .background(ImageProvider(if (isPaused) R.drawable.widget_btn_blue else R.drawable.widget_btn_amber))
                    .cornerRadius(14.dp)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .clickable(actionStartService(toggleIntent, isForegroundService = false))
            ) {
                Text(
                    if (isPaused) "▶ Resume" else "⏸ Pause",
                    style = TextStyle(color = ColorProvider(White), fontSize = 10.sp)
                )
            }
            Spacer(GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier
                    .background(ImageProvider(R.drawable.widget_btn_red))
                    .cornerRadius(14.dp)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .clickable(actionStartService(stopIntent, isForegroundService = false))
            ) {
                Text("⏹ Stop", style = TextStyle(color = ColorProvider(White), fontSize = 10.sp))
            }
        }
    }
}

// ── Hero Recording ────────────────────────────────────────────────────────────
@Composable
private fun HeroRecording(isPaused: Boolean, seconds: Long, source: String) {
    val ctx = LocalContext.current
    val stopIntent   = Intent(ctx, RecordingService::class.java).apply { action = RecordingService.ACTION_STOP }
    val cancelIntent = Intent(ctx, RecordingService::class.java).apply { action = RecordingService.ACTION_CANCEL }
    val toggleIntent = Intent(ctx, RecordingService::class.java).apply {
        action = if (isPaused) RecordingService.ACTION_RESUME else RecordingService.ACTION_PAUSE
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(if (isPaused) R.drawable.widget_bg else R.drawable.widget_bg_recording))
            .padding(12.dp)
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .background(if (isPaused) AmberColor else ErrorRed)
                    .cornerRadius(4.dp)
            ) {}
            Spacer(GlanceModifier.width(6.dp))
            Text(
                "${if (isPaused) "Paused" else "Recording"} · ${sourceLabel(source)}",
                style = TextStyle(
                    color = ColorProvider(if (isPaused) AmberColor else ErrorRed),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                formatTimer(seconds),
                style = TextStyle(color = ColorProvider(White), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(Divider)) {}
        Spacer(GlanceModifier.height(8.dp))
        WaveBars(count = 24, frozen = isPaused)
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = GlanceModifier
                    .background(ImageProvider(if (isPaused) R.drawable.widget_btn_blue else R.drawable.widget_btn_amber))
                    .cornerRadius(16.dp)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .clickable(actionStartService(toggleIntent, isForegroundService = false))
            ) {
                Text(if (isPaused) "▶  Resume" else "⏸  Pause", style = TextStyle(color = ColorProvider(White), fontSize = 12.sp))
            }
            Spacer(GlanceModifier.width(10.dp))
            Box(
                modifier = GlanceModifier
                    .background(ImageProvider(R.drawable.widget_btn_red))
                    .cornerRadius(16.dp)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .clickable(actionStartService(stopIntent, isForegroundService = false))
            ) {
                Text("⏹  Stop", style = TextStyle(color = ColorProvider(White), fontSize = 12.sp))
            }
            Spacer(GlanceModifier.width(10.dp))
            Box(
                modifier = GlanceModifier
                    .background(ImageProvider(R.drawable.widget_bg_surface))
                    .cornerRadius(16.dp)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .clickable(actionStartService(cancelIntent, isForegroundService = false))
            ) {
                Text("🗑  Cancel", style = TextStyle(color = ColorProvider(SlateText), fontSize = 12.sp))
            }
        }
    }
}

// ── Waveform bars ─────────────────────────────────────────────────────────────
@Composable
private fun WaveBars(count: Int, frozen: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        (0 until count).forEach { i ->
            val h = if (frozen) 6 else WAVE[i % WAVE.size]
            Box(
                modifier = GlanceModifier
                    .width(3.dp)
                    .height(h.dp)
                    .background(AccentBlue)
                    .cornerRadius(2.dp)
            ) {}
            if (i < count - 1) Spacer(GlanceModifier.width(2.dp))
        }
    }
}
