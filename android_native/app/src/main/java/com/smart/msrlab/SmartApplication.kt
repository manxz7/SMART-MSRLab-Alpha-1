package com.smart.msrlab

import android.app.Application
import com.smart.msrlab.data.local.PreferencesManager
import com.smart.msrlab.service.NotificationService

/**
 * SmartApplication — Application class
 *
 * Responsibilities:
 *   1. Initialize DJI SDK (required before any DJI operations)
 *   2. Create notification channels
 *   3. Expose global PreferencesManager instance
 *
 * Mirrors Flutter:
 *   DJIApplication.kt (isRegistered companion flag)
 *   main.dart initNotifications()
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
        // The actual registration (with network) happens when user taps 'Register SDK'
        try {
            dji.sdk.sdkmanager.DJISDKManager.getInstance()
        } catch (e: Exception) {
            android.util.Log.w("SmartApp", "DJI SDK not available: ${e.message}")
        }
    }
}
