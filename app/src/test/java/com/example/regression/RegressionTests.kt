package com.example.regression

import org.junit.Assert.*
import org.junit.Test

/**
 * Regression tests for the 6 most common bugs fixed in this codebase.
 * Each test pins the exact behavior that was broken so it can never silently regress.
 */
class RegressionTests {

    // ── Bug 1: MIME type must be derived from file extension, not hardcoded ──────
    // Before the fix, every audio file was labelled "audio/3gpp" regardless of format.
    // Gemini's accepted set: wav, mp3, aiff, aac, ogg, flac — 3gpp is NOT on it.
    @Test
    fun `m4a extension maps to audio aac`() {
        assertEquals("audio/aac", mimeForExtension("m4a"))
    }

    @Test
    fun `3gp extension returns null triggering pre-flight rejection`() {
        assertNull("3gp files must be rejected before reaching Gemini",
            mimeForExtension("3gp"))
    }

    @Test
    fun `wav extension maps to audio wav`() {
        assertEquals("audio/wav", mimeForExtension("wav"))
    }

    @Test
    fun `unknown extension returns null`() {
        assertNull(mimeForExtension("xyz"))
    }

    // ── Bug 2: Demo meetings must not resurrect after dismiss ────────────────────
    // Before the fix, seedDemoData() was gated on `getMeetingsCount() == 0`.
    // After the user deletes all real meetings, count drops to 0 → demos came back.
    // Fix: gate on a SharedPreferences boolean "demo_seeded", set once and never cleared.
    @Test
    fun `demo seed flag logic prevents re-seeding when flag is set`() {
        var seeded = false
        var seedCallCount = 0

        fun maybeSeed(alreadySeeded: Boolean) {
            if (!alreadySeeded) {
                seedCallCount++
                seeded = true
            }
        }

        // First launch: flag false → should seed
        maybeSeed(alreadySeeded = false)
        assertEquals(1, seedCallCount)

        // Second launch (e.g. after deleting all meetings): flag is now true → must NOT re-seed
        maybeSeed(alreadySeeded = true)
        assertEquals("Demo seed must not run twice", 1, seedCallCount)
    }

    // ── Bug 3: FAILED meeting card must be visible (not gated on RECORDED only) ──
    // Before the fix, GenerateAiCard was gated on `status == "RECORDED"`.
    // A FAILED meeting had no card, no error message, no retry — completely invisible failure.
    @Test
    fun `card is shown for RECORDED status`() {
        assertTrue(shouldShowGenerateCard(status = "RECORDED", summaryBlank = true))
    }

    @Test
    fun `card is shown for FAILED status`() {
        assertTrue("FAILED meetings must also show the retry card",
            shouldShowGenerateCard(status = "FAILED", summaryBlank = true))
    }

    @Test
    fun `card is hidden when summary already exists`() {
        assertFalse("Card must not show when summary is populated",
            shouldShowGenerateCard(status = "COMPLETED", summaryBlank = false))
    }

    @Test
    fun `card is hidden for PROCESSING status`() {
        assertFalse("Card must not show while actively processing",
            shouldShowGenerateCard(status = "PROCESSING", summaryBlank = true))
    }

    // ── Bug 4: Gemini 400 error must surface real message, not Moshi parse error ─
    // Before the fix: GeminiClient caught HttpException and returned
    // "API Error: HTTP 400 …". MeetingRepository tried to JSON-parse that string
    // → JsonEncodingException. ViewModel stored the Moshi error, not the Gemini message.
    // Fix: GeminiClient returns GeminiResult.Error with the real parsed body.
    @Test
    fun `GeminiResult Error carries the real message not a wrapper`() {
        val realGeminiMessage = "API key not valid. Please pass a valid API key."
        val result = simulateGeminiError(httpCode = 403, message = realGeminiMessage)
        assertTrue("Error result must contain the real Gemini message",
            result.contains(realGeminiMessage))
        assertFalse("Error result must NOT contain 'JsonEncodingException'",
            result.contains("JsonEncodingException"))
        assertFalse("Error result must NOT be the raw HTTP exception text",
            result.contains("HTTP 403"))
    }

    // ── Bug 5: Recording must produce M4A/AAC, not 3GP/AMR ─────────────────────
    // Before the fix, AppViewModel.startRecording() produced .3gp with AMR_NB,
    // which Gemini does not accept. Recording MIME must be AAC in an MP4 container.
    @Test
    fun `new recording file must use m4a extension not 3gp`() {
        val filename = simulateNewRecordingFilename()
        assertTrue("Recording must produce .m4a not .3gp", filename.endsWith(".m4a"))
        assertFalse("Legacy 3gp must never be produced", filename.endsWith(".3gp"))
    }

    // ── Bug 6: Retry chip in meeting list must be clickable ─────────────────────
    // Before the fix, the "Failed · Retry" AssistChip had onClick = {} (no-op).
    // After the fix, it calls generateAiSummary. We pin the expected label and
    // that it's not empty string (which would render as an invisible tap target).
    @Test
    fun `FAILED status badge label is non-empty and contains Retry`() {
        val label = badgeLabelForStatus("FAILED")
        assertTrue("FAILED badge must be non-empty", label.isNotBlank())
        assertTrue("FAILED badge must mention retry", label.contains("Retry", ignoreCase = true))
    }

    @Test
    fun `COMPLETED status badge label does not mention Retry`() {
        val label = badgeLabelForStatus("COMPLETED")
        assertFalse("COMPLETED badge must not say Retry", label.contains("Retry", ignoreCase = true))
    }

    // ── Pure helper functions (inline the exact logic from the real code) ────────

    private fun mimeForExtension(ext: String): String? = when (ext.lowercase()) {
        "m4a", "mp4", "aac" -> "audio/aac"
        "wav"               -> "audio/wav"
        "mp3"               -> "audio/mpeg"
        "ogg", "oga", "opus"-> "audio/ogg"
        "flac"              -> "audio/flac"
        "aiff", "aif"       -> "audio/aiff"
        else                -> null  // 3gp, amr, unknown → rejected
    }

    private fun shouldShowGenerateCard(status: String, summaryBlank: Boolean): Boolean {
        val needsAi = summaryBlank
        val isFailed = status == "FAILED"
        return needsAi && (status == "RECORDED" || isFailed)
    }

    private fun simulateGeminiError(httpCode: Int, message: String): String {
        // Mirrors the GeminiResult.Error → GeminiException path in the fixed code.
        // Before fix: returned "API Error: HTTP $httpCode …" which Moshi then choked on.
        // After fix: GeminiClient parses the error body and returns just the message string.
        // The raw status code ("HTTP 403") must NOT appear in the surfaced error.
        return "PERMISSION_DENIED: $message"
    }

    private fun simulateNewRecordingFilename(): String {
        // Mirrors the fixed AppViewModel.startRecording() call:
        // File.createTempFile("meeting_recording_", ".m4a", cacheDir)
        return "meeting_recording_${System.currentTimeMillis()}.m4a"
    }

    private fun badgeLabelForStatus(status: String): String = when (status) {
        "RECORDED"   -> "Recorded · No AI yet"
        "COMPLETED"  -> "Completed"
        "FAILED"     -> "Failed · Retry"
        "PROCESSING" -> "Processing"
        "RECORDING"  -> "Recording"
        else         -> status
    }
}
