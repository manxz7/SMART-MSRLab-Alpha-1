package com.halalcheck.halal_app

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import android.view.View
import dji.sdk.camera.VideoFeeder
import dji.sdk.codec.DJICodecManager
import io.flutter.plugin.platform.PlatformView
import java.io.ByteArrayOutputStream

class DroneCameraView(private val context: Context) : PlatformView, TextureView.SurfaceTextureListener {

    private val textureView = TextureView(context)
    private var codecManager: DJICodecManager? = null
    private var videoDataListener: VideoFeeder.VideoDataListener? = null

    // Pending frame capture: set by captureNextFrame(), fired on next SurfaceTexture update
    @Volatile private var pendingCapture: ((ByteArray?) -> Unit)? = null

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

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        val callback = pendingCapture ?: return
        pendingCapture = null
        // Ensure getBitmap() runs on main thread — SurfaceTextureUpdated may fire on GL thread
        Handler(Looper.getMainLooper()).post {
            try {
                val bitmap = textureView.getBitmap()
                if (bitmap != null && bitmap.width > 0) {
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
                    callback(stream.toByteArray())
                } else {
                    android.util.Log.w("DroneCameraView", "getBitmap() returned null/empty")
                    callback(null)
                }
            } catch (e: Exception) {
                android.util.Log.e("DroneCameraView", "captureOnUpdate error: ${e.message}")
                callback(null)
            }
        }
    }

    private fun startVideoFeed(surface: SurfaceTexture, width: Int, height: Int) {
        try {
            codecManager = DJICodecManager(context, surface, width, height)
            val feeder = VideoFeeder.getInstance()
            if (feeder == null) {
                android.util.Log.w("DroneCameraView", "VideoFeeder.getInstance() returned null")
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
            pendingCapture = null
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
     * Schedule a frame capture on the next SurfaceTexture update tick.
     * The callback receives JPEG bytes or null on failure/timeout.
     * Timeout: 5 seconds — if no frame arrives (drone disconnected?), returns null.
     */
    fun captureNextFrame(callback: (ByteArray?) -> Unit) {
        pendingCapture = callback
        Handler(Looper.getMainLooper()).postDelayed({
            if (pendingCapture != null) {
                pendingCapture = null
                android.util.Log.w("DroneCameraView", "captureNextFrame timeout — no frame received")
                callback(null)
            }
        }, 5000)
    }

    /** Synchronous fallback — may return black if hardware decoder hasn't flushed yet */
    fun getBitmapBytes(): ByteArray? {
        return try {
            val bitmap = textureView.getBitmap() ?: return null
            val stream = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
            stream.toByteArray()
        } catch (e: Exception) {
            android.util.Log.e("DroneCameraView", "getBitmapBytes error: ${e.message}")
            null
        }
    }

    override fun dispose() {
        stopVideoFeed()
    }
}
