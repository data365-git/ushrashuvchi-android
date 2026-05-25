package com.example

import android.app.Application
import io.sentry.android.core.SentryAndroid
import io.sentry.SentryLevel

class MeetingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isNotBlank()) {
            SentryAndroid.init(this) { options ->
                options.dsn = dsn
                options.tracesSampleRate = 0.2
                options.isEnableUserInteractionTracing = false
                // Never send transcript content — only structural breadcrumbs
                options.beforeSend = io.sentry.SentryOptions.BeforeSendCallback { event, _ ->
                    event.user = null  // strip any auto-detected user identity
                    event
                }
            }
        }
    }
}
