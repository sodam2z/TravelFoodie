package com.travelfoodie.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "ShakeDetector"
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastShakeTime = 0L
    private val shakeThreshold = 1.3 // G-force threshold (lowered for emulator testing - 1.0 is gravity)
    private val shakeCooldown = 1500L // milliseconds
    private var lastLogTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    fun start() {
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            android.util.Log.d(TAG, "ShakeDetector started - accelerometer registered")
        } else {
            android.util.Log.e(TAG, "No accelerometer sensor available on this device!")
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        android.util.Log.d(TAG, "ShakeDetector stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Calculate delta movement for each axis separately
            val deltaX = kotlin.math.abs(x - lastX)
            val deltaY = kotlin.math.abs(y - lastY)
            val deltaZ = kotlin.math.abs(z - lastZ)

            lastX = x
            lastY = y
            lastZ = z

            val gForce = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
            val now = SystemClock.elapsedRealtime()

            // Threshold for single axis movement (lowered for emulator)
            val singleAxisThreshold = 3.0f

            // Log sensor values periodically (every 2 seconds) for debugging
            if (now - lastLogTime > 2000) {
                android.util.Log.d(TAG, "Accelerometer: gForce=${"%.2f".format(gForce)}, deltaX=${"%.2f".format(deltaX)}, deltaY=${"%.2f".format(deltaY)}, deltaZ=${"%.2f".format(deltaZ)}")
                lastLogTime = now
            }

            // Detect shake if ANY single axis moves significantly (easier for emulator)
            val isShake = gForce > shakeThreshold || deltaX > singleAxisThreshold || deltaY > singleAxisThreshold || deltaZ > singleAxisThreshold

            if (isShake && now - lastShakeTime > shakeCooldown) {
                android.util.Log.d(TAG, "SHAKE DETECTED! gForce=${"%.2f".format(gForce)}, deltaX=${"%.2f".format(deltaX)}, deltaY=${"%.2f".format(deltaY)}, deltaZ=${"%.2f".format(deltaZ)}")
                lastShakeTime = now
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }
}
