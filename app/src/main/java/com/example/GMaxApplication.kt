package com.example

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import java.util.concurrent.Executor

/**
 * Custom [Application] for G Max Gaming Hub.
 *
 * Suppresses Android Privacy Sandbox `android.adservices.MEASUREMENT_SERVICE` null-binding
 * errors on devices and emulators where the system AdServices measurement service is disabled,
 * while preserving full Unity Ads SDK ad loading and rendering.
 */
class GMaxApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AdServicesSafeContextWrapper(base))
        com.example.util.AppCrashGuard.install(this)
        ensureWebViewCrashpadDirs(this)
    }

    override fun onCreate() {
        com.example.util.AppCrashGuard.install(this)
        ensureWebViewCrashpadDirs(this)
        super.onCreate()
    }

    private fun ensureWebViewCrashpadDirs(context: Context) {
        try {
            val cache = context.cacheDir ?: return
            java.io.File(cache, "WebView/Crashpad").mkdirs()
            java.io.File(cache, "org.chromium.android_webview").mkdirs()
        } catch (_: Exception) {
        }
    }

    override fun getSystemServiceName(serviceClass: Class<*>): String? {
        if (serviceClass.name.startsWith("android.adservices.")) {
            return null
        }
        return super.getSystemServiceName(serviceClass)
    }

    override fun getSystemService(name: String): Any? {
        if (name.contains("adservices", ignoreCase = true) || name == "measurement") {
            return null
        }
        val service = super.getSystemService(name)
        if (service != null && service.javaClass.name.startsWith("android.adservices.")) {
            return null
        }
        return service
    }

    override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
        if (isAdServicesIntent(service)) {
            return false
        }
        return super.bindService(service, conn, flags)
    }

    override fun bindService(
        service: Intent,
        flags: Int,
        executor: Executor,
        conn: ServiceConnection
    ): Boolean {
        if (isAdServicesIntent(service)) {
            return false
        }
        return super.bindService(service, flags, executor, conn)
    }

    companion object {
        fun isAdServicesIntent(intent: Intent?): Boolean {
            if (intent == null) return false
            val action = intent.action.orEmpty()
            val pkg = intent.`package`.orEmpty()
            val componentClass = intent.component?.className.orEmpty()
            return action.startsWith("android.adservices.") ||
                action.contains("MEASUREMENT_SERVICE", ignoreCase = true) ||
                pkg.contains("adservices", ignoreCase = true) ||
                componentClass.startsWith("android.adservices.")
        }
    }
}

private class AdServicesSafeContextWrapper(base: Context) : ContextWrapper(base) {

    override fun getSystemServiceName(serviceClass: Class<*>): String? {
        if (serviceClass.name.startsWith("android.adservices.")) {
            return null
        }
        return super.getSystemServiceName(serviceClass)
    }

    override fun getSystemService(name: String): Any? {
        if (name.contains("adservices", ignoreCase = true) || name == "measurement") {
            return null
        }
        val service = super.getSystemService(name)
        if (service != null && service.javaClass.name.startsWith("android.adservices.")) {
            return null
        }
        return service
    }

    override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
        if (GMaxApplication.isAdServicesIntent(service)) {
            return false
        }
        return super.bindService(service, conn, flags)
    }

    override fun bindService(
        service: Intent,
        flags: Int,
        executor: Executor,
        conn: ServiceConnection
    ): Boolean {
        if (GMaxApplication.isAdServicesIntent(service)) {
            return false
        }
        return super.bindService(service, flags, executor, conn)
    }
}
