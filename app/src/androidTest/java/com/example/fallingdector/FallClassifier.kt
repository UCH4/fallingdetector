package com.example.fallingdector

// Debes añadir el modelo .tflite a la carpeta 'app/src/main/assets'
// para que esta clase pueda cargarlo correctamente.

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class FallClassifier(private val context: Context) {

    private var tflite: Interpreter? = null
    // Parámetros para el modelo (deben coincidir con el entrenamiento)
    private val INPUT_SIZE = 500 // El tamaño de la ventana (500 muestras)
    private val NUM_FEATURES = 1 // Solo usamos la magnitud de aceleración (A = sqrt(x²+y²+z²))

    init {
        try {
            tflite = Interpreter(loadModelFile())
            Log.d("FallClassifier", "Modelo TFLite cargado correctamente.")
        } catch (e: Exception) {
            Log.e("FallClassifier", "Error al cargar el modelo TFLite: ${e.message}")
            tflite = null // Asegura que no se intente usar un intérprete nulo
        }
    }

    private fun loadModelFile(): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd("fall_detector_model.tflite")
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength

        // Mapea el modelo a un ByteBuffer directamente desde el archivo
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Clasifica el evento utilizando el modelo de Machine Learning.
     * @param dataBuffer La ventana de 10 segundos (500 muestras) de la magnitud de aceleración.
     * @return true si el modelo predice "FALL", false si predice "ADL" o "SIT_DOWN_FAST".
     */
    fun classify(dataBuffer: List<Float>): Boolean {
        if (tflite == null) {
            Log.e("FallClassifier", "El intérprete TFLite no está inicializado. Usando lógica de fallback.")
            // Lógica de Fallback: si el impacto fue muy alto, asume que es una caída.
            return dataBuffer.maxOrNull() ?: 0f > 2.5f
        }

        if (dataBuffer.size != INPUT_SIZE) {
            Log.w("FallClassifier", "Tamaño de buffer incorrecto. Se requiere $INPUT_SIZE.")
            return false
        }

        // 1. Preparar el buffer de entrada: (1 x 500 x 1) - Batch, Time, Features
        val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * NUM_FEATURES).apply {
            order(ByteOrder.nativeOrder())
            rewind()
            dataBuffer.forEach { putFloat(it) }
        }

        // 2. Preparar el array de salida: (1 x N_CLASSES)
        // Asumiendo 3 clases: 0=ADL, 1=SIT_DOWN_FAST, 2=FALL
        val outputArray = Array(1) { FloatArray(3) }

        // 3. Ejecutar la inferencia
        try {
            tflite!!.run(inputBuffer, outputArray)
        } catch (e: Exception) {
            Log.e("FallClassifier", "Error de ejecución TFLite: ${e.message}")
            return false
        }

        // 4. Post-procesamiento: encontrar la clase con la mayor probabilidad
        val probabilities = outputArray[0]
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1

        // Asumiendo que la clase 2 es "FALL"
        val isFall = maxIndex == 2

        Log.d("FallClassifier", "Probabilidades: ADL=${probabilities[0]}, SIT=${probabilities[1]}, FALL=${probabilities[2]}")

        return isFall
    }

    fun close() {
        tflite?.close()
    }
}