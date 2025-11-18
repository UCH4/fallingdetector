package com.example.fallingdector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

// Clave para pasar el número de teléfono de emergencia de la MainActivity a este Servicio.
private const val KEY_PHONE = "emergency_phone"

/**
 * `FallDetectionService` es un `Service` de Android, diseñado para ejecutar tareas de larga duración en segundo plano.
 * Su propósito principal es alojar el `FallDetector` y mantenerlo vivo incluso si el usuario cierra la aplicación.
 *
 * AL HEREDAR DE `Service`, le decimos al sistema Android que esta clase tiene un ciclo de vida independiente de las actividades.
 * AL IMPLEMENTAR `FallDetector.FallListener`, esta clase actúa como el "oyente" que recibe los eventos del detector
 * (cambios de estado, datos de sensores, caídas confirmadas) y decide qué hacer con ellos.
 */
class FallDetectionService : Service(), FallDetector.FallListener {

    // La instancia del detector de caídas. Se inicializa tardíamente (`lateinit`) cuando se inicia el servicio.
    private lateinit var detector: FallDetector
    // Almacena el número del contacto de emergencia, recibido desde la MainActivity.
    private var contactNumber: String = ""
    // Identificador único para el canal de notificación, requerido por Android 8.0+.
    private val CHANNEL_ID = "fall_detector"

    // --- Ciclo de Vida del Servicio ---

