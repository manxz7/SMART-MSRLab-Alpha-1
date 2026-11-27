package com.smart.msrlab

import android.app.Application
import com.smart.msrlab.data.local.PreferencesManager
import com.smart.msrlab.service.NotificationService

/**
 * SmartApplication — Application class
 */
class SmartApplication : Application() {

    companion object {
        /** True once DJI SDK has been successfully registered */
        var isDjiRegistered = false

        /** Global PreferencesManager — access from anywhere */
        lateinit var prefs: PreferencesManager
            private set
    }

    /**
     * DJI SDK v4 dimuatkan di sini (sebelum onCreate) — sama seperti DJIApplication
     * dalam app Flutter. Helper.install() memuatkan kelas "dji-sdk-provided".
     */
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        androidx.multidex.MultiDex.install(this)
        com.cySdkyc.clx.Helper.install(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize PreferencesManager
        prefs = PreferencesManager(this)

        // Create notification channels
        NotificationService.createChannel(this)
    }
}
