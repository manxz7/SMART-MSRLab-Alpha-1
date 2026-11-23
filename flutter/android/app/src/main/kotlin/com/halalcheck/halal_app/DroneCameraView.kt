package com.halalcheck.halal_app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import android.view.View
import dji.sdk.camera.VideoFeeder
import dji.sdk.codec.DJICodecManager
import io.flutter.plugin.platform.PlatformView
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class DroneCameraView(private val context: Context) : PlatformView, TextureView.SurfaceTextureListener {

    private val textureView = TextureView(context)
    private var codecManager: DJICodecManager? = null
    private var videoDataListener: VideoFeeder.VideoDataListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        textureView.surfaceTextureListener = this
    }

    override fun getView(): View = textureView

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        startVideoFeed(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        codecManager?.cleanSurface()
        startVideoFeed(surface, width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopVideoFeed()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    private fun startVideoFeed(surface: SurfaceTexture, width: Int, height: Int) {
        try {
            codecManager = DJICodecManager(context, surface, width, height)
            val feeder = VideoFeeder.getInstance() ?: run {
                android.util.Log.w("DroneCameraView", "VideoFeeder null")
                return
            }
            val listener = VideoFeeder.VideoDataListener { videoBuffer, size ->
                codecManager?.sendDataToDecoder(videoBuffer, size)
            }
            videoDataListener = listener
            feeder.primaryVideoFeed?.addVideoDataListener(listener)
        } catch (e: Exception) {
            android.util.Log.e("DroneCameraView", "startVideoFeed error: ${e.message}", e)
        }
    }

    private fun stopVideoFeed() {
        try {
            videoDataListener?.let {
                VideoFeeder.getInstance()?.primaryVideoFeed?.removeVideoDataListener(it)
            }
            videoDataListener = null
            codecManager?.cleanSurface()
            codecManager?.destroyCodec()
            codecManager = null
        } catch (e: Exception) {
            android.util.Log.e("DroneCameraView", "stopVideoFeed error: ${e.message}", e)
        }
    }

    /**
     * Capture frame menggunakan DJI SDK's getBitmap() — terus dari hardware decoder.
     * Ini guna DJI's internal buffer, bukan TextureView.getBitmap() yang return hitam.
     *
     * Fallback: YUV callback kalau getBitmap() gagal.
     */
    fun captureNextFrame(callback: (ByteArray?) -> Unit) {
        val cm = codecManager ?: run {
            android.util.Log.w("DroneCameraView", "captureNextFrame: codecManager null")
            callback(null)
            return
        }

        val responded = AtomicBoolean(false)
        fun respond(jpeg: ByteArray?) {
            if (responded.getAndSet(true)) return  // already responded, skip
            mainHandler.post { callback(jpeg) }
        }

        android.util.Log.d("DroneCameraView", "Trying DJI getBitmap()...")

        cm.getBitmap(object : DJICodecManager.OnGetBitmapListener {
            override fun onGetBitmap(bitmap: Bitmap?) {
                if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                    android.util.Log.d("DroneCameraView", "getBitmap OK: ${bitmap.width}x${bitmap.height}")
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    val jpeg = stream.toByteArray()
                    android.util.Log.d("DroneCameraView", "JPEG: ${jpeg.size} bytes")
                    respond(jpeg)
                } else {
                    android.util.Log.w("DroneCameraView", "getBitmap null/empty — cuba YUV fallback")
                    captureViaYuv(cm) { jpeg -> respond(jpeg) }
                }
            }
        })

        // Global timeout 8 saat (cover getBitmap 5s + YUV 3s)
        // respond() ada guard sendiri — panggil terus, dia akan skip kalau dah jawab
        mainHandler.postDelayed({
            android.util.Log.w("DroneCameraView", "captureNextFrame timeout")
            respond(null)
        }, 8000)
    }

    /**
     * Fallback: ambil frame via YUV callback.
     * Enable YUV, ambil satu frame, disable semula (display pulih dalam 1-2 frame).
     */
    private fun captureViaYuv(cm: DJICodecManager, callback: (ByteArray?) -> Unit) {
        val done = AtomicBoolean(false)

        cm.setYuvDataCallback(object : DJICodecManager.YuvDataCallback {
            override fun onYuvDataReceived(
                format: MediaFormat?,
                yuvBuffer: ByteBuffer?,
                dataSize: Int,
                width: Int,
                height: Int
            ) {
                if (done.getAndSet(true)) return  // frame dah diambil

                cm.enabledYuvData(false)
                cm.setYuvDataCallback(null)

                if (yuvBuffer == null || dataSize == 0 || width <= 0 || height <= 0) {
                    android.util.Log.w("DroneCameraView", "YUV kosong")
                    mainHandler.post { callback(null) }
                    return
                }

                android.util.Log.d("DroneCameraView", "YUV frame: ${width}x${height} size=$dataSize")
                try {
                    val i420 = ByteArray(dataSize)
                    yuvBuffer.rewind()
                    yuvBuffer.get(i420, 0, minOf(dataSize, yuvBuffer.remaining()))

                    val nv21 = i420ToNv21(i420, width, height)
                    val yuvImg = android.graphics.YuvImage(nv21, ImageFormat.NV21, width, height, null)
                    val out = ByteArrayOutputStream()
                    yuvImg.compressToJpeg(Rect(0, 0, width, height), 85, out)
                    val jpeg = out.toByteArray()
                    android.util.Log.d("DroneCameraView", "YUV JPEG: ${jpeg.size} bytes")
                    mainHandler.post { callback(jpeg) }
                } catch (e: Exception) {
                    android.util.Log.e("DroneCameraView", "YUV→JPEG: ${e.message}")
                    mainHandler.post { callback(null) }
                }
            }
        })

        cm.enabledYuvData(true)

        // YUV timeout 3 saat
        mainHandler.postDelayed({
            if (done.getAndSet(true).not()) {  // baru mark done, belum respond
                cm.enabledYuvData(false)
                cm.setYuvDataCallback(null)
                android.util.Log.w("DroneCameraView", "YUV timeout")
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
            nv21[frameSize + i * 2]     = i420[vOffset + i]
            nv21[frameSize + i * 2 + 1] = i420[uOffset + i]
        }
        return nv21
    }

    override fun dispose() {
        stopVideoFeed()
    }
}
