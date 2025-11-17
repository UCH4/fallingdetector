package com.example.fallingdector

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

// Enum para el control de flujo
enum class FallState {
    MONITORING,
    CANDIDATE_EVENT,
    CONFIRMED_FALL
}

class FallDetector(private val context: Context, private val listener: FallListener) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private lateinit var classifier: FallClassifier

    private val BUFFER_SIZE = 500
    private val accelBuffer = mutableListOf<FloatArray>() // [Ax, Ay, Az, Amag]
    private var currentState: FallState = FallState.MONITORING

    interface FallListener {
        fun onFallDetected()
        fun onDetectionStateChanged(state: FallState)
        fun onSensorDataUpdated(ax: Float, ay: Float, az: Float, amag: Float)
    }

    fun start() {
        try {
            classifier = FallClassifier(context)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            currentState = FallState.MONITORING
            listener.onDetectionStateChanged(currentState)
            Log.i("FallDetector", "Oyente de sensor y clasificador iniciados.")
        } catch (e: Exception) {
            Log.e("FallDetector", "Error al iniciar el detector. La detección no funcionará: ${e.message}", e)
            listener.onDetectionStateChanged(FallState.MONITORING)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        Log.i("FallDetector", "Oyente de sensor detenido.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val ax = it.values[0]
            val ay = it.values[1]
            val az = it.values[2]
            val amag = sqrt(ax * ax + ay * ay + az * az)

            // 1. Envía los datos del sensor en tiempo real
            listener.onSensorDataUpdated(ax, ay, az, amag)

            // 2. Log de los valores en tiempo real
            Log.d("FallDetector_Data", "Ax:${String.format("%.2f", ax)}, Ay:${String.format("%.2f", ay)}, Z:${String.format("%.2f", az)}, Mag:${String.format("%.2f", amag)}")

            val MAG_PEAK_THRESHOLD = 20.0f

            if (currentState == FallState.MONITORING && amag > MAG_PEAK_THRESHOLD) {
                currentState = FallState.CANDIDATE_EVENT
                accelBuffer.clear() // Limpiar el búfer al inicio de un evento
                listener.onDetectionStateChanged(currentState)
                Log.i("FallDetector", "Candidato a evento detectado. Recolectando datos...")
            }

            if (currentState == FallState.CANDIDATE_EVENT) {
                if (accelBuffer.size < BUFFER_SIZE) {
                    accelBuffer.add(floatArrayOf(ax, ay, az, amag))
                }

                if (accelBuffer.size == BUFFER_SIZE) {
                    // Buffer lleno, listo para clasificación
                    try {
                        val bufferArray = accelBuffer.toTypedArray()
                        val output = classifier.classify(bufferArray)
                        // Asumiendo que el índice 2 es la clase 'Fall'
                        val fallProb = output[2]
                        val FALL_THRESHOLD = 0.7f

                        if (fallProb > FALL_THRESHOLD) {
                            currentState = FallState.CONFIRMED_FALL
                            listener.onFallDetected()
                            Log.w("FallDetector", "IA CONFIRMA CAÍDA. Probabilidad: $fallProb")
                        } else {
                            currentState = FallState.MONITORING
                            Log.i("FallDetector", "IA rechaza caída. Probabilidad: $fallProb")
                        }

                    } catch (e: Exception) {
                        Log.e("FallDetector", "Error en la clasificación: ${e.message}", e)
                        currentState = FallState.MONITORING
                    }

                    // Limpiar el buffer y volver a monitoreo si no fue confirmada (la confirmación la detiene el servicio)
                    accelBuffer.clear()
                    listener.onDetectionStateChanged(currentState)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}