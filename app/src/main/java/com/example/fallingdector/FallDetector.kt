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
    private val gyroBuffer = mutableListOf<FloatArray>()  // Buffer para [Gx, Gy, Gz]

    // --- Almacenamiento de Última Lectura para la UI ---
    // Guardan la última lectura de cada sensor para poder enviar un paquete de datos combinado
    // al listener, asegurando que la UI siempre tenga la información más reciente de ambos sensores.
    private var lastAccel = FloatArray(4)
    private var lastGyro = FloatArray(3)

    // La variable que mantiene el estado actual de la máquina de estados.
    private var currentState: FallState = FallState.MONITORING

    /**
     * Interfaz de "Callback" (o patrón Listener). Permite a `FallDetector` comunicar eventos importantes
     * a su clase contenedora (en este caso, `FallDetectionService`) sin tener una dependencia directa de ella.
     * Esto se conoce como "Inversión de Control" y es una excelente práctica de diseño de software.
     */
    interface FallListener {
        fun onFallDetected() // Notifica cuando la IA confirma una caída.
        fun onDetectionStateChanged(state: FallState) // Notifica cada cambio en la máquina de estados.
        fun onSensorDataUpdated(ax: Float, ay: Float, az: Float, amag: Float, gx: Float, gy: Float, gz: Float) // Envía datos crudos para la UI.
    }

    /**
     * Inicia el proceso de detección. Crea el clasificador y registra los listeners de los sensores.
     */
    fun start() {
        try {
            // Inicializa el clasificador de IA.
            classifier = FallClassifier(context)
            // Registra esta clase como listener para ambos sensores. `SENSOR_DELAY_GAME` ofrece una buena
            // frecuencia de actualización para la detección en tiempo real sin ser excesiva.
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)
            // Establece el estado inicial y notifica al listener (el Service).
            currentState = FallState.MONITORING
            listener.onDetectionStateChanged(currentState)
            Log.i("FallDetector", "Detector iniciado. Estado inicial: MONITORING.")
        } catch (e: Exception) {
            // Si la inicialización falla (ej. el modelo de IA no se cargó), se captura el error
            // y se notifica para que el usuario sepa que la detección no está activa.
            Log.e("FallDetector", "Error crítico al iniciar el detector. La detección no funcionará.", e)
            listener.onDetectionStateChanged(FallState.MONITORING) // Notifica un estado seguro
        }
    }

    /**
     * Detiene la detección. Es CRUCIAL anular el registro del listener para evitar el consumo de batería
     * y pérdidas de memoria (`memory leaks`) cuando la detección ya no es necesaria.
     */
    fun stop() {
        sensorManager.unregisterListener(this)
        Log.i("FallDetector", "Detector detenido.")
    }

    /**
     * Este método es el corazón del detector. Se llama automáticamente por el sistema Android
     * cada vez que hay una nueva lectura de un sensor que hemos registrado.
     */
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { e ->
            // Un `when` para dirigir los datos del sensor correcto a la lógica correcta.
            when (e.sensor.type) {

                // --- LÓGICA DEL ACELERÓMETRO (EL SENSOR PRINCIPAL) ---
                Sensor.TYPE_ACCELEROMETER -> {
                    val ax = e.values[0]; val ay = e.values[1]; val az = e.values[2]
                    // La magnitud del vector de aceleración es el indicador clave de un impacto.
                    val amag = sqrt(ax * ax + ay * ay + az * az)

                    // Actualiza el último valor conocido del acelerómetro.
                    lastAccel = floatArrayOf(ax, ay, az, amag)
                    // Notifica al listener (el Service) con los datos más recientes de AMBOS sensores para la UI.
                    listener.onSensorDataUpdated(ax, ay, az, amag, lastGyro[0], lastGyro[1], lastGyro[2])

                    // --- LÓGICA DEL DISPARADOR (TRIGGER) ---
                    val MAG_PEAK_THRESHOLD = 20.0f // Umbral de fuerza G. Ajustable según la sensibilidad deseada.
                    if (currentState == FallState.MONITORING && amag > MAG_PEAK_THRESHOLD) {
                        // ¡DISPARADOR ACTIVADO! Hemos detectado un pico de energía.
                        currentState = FallState.CANDIDATE_EVENT // Cambiamos al estado de recolección.
                        accelBuffer.clear() // Limpiamos los buffers para empezar una nueva captura.
                        gyroBuffer.clear()
                        listener.onDetectionStateChanged(currentState) // Notificamos el cambio de estado.
                        Log.i("FallDetector", "Disparador activado (Pico > ${MAG_PEAK_THRESHOLD} Gs). Recolectando datos...")
                    }

                    // --- LÓGICA DE RECOLECCIÓN Y CLASIFICACIÓN ---
                    if (currentState == FallState.CANDIDATE_EVENT) {
                        // Mientras el buffer no esté lleno, seguimos añadiendo datos.
                        if (accelBuffer.size < BUFFER_SIZE) {
                            accelBuffer.add(floatArrayOf(ax, ay, az, amag))
                        }

                        // Una vez que el buffer del acelerómetro se llena, es hora de clasificar.
                        if (accelBuffer.size == BUFFER_SIZE) {
                            try {
                                // El clasificador espera `Array<FloatArray>`, por lo que convertimos las listas.
                                val accelData = accelBuffer.toTypedArray()
                                val gyroData = gyroBuffer.toTypedArray()

                                // Llamamos al clasificador con los datos recolectados.
                                val output = classifier.classify(accelWindow = accelData, gyroWindow = gyroData)

                                // La salida del modelo es un array de probabilidades. Asumimos que el índice 2 corresponde a "Caída".
                                val fallProb = output[2]
                                val FALL_THRESHOLD = 0.7f // Umbral de confianza de la IA.

                                if (fallProb > FALL_THRESHOLD) {
                                    // La IA está segura de que es una caída.
                                    currentState = FallState.CONFIRMED_FALL
                                    listener.onFallDetected() // Notificamos la caída confirmada.
                                    Log.w("FallDetector", "¡CAÍDA CONFIRMADA POR IA! Probabilidad: $fallProb")
                                } else {
                                    // La IA considera que no es una caída (un falso positivo).
                                    currentState = FallState.MONITORING // Volvemos al estado de monitoreo pasivo.
                                    Log.i("FallDetector", "Evento rechazado por la IA. Probabilidad de caída: $fallProb. Volviendo a monitorear.")
                                }
                            } catch (e: Exception) {
                                // Si la clasificación falla, volvemos a un estado seguro para no bloquear la app.
                                Log.e("FallDetector", "Error durante la clasificación.", e)
                                currentState = FallState.MONITORING
                            }

                            // Limpiamos los buffers y notificamos al listener para el siguiente ciclo.
                            accelBuffer.clear()
                            gyroBuffer.clear()
                            listener.onDetectionStateChanged(currentState)
                        }
                    }
                }

                // --- LÓGICA DEL GIROSCOPIO (SENSOR SECUNDARIO) ---
                Sensor.TYPE_GYROSCOPE -> {
                    val gx = e.values[0]; val gy = e.values[1]; val gz = e.values[2]
                    lastGyro = floatArrayOf(gx, gy, gz)

                    // Actualizamos la UI con los datos del giroscopio también.
                    listener.onSensorDataUpdated(lastAccel[0], lastAccel[1], lastAccel[2], lastAccel[3], gx, gy, gz)

                    // IMPORTANTE: El giroscopio solo añade sus datos al buffer si ya estamos en un
                    // estado de recolección, que fue iniciado por el acelerómetro. No puede iniciar la detección por sí mismo.
                    if (currentState == FallState.CANDIDATE_EVENT) {
                        if (gyroBuffer.size < BUFFER_SIZE) {
                            gyroBuffer.add(floatArrayOf(gx, gy, gz))
                        }
                    }
                }
            }
        }
    }

    /**
     * Requerido por la interfaz `SensorEventListener`, pero no lo necesitamos para esta aplicación.
     * Se deja vacío.
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}