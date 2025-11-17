package com.example.fallingdector

// src/main/java/com/example/fallingdector/FallDetector.kt
import android.os.SystemClock
import kotlin.math.abs

// Estados de la máquina de estados para la detección de caídas
enum class FallState {
    NORMAL, // Estado inicial
    FREE_FALL, // Caída libre (aceleración cercana a cero)
    IMPACT // Aceleración alta después de la caída libre
}

class FallDetector {
    private var currentState: FallState = FallState.NORMAL
    private var lastTimestamp: Long = 0
    private var freeFallStartTime: Long = 0

    // Parámetros ajustables
    private val FREE_FALL_THRESHOLD_MIN = 3.0 // Aceleración para detectar "gravedad cero"
    private val IMPACT_THRESHOLD_MIN = 20.0 // Aceleración para detectar el impacto
    private val FREE_FALL_DURATION_MIN = 100 // Duración mínima de la caída libre en ms
    private val FREE_FALL_DURATION_MAX = 500 // Duración máxima de la caída libre en ms

    fun detectFall(acceleration: Double): Boolean {
        val currentTime = SystemClock.elapsedRealtime()

        when (currentState) {
            FallState.NORMAL -> {
                // Si la aceleración es baja, transita a estado de caída libre
                if (acceleration < FREE_FALL_THRESHOLD_MIN) {
                    currentState = FallState.FREE_FALL
                    freeFallStartTime = currentTime
                    lastTimestamp = currentTime
                }
            }
            FallState.FREE_FALL -> {
                val freeFallDuration = currentTime - freeFallStartTime
                // Si la aceleración vuelve a ser normal, o la caída libre dura demasiado,
                // vuelve al estado normal
                if (acceleration >= FREE_FALL_THRESHOLD_MIN || freeFallDuration > FREE_FALL_DURATION_MAX) {
                    currentState = FallState.NORMAL
                }
                // Si la caída libre dura lo suficiente y hay un impacto
                else if (acceleration > IMPACT_THRESHOLD_MIN && freeFallDuration > FREE_FALL_DURATION_MIN) {
                    currentState = FallState.IMPACT
                    return true // ¡Caída detectada!
                }
            }
            FallState.IMPACT -> {
                // Vuelve al estado normal
                currentState = FallState.NORMAL
            }
        }
        lastTimestamp = currentTime
        return false
    }

    fun reset() {
        currentState = FallState.NORMAL
    }
}