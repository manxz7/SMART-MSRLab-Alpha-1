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

    override fun onCreate() {
        super.onCreate()

        // Initialize PreferencesManager
        prefs = PreferencesManager(this)

        // Create notification channels
        NotificationService.createChannel(this)

        // DJI SDK init — must be called in Application.onCreate()
        try {
            dji.sdk.sdkmanager.DJISDKManager.getInstance()
        } catch (e: Exception) {
            android.util.Log.w("SmartApp", "DJI SDK not available: ${e.message}")
        }
    }
}
