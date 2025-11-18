package com.example.fallingdector

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

/**
 * `FallState` define una máquina de estados finitos que controla el flujo de la detección.
 * Esto es crucial para la eficiencia: la app no analiza datos constantemente, sino que reacciona a los eventos.
 */
enum class FallState {
    /** Estado 1: La app está escuchando pasivamente un pico de aceleración. Bajo consumo de recursos. */
    MONITORING,
    /** Estado 2: Se ha detectado un pico. La app está activamente recolectando una ventana de datos para su análisis. */
    CANDIDATE_EVENT,
    /** Estado 3: La IA ha confirmado que el evento fue una caída. El estado final para este detector. */
    CONFIRMED_FALL
}

/**
 * `FallDetector` es el núcleo de la lógica de detección. Orquesta la interacción entre los sensores del hardware,
 * la máquina de estados (`FallState`), y el clasificador de IA (`FallClassifier`).
 *
 * Implementa `SensorEventListener` para poder recibir actualizaciones de los sensores del sistema Android.
 */
class FallDetector(private val context: Context, private val listener: FallListener) : SensorEventListener {

    // --- Propiedades del Sistema de Sensores ---
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) // Sensor secundario para recolección de datos.

    // El cerebro de la IA. Será inicializado en `start()`.
    private lateinit var classifier: FallClassifier

    // --- Buffers para la Recolección de Datos ---
    private val BUFFER_SIZE = 500 // El tamaño debe coincidir exactamente con lo que el modelo de IA espera.
    private val accelBuffer = mutableListOf<FloatArray>() // Buffer para [Ax, Ay, Az, Amag]
    private val gyroBuffer = mutableListOf<FloatArray>()  // [Gx, Gy, Gz]

    // --- Almacenamiento de Última Lectura para la UI ---
    private var lastAccel = FloatArray(4)
    private var lastGyro = FloatArray(3)

    // La variable que mantiene el estado actual de la máquina de estados.
    private var currentState: FallState = FallState.MONITORING

    interface FallListener {
        fun onFallDetected()
        fun onDetectionStateChanged(state: FallState)
        fun onSensorDataUpdated(ax: Float, ay: Float, az: Float, amag: Float, gx: Float, gy: Float, gz: Float)
    }

    /**
     * Inicia el proceso de detección.
     * @throws IllegalStateException si el acelerómetro no está disponible en el dispositivo.
     */
    fun start() {
        if (accelerometer == null) {
            throw IllegalStateException("Acelerómetro no disponible en este dispositivo.")
        }

        try {
            classifier = FallClassifier(context)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)
            }
            currentState = FallState.MONITORING
            listener.onDetectionStateChanged(currentState)
            Log.i("FallDetector", "Detector iniciado. Estado inicial: MONITORING.")
        } catch (e: Exception) {
            Log.e("FallDetector", "Error crítico al iniciar el detector.", e)
            throw IllegalStateException("Error al inicializar el detector: ${e.message}")
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        Log.i("FallDetector", "Detector detenido.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { e ->
            when (e.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    val ax = e.values[0]; val ay = e.values[1]; val az = e.values[2]
                    val amag = sqrt(ax * ax + ay * ay + az * az)

                    lastAccel = floatArrayOf(ax, ay, az, amag)
                    listener.onSensorDataUpdated(ax, ay, az, amag, lastGyro[0], lastGyro[1], lastGyro[2])

                    val MAG_PEAK_THRESHOLD = 20.0f
                    if (currentState == FallState.MONITORING && amag > MAG_PEAK_THRESHOLD) {
                        currentState = FallState.CANDIDATE_EVENT
                        accelBuffer.clear()
                        gyroBuffer.clear()
                        listener.onDetectionStateChanged(currentState)
                        Log.i("FallDetector", "Disparador activado (Pico > ${MAG_PEAK_THRESHOLD} Gs). Recolectando datos...")
                    }

                    if (currentState == FallState.CANDIDATE_EVENT) {
                        if (accelBuffer.size < BUFFER_SIZE) {
                            accelBuffer.add(floatArrayOf(ax, ay, az, amag))
                        }

                        if (accelBuffer.size == BUFFER_SIZE) {
                            try {
                                val accelData = accelBuffer.toTypedArray()
                                val gyroData = gyroBuffer.toTypedArray()

                                val output = classifier.classify(accelWindow = accelData, gyroWindow = gyroData)
                                val fallProb = output[2]

                                // Log.d("AI_DEBUG", "Probabilidad de Caída Calculada: $fallProb")

                                val FALL_THRESHOLD = 0.4f

                                if (fallProb > FALL_THRESHOLD) {
                                    // Log.d("AI_DEBUG", "¡UMBRAL SUPERADO! El bloque IF se ha ejecutado.")

                                    currentState = FallState.CONFIRMED_FALL
                                    listener.onFallDetected()
                                    Log.w("FallDetector", "¡CAÍDA CONFIRMADA POR IA! Probabilidad: $fallProb")
                                } else {
                                    currentState = FallState.MONITORING
                                    Log.i("FallDetector", "Evento rechazado por la IA. Probabilidad de caída: $fallProb. Volviendo a monitorear.")
                                }
                            } catch (e: Exception) {
                                Log.e("FallDetector", "Error durante la clasificación.", e)
                                currentState = FallState.MONITORING
                            }

                            accelBuffer.clear()
                            gyroBuffer.clear()
                            listener.onDetectionStateChanged(currentState)
                        }
                    }
                }

                Sensor.TYPE_GYROSCOPE -> {
                    val gx = e.values[0]; val gy = e.values[1]; val gz = e.values[2]
                    lastGyro = floatArrayOf(gx, gy, gz)

                    listener.onSensorDataUpdated(lastAccel[0], lastAccel[1], lastAccel[2], lastAccel[3], gx, gy, gz)

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