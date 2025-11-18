package com.example.fallingdector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

// Clave para pasar el número de teléfono de emergencia de la MainActivity a este Servicio.
private const val KEY_PHONE = "emergency_phone"

class FallDetectionService : Service(), FallDetector.FallListener {

    private lateinit var detector: FallDetector
    private var contactNumber: String = ""
    private val CHANNEL_ID = "fall_detector_service_channel" // Canal para la notificación de servicio
    private val ALERT_CHANNEL_ID = "fall_detector_alert_channel" // Canal para la notificación de ALERTA
    private val ALERT_NOTIFICATION_ID = 2 // ID único para la notificación de alerta

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Ahora creamos ambos canales de notificación al iniciar el servicio
        createServiceNotificationChannel()
        createAlertNotificationChannel()
        Log.i("FallDetectionService", "Servicio creado y canales de notificación listos.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val actionType = intent?.getStringExtra("ACTION_TYPE") ?: "UNKNOWN"
        contactNumber = intent?.getStringExtra(KEY_PHONE) ?: ""

        Log.i("FallDetectionService", "Comando recibido: $actionType")

        when (actionType) {
            "START_DETECTION" -> {
                startForeground(1, createServiceNotification())
                try {
                    detector = FallDetector(this, this)
                    detector.start()
                    Log.i("FallDetectionService", "Detector iniciado correctamente.")
                } catch (e: Exception) {
                    Log.e("FallDetectionService", "Error crítico al iniciar el detector.", e)
                    FallDetectorStatus.updateStatus("ERROR: ${e.message}")
                }
            }
            "RUN_TEST" -> {
                Log.i("FallDetectionService", "Iniciando prueba de alerta manual.")
                launchAlertNotification()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::detector.isInitialized) detector.stop()
        FallDetectorStatus.updateStatus("Detenida")
        Log.i("FallDetectionService", "Servicio destruido y detector detenido.")
    }

    override fun onDetectionStateChanged(state: FallState) {
        val statusText = when (state) {
            FallState.MONITORING -> "Monitoreando (IA)"
            FallState.CANDIDATE_EVENT -> "POSIBLE CAÍDA: Analizando..."
            FallState.CONFIRMED_FALL -> "CAÍDA CONFIRMADA. Mostrando alerta..."
        }
        FallDetectorStatus.updateStatus(statusText)
    }

    override fun onSensorDataUpdated(ax: Float, ay: Float, az: Float, amag: Float, gx: Float, gy: Float, gz: Float) {
        val sensorData = "Acel: X: ${String.format("%.2f", ax)}, Y: ${String.format("%.2f", ay)}, Z: ${String.format("%.2f", az)}\n" +
                         "Giro: X: ${String.format("%.2f", gx)}, Y: ${String.format("%.2f", gy)}, Z: ${String.format("%.2f", gz)}"
        FallDetectorStatus.updateSensorData(sensorData)
    }

    override fun onFallDetected() {
        // Log.d("AI_DEBUG", "FallDetectionService: onFallDetected() ha sido llamado.")
        launchAlertNotification()
    }

    private fun launchAlertNotification() {
        if (contactNumber.isBlank()) {
            Log.e("FallDetectionService", "No se puede lanzar la alerta: número de contacto vacío.")
            return
        }

        // Log.d("AI_DEBUG", "FallDetectionService: Construyendo notificación con fullScreenIntent.")

        val fullScreenIntent = Intent(this, AlertActivity::class.java).apply {
            putExtra("EMERGENCY_CONTACT", contactNumber)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // **ICONO CORREGIDO**
            .setContentTitle("¡Posible Caída Detectada!")
            .setContentText("Pulsa para abrir la pantalla de cancelación.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ALERT_NOTIFICATION_ID, notificationBuilder.build())

        // Log.d("AI_DEBUG", "FallDetectionService: Notificación de alerta enviada al sistema.")
    }

    private fun createServiceNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Servicio de Detección", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Notificación persistente para mantener el servicio activo."
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createAlertNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(ALERT_CHANNEL_ID, "Alertas de Caída", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Canal para las alertas de caída de alta prioridad."
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createServiceNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Detector de Caídas Activo")
            .setContentText("Monitoreando caídas en segundo plano.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // **ICONO CORREGIDO**
            .setOngoing(true)
            .build()
    }
}