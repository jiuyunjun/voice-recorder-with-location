package com.example.voicerecorderlocation.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.HandlerThread
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

data class ImuReading(
    val aNorth: Float,      // world-frame acceleration, North  (m/s²)
    val aEast: Float,       // world-frame acceleration, East   (m/s²)
    val timestampNs: Long   // from SensorEvent.timestamp (elapsedRealtimeNanos)
)

/**
 * Reads TYPE_LINEAR_ACCELERATION (gravity already removed) and
 * TYPE_ROTATION_VECTOR, then rotates the device-frame acceleration
 * into the Earth/world frame (X=East, Y=North) using the rotation matrix.
 *
 * Emits one [ImuReading] per accelerometer sample (~50 Hz).
 * Returns emptyFlow() if the required sensors are not available.
 */
class ImuSampler(context: Context) {
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun readings(): Flow<ImuReading> {
        val accelSensor    = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (accelSensor == null || rotationSensor == null) return emptyFlow()

        return callbackFlow {
            val rotationMatrix   = FloatArray(9)
            val rotationVector   = FloatArray(5)
            var hasRotation      = false

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_ROTATION_VECTOR -> {
                            // Copy values defensively (event.values is reused by the system)
                            System.arraycopy(event.values, 0, rotationVector, 0,
                                minOf(event.values.size, rotationVector.size))
                            hasRotation = true
                        }
                        Sensor.TYPE_LINEAR_ACCELERATION -> {
                            if (!hasRotation) return
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
                            // Android world frame: X=East, Y=North, Z=Up
                            // acc_world = R * acc_device
                            val ax = event.values[0]
                            val ay = event.values[1]
                            val az = event.values[2]
                            val aEast  = rotationMatrix[0]*ax + rotationMatrix[1]*ay + rotationMatrix[2]*az
                            val aNorth = rotationMatrix[3]*ax + rotationMatrix[4]*ay + rotationMatrix[5]*az
                            trySend(ImuReading(aNorth, aEast, event.timestamp))
                        }
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
            }

            // Run callbacks on a dedicated background thread
            val thread = HandlerThread("ImuSamplerThread").also { it.start() }
            val handler = android.os.Handler(thread.looper)

            sensorManager.registerListener(listener, rotationSensor,
                SensorManager.SENSOR_DELAY_UI,   handler)   // rotation changes slowly
            sensorManager.registerListener(listener, accelSensor,
                SensorManager.SENSOR_DELAY_GAME, handler)   // ~50 Hz

            awaitClose {
                sensorManager.unregisterListener(listener)
                thread.quitSafely()
            }
        }
    }
}
