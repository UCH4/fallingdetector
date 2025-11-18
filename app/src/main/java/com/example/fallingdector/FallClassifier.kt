package com.example.fallingdector

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FallClassifier es la clase responsable de interactuar con el modelo de Machine Learning (TensorFlow Lite).
 * Su única función es cargar el modelo desde los assets, recibir los datos de los sensores
 * y devolver la probabilidad de que esos datos representen una caída.
 */
class FallClassifier(context: Context) {

    // El intérprete de TFLite. Es el motor que ejecuta el modelo. Se declara `lateinit` porque su inicialización
    // puede fallar y se realiza en el bloque `init`.
    private lateinit var tflite: Interpreter

    // --- Constantes que definen la estructura del Modelo de IA ---

    // El número de muestras de sensor que el modelo espera en una ventana de tiempo (ej. 500 muestras en 2.5 segundos).
    private val INPUT_SIZE = 500
    // El número de características (features) que el modelo usa para cada muestra del acelerómetro.
    // En nuestro caso: Aceleración en X, Y, Z y la Magnitud del vector de aceleración.
    private val NUM_ACCEL_FEATURES = 4
    // El número de clases de salida que el modelo puede predecir. Por ejemplo: ["Caminando", "Sentado", "Caída"].
    private val NUM_CLASSES = 3
    // El nombre exacto del archivo del modelo, que debe estar en la carpeta `app/src/main/assets/`.
    private val MODEL_NAME = "fall_detector_model.tflite"

    /**
     * El bloque `init` se ejecuta cuando se crea una instancia de `FallClassifier`.
     * Su principal responsabilidad es cargar el modelo de TFLite y preparar el intérprete.
     * Este proceso es crítico y está envuelto en un `try-catch` para evitar que la app se inicie si el modelo no se puede cargar.
     */
    init {
        try {
            // 1. Carga el archivo del modelo en memoria de la forma más eficiente posible.
            val model = loadModelFile(context, MODEL_NAME)

            // 2. Configura las opciones del intérprete. Aquí, por ejemplo, se limita el número de hilos
            // para optimizar el rendimiento en dispositivos móviles.
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }

            // 3. Crea la instancia del intérprete con el modelo cargado y las opciones.
            tflite = Interpreter(model, options)
            Log.i("FallClassifier", "Modelo TFLite cargado y clasificador listo.")

        } catch (e: Exception) {
            // Si algo falla (ej. el archivo no existe, está corrupto, o la dependencia de TFLite falta),
            // se captura el error, se registra con detalle y se lanza una `IllegalStateException`.
            // Esto detiene la creación del objeto y, por ende, de la app, lo cual es deseable porque la app no puede funcionar sin el modelo.
            Log.e("FallClassifier", "ERROR CRÍTICO: No se pudo inicializar el clasificador. Detalle: ${e.message}", e)
            throw IllegalStateException("Fallo al cargar el modelo de IA ($MODEL_NAME). La app no puede funcionar.", e)
        }
    }

    /**
     * Carga el archivo del modelo TFLite desde la carpeta `assets` como un `MappedByteBuffer`.
     * USAR `MappedByteBuffer` ES LA FORMA RECOMENDADA Y MÁS EFICIENTE. En lugar de copiar todo el archivo
     * del modelo a la memoria RAM de la app, crea un mapeo directo a la región de memoria donde está el archivo,
     * ahorrando memoria y tiempo de carga.
     */
    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        // `openFd` obtiene un descriptor de archivo del asset, necesario para el mapeo.
        val fileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        // Aquí ocurre el mapeo: se crea una vista de solo lectura del archivo en memoria.
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Convierte la ventana de datos del acelerómetro (un array de arrays de floats en Kotlin) a un `ByteBuffer`.
     * El intérprete de TFLite opera con Buffers de bytes, no directamente con tipos de datos de Kotlin.
     */
    private fun convertAccelToByteBuffer(window: Array<FloatArray>): ByteBuffer {
        // `allocateDirect` reserva memoria fuera del heap de la JVM. Esto es más rápido porque el sistema operativo
        // puede acceder a esta memoria directamente sin pasar por la JVM, lo cual es ideal para bibliotecas nativas como TFLite.
        // El tamaño es: 4 bytes (un Float) * 500 muestras * 4 features por muestra.
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * NUM_ACCEL_FEATURES)
        // `nativeOrder()` asegura que el orden de los bytes sea el mismo que usa la plataforma nativa, evitando errores de interpretación.
        byteBuffer.order(ByteOrder.nativeOrder())

        // Itera sobre cada muestra y cada feature, y los añade al buffer en el orden correcto.
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until NUM_ACCEL_FEATURES) {
                byteBuffer.putFloat(window[i][j])
            }
        }
        return byteBuffer
    }

    /**
     * El método principal de la clase. Ejecuta la inferencia (clasificación) en la ventana de datos.
     *
     * @param accelWindow La ventana de datos del acelerómetro (500x4).
     * @param gyroWindow La ventana de datos del giroscopio (actualmente no se usa, pero está para futuras mejoras del modelo).
     * @return Un array de floats (`FloatArray`) donde cada posición representa la probabilidad de una clase (ej. output[2] = probabilidad de caída).
     */
    fun classify(accelWindow: Array<FloatArray>, gyroWindow: Array<FloatArray>? = null): FloatArray {
        // Cláusula de guarda: valida que los datos de entrada tengan la forma que el modelo espera. Si no, lanza un error claro.
        if (accelWindow.size != INPUT_SIZE || accelWindow[0].size != NUM_ACCEL_FEATURES) {
            throw IllegalArgumentException("Dimensiones de datos de entrada incorrectas para el modelo de IA.")
        }

        // TODO: Cuando el modelo se actualice para usar el giroscopio, aquí es donde se combinarían
        // los datos de `accelWindow` y `gyroWindow` en un único `ByteBuffer` de entrada.

        // 1. Convierte los datos de Kotlin a un formato que TFLite entienda.
        val inputBuffer = convertAccelToByteBuffer(accelWindow)

        // 2. Prepara el array de salida. La forma es [1][NUM_CLASSES] porque procesamos 1 ventana a la vez (batch size = 1).
        val output = Array(1) { FloatArray(NUM_CLASSES) }

        // 3. Ejecuta la inferencia. El intérprete toma el buffer de entrada y llena el array de salida con las probabilidades.
        tflite.run(inputBuffer, output)

        // 4. Devuelve solo el primer (y único) resultado del batch.
        return output[0]
    }
}