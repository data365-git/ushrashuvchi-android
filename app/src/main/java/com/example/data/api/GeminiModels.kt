package com.example.data.api

object GeminiModels {
    data class Option(val id: String, val label: String, val tier: String)

    val STT_MODELS = listOf(
        Option("gemini-2.5-pro",   "Gemini 2.5 Pro",   "Best quality"),
        Option("gemini-2.5-flash", "Gemini 2.5 Flash", "Recommended"),
        Option("gemini-2.0-flash", "Gemini 2.0 Flash", "Fast"),
        Option("gemini-1.5-pro",   "Gemini 1.5 Pro",   "Legacy"),
        Option("gemini-1.5-flash", "Gemini 1.5 Flash", "Legacy"),
    )
    val LLM_MODELS = STT_MODELS

    const val DEFAULT_STT = "gemini-2.5-flash"
    const val DEFAULT_LLM = "gemini-2.5-flash"
}
