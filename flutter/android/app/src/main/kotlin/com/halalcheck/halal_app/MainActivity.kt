package com.halalcheck.halal_app

import android.hardware.GeomagneticField
import android.os.Handler
import android.os.Looper
import dji.common.error.DJIError
import dji.common.error.DJISDKError
import dji.sdk.base.BaseComponent
import dji.sdk.base.BaseProduct
import dji.sdk.products.Aircraft
import dji.sdk.sdkmanager.DJISDKInitEvent
import dji.sdk.sdkmanager.DJISDKManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMessageCodec

class MainActivity : FlutterActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var cameraViewFactory: DroneCameraViewFactory

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // ── Platform View: Drone live camera feed ────────────────────────────
        cameraViewFactory = DroneCameraViewFactory()
        flutterEngine.platformViewsController.registry
            .registerViewFactory("smart.app/drone_camera", cameraViewFactory)

        // ── Channel 1: Geomagnetic (Qibla compass — tidak berubah) ──────────
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "smart.app/geomagnetic")
            .setMethodCallHandler { call, result ->
                if (call.method == "getDeclination") {
                    val lat  = call.argument<Double>("lat")!!.toFloat()
                    val lon  = call.argument<Double>("lon")!!.toFloat()
                    val alt  = call.argument<Double>("altitude")!!.toFloat()
                    val time = call.argument<Long>("time")!!
                    val field = GeomagneticField(lat, lon, alt, time)
                    result.success(field.declination.toDouble())
                } else {
                    result.notImplemented()
                }
            }

        // ── Channel 2: DJI Drone ─────────────────────────────────────────────
        // Flutter calls → MainActivity (Kotlin) → DJI SDK → Drone
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "smart.app/dji")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "registerSDK"      -> registerDJISDK(result)
                    "isSDKRegistered"  -> result.success(DJIApplication.isRegistered)
                    "getDroneStatus"   -> getDroneStatus(result)
                    "capturePhoto"     -> capturePhoto(result)
                    "captureFrame"     -> captureFrame(result)
                    "startRecording"   -> startRecording(result)
                    "stopRecording"    -> stopRecording(result)
                    else               -> result.notImplemented()
                }
            }
    }

    /**
     * Daftar DJI App Key dengan server DJI (perlu internet).
     * Kalau dah pernah register dalam session ini, terus return success.
     */
    private fun registerDJISDK(result: MethodChannel.Result) {
        if (DJIApplication.isRegistered) {
            result.success("registered")
            return
        }
        try { DJISDKManager.getInstance() } catch (e: Exception) {
            result.error("SDK_INIT_FAILED", "DJI SDK tidak dapat dimuatkan: ${e.message}", null)
            return
        }
        DJISDKManager.getInstance().registerApp(applicationContext, object : DJISDKManager.SDKManagerCallback {
            override fun onRegister(error: DJIError?) {
                if (error == DJISDKError.REGISTRATION_SUCCESS) {
                    DJIApplication.isRegistered = true
                    DJISDKManager.getInstance().startConnectionToProduct()
                    mainHandler.post { result.success("registered") }
                } else {
                    val msg = error?.description ?: "Registration gagal"
                    mainHandler.post { result.error("REG_FAILED", msg, null) }
                }
            }
            override fun onProductConnect(product: BaseProduct?)     {}
            override fun onProductDisconnect()                       {}
            override fun onProductChanged(product: BaseProduct?)     {}
            override fun onComponentChange(
                key: BaseProduct.ComponentKey?,
                from: BaseComponent?,
                to: BaseComponent?
            )                                                        {}
            override fun onInitProcess(event: DJISDKInitEvent?, total: Int) {}
            override fun onDatabaseDownloadProgress(current: Long, total: Long) {}
        })
    }

    /**
     * Semak sama ada drone bersambung dengan app.
     * Returns: "connected:DJI Mini 2" atau "disconnected: <debug info>"
     */
    private fun getDroneStatus(result: MethodChannel.Result) {
        val isReg = DJIApplication.isRegistered
        val product = try { DJISDKManager.getInstance().product } catch (e: Exception) { null }
        android.util.Log.d("DJI_DEBUG", "isRegistered=$isReg, product=$product, connected=${product?.isConnected}")
        if (product == null || !product.isConnected) {
            val reason = when {
                !isReg              -> "SDK belum didaftar"
                product == null     -> "RC tidak dikesan (semak USB permission)"
                else                -> "Drone tidak dikesan (hidupkan drone)"
            }
            result.success("disconnected: $reason")
        } else {
            val name = product.model?.displayName ?: "Unknown Drone"
            result.success("connected:$name")
        }
    }

    /**
     * Trigger kamera drone untuk ambil satu gambar.
     * Gambar disimpan dalam SD card drone (bukan dalam phone).
     * Returns: "photo_captured" atau exception dengan mesej error.
     *
     * Flow: setMode(SHOOT_PHOTO) → startShootPhoto → callback
     */
    private fun capturePhoto(result: MethodChannel.Result) {
        val aircraft = DJISDKManager.getInstance().product as? Aircraft
        val camera   = aircraft?.camera

        if (aircraft == null || !aircraft.isConnected) {
            result.error("NOT_CONNECTED", "Drone tidak bersambung", null)
            return
        }
        if (camera == null) {
            result.error("NO_CAMERA", "Kamera tidak dapat diakses", null)
            return
        }
        // DJI Mini 2 firmware baru: startShootPhoto tidak disokong semasa live streaming.
        // Gunakan butang fizikal pada RC atau DJI Fly app untuk ambil gambar.
        // Kami cuba tetapi expect kemungkinan gagal.
        camera.startShootPhoto { shootError ->
            if (shootError == null) {
                mainHandler.post { result.success("photo_captured") }
            } else {
                mainHandler.post {
                    result.error("NOT_SUPPORTED",
                        "Ambil gambar tidak disokong via SDK semasa live stream. " +
                        "Gunakan butang RC atau DJI Fly app.", null)
                }
            }
        }
    }

    /**
     * Capture frame menggunakan PixelCopy — tangkap terus dari GPU framebuffer.
     * getBitmap() return hitam untuk DJI hardware decoder sebab ia render ke GPU texture,
     * bypass CPU. PixelCopy baca dari GPU surface yang sama yang nampak di skrin.
     *
     * Cara: tangkap FULL window → crop ke kawasan TextureView.
     * Ini elak masalah koordinat yang berlaku kalau terus specify Rect.
     */
    private fun captureFrame(result: MethodChannel.Result) {
        val cameraView = cameraViewFactory.currentView
        if (cameraView == null) {
            result.error("NO_FRAME", "Kamera view belum siap", null)
            return
        }
        val view = cameraView.getView()
        if (view.width == 0 || view.height == 0) {
            result.error("NO_FRAME", "Kamera view belum dirender", null)
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // Tangkap seluruh window sebagai satu bitmap
            val rootView = window.decorView
            val fullBmp = android.graphics.Bitmap.createBitmap(
                rootView.width, rootView.height,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            try {
                android.view.PixelCopy.request(window, fullBmp, { copyResult ->
                    if (copyResult == android.view.PixelCopy.SUCCESS) {
                        // Crop ke kawasan TextureView sahaja
                        val loc = IntArray(2)
                        view.getLocationInWindow(loc)
                        val x = loc[0].coerceIn(0, fullBmp.width - 1)
                        val y = loc[1].coerceIn(0, fullBmp.height - 1)
                        val w = view.width.coerceIn(1, fullBmp.width - x)
                        val h = view.height.coerceIn(1, fullBmp.height - y)

                        val cropped = android.graphics.Bitmap.createBitmap(fullBmp, x, y, w, h)
                        fullBmp.recycle()

                        // Semak sama ada gambar hitam (pixel tengah = hitam)
                        val centerPixel = cropped.getPixel(cropped.width / 2, cropped.height / 2)
                        val isBlack = (centerPixel and 0x00FFFFFF) < 0x080808
                        android.util.Log.d("CaptureFrame", "PixelCopy OK: ${w}x${h}, centerPixel=$centerPixel, isBlack=$isBlack")

                        val stream = java.io.ByteArrayOutputStream()
                        cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
                        mainHandler.post { result.success(stream.toByteArray()) }
                    } else {
                        android.util.Log.w("CaptureFrame", "PixelCopy failed code: $copyResult")
                        // Fallback: captureNextFrame
                        cameraView.captureNextFrame { bytes ->
                            mainHandler.post {
                                if (bytes != null) result.success(bytes)
                                else result.error("NO_FRAME", "PixelCopy($copyResult) + getBitmap gagal", null)
                            }
                        }
                    }
                }, mainHandler)
            } catch (e: Exception) {
                android.util.Log.e("CaptureFrame", "PixelCopy exception: ${e.message}")
                result.error("NO_FRAME", "PixelCopy exception: ${e.message}", null)
            }
        } else {
            // Android < 8: guna getBitmap sahaja
            cameraView.captureNextFrame { bytes ->
                mainHandler.post {
                    if (bytes != null) result.success(bytes)
                    else result.error("NO_FRAME", "getBitmap gagal", null)
                }
            }
        }
    }

    /**
     * Mula rekod video. Tukar mode ke RECORD_VIDEO dulu, kemudian start recording.
     * Video disimpan dalam SD card drone.
     */
    private fun startRecording(result: MethodChannel.Result) {
        val aircraft = DJISDKManager.getInstance().product as? Aircraft
        val camera   = aircraft?.camera
        if (aircraft == null || !aircraft.isConnected) {
            result.error("NOT_CONNECTED", "Drone tidak bersambung", null)
            return
        }
        if (camera == null) {
            result.error("NO_CAMERA", "Kamera tidak dapat diakses", null)
            return
        }
        // DJI Mini 2 firmware baru tak perlukan setMode — terus record
        camera.startRecordVideo { error ->
            if (error == null) {
                mainHandler.post { result.success("recording_started") }
            } else {
                mainHandler.post {
                    result.error("REC_ERR", error.description, null)
                }
            }
        }
    }

    /**
     * Berhenti rekod video. Video akan disimpan automatik dalam SD card drone.
     */
    private fun stopRecording(result: MethodChannel.Result) {
        val aircraft = DJISDKManager.getInstance().product as? Aircraft
        val camera   = aircraft?.camera
        if (camera == null) {
            result.error("NO_CAMERA", "Kamera tidak dapat diakses", null)
            return
        }
        camera.stopRecordVideo { error ->
            if (error == null) {
                mainHandler.post { result.success("recording_stopped") }
            } else {
                mainHandler.post {
                    result.error("REC_ERR", error?.description ?: "Unknown error", null)
                }
            }
        }
    }
}
