package com.example.e2e.support

import com.example.data.sync.CloudApiBaseUrlProvider
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.TimeUnit

class FakeCloudServer {
    private val server = MockWebServer()

    fun start() {
        server.start()
        CloudApiBaseUrlProvider.overrideForTesting(server.url("/").toString())
    }

    fun stop() {
        server.shutdown()
        CloudApiBaseUrlProvider.resetForTesting()
    }

    fun enqueueRegister(deviceId: String, token: String) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"deviceId":"$deviceId","token":"$token"}"""
        ).addHeader("Content-Type", "application/json"))
    }

    fun enqueueUpsertMeeting(serverId: String) {
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"id":"$serverId"}"""
        ).addHeader("Content-Type", "application/json"))
    }

    fun enqueueAudioUpload(sizeBytes: Long, objectKey: String) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"sizeBytes":$sizeBytes,"objectKey":"$objectKey"}"""
        ).addHeader("Content-Type", "application/json"))
    }

    fun enqueueShare(token: String, url: String) {
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"token":"$token","url":"$url"}"""
        ).addHeader("Content-Type", "application/json"))
    }

    fun enqueueHttp(code: Int, body: String = "{}") {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body)
            .addHeader("Content-Type", "application/json"))
    }

    val receivedRequests: List<RecordedRequest>
        get() = generateSequence { server.takeRequest(50, TimeUnit.MILLISECONDS) }.toList()
}
