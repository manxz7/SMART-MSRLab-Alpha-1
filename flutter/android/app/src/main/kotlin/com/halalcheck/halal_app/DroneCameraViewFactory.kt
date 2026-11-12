package com.halalcheck.halal_app

import android.content.Context
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class DroneCameraViewFactory : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    var currentView: DroneCameraView? = null

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val view = DroneCameraView(context)
        currentView = view
        return view
    }
}
