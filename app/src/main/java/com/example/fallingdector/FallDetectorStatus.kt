package com.example.fallingdector

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Objeto Singleton para gestionar el estado de la detección de caídas en toda la app.
 *
 * Esto reemplaza el sistema de Broadcasts por una fuente de datos centralizada y segura (Single Source of Truth).
 * Usa LiveData para que los componentes de la UI (como MainActivity) puedan observar los cambios
 * de forma segura y consciente del ciclo de vida.
 */
object FallDetectorStatus {

    // LiveData para el estado principal (ej: "Monitoreando", "Caída Detectada", etc.)
    private val _status = MutableLiveData<String>("Detenida")
    val status: LiveData<String> = _status

    // LiveData para los datos crudos del sensor, para visualización en la UI
    private val _sensorData = MutableLiveData<String>("Datos del sensor no disponibles")
    val sensorData: LiveData<String> = _sensorData

    /**
     * Actualiza el estado de la detección.
     * Esta función será llamada desde el FallDetectionService.
     */
    fun updateStatus(newStatus: String) {
        // postValue se asegura de que la actualización ocurra en el hilo principal (UI)
        _status.postValue(newStatus)
    }

    /**
     * Actualiza los datos del sensor que se muestran en la UI.
     * Esta función será llamada desde el FallDetectionService.
     */
    fun updateSensorData(newSensorData: String) {
        _sensorData.postValue(newSensorData)
    }
}
