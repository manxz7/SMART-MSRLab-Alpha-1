package dji.common.error

open class DJIError(open val description: String = "")

class DJISDKError(override val description: String = "") : DJIError(description) {
    companion object {
        @JvmField
        val REGISTRATION_SUCCESS: DJISDKError = DJISDKError("Registration Success")
    }
}
