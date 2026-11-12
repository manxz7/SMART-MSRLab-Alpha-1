package dji.sdk.sdkmanager

import android.content.Context
import dji.common.error.DJIError
import dji.common.error.DJISDKError
import dji.sdk.base.BaseComponent
import dji.sdk.base.BaseProduct

class DJISDKInitEvent(val message: String = "")

class DJISDKManager {
    interface SDKManagerCallback {
        fun onRegister(error: DJIError?)
        fun onProductConnect(p: BaseProduct?)
        fun onProductDisconnect()
        fun onProductChanged(p: BaseProduct?)
        fun onComponentChange(k: BaseProduct.ComponentKey?, f: BaseComponent?, t: BaseComponent?)
        fun onInitProcess(e: DJISDKInitEvent?, total: Int)
        fun onDatabaseDownloadProgress(c: Long, t: Long)
    }

    var product: BaseProduct? = null

    fun registerApp(context: Context, callback: SDKManagerCallback) {
        callback.onRegister(DJISDKError.REGISTRATION_SUCCESS)
    }

    fun startConnectionToProduct() {}

    companion object {
        private val instance = DJISDKManager()
        @JvmStatic
        fun getInstance(): DJISDKManager = instance
    }
}
