package dji.sdk.products

import dji.sdk.base.BaseProduct
import dji.sdk.camera.Camera

class Aircraft : BaseProduct() {
    val camera: Camera? = Camera()
}
