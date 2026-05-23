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
    val content: Content?
)

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
        audioMimeType: String = "audio/3gpp",
        requestJson: Boolean = false
    ): String {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Please configure a valid GEMINI_API_KEY in the Settings screen or in your Secrets panel."
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
            response.candidates?.firstOrNull()?.content?.parts
                ?.firstOrNull { !it.text.isNullOrBlank() }?.text
                ?: "No response from AI."
        } catch (e: Exception) {
            "API Error: ${e.localizedMessage ?: e.message}"
        }
    }
}
