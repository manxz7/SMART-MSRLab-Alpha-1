package com.smart.msrlab.drone

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.YuvImage
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.TextureView
import dji.sdk.camera.VideoFeeder
import dji.sdk.codec.DJICodecManager
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DroneVideoView — paparan live video drone DJI + tangkap frame sebagai JPEG.
 *
 * Port terus dari DroneCameraView.kt + MainActivity.captureFrame() dalam app Flutter
 * (flutter/android/app/src/main/kotlin/com/halalcheck/halal_app/), yang telah diuji
 * dengan DJI Mini 2.
 *
 * Cara tangkap frame (ikut urutan, sama seperti Flutter):
 *   1. PixelCopy tetingkap Activity → crop ke kawasan view ini.
 *      (getBitmap() biasa return hitam sebab decoder DJI render terus ke GPU.)
 *   2. Jika hitam/gagal → DJICodecManager.getBitmap()
 *   3. Jika masih gagal → data YUV dari decoder → JPEG
 */
class DroneVideoView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {

    private var codecManager: DJICodecManager? = null
    private var videoDataListener: VideoFeeder.VideoDataListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        surfaceTextureListener = this
    }

    // ── Kitaran hayat surface ────────────────────────────────────────────────

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        startVideoFeed(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        stopVideoFeed()
        startVideoFeed(surface, width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopVideoFeed()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    private fun startVideoFeed(surface: SurfaceTexture, width: Int, height: Int) {
        try {
            val cm = DJICodecManager(context, surface, width, height)
            codecManager = cm
            val listener = VideoFeeder.VideoDataListener { videoBuffer, size ->
                codecManager?.sendDataToDecoder(videoBuffer, size)
            }
            videoDataListener = listener
            VideoFeeder.getInstance()?.primaryVideoFeed?.addVideoDataListener(listener)
        } catch (e: Exception) {
            Log.e(TAG, "startVideoFeed error: ${e.message}", e)
        }
    }

    fun stopVideoFeed() {
        try {
            videoDataListener?.let {
                VideoFeeder.getInstance()?.primaryVideoFeed?.removeVideoDataListener(it)
            }
            videoDataListener = null
            codecManager?.cleanSurface()
            codecManager?.destroyCodec()
            codecManager = null
        } catch (e: Exception) {
            Log.e(TAG, "stopVideoFeed error: ${e.message}", e)
        }
    }

    // ── Tangkap frame ────────────────────────────────────────────────────────

    /**
     * Tangkap frame semasa sebagai JPEG. [callback] dipanggil di main thread
     * dengan bytes JPEG, atau null jika semua cara gagal.
     */
    fun captureFrame(activity: Activity, callback: (ByteArray?) -> Unit) {
        if (width == 0 || height == 0) {
            callback(null)
            return
        }
        val rootView = activity.window.decorView
        val fullBmp = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(activity.window, fullBmp, { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    val loc = IntArray(2)
                    getLocationInWindow(loc)
                    val x = loc[0].coerceIn(0, fullBmp.width - 1)
                    val y = loc[1].coerceIn(0, fullBmp.height - 1)
                    val w = width.coerceIn(1, fullBmp.width - x)
                    val h = height.coerceIn(1, fullBmp.height - y)
                    val cropped = Bitmap.createBitmap(fullBmp, x, y, w, h)
                    fullBmp.recycle()

                    val center = cropped.getPixel(cropped.width / 2, cropped.height / 2)
                    val isBlack = (center and 0x00FFFFFF) < 0x080808
                    Log.d(TAG, "PixelCopy OK: ${w}x$h, isBlack=$isBlack")
                    if (isBlack) {
                        cropped.recycle()
                        captureFromDecoder(callback)
                    } else {
                        val out = ByteArrayOutputStream()
                        cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        cropped.recycle()
                        callback(out.toByteArray())
                    }
                } else {
                    Log.w(TAG, "PixelCopy gagal: $copyResult")
                    fullBmp.recycle()
                    captureFromDecoder(callback)
                }
            }, mainHandler)
        } catch (e: Exception) {
            Log.e(TAG, "PixelCopy exception: ${e.message}")
            fullBmp.recycle()
            captureFromDecoder(callback)
        }
    }

    /** Sandaran: minta bitmap terus dari decoder DJI, kemudian YUV. */
    private fun captureFromDecoder(callback: (ByteArray?) -> Unit) {
        val cm = codecManager ?: run { callback(null); return }
        val responded = AtomicBoolean(false)
        fun respond(jpeg: ByteArray?) {
            if (responded.getAndSet(true)) return
            mainHandler.post { callback(jpeg) }
        }

        cm.getBitmap(object : DJICodecManager.OnGetBitmapListener {
            override fun onGetBitmap(bitmap: Bitmap?) {
                if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    respond(out.toByteArray())
                } else {
                    captureViaYuv(cm) { respond(it) }
                }
            }
        })
        mainHandler.postDelayed({ respond(null) }, 8000)
    }

    private fun captureViaYuv(cm: DJICodecManager, callback: (ByteArray?) -> Unit) {
        val done = AtomicBoolean(false)
        cm.setYuvDataCallback(object : DJICodecManager.YuvDataCallback {
            override fun onYuvDataReceived(
                format: MediaFormat?, yuvBuffer: ByteBuffer?, dataSize: Int, width: Int, height: Int
            ) {
                if (done.getAndSet(true)) return
                cm.enabledYuvData(false)
                cm.setYuvDataCallback(null)
                if (yuvBuffer == null || dataSize == 0 || width <= 0 || height <= 0) {
                    callback(null); return
                }
                try {
                    val i420 = ByteArray(dataSize)
                    yuvBuffer.rewind()
                    yuvBuffer.get(i420, 0, minOf(dataSize, yuvBuffer.remaining()))
                    val nv21 = i420ToNv21(i420, width, height)
                    val out = ByteArrayOutputStream()
                    YuvImage(nv21, ImageFormat.NV21, width, height, null)
                        .compressToJpeg(Rect(0, 0, width, height), 85, out)
                    callback(out.toByteArray())
                } catch (e: Exception) {
                    Log.e(TAG, "YUV→JPEG: ${e.message}")
                    callback(null)
                }
            }
        })
        cm.enabledYuvData(true)
        mainHandler.postDelayed({
            if (!done.getAndSet(true)) {
                cm.enabledYuvData(false)
                cm.setYuvDataCallback(null)
                callback(null)
            }
        }, 3000)
    }

    private fun i420ToNv21(i420: ByteArray, width: Int, height: Int): ByteArray {
        val frameSize = width * height
        val nv21 = ByteArray(frameSize * 3 / 2)
        System.arraycopy(i420, 0, nv21, 0, frameSize)
        val uOffset = frameSize
        val vOffset = frameSize + frameSize / 4
        for (i in 0 until frameSize / 4) {
            nv21[frameSize + i * 2] = i420[vOffset + i]
            nv21[frameSize + i * 2 + 1] = i420[uOffset + i]
        }
        return nv21
    }

    companion object {
        private const val TAG = "DroneVideoView"
    }
}
