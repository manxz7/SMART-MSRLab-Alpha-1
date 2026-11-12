package dji.sdk.base

open class BaseProduct {
    open val isConnected: Boolean = false
    open val model: Model? = null

    class Model(val displayName: String = "DJI Drone")
    class ComponentKey
}
