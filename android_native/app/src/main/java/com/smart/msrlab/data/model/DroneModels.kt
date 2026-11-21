package com.smart.msrlab.data.model

/**
 * DroneStatus — status sambungan DJI drone
 *
 * Sepadan dengan Flutter drone_service.dart:
 *   "connected:DJI Mini 2" / "disconnected" / "error: ..."
 */
sealed class DroneStatus {
    /** Drone bersambung — modelName = "DJI Mini 2" */
    data class Connected(val modelName: String) : DroneStatus()

    /** Drone tidak bersambung — reason = punca (untuk debug) */
    data class Disconnected(val reason: String = "") : DroneStatus()

    /** Ralat SDK */
    data class Error(val message: String) : DroneStatus()

    /** SDK belum didaftar */
    object Unregistered : DroneStatus()
}

/**
 * DroneLogEntry — satu baris dalam log panel DroneScreen
 */
data class DroneLogEntry(
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * ScanState — state untuk Scan Halal operation di DroneScreen
 */
sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    data class Success(
        val matches: List<com.smart.msrlab.data.model.HalalProduct>,
        val detectedText: String,
    ) : ScanState()
    data class NotFound(val detectedText: String) : ScanState()
    data class Error(val message: String) : ScanState()
}
