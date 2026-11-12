package com.halalcheck.halal_app

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex

class DJIApplication : Application() {

    companion object {
        var isRegistered = false
        var registrationError: String? = null
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
        com.cySdkyc.clx.Helper.install(this)
    }
}
