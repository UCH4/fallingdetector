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
    private val NUM_FEATURES = 4
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
            // ERROR CRÍTICO: Lanza la excepción con un mensaje CLARO
            Log.e("FallClassifier", "ERROR CRÍTICO: No se pudo cargar el modelo. Verifique assets y Gradle. Detalle: ${e.message}", e)
            throw IllegalStateException("Fallo al cargar $MODEL_NAME. Revisa el Logcat (tag FallClassifier) y verifica tus dependencias de TFLite.", e)
        }
    }

    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        // Carga el archivo desde la carpeta app/src/main/assets/
        val fileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun convertToByteBuffer(window: Array<FloatArray>): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * NUM_FEATURES)
        byteBuffer.order(ByteOrder.nativeOrder())

        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until NUM_FEATURES) {
                byteBuffer.putFloat(window[i][j])
            }
        }
        return byteBuffer
    }

    fun classify(window: Array<FloatArray>): FloatArray {
        // Asegura que el tamaño de entrada es el correcto antes de la inferencia
        if (window.size != INPUT_SIZE || window[0].size != NUM_FEATURES) {
            throw IllegalArgumentException("El tamaño de la ventana de datos es incorrecto para la IA.")
        }

        val inputBuffer = convertToByteBuffer(window)
        val output = Array(1) { FloatArray(NUM_CLASSES) }

        tflite.run(inputBuffer, output)
        return output[0]
    }
}