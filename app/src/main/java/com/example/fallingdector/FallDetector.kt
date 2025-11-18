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
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private lateinit var classifier: FallClassifier

    private val BUFFER_SIZE = 500
    private val accelBuffer = mutableListOf<FloatArray>() // [Ax, Ay, Az, Amag]
    private val gyroBuffer = mutableListOf<FloatArray>()  // [Gx, Gy, Gz]

    // Almacenan la última lectura para enviar un paquete de datos completo al listener
    private var lastAccel = FloatArray(4)
    private var lastGyro = FloatArray(3)

    private var currentState: FallState = FallState.MONITORING

    interface FallListener {
        fun onFallDetected()
        fun onDetectionStateChanged(state: FallState)
        fun onSensorDataUpdated(ax: Float, ay: Float, az: Float, amag: Float, gx: Float, gy: Float, gz: Float)
    }

    fun start() {
        try {
            classifier = FallClassifier(context)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)
            currentState = FallState.MONITORING
            listener.onDetectionStateChanged(currentState)
            Log.i("FallDetector", "Oyentes de acelerómetro, giroscopio y clasificador iniciados.")
        } catch (e: Exception) {
            Log.e("FallDetector", "Error al iniciar el detector. La detección no funcionará: ${e.message}", e)
            listener.onDetectionStateChanged(FallState.MONITORING)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        Log.i("FallDetector", "Oyentes de sensores detenidos.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    val ax = it.values[0]
                    val ay = it.values[1]
                    val az = it.values[2]
                    val amag = sqrt(ax * ax + ay * ay + az * az)

                    lastAccel[0] = ax; lastAccel[1] = ay; lastAccel[2] = az; lastAccel[3] = amag

                    // 1. Envía siempre el paquete de datos más reciente al listener
                    listener.onSensorDataUpdated(ax, ay, az, amag, lastGyro[0], lastGyro[1], lastGyro[2])

                    // 2. Log de los valores en tiempo real
                    Log.d("FallDetector_Data", "Ax:${String.format("%.2f", ax)}, Ay:${String.format("%.2f", ay)}, Z:${String.format("%.2f", az)}, Mag:${String.format("%.2f", amag)}")

                    val MAG_PEAK_THRESHOLD = 20.0f

                    if (currentState == FallState.MONITORING && amag > MAG_PEAK_THRESHOLD) {
                        currentState = FallState.CANDIDATE_EVENT
                        accelBuffer.clear()
                        gyroBuffer.clear() // Limpiar ambos búferes al inicio de un evento
                        listener.onDetectionStateChanged(currentState)
                        Log.i("FallDetector", "Candidato a evento detectado. Recolectando datos de acelerómetro y giroscopio...")
                    }

                    if (currentState == FallState.CANDIDATE_EVENT) {
                        if (accelBuffer.size < BUFFER_SIZE) {
                            accelBuffer.add(floatArrayOf(ax, ay, az, amag))
                        }

                        // La lógica de clasificación se dispara cuando el buffer principal (acelerómetro) se llena
                        if (accelBuffer.size == BUFFER_SIZE) {
                            try {
                                val accelBufferArray = accelBuffer.toTypedArray()
                                val gyroBufferArray = gyroBuffer.toTypedArray()

                                // Llamada al clasificador corregida con los nombres de los argumentos
                                val output = classifier.classify(accelWindow = accelBufferArray, gyroWindow = gyroBufferArray)

                                val fallProb = output[2] // Asumiendo que el índice 2 es la clase 'Fall'
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

                            accelBuffer.clear()
                            gyroBuffer.clear()
                            listener.onDetectionStateChanged(currentState)
                        }
                    }
                }

                Sensor.TYPE_GYROSCOPE -> {
                    val gx = it.values[0]
                    val gy = it.values[1]
                    val gz = it.values[2]

                    lastGyro[0] = gx; lastGyro[1] = gy; lastGyro[2] = gz

                    // Envía el paquete de datos actualizado
                    listener.onSensorDataUpdated(lastAccel[0], lastAccel[1], lastAccel[2], lastAccel[3], gx, gy, gz)

                    // Log de los valores en tiempo real
                    Log.d("FallDetector_Data", "Gx:${String.format("%.2f", gx)}, Gy:${String.format("%.2f", gy)}, Gz:${String.format("%.2f", gz)}")

                    if (currentState == FallState.CANDIDATE_EVENT) {
                        if (gyroBuffer.size < BUFFER_SIZE) {
                            gyroBuffer.add(floatArrayOf(gx, gy, gz))
                        }
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
