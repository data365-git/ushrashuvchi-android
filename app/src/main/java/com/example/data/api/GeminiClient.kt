package com.example.data.api

import android.util.Base64
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    @Json(name = "inline_data") val inlineData: Blob? = null
)

@JsonClass(generateAdapter = true)
data class Blob(
    @Json(name = "mime_type") val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float? = null,
    @Json(name = "response_mime_type") val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?,
    val finishReason: String? = null
)

sealed class GeminiResult {
    data class Text(val text: String) : GeminiResult()
    data class Error(
        val httpCode: Int?,
        val status: String?,
        val message: String,
        val raw: String?
    ) : GeminiResult()
}

class GeminiException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface GeminiApiService {
    @POST
    suspend fun generateContent(
        @Url url: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private var customApiKey: String? = null

    fun setCustomApiKey(key: String?) {
        customApiKey = key
    }

    fun getEffectiveApiKey(): String {
        return customApiKey ?: BuildConfig.GEMINI_API_KEY
    }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    suspend fun getAiResponse(
        prompt: String,
        systemInstructionText: String? = null,
        modelName: String = "gemini-2.0-flash",
        audioBase64: String? = null,
        audioMimeType: String = "audio/aac",
        requestJson: Boolean = false
    ): GeminiResult {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return GeminiResult.Error(
                null, "NO_KEY",
                "No Gemini API key configured. Open Settings and paste a key from Google AI Studio.",
                null
            )
        }

        val userParts = mutableListOf<Part>(Part(text = prompt))
        if (!audioBase64.isNullOrBlank()) {
            userParts.add(Part(inlineData = Blob(mimeType = audioMimeType, data = audioBase64)))
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = userParts)),
            systemInstruction = systemInstructionText?.let { Content(parts = listOf(Part(text = it))) },
            generationConfig = if (requestJson) GenerationConfig(responseMimeType = "application/json") else null
        )

        val url = "v1beta/models/$modelName:generateContent"
        return try {
            val response = service.generateContent(url, apiKey, request)
            val candidate = response.candidates?.firstOrNull()
            val finish = candidate?.finishReason
            val text = candidate?.content?.parts?.firstOrNull { !it.text.isNullOrBlank() }?.text
            when {
                !text.isNullOrBlank() -> GeminiResult.Text(text)
                finish == "SAFETY" -> GeminiResult.Error(null, "SAFETY", "Gemini blocked the response on safety filters.", null)
                finish == "RECITATION" -> GeminiResult.Error(null, "RECITATION", "Gemini blocked the response (recitation).", null)
                finish == "MAX_TOKENS" -> GeminiResult.Error(null, "MAX_TOKENS", "Response truncated. Try a smaller prompt.", null)
                else -> GeminiResult.Error(null, "EMPTY", "Gemini returned no text (finish=$finish).", response.toString())
            }
        } catch (e: retrofit2.HttpException) {
            val body = e.response()?.errorBody()?.string()
            val parsed = parseGeminiError(body)
            GeminiResult.Error(e.code(), parsed?.first, parsed?.second ?: (e.message() ?: "HTTP ${e.code()}"), body)
        } catch (e: java.net.UnknownHostException) {
            GeminiResult.Error(null, "NETWORK", "No internet connection. Check Wi-Fi/data and try again.", null)
        } catch (e: java.net.SocketTimeoutException) {
            GeminiResult.Error(null, "TIMEOUT", "Gemini took too long to respond (60 s). Try again.", null)
        } catch (e: Exception) {
            GeminiResult.Error(null, "UNKNOWN", e.message ?: "Unknown error", null)
        }
    }

    private fun parseGeminiError(body: String?): Pair<String?, String?>? {
        if (body.isNullOrBlank()) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val outer = moshi.adapter(Map::class.java).fromJson(body) as? Map<*, *>
            val err = outer?.get("error") as? Map<*, *>
            (err?.get("status") as? String) to (err?.get("message") as? String)
        } catch (_: Exception) { null }
    }
}
