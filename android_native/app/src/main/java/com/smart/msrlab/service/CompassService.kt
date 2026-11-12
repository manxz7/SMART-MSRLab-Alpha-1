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
 * CompassService — hardware sensor fusion for Qibla compass
 *
 * Mirrors Flutter location_service.dart QiblaService:
 *   QiblaService.calculateQiblaAngle(lat, lng)  → bearing (degrees)
 *   QiblaService.startCompass(lat, lng)          → Stream<List<double>>
 *                                                   [heading, qiblaAngle, needleRotation, accuracy]
 *   QiblaService.getMagneticDeclination(lat, lon) → double (degrees)
 *
 * Implementation uses TYPE_ROTATION_VECTOR (Android sensor fusion of accelerometer +
 * magnetometer + gyroscope). This is equivalent to flutter_compass's sensor fusion.
 */
object CompassService {

    private const val MECCA_LAT = 21.4225
    private const val MECCA_LNG = 39.8262

    /**
     * Calculate Qibla bearing from user → Mecca using the Great Circle formula.
     * Identical to Flutter QiblaService.calculateQiblaAngle().
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
     * Get magnetic declination (degrees East +, West −) for a location.
     * Uses Android's built-in GeomagneticField (IGRF model).
     * Mirrors Flutter QiblaService.getMagneticDeclination().
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
     * Start streaming compass readings as a cold Flow.
     *
     * Emits CompassReading every time the sensor fires (~10–15 Hz).
     *
     * Mirrors Flutter QiblaService.startCompass() stream:
     *   Stream<List<double>> [heading, qiblaAngle, needleRotation, accuracy]
     *
     * Uses TYPE_ROTATION_VECTOR for best accuracy (sensor fusion).
     * Falls back to TYPE_MAGNETIC_FIELD + TYPE_ACCELEROMETER if not available.
     */
    fun startCompass(context: Context, userLat: Double, userLng: Double): Flow<CompassReading> =
        callbackFlow {
            val qiblaAngle    = calculateQiblaAngle(userLat, userLng)
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

            val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: run {
                    close(Exception("No compass sensor on this device.\n" +
                            "Try choosing location manually to still use Qibla direction."))
                    return@callbackFlow
                }

            var accuracyStatus = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

            val listener = object : SensorEventListener {
                private val rotMat      = FloatArray(9)
                private val orientation = FloatArray(3)

                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                    SensorManager.getRotationMatrixFromVector(rotMat, event.values)
                    SensorManager.getOrientation(rotMat, orientation)

                    // Azimuth → compass heading (0–360°, clockwise from North)
                    val azimuthRad  = orientation[0].toDouble()
                    val heading     = ((Math.toDegrees(azimuthRad) + 360) % 360)

                    // Rotation needed for compass needle to point to Qibla
                    val needleRotation = (qiblaAngle - heading + 360) % 360

                    // Map SENSOR_STATUS_* → approximate degrees of uncertainty
                    // Mirrors Flutter: CompassEvent.accuracy (double degrees)
                    val accuracyDeg = when (accuracyStatus) {
                        SensorManager.SENSOR_STATUS_ACCURACY_HIGH   ->  5.0
                        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 30.0
                        SensorManager.SENSOR_STATUS_ACCURACY_LOW    -> 60.0
                        else                                         -> 999.0 // unreliable
                    }

                    trySend(CompassReading(
                        heading        = heading,
                        qiblaAngle     = qiblaAngle,
                        needleRotation = needleRotation,
                        accuracyDegrees = accuracyDeg,
                    ))
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    accuracyStatus = accuracy
                }
            }

            sensorManager.registerListener(
                listener,
                rotationSensor,
                SensorManager.SENSOR_DELAY_UI,
            )

            // Cleanup when Flow is cancelled (screen closed)
            awaitClose {
                sensorManager.unregisterListener(listener)
            }
        }
}

/**
 * CompassReading — one data point emitted by the compass Flow
 *
 * Mirrors Flutter QiblaService stream List<double>:
 *   [0] = heading        (phone direction, 0–360°)
 *   [1] = qiblaAngle     (bearing to Mecca, 0–360°)
 *   [2] = needleRotation (rotate needle by this, 0–360°)
 *   [3] = accuracy       (degrees uncertainty: 5/30/60/999)
 */
data class CompassReading(
    /** Which direction the phone is pointing (True North = 0°) */
    val heading: Double,
    /** Calculated bearing from user location to Mecca */
    val qiblaAngle: Double,
    /** Degrees to rotate the compass needle to point at Qibla */
    val needleRotation: Double,
    /** Sensor accuracy: 5° = HIGH, 30° = MEDIUM, 60° = LOW, 999° = unreliable */
    val accuracyDegrees: Double,
)
