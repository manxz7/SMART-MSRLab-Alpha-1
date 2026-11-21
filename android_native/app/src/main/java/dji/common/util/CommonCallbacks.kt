package dji.common.util

import dji.common.error.DJIError

class CommonCallbacks {
    fun interface CompletionCallback {
        fun onResult(error: DJIError?)
    }
}
