package com.example.data.sync

import com.example.BuildConfig

/**
 * Indirection over BuildConfig.CLOUD_API_BASE_URL so tests can override the URL
 * to point at a MockWebServer instance without rebuilding.
 */
object CloudApiBaseUrlProvider {
    @Volatile
    private var override: String? = null

    fun current(): String = override ?: BuildConfig.CLOUD_API_BASE_URL

    @androidx.annotation.VisibleForTesting
    fun overrideForTesting(url: String) { override = url }

    @androidx.annotation.VisibleForTesting
    fun resetForTesting() { override = null }
}
