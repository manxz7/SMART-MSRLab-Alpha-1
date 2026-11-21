package dji.sdk.codec

import android.content.Context
import android.view.SurfaceHolder

class DJICodecManager(
    context: Context,
    surfaceHolder: SurfaceHolder,
    val videoWidth: Int,
    val videoHeight: Int
) {
    fun sendDataToDecoder(data: ByteArray?, size: Int) {}
    fun cleanSurface() {}
    fun destroyCodec() {}
    fun destroy() {}
}
