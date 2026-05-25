package com.example.e2e

import com.example.data.sync.*
import com.example.e2e.support.*
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudSyncJourneyTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var cloud: FakeCloudServer

    @Before
    fun setup() {
        cloud = FakeCloudServer().also { it.start() }
    }

    @After
    fun teardown() { cloud.stop() }

    @Test
    fun `register device returns deviceId and JWT`() = runBlocking {
        cloud.enqueueRegister("dev-uuid-123", "fake-jwt-token")
        val api = CloudApiService.create(CloudApiBaseUrlProvider.current())
        val resp = api.register(RegisterRequest(name = "Test device", existingDeviceId = null))
        assertTrue(resp.isSuccessful)
        val body = resp.body()!!
        assertEquals("dev-uuid-123", body.deviceId)
        assertEquals("fake-jwt-token", body.token)
    }

    @Test
    fun `upsert meeting returns server-side UUID`() = runBlocking {
        cloud.enqueueUpsertMeeting("server-meeting-uuid-abc")
        val api = CloudApiService.create(CloudApiBaseUrlProvider.current())
        val resp = api.upsertMeeting(
            auth = "Bearer test",
            body = UpsertMeetingRequest(
                clientId = 42,
                title = "test meeting",
                date = 1700000000000L,
                durationSeconds = 60,
                status = "RECORDED",
                audioSource = "OFFLINE_MEET"
            )
        )
        assertTrue(resp.isSuccessful)
        assertEquals("server-meeting-uuid-abc", resp.body()!!.id)
    }

    @Test
    fun `audio upload multipart returns size and object key`() = runBlocking {
        cloud.enqueueAudioUpload(sizeBytes = 12_345L, objectKey = "audio/dev/meeting.m4a")
        val api = CloudApiService.create(CloudApiBaseUrlProvider.current())
        val fixtureFile = FakeRecordingHarness(tmp.newFolder("audio"))
            .writeFixtureAudio("test.m4a", sizeBytes = 12_345L)
        val body = MultipartBody.Part.createFormData(
            "audio",
            fixtureFile.name,
            fixtureFile.asRequestBody("audio/mp4".toMediaTypeOrNull())
        )
        val resp = api.uploadAudio("Bearer test", "server-uuid", body)
        assertTrue(resp.isSuccessful)
        assertEquals(12_345L, resp.body()!!.sizeBytes)
        assertEquals("audio/dev/meeting.m4a", resp.body()!!.objectKey)
    }

    @Test
    fun `create share returns token and URL`() = runBlocking {
        cloud.enqueueShare(token = "share-abc-123", url = "https://x.app/s/share-abc-123")
        val api = CloudApiService.create(CloudApiBaseUrlProvider.current())
        val resp = api.createShare(
            "Bearer test",
            "server-uuid",
            CreateShareRequest(password = null, expiresInDays = 30)
        )
        assertTrue(resp.isSuccessful)
        val b = resp.body()!!
        assertTrue(b.token.isNotBlank())
        assertTrue(b.url.contains("share-abc-123"))
    }

    @Test
    fun `server 502 surfaces as unsuccessful response`() = runBlocking {
        cloud.enqueueHttp(502, """{"error":"bad gateway"}""")
        val api = CloudApiService.create(CloudApiBaseUrlProvider.current())
        val resp = api.upsertMeeting(
            "Bearer test",
            UpsertMeetingRequest(
                clientId = 1, title = "x", date = 0, durationSeconds = 0,
                status = "RECORDED", audioSource = "OFFLINE_MEET"
            )
        )
        assertFalse(resp.isSuccessful)
        assertEquals(502, resp.code())
    }

    @Test
    fun `401 on upsert surfaces as unauthorized - caller must re-register`() = runBlocking {
        // First call returns 401 (token expired on server)
        cloud.enqueueHttp(401, """{"error":"unauthorized"}""")
        val api = CloudApiService.create(CloudApiBaseUrlProvider.current())
        val resp = api.upsertMeeting(
            "Bearer expired-token",
            UpsertMeetingRequest(
                clientId = 99, title = "auth-test", date = 0,
                durationSeconds = 0, status = "RECORDED", audioSource = "OFFLINE_MEET"
            )
        )
        assertFalse("401 must be unsuccessful", resp.isSuccessful)
        assertEquals(401, resp.code())

        // Recovery: re-register returns a fresh token
        cloud.enqueueRegister("dev-uuid-999", "fresh-jwt-token")
        val regResp = api.register(RegisterRequest(name = "re-register", existingDeviceId = null))
        assertTrue("Re-registration must succeed", regResp.isSuccessful)
        assertEquals("fresh-jwt-token", regResp.body()!!.token)
    }
}
