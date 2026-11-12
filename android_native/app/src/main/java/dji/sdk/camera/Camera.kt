package dji.sdk.camera

import dji.common.util.CommonCallbacks

class Camera {
    fun startShootPhoto(callback: CommonCallbacks.CompletionCallback) {
        callback.onResult(null)
    }

    fun startRecordVideo(callback: CommonCallbacks.CompletionCallback) {
        callback.onResult(null)
    }

    fun stopRecordVideo(callback: CommonCallbacks.CompletionCallback) {
        callback.onResult(null)
    }
}
