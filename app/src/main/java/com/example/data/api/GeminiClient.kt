package com.example.data.api

import android.util.Base64
import com.example.BuildConfig
import io.sentry.Sentry
import io.sentry.SentryLevel
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

enum class ErrKind {
    NO_KEY, BAD_KEY, QUOTA, MODEL_NOT_FOUND, BAD_INPUT,
    SAFETY_BLOCKED, EMPTY_RESPONSE, NETWORK, TIMEOUT, SERVER, UNKNOWN
}

data class TokenUsage(val prompt: Int, val response: Int, val total: Int)

sealed class GeminiResult {
    data class Text(val text: String, val usage: TokenUsage? = null) : GeminiResult()
    data class Error(
        val kind: ErrKind,
        val httpCode: Int?,
        val status: String?,
        val message: String,
        val suggestion: String,
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

    @androidx.annotation.VisibleForTesting
    @Volatile
    internal var overrideBaseUrl: String? = null

    @androidx.annotation.VisibleForTesting
    fun overrideBaseUrlForTesting(url: String) {
        overrideBaseUrl = url
    }

    @androidx.annotation.VisibleForTesting
    fun resetBaseUrlForTesting() {
        overrideBaseUrl = null
    }

    private val effectiveBaseUrl: String get() = overrideBaseUrl ?: BASE_URL

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

    // Retrofit base URL is only used to satisfy the builder; every call uses @Url with
    // a fully-qualified URL built from effectiveBaseUrl so test overrides take effect.
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
            return buildError(null, "NO_KEY", "No Gemini API key configured.", null, modelName)
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

        val url = "${effectiveBaseUrl.trimEnd('/')}/v1beta/models/$modelName:generateContent"
        return try {
            val response = service.generateContent(url, apiKey, request)
            val candidate = response.candidates?.firstOrNull()
            val finish = candidate?.finishReason
            val text = candidate?.content?.parts?.firstOrNull { !it.text.isNullOrBlank() }?.text
            when {
                !text.isNullOrBlank() -> GeminiResult.Text(text)
                finish == "SAFETY" -> buildError(null, "SAFETY", "Gemini blocked the response on safety filters.", null, modelName)
                finish == "RECITATION" -> buildError(null, "RECITATION", "Gemini blocked the response (recitation).", null, modelName)
                finish == "MAX_TOKENS" -> buildError(null, "MAX_TOKENS", "Response truncated. Try a smaller prompt.", null, modelName)
                else -> buildError(null, "EMPTY", "Gemini returned no text (finish=$finish).", response.toString(), modelName)
            }
        } catch (e: retrofit2.HttpException) {
            val body = e.response()?.errorBody()?.string()
            val parsed = parseGeminiError(body)
            val errMsg = parsed?.second ?: (e.message() ?: "HTTP ${e.code()}")
            Sentry.addBreadcrumb(io.sentry.Breadcrumb().apply {
                category = "gemini"
                message = "Gemini error: $errMsg"
                level = SentryLevel.ERROR
            })
            Sentry.captureException(e)
            buildError(e.code(), parsed?.first, errMsg, body, modelName)
        } catch (e: java.net.UnknownHostException) {
            Sentry.addBreadcrumb(io.sentry.Breadcrumb().apply {
                category = "gemini"
                message = "Gemini error: No internet connection"
                level = SentryLevel.ERROR
            })
            Sentry.captureException(e)
            buildError(null, "NETWORK", "No internet connection.", null, modelName)
        } catch (e: java.net.SocketTimeoutException) {
            Sentry.addBreadcrumb(io.sentry.Breadcrumb().apply {
                category = "gemini"
                message = "Gemini error: Request timed out after 60s"
                level = SentryLevel.ERROR
            })
            Sentry.captureException(e)
            buildError(null, "TIMEOUT", "Gemini took too long to respond.", null, modelName)
        } catch (e: Exception) {
            Sentry.addBreadcrumb(io.sentry.Breadcrumb().apply {
                category = "gemini"
                message = "Gemini error: ${e.message ?: "Unknown error"}"
                level = SentryLevel.ERROR
            })
            Sentry.captureException(e)
            buildError(null, "UNKNOWN", e.message ?: "Unknown error", null, modelName)
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

    private fun buildError(
        httpCode: Int?,
        status: String?,
        message: String,
        raw: String?,
        model: String = ""
    ): GeminiResult.Error {
        val kind = when {
            httpCode == null && status == "NO_KEY"      -> ErrKind.NO_KEY
            httpCode == null && status == "NETWORK"     -> ErrKind.NETWORK
            httpCode == null && status == "TIMEOUT"     -> ErrKind.TIMEOUT
            httpCode == null && status == "SAFETY"      -> ErrKind.SAFETY_BLOCKED
            httpCode == null && status == "EMPTY"       -> ErrKind.EMPTY_RESPONSE
            httpCode == 401 || httpCode == 403          -> ErrKind.BAD_KEY
            httpCode == 429                             -> ErrKind.QUOTA
            httpCode == 404                             -> ErrKind.MODEL_NOT_FOUND
            httpCode == 400                             -> ErrKind.BAD_INPUT
            httpCode != null && httpCode >= 500         -> ErrKind.SERVER
            status == "PERMISSION_DENIED"               -> ErrKind.BAD_KEY
            status == "RESOURCE_EXHAUSTED"              -> ErrKind.QUOTA
            status == "NOT_FOUND"                       -> ErrKind.MODEL_NOT_FOUND
            status == "INVALID_ARGUMENT"                -> ErrKind.BAD_INPUT
            else                                        -> ErrKind.UNKNOWN
        }
        val suggestion = when (kind) {
            ErrKind.NO_KEY         -> "Set your Gemini API key in Settings → AI."
            ErrKind.BAD_KEY        -> "Your API key is rejected. Get a new one at aistudio.google.com/apikey."
            ErrKind.QUOTA          -> "Quota exhausted on $model. Open Settings → AI to switch models."
            ErrKind.MODEL_NOT_FOUND -> "Model $model is unavailable. Switch in Settings → AI."
            ErrKind.BAD_INPUT      -> "Audio rejected: $message. Try re-recording."
            ErrKind.SAFETY_BLOCKED -> "Gemini's safety filter blocked this response."
            ErrKind.EMPTY_RESPONSE -> "Gemini returned nothing. The audio may have no speech."
            ErrKind.NETWORK        -> "No internet connection. Check Wi-Fi or mobile data."
            ErrKind.TIMEOUT        -> "Gemini took too long. Try a shorter recording."
            ErrKind.SERVER         -> "Gemini servers are having issues (HTTP $httpCode). Try again soon."
            ErrKind.UNKNOWN        -> "Unexpected error: $message"
        }
        return GeminiResult.Error(kind, httpCode, status, message, suggestion, raw)
    }
}
