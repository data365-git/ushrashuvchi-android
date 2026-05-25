package com.example.data.api

object GeminiPricing {
    data class ModelRate(val inputPerM: Double, val outputPerM: Double, val audioPerM: Double)

    private val table = mapOf(
        "gemini-2.5-flash" to ModelRate(0.30, 2.50, 1.00),
        "gemini-2.5-pro" to ModelRate(1.25, 10.00, 2.00),
        "gemini-2.0-flash" to ModelRate(0.10, 0.40, 0.70),
        "gemini-1.5-flash" to ModelRate(0.075, 0.30, 0.50),
        "gemini-1.5-pro" to ModelRate(1.25, 5.00, 1.50)
    )

    fun computeMicros(model: String, promptTokens: Int, responseTokens: Int, audioTokens: Int = 0): Long {
        val rate = table[model] ?: table.entries.firstOrNull { model.startsWith(it.key) }?.value
            ?: ModelRate(0.50, 5.00, 1.50)
        val usd = (
            promptTokens * rate.inputPerM +
            responseTokens * rate.outputPerM +
            audioTokens * rate.audioPerM
        ) / 1_000_000.0
        return (usd * 1_000_000).toLong()
    }

    fun microsToUsd(micros: Long): Double = micros / 1_000_000.0
    fun microsToUzs(micros: Long, exchangeRate: Double = 12_600.0): Double =
        microsToUsd(micros) * exchangeRate
}
