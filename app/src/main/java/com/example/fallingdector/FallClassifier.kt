package com.example.fallingdector

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FallClassifier(context: Context) {

    private lateinit var tflite: Interpreter
    private val INPUT_SIZE = 500
    // Por ahora, el modelo solo usa 4 features del acelerómetro (Ax, Ay, Az, Amag)
    private val NUM_ACCEL_FEATURES = 4
    private val NUM_CLASSES = 3
    private val MODEL_NAME = "fall_detector_model.tflite"

    init {
        try {
            val model = loadModelFile(context, MODEL_NAME)
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            tflite = Interpreter(model, options)
            Log.i("FallClassifier", "Modelo TFLite cargado correctamente: $MODEL_NAME")
        } catch (e: Exception) {
            Log.e("FallClassifier", "ERROR CRÍTICO: No se pudo cargar el modelo. Detalle: ${e.message}", e)
            throw IllegalStateException("Fallo al cargar $MODEL_NAME. Revisa el Logcat (tag FallClassifier) y verifica tus dependencias de TFLite.", e)
        }
    }

    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // El ByteBuffer ahora solo se usa para los datos del acelerómetro
    private fun convertAccelToByteBuffer(window: Array<FloatArray>): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * NUM_ACCEL_FEATURES)
        byteBuffer.order(ByteOrder.nativeOrder())

        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until NUM_ACCEL_FEATURES) {
                byteBuffer.putFloat(window[i][j])
            }
        }
        return byteBuffer
    }

    // El segundo parámetro `gyroWindow` no se usa por ahora, pero está listo para el futuro.
    fun classify(accelWindow: Array<FloatArray>, gyroWindow: Array<FloatArray>? = null): FloatArray {
        // La validación se hace solo sobre los datos del acelerómetro, que son los que usa el modelo actual.
        if (accelWindow.size != INPUT_SIZE || accelWindow[0].size != NUM_ACCEL_FEATURES) {
            throw IllegalArgumentException("El tamaño de la ventana de datos del acelerómetro es incorrecto para la IA.")
        }

        // TODO: Cuando el modelo se actualice, aquí se combinarían los datos de accelWindow y gyroWindow
        // en un único ByteBuffer de entrada. Por ahora, ignoramos gyroWindow.

        val inputBuffer = convertAccelToByteBuffer(accelWindow)
        val output = Array(1) { FloatArray(NUM_CLASSES) }

        tflite.run(inputBuffer, output)
        return output[0]
    }
}