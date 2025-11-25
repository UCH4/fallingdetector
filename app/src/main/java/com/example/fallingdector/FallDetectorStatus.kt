package com.example.fallingdector

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * FallDetectorStatus es un objeto Singleton que actúa como la "Fuente Única de Verdad" (Single Source of Truth)
 * para el estado de la aplicación. Centraliza toda la información sobre el estado de la detección
 * asegurando que todos los componentes accedan a datos consistentes.
 */
object FallDetectorStatus {

    // --- LiveData para el Estado del Servicio ---
    private val _status = MutableLiveData<String>("Detenida")
    val status: LiveData<String> = _status

    // --- LiveData para los Datos de los Sensores ---
    private val _sensorData = MutableLiveData<String>("Datos del sensor no disponibles")
    val sensorData: LiveData<String> = _sensorData

    // --- **NUEVO**: Indicador de Estado de la UI ---
    /**
     * Este flag nos permite saber si la MainActivity está en primer plano (visible para el usuario).
     * Es 'volatile' para asegurar que los cambios realizados desde el hilo principal (UI) sean inmediatamente
     * visibles para otros hilos (como el del servicio).
     */
    @Volatile
    var isAppInForeground = false

    // --- Funciones para Actualizar el Estado ---

    /**
     * Actualiza el valor del estado de la detección (ej. "Monitoreando", "Analizando...").
     * Se usa `postValue()` para garantizar que la actualización se ejecute en el hilo de la UI de forma segura.
     */
    fun updateStatus(newStatus: String) {
        _status.postValue(newStatus)
    }

    /**
     * Actualiza el valor de los datos de los sensores que se muestran en la UI.
     */
    fun updateSensorData(newSensorData: String) {
        _sensorData.postValue(newSensorData)
    }
}
