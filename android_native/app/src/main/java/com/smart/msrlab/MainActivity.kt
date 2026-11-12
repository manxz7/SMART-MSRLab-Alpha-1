package com.smart.msrlab

import android.hardware.GeomagneticField
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smart.msrlab.ui.screens.*
import com.smart.msrlab.ui.theme.SmartAppTheme
import dji.common.error.DJIError
import dji.common.error.DJISDKError
import dji.sdk.base.BaseComponent
import dji.sdk.base.BaseProduct
import dji.sdk.sdkmanager.DJISDKInitEvent
import dji.sdk.sdkmanager.DJISDKManager

/**
 * MainActivity — Single Activity hosting the entire Compose navigation tree.
 *
 * Also hosts the DJI SDK registration and geomagnetic channel
 * (ported directly from Flutter android/MainActivity
 */
class MainActivity : ComponentActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartAppTheme {
                SmartAppNavigation()
            }
        }
    }

    /**
     * Register DJI SDK with the DJI server.
     * Called from DroneScreen when user taps 'Daftar SDK'.
     */
    fun registerDjiSdk(onResult: (Boolean, String) -> Unit) {
        if (SmartApplication.isDjiRegistered) {
            onResult(true, "registered")
            return
        }
        try {
            DJISDKManager.getInstance().registerApp(applicationContext,
                object : DJISDKManager.SDKManagerCallback {
                    override fun onRegister(error: DJIError?) {
                        if (error == DJISDKError.REGISTRATION_SUCCESS) {
                            SmartApplication.isDjiRegistered = true
                            DJISDKManager.getInstance().startConnectionToProduct()
                            mainHandler.post { onResult(true, "registered") }
                        } else {
                            mainHandler.post {
                                onResult(false, error?.description ?: "Registration gagal")
                            }
                        }
                    }
                    override fun onProductConnect(p: BaseProduct?)     {}
                    override fun onProductDisconnect()                 {}
                    override fun onProductChanged(p: BaseProduct?)     {}
                    override fun onComponentChange(
                        k: BaseProduct.ComponentKey?,
                        f: BaseComponent?, t: BaseComponent?) {}
                    override fun onInitProcess(e: DJISDKInitEvent?, total: Int) {}
                    override fun onDatabaseDownloadProgress(c: Long, t: Long) {}
                })
        } catch (e: Exception) {
            onResult(false, "DJI SDK error: ${e.message}")
        }
    }

    /**
     * Get magnetic declination for a location.
     * Used by QiblaScreen — mirrors Flutter 'smart.app/geomagnetic' MethodChannel.
     */
    fun getMagneticDeclination(lat: Float, lon: Float): Float {
        return try {
            GeomagneticField(lat, lon, 0f, System.currentTimeMillis()).declination
        } catch (_: Exception) { 0f }
    }
}

@Composable
fun SmartAppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(onNavigateTo = { route -> navController.navigate(route) })
        }
        composable("scanner") {
            ScannerScreen(onBack = { navController.popBackStack() })
        }
        composable("logo_scan") {
            LogoScanScreen(onBack = { navController.popBackStack() })
        }
        composable("qibla") {
            QiblaScreen(onBack = { navController.popBackStack() })
        }
        composable("prayer") {
            PrayerScreen(onBack = { navController.popBackStack() })
        }
        composable("tasbeeh") {
            TasbeehScreen(onBack = { navController.popBackStack() })
        }
        composable("calendar") {
            CalendarScreen(onBack = { navController.popBackStack() })
        }
        composable("drone") {
            DroneScreen(onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("halal_search") {
            HalalSearchScreen(onBack = { navController.popBackStack() })
        }
    }
}
