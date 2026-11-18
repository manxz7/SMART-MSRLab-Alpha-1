package com.smart.msrlab.service

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.*

/**
 * CompassService — Real-time hardware sensor fusion for Qibla compass.
 *
 * Features:
 *   1. Calculate Qibla bearing (Great Circle formula) to Mecca (21.4225° N, 39.8262° E).
 *   2. Real-time True North heading using GeomagneticField declination.
 *   3. Dual sensor strategy: Primary TYPE_ROTATION_VECTOR (sensor fusion),
 *      Fallback TYPE_ACCELEROMETER + TYPE_MAGNETIC_FIELD for universal device support.
 *   4. Low-pass filter for smooth 60fps live updates.
 */
object CompassService {

    private const val MECCA_LAT = 21.4225
    private const val MECCA_LNG = 39.8262

    /**
     * Calculate Qibla bearing from user → Mecca using the Great Circle formula (degrees, 0–360°).
     */
    fun calculateQiblaAngle(userLat: Double, userLng: Double): Double {
        val lat1     = Math.toRadians(userLat)
        val lat2     = Math.toRadians(MECCA_LAT)
        val deltaLng = Math.toRadians(MECCA_LNG - userLng)
        val x        = sin(deltaLng) * cos(lat2)
        val y        = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLng)
        val bearing  = atan2(x, y) * 180.0 / Math.PI
        return (bearing + 360.0) % 360.0
    }

    /**
     * Get magnetic declination (degrees East +, West −) for user location (IGRF model).
     */
    fun getMagneticDeclination(lat: Double, lng: Double): Float {
        return try {
            GeomagneticField(
                lat.toFloat(), lng.toFloat(), 0f,
                System.currentTimeMillis(),
            ).declination
        } catch (_: Exception) { 0f }
    }

    /**
     * Start live compass Flow.
     * Emits real-time CompassReading on hardware sensor events.
     */
    fun startCompass(context: Context, userLat: Double, userLng: Double): Flow<CompassReading> =
        callbackFlow {
            val qiblaAngle    = calculateQiblaAngle(userLat, userLng)
            val declination   = getMagneticDeclination(userLat, userLng)
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

            val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            val accelSensor    = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val magSensor      = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

            if (rotationSensor == null && (accelSensor == null || magSensor == null)) {
                close(Exception("No compass hardware sensor found on this device."))
                return@callbackFlow
            }

            var accuracyStatus = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
            var currentSmoothedHeading = -1.0

            val listener = object : SensorEventListener {
                private val rotMat      = FloatArray(9)
                private val orientation = FloatArray(3)

                // Fallback arrays
                private val lastAccel   = FloatArray(3)
                private val lastMag     = FloatArray(3)
                private var hasAccel    = false
                private var hasMag      = false

                override fun onSensorChanged(event: SensorEvent) {
                    var rawAzimuthDeg = Double.NaN

                    if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                        SensorManager.getRotationMatrixFromVector(rotMat, event.values)
                        SensorManager.getOrientation(rotMat, orientation)
                        rawAzimuthDeg = (Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0
                    } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        System.arraycopy(event.values, 0, lastAccel, 0, 3)
                        hasAccel = true
                    } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                        System.arraycopy(event.values, 0, lastMag, 0, 3)
                        hasMag = true
                    }

                    if (rawAzimuthDeg.isNaN() && hasAccel && hasMag) {
                        if (SensorManager.getRotationMatrix(rotMat, null, lastAccel, lastMag)) {
                            SensorManager.getOrientation(rotMat, orientation)
                            rawAzimuthDeg = (Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0
                        }
                    }

                    if (!rawAzimuthDeg.isNaN()) {
                        // True North Heading = Magnetic Heading + Declination
                        val trueHeading = (rawAzimuthDeg + declination + 360.0) % 360.0

                        // Smooth updates via exponential filter (0.35 factor for responsive & steady needle)
                        val smoothedHeading = if (currentSmoothedHeading < 0) {
                            trueHeading
                        } else {
                            var diff = (trueHeading - currentSmoothedHeading) % 360.0
                            if (diff > 180.0) diff -= 360.0
                            if (diff < -180.0) diff += 360.0
                            (currentSmoothedHeading + diff * 0.35 + 360.0) % 360.0
                        }
                        currentSmoothedHeading = smoothedHeading

                        // Needle rotation to point to Qibla relative to top of phone
                        val needleRotation = (qiblaAngle - smoothedHeading + 360.0) % 360.0

                        val accuracyDeg = when (accuracyStatus) {
                            SensorManager.SENSOR_STATUS_ACCURACY_HIGH   ->  5.0
                            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 15.0
                            SensorManager.SENSOR_STATUS_ACCURACY_LOW    -> 45.0
                            else                                         -> 999.0
                        }

                        trySend(CompassReading(
                            heading         = smoothedHeading,
                            qiblaAngle      = qiblaAngle,
                            needleRotation  = needleRotation,
                            accuracyDegrees = accuracyDeg,
                        ))
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    if (accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE) {
                        accuracyStatus = accuracy
                    }
                }
            }

            if (rotationSensor != null) {
                sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
            } else {
                sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_GAME)
                sensorManager.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_GAME)
            }

            awaitClose {
                sensorManager.unregisterListener(listener)
            }
        }
}

/**
 * CompassReading — Live data point emitted by CompassService
 */
data class CompassReading(
    /** Which direction the top of the phone is pointing relative to True North (0–360°) */
    val heading: Double,
    /** Calculated bearing from user location to Mecca (0–360°) */
    val qiblaAngle: Double,
    /** Degrees to rotate the Qibla arrow to point at Mecca (0–360°) */
    val needleRotation: Double,
    /** Sensor accuracy uncertainty in degrees */
    val accuracyDegrees: Double,
)