    /**
     * `onBind` se usa para servicios "vinculados". En nuestro caso, el servicio no necesita ser vinculado,
     * sino que se inicia y se detiene explícitamente, por lo que devolvemos `null`.
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * `onCreate` se llama solo una vez, la primera vez que se crea el servicio.
     * Es el lugar ideal para realizar configuraciones iniciales que no cambian, como crear el canal de notificación.
     */
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i("FallDetectionService", "Servicio creado.")
    }

    /**
     * `onStartCommand` se llama cada vez que un componente (como MainActivity) inicia el servicio a través de `startService()`.
     * Es el método principal donde se gestiona la lógica de inicio.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Extrae la acción a realizar y el número de teléfono del Intent que inició el servicio.
        val actionType = intent?.getStringExtra("ACTION_TYPE") ?: "UNKNOWN"
        contactNumber = intent?.getStringExtra(KEY_PHONE) ?: ""

        Log.i("FallDetectionService", "Comando recibido: $actionType")

        when (actionType) {
            "START_DETECTION" -> {
                // 1. Convertir en un Servicio de Primer Plano (Foreground Service).
                // Esto es OBLIGATORIO para tareas de larga duración que acceden a sensores o ubicación.
                // Muestra una notificación persistente al usuario y evita que el sistema mate el servicio.
                startForeground(1, createNotification())

                // 2. Iniciar el detector de caídas.
                try {
                    detector = FallDetector(this, this) // `this` se pasa como contexto y como listener.
                    detector.start()
                    Log.i("FallDetectionService", "Detector iniciado correctamente.")
                } catch (e: Exception) {
                    // Si el detector falla al iniciar (ej. el modelo de IA no cargó), lo notificamos en el estado.
                    Log.e("FallDetectionService", "Error crítico al iniciar el detector.", e)
                    FallDetectorStatus.updateStatus("ERROR: ${e.message}")
                }
            }
            "RUN_TEST" -> {
                // Acción para depuración: permite probar la AlertActivity directamente.
                Log.i("FallDetectionService", "Iniciando prueba de alerta manual.")
                launchAlertActivity()
            }
        }

        // `START_STICKY` le dice al sistema que, si tiene que matar el servicio por falta de memoria,
        // intente recrearlo más tarde. Es ideal para servicios que deben estar siempre activos como este.
        return START_STICKY
    }

    /**
     * `onDestroy` se llama cuando el servicio se detiene (a través de `stopService()` o por el sistema).
     * Es el lugar para limpiar todos los recursos.
     */
    override fun onDestroy() {
        super.onDestroy()
        // Es CRUCIAL detener el detector para anular el registro de los listeners de sensores
        // y así evitar el consumo de batería y posibles "memory leaks".
        if (::detector.isInitialized) detector.stop()
        // Actualiza el estado global para que la UI refleje que el servicio está detenido.
        FallDetectorStatus.updateStatus("Detenida")
        Log.i("FallDetectionService", "Servicio destruido y detector detenido.")
    }

    // --- Implementación de los Callbacks de FallDetector.FallListener ---

    /**
     * Se ejecuta cuando el `FallDetector` cambia su estado interno (`MONITORING`, `CANDIDATE_EVENT`, etc.).
     * Su trabajo es traducir el estado técnico en un mensaje legible para el usuario.
     */
    override fun onDetectionStateChanged(state: FallState) {
        val statusText = when (state) {
            FallState.MONITORING -> "Monitoreando (IA)"
            FallState.CANDIDATE_EVENT -> "POSIBLE CAÍDA: Analizando..."
            FallState.CONFIRMED_FALL -> "CAÍDA CONFIRMADA. Esperando acción..."
        }
        // Envía el nuevo estado a FallDetectorStatus para que la MainActivity lo reciba.
        FallDetectorStatus.updateStatus(statusText)
    }

    /**
     * Se ejecuta con cada nueva lectura de los sensores. Su única función es formatear los datos
     * en un string y enviarlos a FallDetectorStatus para que la UI los muestre.
     */
    override fun onSensorDataUpdated(ax: Float, ay: Float, az: Float, amag: Float, gx: Float, gy: Float, gz: Float) {
        val sensorData = "Acel: X: ${String.format("%.2f", ax)}, Y: ${String.format("%.2f", ay)}, Z: ${String.format("%.2f", az)}\n" +
                         "Giro: X: ${String.format("%.2f", gx)}, Y: ${String.format("%.2f", gy)}, Z: ${String.format("%.2f", gz)}"
        FallDetectorStatus.updateSensorData(sensorData)
    }

    /**
     * ¡El callback más importante! Se ejecuta cuando el `FallDetector` confirma una caída.
     * La única responsabilidad de este servicio es delegar la acción a la `AlertActivity`.
     */
    override fun onFallDetected() {
        launchAlertActivity()
    }

    // --- Lógica de la Alerta ---

    /**
     * Lanza la `AlertActivity`, que es la pantalla de confirmación/cancelación para el usuario.
     */
    private fun launchAlertActivity() {
        // Cláusula de guarda: No se puede lanzar la alerta si no hay un número de contacto.
        if (contactNumber.isBlank()) {
            Log.e("FallDetectionService", "No se puede iniciar alerta: número de contacto vacío.")
            return
        }
        Log.w("FallDetectionService", "¡CAÍDA DETECTADA! Lanzando AlertActivity.")
        val intent = Intent(this, AlertActivity::class.java).apply {
            // `FLAG_ACTIVITY_NEW_TASK` es OBLIGATORIO al iniciar una actividad desde un contexto que no es una actividad (como un Service).
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            // Pasa el número de contacto a la AlertActivity para que sepa a quién llamar/enviar SMS.
            putExtra("EMERGENCY_CONTACT", contactNumber)
        }
        startActivity(intent)
    }

    // --- Configuración de la Notificación de Primer Plano ---

    /**
     * Crea el canal de notificación. Es un requisito para Android 8.0 (API 26) y superior.
     * Las notificaciones deben pertenecer a un canal.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Detector de Caídas", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Notificación persistente para mantener el servicio activo."
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /**
     * Construye la notificación persistente que se muestra al usuario mientras el servicio está activo.
     */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Detector de Caídas Activo")
            .setContentText("Monitoreando caídas en segundo plano.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // TODO: Reemplazar con un icono personalizado.
            .setOngoing(true) // Hace que la notificación no se pueda descartar por el usuario.
            .build()
    }
}
