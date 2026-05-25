package com.example.data.sync

import com.squareup.moshi.JsonClass
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class RegisterRequest(val name: String?, val existingDeviceId: String?)

@JsonClass(generateAdapter = true)
data class RegisterResponse(val deviceId: String, val token: String)

@JsonClass(generateAdapter = true)
data class UpsertMeetingRequest(
    val clientId: Int,
    val title: String,
    val date: Long,
    val durationSeconds: Long,
    val status: String,
    val audioSource: String,
    val summary: String? = null,
    val chaptersJson: String? = null,
    val refinedJson: String? = null
)

@JsonClass(generateAdapter = true)
data class UpsertMeetingResponse(val id: String)

@JsonClass(generateAdapter = true)
data class TranscriptLineDto(
    val tsStartMs: Long,
    val tsEndMs: Long,
    val speaker: String,
    val text: String
)

@JsonClass(generateAdapter = true)
data class TaskDto(
    val title: String,
    val assignee: String,
    val isCompleted: Boolean,
    val dueAt: Long? = null
)

@JsonClass(generateAdapter = true)
data class CreateShareRequest(val password: String? = null, val expiresInDays: Int? = null)

@JsonClass(generateAdapter = true)
data class CreateShareResponse(val token: String, val url: String)

@JsonClass(generateAdapter = true)
data class AudioUploadResponse(val sizeBytes: Long, val objectKey: String)

interface CloudApiService {
    @POST("/api/v1/devices/register")
    suspend fun register(@Body body: RegisterRequest): Response<RegisterResponse>

    @POST("/api/v1/meetings")
    suspend fun upsertMeeting(
        @Header("Authorization") auth: String,
        @Body body: UpsertMeetingRequest
    ): Response<UpsertMeetingResponse>

    @PUT("/api/v1/meetings/{id}/transcript")
    suspend fun putTranscript(
        @Header("Authorization") auth: String,
        @Path("id") meetingId: String,
        @Body lines: List<TranscriptLineDto>
    ): Response<Unit>

    @PUT("/api/v1/meetings/{id}/tasks")
    suspend fun putTasks(
        @Header("Authorization") auth: String,
        @Path("id") meetingId: String,
        @Body tasks: List<TaskDto>
    ): Response<Unit>

    @Multipart
    @POST("/api/v1/meetings/{id}/audio")
    suspend fun uploadAudio(
        @Header("Authorization") auth: String,
        @Path("id") meetingId: String,
        @Part audio: MultipartBody.Part
    ): Response<AudioUploadResponse>

    @DELETE("/api/v1/meetings/{id}")
    suspend fun deleteMeeting(
        @Header("Authorization") auth: String,
        @Path("id") meetingId: String
    ): Response<Unit>

    @POST("/api/v1/meetings/{id}/share")
    suspend fun createShare(
        @Header("Authorization") auth: String,
        @Path("id") meetingId: String,
        @Body body: CreateShareRequest
    ): Response<CreateShareResponse>

    @DELETE("/api/v1/meetings/{id}/share/{token}")
    suspend fun revokeShare(
        @Header("Authorization") auth: String,
        @Path("id") meetingId: String,
        @Path("token") shareToken: String
    ): Response<Unit>

    companion object {
        fun create(baseUrl: String): CloudApiService {
            val moshi = com.squareup.moshi.Moshi.Builder().build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            return retrofit2.Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .client(client)
                .addConverterFactory(retrofit2.converter.moshi.MoshiConverterFactory.create(moshi))
                .build()
                .create(CloudApiService::class.java)
        }
    }
}
