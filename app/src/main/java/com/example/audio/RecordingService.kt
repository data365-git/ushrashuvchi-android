package com.example.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.data.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class RecordingService : Service() {

    companion object {
        const val ACTION_START = "com.example.audio.ACTION_START"
        const val ACTION_PAUSE = "com.example.audio.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.audio.ACTION_RESUME"
        const val ACTION_STOP = "com.example.audio.ACTION_STOP"
        const val ACTION_CANCEL = "com.example.audio.ACTION_CANCEL"

        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_OUTPUT_PATH = "extra_output_path"
        const val EXTRA_TOPIC = "extra_topic"
        const val EXTRA_FOLDER_SLUG = "extra_folder_slug"
        const val EXTRA_MEETING_ID = "extra_meeting_id"

        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "recording_channel"

        private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
        val state: StateFlow<RecorderState> = _state.asStateFlow()
    }

    private var recorder: MediaRecorder? = null
    private var outputPath: String? = null
    private var sessionId: String? = null
    private var meetingId: Int = -1
    private var folderSlug: String = "_inbox"
    private var topic: String = ""
    private var startTimeMs: Long = 0L
    private var elapsedMs: Long = 0L
    private var isPaused: Boolean = false
    private var wasAutoPaused: Boolean = false

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val tickHandler = Handler(Looper.getMainLooper())
    private var lastTickTime: Long = 0L
    private var checkpointTickCount: Int = 0

    private var recoveryStore: RecoveryStore? = null
    private var fileManager: RecordingFileManager? = null
    private var db: AppDatabase? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Headset unplugged — log only, do not pause
        }
    }

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val current = _state.value
                if (current is RecorderState.Active && !current.isPaused) {
                    wasAutoPaused = true
                    pauseRecorder()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasAutoPaused) {
                    wasAutoPaused = false
                    resumeRecorder()
                }
            }
        }
    }

    private val tickRunnable: Runnable = object : Runnable {
        override fun run() {
            val current = _state.value
            if (current is RecorderState.Active && !current.isPaused) {
                val now = System.currentTimeMillis()
                val delta = if (lastTickTime > 0) now - lastTickTime else 100L
                lastTickTime = now
                elapsedMs += delta
                val amplitude = try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
                val size = outputPath?.let { File(it).length() } ?: 0L
                _state.value = RecorderState.Active(
                    sessionId = current.sessionId,
                    outputAbsolutePath = current.outputAbsolutePath,
                    elapsedMs = elapsedMs,
                    amplitude = amplitude,
                    isPaused = false,
                    sizeBytes = size
                )
                checkpointTickCount++
                if (checkpointTickCount >= 50) { // ~5s at 100ms
                    checkpointTickCount = 0
                    saveCheckpoint()
                }
                tickHandler.postDelayed(this, 100L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        recoveryStore = RecoveryStore(applicationContext)
        fileManager = RecordingFileManager(applicationContext)
        db = AppDatabase.getDatabase(applicationContext)
        createNotificationChannel()
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> pauseRecorder()
            ACTION_RESUME -> resumeRecorder()
            ACTION_STOP -> handleStop()
            ACTION_CANCEL -> handleCancel()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH) ?: return
        topic = intent.getStringExtra(EXTRA_TOPIC) ?: ""
        folderSlug = intent.getStringExtra(EXTRA_FOLDER_SLUG) ?: "_inbox"
        meetingId = intent.getIntExtra(EXTRA_MEETING_ID, -1)

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(applicationContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
                try {
                    setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                } catch (_: Exception) {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                }
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioChannels(1)
                setAudioEncodingBitRate(64_000)
                setMaxFileSize(2L * 1024 * 1024 * 1024)
                setOutputFile(outputPath)
                prepare()
                start()
            }
            startTimeMs = System.currentTimeMillis()
            elapsedMs = 0L
            lastTickTime = 0L
            isPaused = false
            _state.value = RecorderState.Active(
                sessionId = sessionId!!,
                outputAbsolutePath = outputPath!!,
                elapsedMs = 0L,
                amplitude = 0,
                isPaused = false,
                sizeBytes = 0L
            )
            tickHandler.post(tickRunnable)
        } catch (e: Exception) {
            _state.value = RecorderState.Error("Failed to start recording: ${e.message}", recoverable = false)
            stopSelf()
        }
    }

    private fun pauseRecorder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { recorder?.pause() } catch (_: Exception) {}
        }
        isPaused = true
        tickHandler.removeCallbacks(tickRunnable)
        val current = _state.value
        if (current is RecorderState.Active) {
            _state.value = current.copy(isPaused = true)
        }
    }

    private fun resumeRecorder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { recorder?.resume() } catch (_: Exception) {}
        }
        isPaused = false
        lastTickTime = 0L
        tickHandler.post(tickRunnable)
        val current = _state.value
        if (current is RecorderState.Active) {
            _state.value = current.copy(isPaused = false)
        }
    }

    private fun handleStop() {
        tickHandler.removeCallbacks(tickRunnable)
        val path = outputPath
        val sid = sessionId
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null

        if (path != null && sid != null) {
            val audioFile = File(path)
            serviceScope.launch {
                try {
                    val sidecar = RecordingSidecar(
                        recordingId = sid,
                        topic = topic,
                        folder = folderSlug,
                        createdAt = startTimeMs,
                        durationMs = elapsedMs,
                        mimeType = "audio/mp4",
                        sampleRateHz = 44100,
                        channels = 1,
                        bitrateKbps = 64,
                        sizeBytes = audioFile.length(),
                        checksum = null,
                        device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                        appVersion = applicationContext.packageManager
                            .getPackageInfo(applicationContext.packageName, 0).versionName ?: "1.0"
                    )
                    fileManager?.writeSidecar(audioFile, sidecar)
                    db?.recordingSessionDao()?.updateState(sid, "COMPLETED", System.currentTimeMillis())
                } catch (_: Exception) {}
            }
        }

        recoveryStore?.clear()
        _state.value = RecorderState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleCancel() {
        tickHandler.removeCallbacks(tickRunnable)
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null

        val path = outputPath
        val sid = sessionId
        if (path != null) {
            serviceScope.launch {
                try {
                    fileManager?.moveToTrash(File(path))
                    if (sid != null) {
                        db?.recordingSessionDao()?.updateState(sid, "DISCARDED", System.currentTimeMillis())
                    }
                } catch (_: Exception) {}
            }
        }

        recoveryStore?.clear()
        _state.value = RecorderState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun saveCheckpoint() {
        val sid = sessionId ?: return
        val path = outputPath ?: return
        recoveryStore?.save(
            RecoveryCheckpoint(
                active = true,
                sessionId = sid,
                meetingId = meetingId,
                outputAbsolutePath = path,
                startedAt = startTimeMs,
                lastTickAt = System.currentTimeMillis(),
                folderSlug = folderSlug,
                topic = topic
            )
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recording",
            NotificationManager.IMPORTANCE_HIGH
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Recording in progress")
        .setContentText(topic.ifBlank { "Meeting recording" })
        .setSmallIcon(android.R.drawable.presence_audio_online)
        .setOngoing(true)
        .addAction(
            android.R.drawable.ic_media_pause,
            "Pause",
            makePendingIntent(ACTION_PAUSE)
        )
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            makePendingIntent(ACTION_STOP)
        )
        .build()

    private fun makePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, RecordingService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        tickHandler.removeCallbacks(tickRunnable)
        try { recorder?.release() } catch (_: Exception) {}
        try { unregisterReceiver(noisyReceiver) } catch (_: Exception) {}
        serviceJob.cancel()
    }
}
