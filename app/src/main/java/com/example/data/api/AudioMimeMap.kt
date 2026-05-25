package com.example.data.api

/**
 * Maps audio file extensions to Gemini-accepted MIME types.
 *
 * Gemini's accepted audio set: wav, mp3, aiff, aac, ogg, flac.
 * The legacy `.3gp` (AMR_NB) format is present in the map for completeness
 * but [isSupported] returns false for it — calls must be rejected pre-flight.
 */
object AudioMimeMap {
    private val EXT_TO_MIME = mapOf(
        "m4a" to "audio/aac",
        "mp4" to "audio/aac",
        "aac" to "audio/aac",
        "wav" to "audio/wav",
        "mp3" to "audio/mpeg",
        "ogg" to "audio/ogg",
        "oga" to "audio/ogg",
        "opus" to "audio/ogg",
        "flac" to "audio/flac",
        "aiff" to "audio/aiff",
        "aif" to "audio/aiff",
        "3gp" to "audio/3gpp"
    )

    fun forExtension(extension: String): String =
        EXT_TO_MIME[extension.lowercase().trim('.')] ?: "application/octet-stream"

    fun isSupported(extension: String): Boolean {
        val ext = extension.lowercase().trim('.')
        return ext in EXT_TO_MIME && ext != "3gp"
    }
}
