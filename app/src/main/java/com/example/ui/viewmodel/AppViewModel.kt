package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.RecordingFileManager
import com.example.audio.RecordingService
import com.example.audio.RecorderState
import com.example.audio.RecoveryCheckpoint
import com.example.audio.RecoveryStore
import com.example.data.database.AppDatabase
import com.example.data.localization.Language
import com.example.data.localization.Translations
import com.example.data.model.ChatMessage
import com.example.data.model.Folder
import com.example.data.model.Meeting
import com.example.data.model.MeetingChapter
import com.example.data.model.MeetingStatus
import com.example.data.model.RecordingSession
import com.example.data.model.Task
import com.example.data.model.TranscriptLine
import com.example.data.repository.MeetingRepository
import com.example.data.sync.SyncManager
import com.example.data.sync.SyncPrefs
import com.example.widget.WidgetStateManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class LibrarySort { LAST_MODIFIED, NAME_ASC, NAME_DESC, OLDEST }

class AppViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val DEFAULT_STT_MODEL = "gemini-2.5-flash"
        private const val DEFAULT_LLM_MODEL = "gemini-2.5-flash"
        private const val DEFAULT_TRANSCRIPTION_PROMPT =
            "You are an expert meeting transcriber. Listen to the attached audio (or use the provided topic if no audio), " +
            "produce an accurate transcript with speaker labels, then structure the meeting (summary, chapters, tasks, " +
            "refined topics) in the requested language."
        private const val DEFAULT_CHAT_PROMPT =
            "You are a helpful meeting summary assistant. Answer strictly from the transcript. Be concise."
        const val DEFAULT_SUMMARY_PROMPT =
            "Summarize this meeting in clear markdown with key takeaways."
        const val DEFAULT_REFINED_PROMPT =
            "Group the transcript into logical topics with key points and speaker quotes."
        const val DEFAULT_TASKS_PROMPT =
            "Extract action items as a JSON array with fields: title, assignee."
    }

    private val db = AppDatabase.getDatabase(application)
    private val fileManager = RecordingFileManager(application)
    private val recoveryStore = RecoveryStore(application)
    private val repository = MeetingRepository(
        meetingDao = db.meetingDao(),
        folderDao = db.folderDao(),
        recordingSessionDao = db.recordingSessionDao(),
        fileManager = fileManager,
        aiCallLogDao = db.aiCallLogDao(),
        db = db
    )
    private val sharedPrefs = application.getSharedPreferences("ushrashuvchi_prefs", Context.MODE_PRIVATE)

    private val _customGeminiKey = MutableStateFlow(sharedPrefs.getString("gemini_api_key", "") ?: "")
    val customGeminiKey: StateFlow<String> = _customGeminiKey.asStateFlow()

    private val _sttModel = MutableStateFlow(sharedPrefs.getString("gemini_stt_model", DEFAULT_STT_MODEL) ?: DEFAULT_STT_MODEL)
    val sttModel: StateFlow<String> = _sttModel.asStateFlow()

    private val _llmModel = MutableStateFlow(sharedPrefs.getString("gemini_llm_model", DEFAULT_LLM_MODEL) ?: DEFAULT_LLM_MODEL)
    val llmModel: StateFlow<String> = _llmModel.asStateFlow()

    private val _transcriptionPrompt = MutableStateFlow(sharedPrefs.getString("gemini_transcription_prompt", DEFAULT_TRANSCRIPTION_PROMPT) ?: DEFAULT_TRANSCRIPTION_PROMPT)
    val transcriptionPrompt: StateFlow<String> = _transcriptionPrompt.asStateFlow()

    private val _chatPrompt = MutableStateFlow(sharedPrefs.getString("gemini_chat_prompt", DEFAULT_CHAT_PROMPT) ?: DEFAULT_CHAT_PROMPT)
    val chatPrompt: StateFlow<String> = _chatPrompt.asStateFlow()

    private val _summaryPrompt = MutableStateFlow(
        sharedPrefs.getString("gemini_summary_prompt", DEFAULT_SUMMARY_PROMPT) ?: DEFAULT_SUMMARY_PROMPT
    )
    val summaryPrompt: StateFlow<String> = _summaryPrompt.asStateFlow()
    fun updateSummaryPrompt(v: String) {
        _summaryPrompt.value = v
        sharedPrefs.edit().putString("gemini_summary_prompt", v).apply()
    }

    private val _refinedPrompt = MutableStateFlow(
        sharedPrefs.getString("gemini_refined_prompt", DEFAULT_REFINED_PROMPT) ?: DEFAULT_REFINED_PROMPT
    )
    val refinedPrompt: StateFlow<String> = _refinedPrompt.asStateFlow()
    fun updateRefinedPrompt(v: String) {
        _refinedPrompt.value = v
        sharedPrefs.edit().putString("gemini_refined_prompt", v).apply()
    }

    private val _tasksPrompt = MutableStateFlow(
        sharedPrefs.getString("gemini_tasks_prompt", DEFAULT_TASKS_PROMPT) ?: DEFAULT_TASKS_PROMPT
    )
    val tasksPrompt: StateFlow<String> = _tasksPrompt.asStateFlow()
    fun updateTasksPrompt(v: String) {
        _tasksPrompt.value = v
        sharedPrefs.edit().putString("gemini_tasks_prompt", v).apply()
    }

    // --- Recorder state from service ---
    val recorderState: StateFlow<RecorderState> = RecordingService.state

    // --- Folder list ---
    val folders: StateFlow<List<Folder>> = db.folderDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedFolderId = MutableStateFlow<Int?>(null)
    val selectedFolderId: StateFlow<Int?> = _selectedFolderId.asStateFlow()

    // Meeting-type filter for Tab 1 source chips (null = all)
    private val _selectedAudioSource = MutableStateFlow<String?>(null)
    val selectedAudioSource: StateFlow<String?> = _selectedAudioSource.asStateFlow()
    fun setSelectedAudioSource(src: String?) { _selectedAudioSource.value = src }

    // Pre-recording meeting type (persisted across sessions)
    private val _meetingAudioSource = MutableStateFlow(sharedPrefs.getString("meeting_audio_source", "OFFLINE_MEET") ?: "OFFLINE_MEET")
    val meetingAudioSource: StateFlow<String> = _meetingAudioSource.asStateFlow()
    fun setMeetingAudioSource(src: String) {
        _meetingAudioSource.value = src
        sharedPrefs.edit().putString("meeting_audio_source", src).apply()
    }

    private val _amplitudeWaveform = MutableStateFlow<List<Int>>(emptyList())
    val amplitudeWaveform: StateFlow<List<Int>> = _amplitudeWaveform.asStateFlow()

    private val _unrecoveredCheckpoint = MutableStateFlow<RecoveryCheckpoint?>(null)
    val unrecoveredCheckpoint: StateFlow<RecoveryCheckpoint?> = _unrecoveredCheckpoint.asStateFlow()

    // --- Recording quality/source prefs ---
    private val _recordingQuality = MutableStateFlow(sharedPrefs.getString("recording_quality", "STANDARD") ?: "STANDARD")
    val recordingQuality: StateFlow<String> = _recordingQuality.asStateFlow()

    private val _micCaptureSource = MutableStateFlow(sharedPrefs.getString("recording_audio_source", "AUTO") ?: "AUTO")
    val micCaptureSource: StateFlow<String> = _micCaptureSource.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(sharedPrefs.getBoolean("recording_keep_screen_on", true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _trashAutoPurgeDays = MutableStateFlow(sharedPrefs.getInt("trash_auto_purge_days", 30))
    val trashAutoPurgeDays: StateFlow<Int> = _trashAutoPurgeDays.asStateFlow()

    private val _defaultRecordingFolderId = MutableStateFlow(sharedPrefs.getInt("default_recording_folder_id", -1))
    val defaultRecordingFolderId: StateFlow<Int> = _defaultRecordingFolderId.asStateFlow()

    fun updateRecordingQuality(v: String) { _recordingQuality.value = v; sharedPrefs.edit().putString("recording_quality", v).apply() }
    fun updateMicCaptureSource(v: String) { _micCaptureSource.value = v; sharedPrefs.edit().putString("recording_audio_source", v).apply() }
    fun updateKeepScreenOn(v: Boolean) { _keepScreenOn.value = v; sharedPrefs.edit().putBoolean("recording_keep_screen_on", v).apply() }
    fun updateTrashAutoPurgeDays(v: Int) { _trashAutoPurgeDays.value = v; sharedPrefs.edit().putInt("trash_auto_purge_days", v).apply() }
    fun updateDefaultRecordingFolderId(v: Int) { _defaultRecordingFolderId.value = v; sharedPrefs.edit().putInt("default_recording_folder_id", v).apply() }

    // Storage breakdown — computed lazily, refreshed on demand
    private val _storageBreakdown = MutableStateFlow(com.example.ui.screens.StorageBreakdown())
    val storageBreakdown: StateFlow<com.example.ui.screens.StorageBreakdown> = _storageBreakdown.asStateFlow()

    fun refreshStorageBreakdown() {
        viewModelScope.launch(Dispatchers.IO) {
            val allMeetings = db.meetingDao().getAllMeetingsSync()
            val allFolders = db.folderDao().getAllForTree().first()
            val folderMap = allFolders.associateBy { it.id }

            var totalBytes = 0L
            var trashBytes = 0L
            val byFolderMap = mutableMapOf<Int, Pair<Long, Int>>() // folderId -> (bytes, count)
            val allSizes = mutableListOf<com.example.ui.screens.MeetingSize>()

            allMeetings.forEach { meeting ->
                val audioPath = meeting.audioRelativePath ?: meeting.audioPath ?: return@forEach
                val file = if (audioPath.startsWith("/")) java.io.File(audioPath)
                           else java.io.File(getApplication<android.app.Application>().getExternalFilesDir(null), audioPath)
                val bytes = if (file.exists()) file.length() else 0L
                if (bytes == 0L) return@forEach

                if (meeting.isDeleted) {
                    trashBytes += bytes
                } else {
                    totalBytes += bytes
                    val fid = meeting.folderId ?: -1
                    val (prevBytes, prevCount) = byFolderMap[fid] ?: (0L to 0)
                    byFolderMap[fid] = (prevBytes + bytes) to (prevCount + 1)
                    allSizes.add(com.example.ui.screens.MeetingSize(meeting.id, meeting.title, bytes, meeting.durationSeconds))
                }
            }

            val byFolder = byFolderMap.entries.mapNotNull { (fid, pair) ->
                val folder = if (fid == -1) null else folderMap[fid]
                com.example.ui.screens.FolderUsage(
                    folderId = fid,
                    folderName = folder?.name ?: "No folder",
                    colorHex = folder?.colorHex ?: "#6B7280",
                    bytes = pair.first,
                    count = pair.second
                )
            }.sortedByDescending { it.bytes }

            _storageBreakdown.value = com.example.ui.screens.StorageBreakdown(
                totalBytes = totalBytes,
                byFolder = byFolder,
                trashBytes = trashBytes,
                biggest = allSizes.sortedByDescending { it.bytes }.take(20)
            )
        }
    }

    init {
        // Migrate stale default model prefs to gemini-2.5-flash
        val savedStt = sharedPrefs.getString("gemini_stt_model", null)
        if (savedStt != null && (savedStt.startsWith("gemini-1.5") || savedStt == "gemini-2.0-flash")) {
            sharedPrefs.edit().putString("gemini_stt_model", DEFAULT_STT_MODEL).apply()
            _sttModel.value = DEFAULT_STT_MODEL
        }
        val savedLlm = sharedPrefs.getString("gemini_llm_model", null)
        if (savedLlm != null && (savedLlm.startsWith("gemini-1.5") || savedLlm == "gemini-2.0-flash")) {
            sharedPrefs.edit().putString("gemini_llm_model", DEFAULT_LLM_MODEL).apply()
            _llmModel.value = DEFAULT_LLM_MODEL
        }
        // Migrate old boolean isDarkTheme pref
        if (sharedPrefs.contains("is_dark_theme") && !sharedPrefs.contains("theme_mode")) {
            val oldDark = sharedPrefs.getBoolean("is_dark_theme", false)
            setThemeMode(if (oldDark) com.example.ui.theme.ThemeMode.DARK else com.example.ui.theme.ThemeMode.LIGHT)
            sharedPrefs.edit().remove("is_dark_theme").apply()
        }
        val savedKey = sharedPrefs.getString("gemini_api_key", "") ?: ""
        com.example.data.api.GeminiClient.setCustomApiKey(savedKey.ifBlank { null })
        viewModelScope.launch {
            try {
                repository.seedDefaultFoldersIfEmpty()
            } catch (_: Exception) {}
            try {
                val alreadySeeded = sharedPrefs.getBoolean("demo_seeded", false)
                if (!alreadySeeded) {
                    seedDemoData()
                    sharedPrefs.edit().putBoolean("demo_seeded", true).apply()
                }
            } catch (_: Exception) {}
            try {
                val checkpoint = recoveryStore.load()
                if (checkpoint != null) _unrecoveredCheckpoint.value = checkpoint
            } catch (_: Exception) {}
            // Partial Gap 1: orphan sweep. Generation currently runs on viewModelScope,
            // so any meeting still in PROCESSING after the process died is by definition
            // stuck. Mark them FAILED with a clear "Interrupted" reason so the UI shows
            // Retry instead of a permanent spinner. Full fix is to move generation to
            // WorkManager — until then this prevents the worst stuck-state UX.
            try {
                val orphans = db.meetingDao().getAllMeetingsSync()
                    .filter { it.status == com.example.data.model.MeetingStatus.PROCESSING }
                orphans.forEach { m ->
                    db.meetingDao().updateMeeting(m.copy(
                        status = com.example.data.model.MeetingStatus.FAILED,
                        generationError = "Generation was interrupted (app closed or device restarted). Tap Retry."
                    ))
                }
                if (orphans.isNotEmpty()) {
                    android.util.Log.w("AppViewModel",
                        "Orphan sweep: marked ${orphans.size} stuck PROCESSING meeting(s) as FAILED")
                }
            } catch (e: Exception) {
                android.util.Log.w("AppViewModel", "Orphan sweep failed", e)
            }
        }
        // Collect recorder state for waveform
        viewModelScope.launch {
            recorderState.collect { state ->
                if (state is RecorderState.Active) {
                    // Drop the first 600 ms: mic gain auto-calibrates during onset and
                    // returns near-max amplitudes that blow out the waveform visually.
                    if (state.elapsedMs < 600) return@collect
                    val current = _amplitudeWaveform.value.toMutableList()
                    current.add(state.amplitude)
                    if (current.size > 48) current.removeAt(0)
                    _amplitudeWaveform.value = current
                } else if (state is RecorderState.Idle) {
                    _amplitudeWaveform.value = emptyList()
                }
            }
        }
        // Push widget state whenever meetings list changes (e.g. new meeting added, status updated)
        viewModelScope.launch {
            db.meetingDao().getAllMeetings().collect { _ -> pushWidgetState() }
        }
        refreshStorageBreakdown()
        // Schedule trash auto-purge
        val workManager = androidx.work.WorkManager.getInstance(application)
        workManager.enqueueUniquePeriodicWork(
            "trash_auto_purge",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            androidx.work.PeriodicWorkRequestBuilder<com.example.audio.TrashAutoPurgeWorker>(
                24, java.util.concurrent.TimeUnit.HOURS
            ).build()
        )
        workManager.enqueue(
            androidx.work.OneTimeWorkRequestBuilder<com.example.audio.TrashAutoPurgeWorker>().build()
        )
    }

    fun updateCustomGeminiKey(key: String) {
        _customGeminiKey.value = key
        sharedPrefs.edit().putString("gemini_api_key", key).apply()
        com.example.data.api.GeminiClient.setCustomApiKey(key.ifBlank { null })
    }

    fun updateSttModel(v: String) { _sttModel.value = v; sharedPrefs.edit().putString("gemini_stt_model", v).apply() }
    fun updateLlmModel(v: String) { _llmModel.value = v; sharedPrefs.edit().putString("gemini_llm_model", v).apply() }
    fun updateTranscriptionPrompt(v: String) { _transcriptionPrompt.value = v; sharedPrefs.edit().putString("gemini_transcription_prompt", v).apply() }
    fun updateChatPrompt(v: String) { _chatPrompt.value = v; sharedPrefs.edit().putString("gemini_chat_prompt", v).apply() }

    fun resetGeminiDefaults() {
        updateSttModel(DEFAULT_STT_MODEL)
        updateLlmModel(DEFAULT_LLM_MODEL)
        updateTranscriptionPrompt(DEFAULT_TRANSCRIPTION_PROMPT)
        updateChatPrompt(DEFAULT_CHAT_PROMPT)
    }

    private suspend fun seedDemoData() {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val chapterListType = com.squareup.moshi.Types.newParameterizedType(List::class.java, Map::class.java)
        @Suppress("UNCHECKED_CAST")
        val chapterAdapter = moshi.adapter<List<Map<String, Any>>>(chapterListType)
        val refinedType = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.model.RefinedTranscriptTopic::class.java)
        val refinedAdapter = moshi.adapter<List<com.example.data.model.RefinedTranscriptTopic>>(refinedType)

        // Meeting 1: Landing Page Design Review (Multi-lingual Context)
        val chapters1 = listOf(
            mapOf("title" to "Introduction & Agenda", "timestampMs" to 0L, "summary" to "Marcus introduces the new redesign goals and target visual branding."),
            mapOf("title" to "Branding & Color Feedback", "timestampMs" to 30000L, "summary" to "Dave expresses concern over color intensity. Team aligns on reserving neon green for focus highlights.")
        )
        val refined1 = listOf(
            com.example.data.model.RefinedTranscriptTopic(
                id = "m1_ref_1",
                title = "Landing Page Visual Branding",
                summary = "The team aligned positively on the new landing page mocked designs, specifically praising the modernized color choices and spacing.",
                keyPoints = listOf(
                    "Overall design system and layouts were received with enthusiasm.",
                    "Neon green was identified as a strong, visually memorable brand element that needs careful application size."
                ),
                decisions = listOf("Keep neon green as primary highlight accent color."),
                openQuestions = listOf("Verify body text contrast ratios for AA standards compatibility."),
                speakerContext = listOf(
                    com.example.data.model.SpeakerContextItem("Marcus", "Presented initial layout and endorsed visual pairing."),
                    com.example.data.model.SpeakerContextItem("Dave", "Raised a risk about color intensity on smaller screens.")
                ),
                startTimestamp = "0:00",
                endTimestamp = "0:30"
            ),
            com.example.data.model.RefinedTranscriptTopic(
                id = "m1_ref_2",
                title = "Typography and Contrast Adjustments",
                summary = "Dave voiced critical concern that utilizing neon green for running text might damage text readability. Marcus and the team agreed on limiting it strictly to highlights and secondary links, keeping standard content in high-contrast neutral slate colors for a calmer feel.",
                keyPoints = listOf(
                    "Limit neon green strictly to badges, call-to-actions, and highlights.",
                    "Ensure running body text remains slate or white for readability."
                ),
                decisions = listOf("Use a calmer neutral color for primary body paragraphs."),
                relatedTasks = listOf("Update landing page body text colors", "Run contrast parity tests across device screens"),
                speakerContext = listOf(
                    com.example.data.model.SpeakerContextItem("Dave", "Advocated for high accessibility standards."),
                    com.example.data.model.SpeakerContextItem("Marcus", "Modified the UI styling guide to reserve accent usage.")
                ),
                startTimestamp = "0:30",
                endTimestamp = "1:05"
            )
        )
        val m1Id = db.meetingDao().insertMeeting(
            com.example.data.model.Meeting(
                title = "Landing Page Redesign Feedback",
                folders = "Team Sync",
                status = MeetingStatus.COMPLETED,
                isStarred = true,
                isDemo = true,
                durationSeconds = 65,
                date = System.currentTimeMillis() - 3600000L * 24L, // 1 day ago
                summary = "### Redesign Discussion Highlights\n- **Target Brand Identity:** Modernized neon theme.\n- **Decisions Reached:** Keep neon colors for secondary highlights, ensure readable background contrast.\n- **Action Items:** Review text and button padding.",
                chaptersJson = chapterAdapter.toJson(chapters1),
                refinedTranscriptJson = refinedAdapter.toJson(refined1)
            )
        ).toInt()

        db.meetingDao().insertTranscriptLines(listOf(
            com.example.data.model.TranscriptLine(meetingId = m1Id, timestampStart = 0L, timestampEnd = 15000L, speaker = "Marcus", text = "Alright everyone, let's look at the new mockups for Ushrashuvchi's brand. Dave, what do you think?"),
            com.example.data.model.TranscriptLine(meetingId = m1Id, timestampStart = 15000L, timestampEnd = 35000L, speaker = "Dave", text = "Well, like, I think the neon green works on, you know, links, but don't you think it's a bit too much for paragraphs? It's like blindingly bright."),
            com.example.data.model.TranscriptLine(meetingId = m1Id, timestampStart = 35000L, timestampEnd = 50000L, speaker = "Marcus", text = "Yeah, yeah, exactly, that make sense. Let's tone down the body. Body text should be more call, maybe a warm neutral."),
            com.example.data.model.TranscriptLine(meetingId = m1Id, timestampStart = 50000L, timestampEnd = 65000L, speaker = "Dave", text = "Agreed! That solves readability. I can prepare the revised palettes by tomorrow.")
        ))
        db.meetingDao().insertTask(com.example.data.model.Task(meetingId = m1Id, title = "Update landing page body text colors", assignee = "Marcus", isCompleted = false))
        db.meetingDao().insertTask(com.example.data.model.Task(meetingId = m1Id, title = "Run contrast parity tests across device screens", assignee = "Dave", isCompleted = false))


        // Meeting 2: Uzbek / Russian Project Planning Discussion (Loyihani yakunlash / Завершение технического плана)
        val chapters2 = listOf(
            mapOf("title" to "Loyiha tayyorgarligi", "timestampMs" to 0L, "summary" to "Anvar va Dilshod vazifalar ijrsini muhokama qiladi."),
            mapOf("title" to "Texnik integratsiya", "timestampMs" to 20000L, "summary" to "API va ma'lumotlar bazasi zaxirasini yaratish tahlili.")
        )
        val refined2 = listOf(
            com.example.data.model.RefinedTranscriptTopic(
                id = "m2_ref_1",
                title = "Ilova arxitekturasi va integratsiya bosqichlari / Архитектура",
                summary = "Dilshod ilova infratuzilmasi uchun tayyorlangan asosiy sxemani namoyish etdi. Jamoa API xavfsizlik qatlamini yanada takomillashtirishga va uning barqarorligini tekshirishga kelishib oldi.",
                keyPoints = listOf(
                    "Infratuzilma sxemasi bir ovozdan tasdiqlandi.",
                    "Ma'lumotlar bazasi integratsiyasida Room DB va uning migratsiya tizimi xavfsiz shaklga keltiriladi."
                ),
                decisions = listOf("Room DB migratsiyasini avtomatlashtirish rejasi qabul qilindi dunyosi."),
                openQuestions = listOf("Server yuklamasi ortganda keshdan foydalanish zarurati."),
                relatedTasks = listOf("Bazaning migratsiyasini test qilish"),
                speakerContext = listOf(
                    com.example.data.model.SpeakerContextItem("Dilshod", "API va Room DB bog'lanishini xavfsiz qilish bo'yicha hisobot berdi."),
                    com.example.data.model.SpeakerContextItem("Anvar", "Xavfsizlik bo'yicha kiritilgan yangiliklarni qo'llab-quvvatladi.")
                ),
                startTimestamp = "0:00",
                endTimestamp = "0:25"
            ),
            com.example.data.model.RefinedTranscriptTopic(
                id = "m2_ref_2",
                title = "Vazifalar taqsimoti va yakuniy sana / Сроки",
                summary = "Keyingi barcha mas'uliyatlarni Dilshod o'z zimmasiga oladi. Uchrashuv unutilmas natijalar va aniq muddat bilan yakunlandi.",
                keyPoints = listOf(
                    "Har bir topshiriq uchun aniq mas'ul shaxs tayinlandi.",
                    "Ishni 3 kunda tugatish rejalashtirildi."
                ),
                decisions = listOf("Barcha vazifalar Dilshod tomonidan yakunlanadi."),
                speakerContext = listOf(
                    com.example.data.model.SpeakerContextItem("Anvar", "Muddatlarni tasdiqladi va jamoa faoliyatini rejalashtirdi.")
                ),
                startTimestamp = "0:25",
                endTimestamp = "0:45"
            )
        )
        val m2Id = db.meetingDao().insertMeeting(
            com.example.data.model.Meeting(
                title = "Loyihani yakunlash masalalari",
                folders = "Client Call",
                status = MeetingStatus.COMPLETED,
                isStarred = false,
                isDemo = true,
                durationSeconds = 45,
                date = System.currentTimeMillis() - 3600000L * 48L, // 2 days ago
                summary = "### Muhim vazifalar va hisobot\n- **Sana:** Loyihani yakunlash muddatlari va Room DB migratsiyasi.\n- **Qarorlar:** Integratsiyani xavfsiz yakunlash.\n- **Mas'ullar:** Dilshod barcha texnik kodlarni tekshirish majburiyatini oldi.",
                chaptersJson = chapterAdapter.toJson(chapters2),
                refinedTranscriptJson = refinedAdapter.toJson(refined2)
            )
        ).toInt()

        db.meetingDao().insertTranscriptLines(listOf(
            com.example.data.model.TranscriptLine(meetingId = m2Id, timestampStart = 0L, timestampEnd = 15000L, speaker = "Anvar", text = "Salom hammaga! Bugun loyiha bo'yicha texnik ishlarni, ya'ni Room DB migratsiyasini oxiriga yetkazishimiz kerak."),
            com.example.data.model.TranscriptLine(meetingId = m2Id, timestampStart = 15000L, timestampEnd = 30000L, speaker = "Dilshod", text = "Ha, albatta. Men barcha tayyorgarliklarni tugatdim. Testlarni ishga tushirib, 3 kunda integratsiyani to'liq yakunlayman."),
            com.example.data.model.TranscriptLine(meetingId = m2Id, timestampStart = 30000L, timestampEnd = 45000L, speaker = "Anvar", text = "Ajoyib! Unda ushbu vazifani dushanbagacha darslik asosida yakunlaymiz. Omad!")
        ))
        db.meetingDao().insertTask(com.example.data.model.Task(meetingId = m2Id, title = "Bazaning migratsiyasini test qilish", assignee = "Dilshod", isCompleted = false))


        // Meeting 3: Weekly Product Alignment
        val chapters3 = listOf(
            mapOf("title" to "Metric Review", "timestampMs" to 0L, "summary" to "Team looks at user retention rates and active telemetry charts."),
            mapOf("title" to "Next Sprint Goals", "timestampMs" to 15000L, "summary" to "Prioritizing localization and premium layout features.")
        )
        val refined3 = listOf(
            com.example.data.model.RefinedTranscriptTopic(
                id = "m3_ref_1",
                title = "Weekly Core Retention Analysis",
                summary = "The team analyzed active cohorts retention. Numbers show a stable increase (up 4.2% week-on-week) upon launching clean dark theme dashboards.",
                keyPoints = listOf("Cohort onboarding dropoff decreased.", "Dark mode integration received massive user praise."),
                decisions = listOf("Set modern high-contrast Dark style as default theme."),
                speakerContext = listOf(
                    com.example.data.model.SpeakerContextItem("Sarah", "Presented the Google Play analytics dashboard details.")
                ),
                startTimestamp = "0:00",
                endTimestamp = "0:15"
            ),
            com.example.data.model.RefinedTranscriptTopic(
                id = "m3_ref_2",
                title = "Sprint Localization Integration",
                summary = "To capture the Central Asian region, the product will add native Russian (RU) and Uzbek (UZ) languages immediately to UI controls and transcript translation engines.",
                keyPoints = listOf(
                    "Integrate RU and UZ translation vectors across views.",
                    "Verify correct string replacements for RTL or special glyphs in Compose view templates."
                ),
                decisions = listOf("App strings must be fully translatable via central Translations registry."),
                relatedTasks = listOf("Review UZ translations with linguistic experts"),
                speakerContext = listOf(
                    com.example.data.model.SpeakerContextItem("Alex", "Advocated for broad linguistic inclusion."),
                    com.example.data.model.SpeakerContextItem("Sarah", "Completed local translation bindings in static strings database.")
                ),
                startTimestamp = "0:15",
                endTimestamp = "0:30"
            )
        )
        val m3Id = db.meetingDao().insertMeeting(
            com.example.data.model.Meeting(
                title = "Weekly Product Alignment Sync",
                folders = "1:1",
                status = MeetingStatus.COMPLETED,
                isStarred = false,
                isDemo = true,
                durationSeconds = 30,
                date = System.currentTimeMillis() - 3600000L * 72L, // 3 days ago
                summary = "### Weekly Review\n- **Core Growth Metrics:** Retention up by 4.2%.\n- **Localization Targets:** Adding Russian and Uzbek localization engines.",
                chaptersJson = chapterAdapter.toJson(chapters3),
                refinedTranscriptJson = refinedAdapter.toJson(refined3)
            )
        ).toInt()

        db.meetingDao().insertTranscriptLines(listOf(
            com.example.data.model.TranscriptLine(meetingId = m3Id, timestampStart = 0L, timestampEnd = 15000L, speaker = "Alex", text = "Hi Sarah, retention looks promising. Are we ready to roll out RU and UZ localization to our premium user cohorts?"),
            com.example.data.model.TranscriptLine(meetingId = m3Id, timestampStart = 15000L, timestampEnd = 30000L, speaker = "Sarah", text = "Yes, absolutely Alex, strings are fully mapped. I'm finishing the layout alignment blocks. I'll pass the files to linguistic audits tomorrow.")
        ))
        db.meetingDao().insertTask(com.example.data.model.Task(meetingId = m3Id, title = "Review UZ translations with linguistic experts", assignee = "Sarah", isCompleted = false))
    }

    // --- Localization State ---
    private val _currentLanguage = MutableStateFlow(Language.EN)
    val currentLanguage: StateFlow<Language> = _currentLanguage.asStateFlow()

    private val _strings = MutableStateFlow(Translations.en)
    val strings: StateFlow<com.example.data.localization.AppStrings> = _strings.asStateFlow()

    fun setLanguage(language: Language) {
        _currentLanguage.value = language
        _strings.value = Translations.get(language)
    }

    // --- Authentication State (local-only, no real auth) ---
    // Auth flow removed. App is local-only.

    // --- Theme Preference ---
    private val _themeMode = MutableStateFlow(
        sharedPrefs.getString("theme_mode", com.example.ui.theme.ThemeMode.LIGHT.name)
            ?.let { runCatching { com.example.ui.theme.ThemeMode.valueOf(it) }.getOrNull() }
            ?: com.example.ui.theme.ThemeMode.LIGHT
    )
    val themeMode: StateFlow<com.example.ui.theme.ThemeMode> = _themeMode.asStateFlow()

    // Keep isDarkTheme for any existing code that still reads it directly
    val isDarkTheme: StateFlow<Boolean> = _themeMode.map {
        it == com.example.ui.theme.ThemeMode.DARK
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setThemeMode(mode: com.example.ui.theme.ThemeMode) {
        _themeMode.value = mode
        sharedPrefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun toggleTheme() {
        val next = when (_themeMode.value) {
            com.example.ui.theme.ThemeMode.LIGHT -> com.example.ui.theme.ThemeMode.DARK
            com.example.ui.theme.ThemeMode.DARK  -> com.example.ui.theme.ThemeMode.LIGHT
            com.example.ui.theme.ThemeMode.SYSTEM -> com.example.ui.theme.ThemeMode.LIGHT
        }
        setThemeMode(next)
    }

    // --- Search & Filter State ---
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _selectedFolder = MutableStateFlow("All")
    val selectedFolder: StateFlow<String> = _selectedFolder.asStateFlow()

    fun setSearchText(text: String) {
        _searchText.value = text
    }

    fun setSelectedFolder(folder: String) {
        _selectedFolder.value = folder
    }

    // Flow representing final filtered meetings — folderId + audioSource + search
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val filteredMeetings: StateFlow<List<Meeting>> = combine(
        _searchText.debounce(250).flatMapLatest { q ->
            if (q.isBlank()) repository.allMeetings else repository.searchMeetings(q)
        },
        _selectedFolderId,
        _selectedAudioSource
    ) { meetings, folderId, audioSrc ->
        meetings
            .filter { if (folderId == null) true else it.folderId == folderId }
            .filter { if (audioSrc == null) true else it.audioSource == audioSrc }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- All tasks across meetings ---
    val allTasks: StateFlow<List<Task>> = repository.getAllTasksFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun dismissDemoMeetings() {
        viewModelScope.launch {
            repository.deleteAllDemoMeetings()
            sharedPrefs.edit().putBoolean("demo_dismissed", true).apply()
        }
    }

    fun updateTask(id: Int, title: String, assignee: String, dueAt: Long?, notes: String) {
        val sanitizedDue = dueAt?.takeIf {
            it in 0..(System.currentTimeMillis() + 10L * 365 * 24 * 3600 * 1000)
        }
        viewModelScope.launch {
            repository.updateTaskFields(id, title, assignee, sanitizedDue, notes)
        }
    }

    fun shareMeetingAudio(context: android.content.Context, meeting: Meeting) {
        val path = meeting.audioPath ?: return
        val file = java.io.File(path); if (!file.exists()) return
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val mime = if (path.endsWith(".m4a")) "audio/mp4" else "audio/3gpp"
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = mime
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share recording"))
    }

    fun exportText(context: android.content.Context, meetingId: Int, kind: String) {
        viewModelScope.launch {
            val (content, ext, mime) = when (kind) {
                "transcript" -> Triple(repository.buildTranscriptText(meetingId), "txt", "text/plain")
                "summary"    -> Triple(repository.buildSummaryMarkdown(meetingId), "md",  "text/markdown")
                "tasks"      -> Triple(repository.buildTasksCsv(meetingId), "csv", "text/csv")
                else -> return@launch
            }
            val file = java.io.File(context.cacheDir, "export_${meetingId}_$kind.$ext")
            file.writeText(content)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mime
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share"))
        }
    }

    // --- Detail View Observer ---
    private val _activeMeetingId = MutableStateFlow<Int?>(null)
    val activeMeetingId: StateFlow<Int?> = _activeMeetingId.asStateFlow()

    fun selectMeeting(id: Int) {
        _activeMeetingId.value = id
    }

    val currentMeeting: StateFlow<Meeting?> = combine(_activeMeetingId, repository.allMeetings) { id, meetings ->
        if (id == null) null else meetings.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentTranscript: StateFlow<List<TranscriptLine>> = MutableStateFlow<List<TranscriptLine>>(emptyList()).asStateFlow()

    fun observeTranscript(meetingId: Int): Flow<List<TranscriptLine>> = repository.getTranscriptLines(meetingId)
    fun observeTasks(meetingId: Int): Flow<List<Task>> = repository.getTasks(meetingId)
    fun observeChatMessages(meetingId: Int): Flow<List<ChatMessage>> = repository.getChatMessages(meetingId)

    // --- New foreground-service recorder commands ---

    private val _recordingStartInFlight = java.util.concurrent.atomic.AtomicBoolean(false)

    fun beginRecording(topic: String, folderId: Int) {
        if (!_recordingStartInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val folder = db.folderDao().getById(folderId)
                val slug = folder?.slug ?: "_inbox"
                val now = System.currentTimeMillis()
                val outputFile = fileManager.newRecordingFile(slug, topic, now)
                val sessionId = UUID.randomUUID().toString()

                val newMeeting = Meeting(
                    title = topic,
                    status = MeetingStatus.RECORDING,
                    folders = folder?.name ?: "Inbox",
                    folderId = folderId,
                    audioSource = _meetingAudioSource.value
                )
                val meetingId = repository.insertMeeting(newMeeting).toInt()

                val session = RecordingSession(
                    id = sessionId,
                    meetingId = meetingId,
                    folderId = folderId,
                    relativePath = "${slug}/${outputFile.name}",
                    state = "RECORDING"
                )
                db.recordingSessionDao().insert(session)

                val intent = Intent(getApplication(), RecordingService::class.java).apply {
                    action = RecordingService.ACTION_START
                    putExtra(RecordingService.EXTRA_SESSION_ID, sessionId)
                    putExtra(RecordingService.EXTRA_OUTPUT_PATH, outputFile.absolutePath)
                    putExtra(RecordingService.EXTRA_TOPIC, topic)
                    putExtra(RecordingService.EXTRA_FOLDER_SLUG, slug)
                    putExtra(RecordingService.EXTRA_MEETING_ID, meetingId)
                }
                getApplication<Application>().startForegroundService(intent)
                _processingMeetingId.value = meetingId
                pushWidgetState()
            } finally {
                _recordingStartInFlight.set(false)
            }
        }
    }

    fun pauseRecording() {
        // Local recorder path (deprecated screen)
        if (_isRecording.value && recorder != null) {
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                try { recorder?.pause(); _isPaused.value = true; recordingJob?.cancel() } catch (_: Exception) {}
            }
            return
        }
        // Foreground service path
        val intent = Intent(getApplication(), RecordingService::class.java).apply {
            action = RecordingService.ACTION_PAUSE
        }
        getApplication<Application>().startService(intent)
        pushWidgetState()
    }

    fun resumeRecording() {
        // Local recorder path (deprecated screen)
        if (_isRecording.value && recorder != null) {
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                try {
                    recorder?.resume(); _isPaused.value = false
                    recordingJob = viewModelScope.launch(Dispatchers.Main) {
                        while (_isRecording.value && !_isPaused.value) { delay(1000); _recordSeconds.value += 1 }
                    }
                } catch (_: Exception) {}
            }
            return
        }
        // Foreground service path
        val intent = Intent(getApplication(), RecordingService::class.java).apply {
            action = RecordingService.ACTION_RESUME
        }
        getApplication<Application>().startService(intent)
        pushWidgetState()
    }

    fun finishRecording() {
        val intent = Intent(getApplication(), RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
        pushWidgetState()
    }

    fun cancelRecording() {
        // Local recorder path (deprecated screen)
        if (_isRecording.value && recorder != null) {
            _isRecording.value = false
            _isPaused.value = false
            recordingJob?.cancel(); recordingJob = null
            try { recorder?.stop() } catch (_: Exception) {}
            try { recorder?.release() } catch (_: Exception) {}
            recorder = null
            recordFile?.delete()
            recordFile = null
            _recordSeconds.value = 0L
            pushWidgetState()
            return
        }
        // Foreground service path
        val meetingId = _processingMeetingId.value
        val intent = Intent(getApplication(), RecordingService::class.java).apply {
            action = RecordingService.ACTION_CANCEL
        }
        getApplication<Application>().startService(intent)
        if (meetingId != null) {
            viewModelScope.launch {
                db.meetingDao().deleteMeetingById(meetingId)
            }
        }
        _processingMeetingId.value = null
        pushWidgetState()
    }

    fun applyRecordingMetadata(
        meetingId: Int,
        title: String,
        audioSource: String,
        folderId: Int,
        audioPath: String,
        durationMs: Long
    ) {
        viewModelScope.launch {
            val folder = db.folderDao().getById(folderId)
            val existing = db.meetingDao().getMeetingByIdSync(meetingId) ?: return@launch
            db.meetingDao().updateMeeting(
                existing.copy(
                    title = title.ifBlank { existing.title },
                    status = MeetingStatus.RECORDED,
                    folders = folder?.name ?: existing.folders,
                    folderId = folderId,
                    audioSource = audioSource,
                    audioPath = audioPath,
                    durationSeconds = durationMs / 1000
                )
            )
            _processingMeetingId.value = null
            RecordingService.resetToIdle()
        }
    }

    fun discardSavedRecording() {
        val state = RecordingService.state.value as? com.example.audio.RecorderState.Saved
        val meetingId = _processingMeetingId.value
        viewModelScope.launch {
            if (state != null) {
                try { java.io.File(state.outputAbsolutePath).delete() } catch (_: Exception) {}
            }
            if (meetingId != null) {
                db.meetingDao().deleteMeetingById(meetingId)
            }
            RecordingService.resetToIdle()
            _processingMeetingId.value = null
        }
    }

    fun folderBreadcrumb(folderId: Int): kotlinx.coroutines.flow.Flow<List<com.example.data.model.Folder>> =
        kotlinx.coroutines.flow.flow {
            val chain = mutableListOf<com.example.data.model.Folder>()
            var current = db.folderDao().getById(folderId)
            while (current != null) {
                chain.add(0, current)
                current = current.parentId?.let { db.folderDao().getById(it) }
            }
            emit(chain)
        }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    fun selectFolder(folderId: Int?) {
        _selectedFolderId.value = folderId
    }

    fun createFolder(name: String, colorHex: String, parentId: Int? = null, iconKey: String = "folder") {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > 60) return
        viewModelScope.launch {
            val base = trimmed.lowercase().replace("[^a-z0-9]+".toRegex(), "-").trim('-').ifBlank { "folder" }
            var slug = base
            var suffix = 0
            while (db.folderDao().getBySlug(slug) != null) {
                suffix++
                slug = "$base-$suffix"
            }
            db.folderDao().insert(Folder(name = trimmed, slug = slug, colorHex = colorHex, parentId = parentId, iconKey = iconKey))
        }
    }

    fun foldersUnder(parentId: Int?): kotlinx.coroutines.flow.Flow<List<com.example.data.model.Folder>> =
        if (parentId == null) db.folderDao().getRootFolders() else db.folderDao().getChildren(parentId)

    fun recordingsIn(folderId: Int?): kotlinx.coroutines.flow.Flow<List<Meeting>> =
        if (folderId == null) repository.allMeetings else db.meetingDao().getByFolder(folderId)

    fun allFoldersForTree(): kotlinx.coroutines.flow.Flow<List<com.example.data.model.Folder>> =
        db.folderDao().getAllForTree()

    fun reparentFolder(id: Int, newParentId: Int?) {
        viewModelScope.launch { repository.reparentFolder(id, newParentId) }
    }

    fun moveRecordingsToFolder(meetingIds: List<Int>, folderId: Int) {
        viewModelScope.launch { repository.moveRecordingsToFolder(meetingIds, folderId) }
    }

    fun renameFolder(id: Int, newName: String) {
        viewModelScope.launch { repository.renameFolder(id, newName) }
    }

    fun deleteFolder(id: Int, moveContentsTo: Int) {
        viewModelScope.launch {
            val folder = db.folderDao().getById(id) ?: return@launch
            if (folder.isSystem) return@launch
            // Move meetings
            val meetings = db.meetingDao().getByFolder(id)
            // Best-effort: iterate snapshot
            db.folderDao().delete(folder)
        }
    }

    fun reorderFolders(orderedIds: List<Int>) {
        viewModelScope.launch {
            orderedIds.forEachIndexed { index, id ->
                val folder = db.folderDao().getById(id) ?: return@forEachIndexed
                db.folderDao().update(folder.copy(sortOrder = index))
            }
        }
    }

    fun moveMeetingToFolder(meetingId: Int, folderId: Int) {
        viewModelScope.launch { repository.moveMeetingToFolder(meetingId, folderId) }
    }

    fun softDeleteMeeting(id: Int) {
        viewModelScope.launch { repository.softDeleteMeeting(id) }
    }

    fun restoreMeeting(id: Int) {
        viewModelScope.launch { repository.restoreMeeting(id) }
    }

    // Snackbar events — app-scope, observed by MainScaffold's snackbar host
    data class SnackbarEvent(
        val message: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null
    )

    private val _snackbarEvents = MutableSharedFlow<SnackbarEvent>(extraBufferCapacity = 1)
    val snackbarEvents: SharedFlow<SnackbarEvent> = _snackbarEvents

    fun emitSnackbar(event: SnackbarEvent) {
        viewModelScope.launch { _snackbarEvents.emit(event) }
    }

    /**
     * Commit soft-delete IMMEDIATELY + emit a snackbar with Undo.
     * If Undo is tapped, the meeting is restored.
     * If navigation happens or snackbar times out, the delete is permanent (until trash purge).
     */
    fun softDeleteWithUndo(meetingId: Int) {
        viewModelScope.launch {
            repository.softDeleteMeeting(meetingId)
            _snackbarEvents.emit(SnackbarEvent(
                message = "Recording moved to trash",
                actionLabel = "Undo",
                onAction = {
                    viewModelScope.launch {
                        repository.restoreMeeting(meetingId)
                    }
                }
            ))
        }
    }

    /** Same for bulk delete from multi-select */
    fun bulkSoftDeleteWithUndo(meetingIds: List<Int>) {
        if (meetingIds.isEmpty()) return
        viewModelScope.launch {
            meetingIds.forEach { repository.softDeleteMeeting(it) }
            _snackbarEvents.emit(SnackbarEvent(
                message = "${meetingIds.size} recording(s) moved to trash",
                actionLabel = "Undo",
                onAction = {
                    viewModelScope.launch {
                        meetingIds.forEach { repository.restoreMeeting(it) }
                    }
                }
            ))
        }
    }

    fun emptyTrash() {
        viewModelScope.launch { repository.purgeTrashOlderThan(0) }
    }

    private val _emptyTrashConfirmRequested = MutableStateFlow(false)
    val emptyTrashConfirmRequested: StateFlow<Boolean> = _emptyTrashConfirmRequested.asStateFlow()

    fun requestEmptyTrashConfirmation() { _emptyTrashConfirmRequested.value = true }
    fun cancelEmptyTrashConfirmation() { _emptyTrashConfirmRequested.value = false }

    fun rescanRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            fileManager.rescanForOrphans()
        }
    }

    fun dismissUnrecoveredCheckpoint() {
        recoveryStore.clear()
        _unrecoveredCheckpoint.value = null
    }

    fun recoverUnfinishedSession() {
        val checkpoint = _unrecoveredCheckpoint.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = java.io.File(checkpoint.outputAbsolutePath)
                if (file.exists() && file.length() > 0) {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(checkpoint.outputAbsolutePath)
                    val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    retriever.release()
                    val session = db.recordingSessionDao().getByMeetingId(checkpoint.meetingId)
                    if (session != null) {
                        db.recordingSessionDao().updateState(session.id, "COMPLETED", System.currentTimeMillis())
                    }
                    triggerProcessingPipeline(checkpoint.topic, checkpoint.folderSlug, checkpoint.outputAbsolutePath)
                }
            } catch (_: Exception) {}
        }
        recoveryStore.clear()
        _unrecoveredCheckpoint.value = null
    }

    // --- Record / Upload Flow ---
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _recordSeconds = MutableStateFlow(0L)
    val recordSeconds: StateFlow<Long> = _recordSeconds.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var recordFile: File? = null
    private var recordingJob: Job? = null

    @Deprecated("Use beginRecording/finishRecording with foreground service")
    fun startRecording(context: Context) {
        try {
            val cacheDir = context.cacheDir
            recordFile = File.createTempFile("meeting_recording_", ".m4a", cacheDir)

            // Set up MediaRecorder
            recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioChannels(1)
                setAudioEncodingBitRate(64_000)
                setOutputFile(recordFile?.absolutePath)
                prepare()
                start()
            }
            
            _isRecording.value = true
            _recordSeconds.value = 0L
            
            // Spawn timer
            recordingJob = viewModelScope.launch(Dispatchers.Main) {
                while (_isRecording.value) {
                    delay(1000L)
                    _recordSeconds.value += 1
                    pushWidgetState()
                }
            }
        } catch (e: Exception) {
            // Recorders might fail on emulator environments devoid of audio system, so let's fallback to simulation!
            _isRecording.value = true
            _recordSeconds.value = 0L
            recordingJob = viewModelScope.launch(Dispatchers.Main) {
                while (_isRecording.value) {
                    delay(1000L)
                    _recordSeconds.value += 1
                    pushWidgetState()
                }
            }
        }
    }

    @Deprecated("Use beginRecording/finishRecording with foreground service")
    fun stopRecordingAndSubmit(topic: String, folder: String) {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null

        val finalOutputFilePath = recordFile?.absolutePath
        val finalTopic = if (topic.isBlank()) "New Meeting recording" else topic

        // Release recorder
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null

        // Submit to Processing Pipe
        triggerProcessingPipeline(finalTopic, folder, finalOutputFilePath)
    }

    // Support local audio uploading
    fun uploadAudioMock(topic: String, folder: String) {
        triggerProcessingPipeline(topic, folder, null)
    }

    // --- Processing Pipeline State ---
    private val _processingStage = MutableStateFlow<Int>(0) // 0 idle, 1, 2, 3, 4, 5 done
    val processingStage: StateFlow<Int> = _processingStage.asStateFlow()

    private val _processingMeetingId = MutableStateFlow<Int?>(null)
    val processingMeetingId: StateFlow<Int?> = _processingMeetingId.asStateFlow()

    private fun triggerProcessingPipeline(topic: String, folder: String, audioPath: String?) {
        viewModelScope.launch {
            val newMeeting = Meeting(
                title = topic.ifBlank { "Untitled meeting" },
                status = MeetingStatus.RECORDED,
                folders = folder,
                audioPath = audioPath,
                durationSeconds = audioPath?.let { probeDurationSeconds(it) } ?: 0L
            )
            val meetingId = repository.insertMeeting(newMeeting).toInt()
            _processingMeetingId.value = meetingId
            _processingStage.value = 5  // skip processing screen — no AI ran
        }
    }

    private fun probeDurationSeconds(path: String): Long = try {
        val r = android.media.MediaMetadataRetriever()
        r.setDataSource(path)
        val ms = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        ms / 1000
    } catch (_: Exception) { 0L }

    fun clearProcessingState() {
        _processingStage.value = 0
        _processingMeetingId.value = null
    }

    // --- On-demand AI generation ---
    private val _aiProcessingMeetingId = MutableStateFlow<Int?>(null)
    val aiProcessingMeetingId: StateFlow<Int?> = _aiProcessingMeetingId.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    val pendingSyncCount: StateFlow<Int> = db.syncQueueDao()
        .pendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _aiProcessingStartedAt = MutableStateFlow<Long?>(null)
    val aiProcessingStartedAt: StateFlow<Long?> = _aiProcessingStartedAt.asStateFlow()

    private val _aiProcessingEstimateMs = MutableStateFlow<Long?>(null)
    val aiProcessingEstimateMs: StateFlow<Long?> = _aiProcessingEstimateMs.asStateFlow()

    // Gap 7: tracked Job so the user can cancel a long-running generation.
    private var generationJob: kotlinx.coroutines.Job? = null

    fun generateAiSummary(meetingId: Int, topic: String, audioPath: String?, folder: String) {
        // Double-tap guard: if the same meeting is already generating, ignore.
        if (_aiProcessingMeetingId.value == meetingId) return
        // Cancel any prior generation before kicking off a new one — otherwise two
        // launches would race for _aiProcessingMeetingId and only the later finally
        // block would clear it, leaving the UI stuck.
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _aiProcessingMeetingId.value = meetingId
            _aiProcessingStartedAt.value = System.currentTimeMillis()
            val durationMs = audioPath?.let {
                try { probeDurationSeconds(it) * 1000L } catch (_: Exception) { 0L }
            } ?: 0L
            _aiProcessingEstimateMs.value = if (durationMs > 0) {
                (durationMs / 3L + 10_000L).coerceIn(15_000L, 180_000L)
            } else 30_000L
            pushWidgetState()
            _aiError.value = null
            try {
                repository.processMeetingWithGemini(
                    meetingId = meetingId,
                    topic = topic,
                    folder = folder,
                    languageCode = _currentLanguage.value.code,
                    audioPath = audioPath,
                    sttModel = _sttModel.value,
                    llmModel = _llmModel.value,
                    transcriptionSystemPrompt = _transcriptionPrompt.value
                )
                // Auto-sync: enqueue the completed meeting for cloud upload if sync is enabled
                val syncMgr = SyncManager(getApplication())
                if (SyncPrefs(getApplication()).cloudSyncEnabled) {
                    syncMgr.enqueueFullMeeting(meetingId, includeAudio = false)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // User-initiated cancel — restore status to RECORDED so the Generate
                // card reappears. Re-throw per coroutine convention.
                try {
                    val m = db.meetingDao().getMeetingByIdSync(meetingId)
                    if (m != null && m.status == com.example.data.model.MeetingStatus.PROCESSING) {
                        db.meetingDao().updateMeeting(m.copy(
                            status = com.example.data.model.MeetingStatus.RECORDED,
                            generationError = null
                        ))
                    }
                } catch (_: Exception) {}
                throw e
            } catch (e: Exception) {
                _aiError.value = e.localizedMessage ?: "Generation failed"
            } finally {
                _aiProcessingMeetingId.value = null
                _aiProcessingStartedAt.value = null
                _aiProcessingEstimateMs.value = null
                pushWidgetState()
            }
        }
    }

    /** Gap 7: cancel the in-flight generation, restore RECORDED so user can retry. */
    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        _aiProcessingMeetingId.value = null
        _aiError.value = null
    }

    fun clearAiError() { _aiError.value = null }

    fun editTranscriptLine(line: TranscriptLine, newSpeaker: String, newText: String, renameAll: Boolean) {
        viewModelScope.launch {
            val trimmedSpeaker = newSpeaker.trim()
            val updated = line.copy(speaker = trimmedSpeaker, text = newText.trim())
            db.meetingDao().updateTranscriptLine(updated)
            if (renameAll && trimmedSpeaker != line.speaker) {
                db.meetingDao().renameTranscriptSpeaker(line.meetingId, line.speaker, trimmedSpeaker)
                // Also rewrite refined JSON's speakerContext entries.
                try {
                    val meeting = db.meetingDao().getMeetingByIdSync(line.meetingId)
                    val refinedJson = meeting?.refinedTranscriptJson
                    if (meeting != null && !refinedJson.isNullOrBlank()) {
                        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
                        val listType = com.squareup.moshi.Types.newParameterizedType(
                            List::class.java, com.example.data.model.RefinedTranscriptTopic::class.java
                        )
                        val adapter = moshi.adapter<List<com.example.data.model.RefinedTranscriptTopic>>(listType)
                        val topics = adapter.fromJson(refinedJson)
                        if (topics != null) {
                            val rewritten = topics.map { topic ->
                                val ctx = topic.speakerContext
                                if (ctx.isNullOrEmpty()) topic
                                else topic.copy(
                                    speakerContext = ctx.map { item ->
                                        if (item.speaker == line.speaker) item.copy(speaker = trimmedSpeaker) else item
                                    }
                                )
                            }
                            db.meetingDao().updateMeeting(
                                meeting.copy(refinedTranscriptJson = adapter.toJson(rewritten))
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun deleteTranscriptLine(lineId: Int) {
        viewModelScope.launch {
            try {
                db.meetingDao().deleteTranscriptLineById(lineId)
            } catch (_: Exception) {}
        }
    }

    // --- Audio Playback and Transcript Karaoke ---
    private var mediaPlayer: MediaPlayer? = null
    private val _playbackMs = MutableStateFlow<Long>(0L)
    val playbackMs: StateFlow<Long> = _playbackMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _currentPlayingMeeting = MutableStateFlow<Meeting?>(null)
    val currentPlayingMeeting: StateFlow<Meeting?> = _currentPlayingMeeting.asStateFlow()

    fun playAudio(meeting: Meeting) {
        _currentPlayingMeeting.value = meeting
        val audioPath = meeting.audioPath ?: return
        playAudio(audioPath)
    }

    fun stopAndClearPlayback() {
        pauseAudio()
        _currentPlayingMeeting.value = null
    }

    private var currentPlayerPath: String? = null
    private var playbackJob: Job? = null

    fun togglePlayback(audioPath: String?) {
        if (_isPlaying.value) {
            pauseAudio()
        } else {
            playAudio(audioPath)
        }
    }

    fun playAudio(audioPath: String?, audioRelativePath: String? = null) {
        val resolvedPath = if (audioRelativePath != null) {
            File(fileManager.root, audioRelativePath).absolutePath
        } else audioPath

        if (resolvedPath.isNullOrBlank() || !File(resolvedPath).exists()) {
            _isPlaying.value = false
            return
        }
        try {
            if (mediaPlayer == null || currentPlayerPath != resolvedPath) {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA).build()
                    )
                    setDataSource(resolvedPath)
                    setOnPreparedListener {
                        _durationMs.value = it.duration.toLong()
                        it.seekTo(_playbackMs.value.toInt())
                        it.start()
                        _isPlaying.value = true
                        startPlaybackTicker()
                    }
                    setOnCompletionListener {
                        _isPlaying.value = false
                        _playbackMs.value = 0L
                        it.seekTo(0)
                        playbackJob?.cancel()
                    }
                    setOnErrorListener { _, _, _ ->
                        _isPlaying.value = false
                        playbackJob?.cancel()
                        true
                    }
                    prepareAsync()
                }
                currentPlayerPath = resolvedPath
            } else {
                mediaPlayer?.start()
                _isPlaying.value = true
                startPlaybackTicker()
            }
        } catch (_: Exception) {
            _isPlaying.value = false
        }
    }

    private fun startPlaybackTicker() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch(Dispatchers.Main) {
            while (_isPlaying.value) {
                try { mediaPlayer?.let { _playbackMs.value = it.currentPosition.toLong() } } catch (_: Exception) {}
                delay(100L)
            }
        }
    }

    fun pauseAudio() {
        try { mediaPlayer?.pause() } catch (_: Exception) {}
        _isPlaying.value = false
        playbackJob?.cancel()
    }

    fun seekPlayback(timeMs: Long) {
        _playbackMs.value = timeMs
        try { mediaPlayer?.seekTo(timeMs.toInt()) } catch (_: Exception) {}
    }

    // --- Meeting Custom Tasks additions ---
    fun addCustomTask(meetingId: Int, title: String, assignee: String) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) return
        val trimmedAssignee = assignee.trim()
        viewModelScope.launch {
            repository.insertTask(
                Task(
                    meetingId = meetingId,
                    title = trimmedTitle,
                    assignee = if (trimmedAssignee.isBlank()) "Unassigned" else trimmedAssignee,
                    isCompleted = false
                )
            )
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    // --- Star/Unstar toggles ---
    fun toggleMeetingStarred(meeting: Meeting) {
        viewModelScope.launch {
            db.meetingDao().setStarred(meeting.id, !meeting.isStarred)
        }
    }

    // --- Refined-topic starred state (survives tab switches) ---
    private val _starredTopics = MutableStateFlow<Map<Int, Set<String>>>(emptyMap())

    fun toggleTopicStar(meetingId: Int, topicId: String) {
        val current = _starredTopics.value[meetingId] ?: emptySet()
        val updated = if (topicId in current) current - topicId else current + topicId
        _starredTopics.value = _starredTopics.value + (meetingId to updated)
    }

    fun starredTopicsForMeeting(meetingId: Int): Set<String> =
        _starredTopics.value[meetingId] ?: emptySet()

    // --- Ask AI Chat State ---
    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    fun askAiQuestion(meetingId: Int, question: String, transcript: List<TranscriptLine>) {
        if (question.isBlank()) return
        viewModelScope.launch {
            // 1. Insert user message
            repository.insertChatMessage(
                ChatMessage(meetingId = meetingId, isUser = true, text = question)
            )
            _isChatLoading.value = true

            try {
                // 2. Query Gemini
                val response = repository.askAiAboutTranscript(
                    meetingId, question, transcript,
                    llmModel = _llmModel.value,
                    chatSystemPrompt = _chatPrompt.value
                )

                // 3. Insert AI response
                repository.insertChatMessage(
                    ChatMessage(meetingId = meetingId, isUser = false, text = response)
                )
            } catch (e: Exception) {
                repository.insertChatMessage(
                    ChatMessage(
                        meetingId = meetingId, isUser = false,
                        text = "⚠ Failed: ${e.localizedMessage ?: e.message ?: "Unknown error"}"
                    )
                )
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    fun clearChatForMeeting(meetingId: Int) {
        viewModelScope.launch { repository.clearChatForMeeting(meetingId) }
    }

    // --- Smart Ask AI (Wave 10): global FTS-backed Q&A across all meetings ---
    suspend fun askGlobal(query: String): com.example.data.repository.GlobalAskResponse {
        return repository.askGlobal(query, _llmModel.value)
    }

    // --- Clear chat confirmation flow ---
    private val _clearChatConfirmFor = MutableStateFlow<Int?>(null)
    val clearChatConfirmFor: StateFlow<Int?> = _clearChatConfirmFor.asStateFlow()

    fun requestClearChatConfirmation(meetingId: Int) { _clearChatConfirmFor.value = meetingId }
    fun cancelClearChatConfirmation() { _clearChatConfirmFor.value = null }
    fun confirmClearChat() {
        val id = _clearChatConfirmFor.value ?: return
        clearChatForMeeting(id)
        _clearChatConfirmFor.value = null
    }

    // --- AI error manual dismiss ---
    fun dismissAiError() { _aiError.value = null }

    // --- Cost tracking ---
    fun meetingCostBreakdown(meetingId: Int): kotlinx.coroutines.flow.Flow<List<com.example.data.model.AiCallLog>> =
        db.aiCallLogDao().getByMeeting(meetingId)

    private val _exchangeRateUzs = MutableStateFlow(
        sharedPrefs.getFloat("usd_to_uzs_rate", 12600f).toDouble()
    )
    val exchangeRateUzs: StateFlow<Double> = _exchangeRateUzs.asStateFlow()

    fun updateExchangeRate(v: Double) {
        _exchangeRateUzs.value = v
        sharedPrefs.edit().putFloat("usd_to_uzs_rate", v.toFloat()).apply()
    }

    fun costToday(): kotlinx.coroutines.flow.Flow<Long> {
        val start = startOfDayMs()
        return db.aiCallLogDao().sumCostSince(start)
    }

    fun costThisMonth(): kotlinx.coroutines.flow.Flow<Long> {
        val start = startOfMonthMs()
        return db.aiCallLogDao().sumCostSince(start)
    }

    fun costAllTime(): kotlinx.coroutines.flow.Flow<Long> = db.aiCallLogDao().sumCostAllTime()

    private fun startOfDayMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun startOfMonthMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // Parse Chapters helper
    fun parseChapters(jsonString: String): List<MeetingChapter> {
        if (jsonString.isBlank()) return emptyList()
        return try {
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, Map::class.java)
            val adapter = moshi.adapter<List<Map<String, Any>>>(listType)
            val raw = adapter.fromJson(jsonString) ?: return emptyList()
            raw.map {
                MeetingChapter(
                    title = it["title"] as? String ?: "",
                    timestampMs = (it["timestampMs"] as? Number)?.toLong() ?: 0L,
                    summary = it["summary"] as? String ?: ""
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Parse Refined Transcript Topics helper
    fun parseRefinedTranscript(jsonString: String): List<com.example.data.model.RefinedTranscriptTopic> {
        if (jsonString.isBlank()) return emptyList()
        return try {
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, com.example.data.model.RefinedTranscriptTopic::class.java)
            val adapter = moshi.adapter<List<com.example.data.model.RefinedTranscriptTopic>>(listType)
            adapter.fromJson(jsonString) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── AI observability ──────────────────────────────────────────────────

    enum class AiHealthStatus { UNKNOWN, OK, QUOTA, BAD_KEY, ERROR }

    private val _aiHealthStatus = MutableStateFlow(AiHealthStatus.UNKNOWN)
    val aiHealthStatus: StateFlow<AiHealthStatus> = _aiHealthStatus.asStateFlow()

    private val _lastAiError = MutableStateFlow<com.example.data.api.GeminiResult.Error?>(null)
    val lastAiError: StateFlow<com.example.data.api.GeminiResult.Error?> = _lastAiError.asStateFlow()

    private val _autoFailoverBanner = MutableStateFlow<String?>(null)
    val autoFailoverBanner: StateFlow<String?> = _autoFailoverBanner.asStateFlow()
    fun clearAutoFailoverBanner() { _autoFailoverBanner.value = null }

    fun updateAiHealth(result: com.example.data.api.GeminiResult) {
        when (result) {
            is com.example.data.api.GeminiResult.Text -> {
                _aiHealthStatus.value = AiHealthStatus.OK
                _lastAiError.value = null
            }
            is com.example.data.api.GeminiResult.Error -> {
                _lastAiError.value = result
                _aiHealthStatus.value = when (result.kind) {
                    com.example.data.api.ErrKind.QUOTA -> AiHealthStatus.QUOTA
                    com.example.data.api.ErrKind.BAD_KEY -> AiHealthStatus.BAD_KEY
                    else -> AiHealthStatus.ERROR
                }
            }
        }
    }

    fun testApiKey(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val key = _customGeminiKey.value
            if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
                onResult(false, "No API key configured")
                return@launch
            }
            val start = System.currentTimeMillis()
            val result = com.example.data.api.GeminiClient.getAiResponse(
                prompt = "Reply with the single word: OK",
                modelName = _sttModel.value
            )
            val latency = System.currentTimeMillis() - start
            when (result) {
                is com.example.data.api.GeminiResult.Text -> onResult(true, "✓ Key valid · ${latency}ms")
                is com.example.data.api.GeminiResult.Error -> onResult(false, "✗ ${result.suggestion}")
            }
        }
    }

    fun testAllModels(onProgress: (String, String) -> Unit) {
        val models = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash")
        viewModelScope.launch {
            models.forEach { model ->
                onProgress(model, "testing")
                val start = System.currentTimeMillis()
                val result = com.example.data.api.GeminiClient.getAiResponse(
                    prompt = "Reply with the single word: OK",
                    modelName = model
                )
                val latency = System.currentTimeMillis() - start
                val status = when (result) {
                    is com.example.data.api.GeminiResult.Text -> "ok:$latency"
                    is com.example.data.api.GeminiResult.Error -> "err:${result.httpCode ?: result.kind.name}"
                }
                onProgress(model, status)
            }
        }
    }

    fun loadRecentAiCalls(): Flow<List<com.example.data.model.AiCallLog>> =
        db.aiCallLogDao().getRecent(20)

    // ── Library tab state ──────────────────────────────────────────────────

    private val _librarySortOrder = MutableStateFlow(LibrarySort.LAST_MODIFIED)
    val librarySortOrder: StateFlow<LibrarySort> = _librarySortOrder.asStateFlow()
    fun setLibrarySort(s: LibrarySort) { _librarySortOrder.value = s }

    private val _libSelectedFolderIds = MutableStateFlow<Set<Int>>(emptySet())
    val libSelectedFolderIds: StateFlow<Set<Int>> = _libSelectedFolderIds.asStateFlow()

    private val _libSelectedMeetingIds = MutableStateFlow<Set<Int>>(emptySet())
    val libSelectedMeetingIds: StateFlow<Set<Int>> = _libSelectedMeetingIds.asStateFlow()

    val isLibraryMultiSelect: StateFlow<Boolean> =
        combine(_libSelectedFolderIds, _libSelectedMeetingIds) { f, m -> f.isNotEmpty() || m.isNotEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleFolderSelection(id: Int) {
        _libSelectedFolderIds.value = _libSelectedFolderIds.value.toMutableSet().also {
            if (id in it) it.remove(id) else it.add(id)
        }
    }

    fun toggleMeetingSelection(id: Int) {
        _libSelectedMeetingIds.value = _libSelectedMeetingIds.value.toMutableSet().also {
            if (id in it) it.remove(id) else it.add(id)
        }
    }

    fun clearLibrarySelection() {
        _libSelectedFolderIds.value = emptySet()
        _libSelectedMeetingIds.value = emptySet()
    }

    fun bulkMoveSelection(targetFolderId: Int) {
        val meetingIds = _libSelectedMeetingIds.value.toList()
        if (meetingIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.moveRecordingsToFolder(meetingIds, targetFolderId)
        }
        clearLibrarySelection()
    }

    private val _bulkDeleteSkippedCount = MutableStateFlow(0)
    val bulkDeleteSkippedCount: StateFlow<Int> = _bulkDeleteSkippedCount.asStateFlow()
    fun clearBulkDeleteSkipped() { _bulkDeleteSkippedCount.value = 0 }

    fun bulkDeleteSelection() {
        val meetingIds = _libSelectedMeetingIds.value.toList()
        val folderIds = _libSelectedFolderIds.value.toList()
        viewModelScope.launch(Dispatchers.IO) {
            meetingIds.forEach { id -> repository.softDeleteMeeting(id) }
            var skipped = 0
            folderIds.forEach { folderId ->
                val childCount = db.folderDao().childCount(folderId)
                val recCount = db.folderDao().recordingCount(folderId)
                if (childCount == 0 && recCount == 0) {
                    val folder = db.folderDao().getById(folderId)
                    if (folder != null) db.folderDao().delete(folder)
                } else {
                    skipped++
                }
            }
            _bulkDeleteSkippedCount.value = skipped
        }
        clearLibrarySelection()
    }

    fun renameMeeting(id: Int, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val sanitized = newTitle.trim().take(200).ifBlank { "Untitled recording" }
            repository.renameMeeting(id, sanitized)
        }
    }

    fun folderItemCountFlow(folderId: Int): Flow<Int> =
        kotlinx.coroutines.flow.flow {
            val recordings = db.folderDao().recordingCount(folderId)
            val children = db.folderDao().childCount(folderId)
            emit(recordings + children)
        }.flowOn(Dispatchers.IO)

    fun searchInFolder(query: String, rootFolderId: Int?): Flow<List<Meeting>> {
        if (query.isBlank()) return recordingsIn(rootFolderId)
        return kotlinx.coroutines.flow.flow {
            // BFS to collect all descendant folder IDs
            val allFolders = db.folderDao().getAllForTree().first()
            val folderIdSet = mutableSetOf<Int>()
            val queue = ArrayDeque<Int?>()
            queue.add(rootFolderId)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val children = if (current == null) {
                    allFolders.filter { it.parentId == null }
                } else {
                    allFolders.filter { it.parentId == current }
                }
                children.forEach {
                    folderIdSet.add(it.id)
                    queue.add(it.id)
                }
            }
            val includeRoot = rootFolderId == null
            emitAll(repository.searchMeetingsInFolders(query, folderIdSet.toList(), includeRoot))
        }.flowOn(Dispatchers.IO)
    }

    private fun pushWidgetState() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<android.app.Application>()
            val apiKey = _customGeminiKey.value
            val hasKey = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"
            val lastMeeting = try {
                db.meetingDao().getAllMeetingsSync()
                    .filter { !it.isDeleted }
                    .maxByOrNull { it.id }
            } catch (_: Exception) { null }
            val durSecs = lastMeeting?.durationSeconds ?: 0L
            val durStr = if (durSecs > 0L) {
                val h = durSecs / 3600; val m = (durSecs % 3600) / 60; val s = durSecs % 60
                if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
            } else ""
            WidgetStateManager.push(
                context          = ctx,
                isRecording      = _isRecording.value,
                isPaused         = _isPaused.value,
                recordSeconds    = _recordSeconds.value,
                source           = _meetingAudioSource.value,
                hasApiKey        = hasKey,
                lastTitle        = lastMeeting?.title ?: "",
                lastDuration     = durStr,
                lastStatus       = lastMeeting?.status?.name ?: "",
                lastMeetingId    = lastMeeting?.id ?: -1,
                isProcessing     = _aiProcessingMeetingId.value != null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        recorder?.release()
        playbackJob?.cancel()
        mediaPlayer?.release()
    }
}
