package com.example.fallingdector

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * FallDetectorStatus es un objeto Singleton que actúa como la "Fuente Única de Verdad" (Single Source of Truth)
 * para el estado de la aplicación. Esto significa que centraliza toda la información sobre el estado de la detección
 * de caídas, asegurando que todos los componentes (servicios, actividades) accedan a datos consistentes.
 *
 * AL USAR UN `object` EN KOTLIN, se garantiza que solo exista una instancia de esta clase en toda la aplicación (patrón Singleton),
 * lo cual es ideal para gestionar un estado global.
 *
 * REEMPLAZA AL `LocalBroadcastManager` para una comunicación más moderna, segura y eficiente entre el `FallDetectionService`
 * (que se ejecuta en segundo plano) y la `MainActivity` (que se ejecuta en primer plano).
 */
object FallDetectorStatus {

    // --- LiveData para el Estado del Servicio ---

    /**
     * `_status` es un `MutableLiveData`. La palabra "Mutable" significa que su valor puede cambiar.
     * Se mantiene `private` para que solo este objeto (FallDetectorStatus) pueda modificarlo. Esto sigue el principio de encapsulación,
     * evitando que componentes externos, como la UI, modifiquen el estado directamente.
     * Se inicializa con el estado "Detenida".
     */
    private val _status = MutableLiveData<String>("Detenida")

    /**
     * `status` es un `LiveData` (no mutable). Esta es la versión pública y de solo lectura de `_status`.
     * Los componentes de la UI, como `MainActivity`, observarán este `LiveData`. Al ser de solo lectura,
     * la UI puede reaccionar a los cambios de estado, pero no puede cambiarlos, lo que previene errores y asegura un flujo de datos unidireccional.
     */
    val status: LiveData<String> = _status


    // --- LiveData para los Datos de los Sensores ---

    /**
     * `_sensorData` es el `MutableLiveData` privado para los datos crudos de los sensores. Al igual que con `_status`,
     * solo este objeto puede modificar su contenido.
     */
    private val _sensorData = MutableLiveData<String>("Datos del sensor no disponibles")

    /**
     * `sensorData` es la versión pública y de solo lectura que la `MainActivity` observará para mostrar
     * los valores del acelerómetro y el giroscopio en tiempo real.
     */
    val sensorData: LiveData<String> = _sensorData


    // --- Funciones para Actualizar el Estado ---

    /**
     * Actualiza el valor del estado de la detección (ej. "Monitoreando", "Analizando...").
     * Esta función está diseñada para ser llamada desde el `FallDetectionService`, que es quien conoce el estado real de la detección.
     *
     * @param newStatus El nuevo texto de estado que se debe mostrar en la aplicación.
     */
    fun updateStatus(newStatus: String) {
        /**
         * Se utiliza `postValue()` en lugar de `setValue()`. Esto es CRUCIAL.
         * `postValue()` es seguro para llamar desde hilos de segundo plano (background threads), como nuestro `FallDetectionService`.
         * Internamente, se encarga de despachar la actualización del valor al hilo principal (UI thread), donde los observadores de la UI
         * pueden recibir la actualización de forma segura.
         * Si usáramos `setValue()` desde un hilo que no es el principal, la aplicación se cerraría con un error (crash).
         */
        _status.postValue(newStatus)
    }

    /**
     * Actualiza el valor de los datos de los sensores que se muestran en la UI.
     * Será llamado continuamente desde el `FallDetectionService` cada vez que haya una nueva lectura de los sensores.
     *
     * @param newSensorData El nuevo texto con los datos de los sensores.
     */
    fun updateSensorData(newSensorData: String) {
        // Al igual que con `updateStatus`, se usa `postValue()` para garantizar la seguridad entre hilos.
        _sensorData.postValue(newSensorData)
    }
}
