package com.example.e2e.support

import com.example.data.api.GeminiClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import java.util.concurrent.TimeUnit

class FakeGeminiServer {
    private val server = MockWebServer()
    val requests: MutableList<RecordedRequest> = mutableListOf()

    fun start() {
        server.start()
        GeminiClient.overrideBaseUrlForTesting(server.url("/").toString())
    }

    fun stop() {
        server.shutdown()
        GeminiClient.resetBaseUrlForTesting()
    }

    fun drain() {
        while (true) {
            val r = server.takeRequest(50, TimeUnit.MILLISECONDS) ?: break
            requests.add(r)
        }
    }

    fun enqueueText(text: String) {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"")
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"candidates":[{"content":{"parts":[{"text":"$escaped"}],"role":"model"},"finishReason":"STOP","index":0}],
              |"usageMetadata":{"promptTokenCount":100,"candidatesTokenCount":200,"totalTokenCount":300}}""".trimMargin()
        ).addHeader("Content-Type", "application/json"))
    }

    fun enqueueStructuredJson(json: String) {
        val escaped = json.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"candidates":[{"content":{"parts":[{"text":"$escaped"}]},"finishReason":"STOP"}],
              |"usageMetadata":{"promptTokenCount":500,"candidatesTokenCount":900,"totalTokenCount":1400}}""".trimMargin()
        ).addHeader("Content-Type", "application/json"))
    }

    fun enqueueHttp(code: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body)
            .addHeader("Content-Type", "application/json"))
    }

    fun enqueueNetworkFailure() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
    }
}
