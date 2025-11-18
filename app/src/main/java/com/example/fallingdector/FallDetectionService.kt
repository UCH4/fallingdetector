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

private const val KEY_PHONE = "emergency_phone"

class FallDetectionService : Service(), FallDetector.FallListener {

    private lateinit var detector: FallDetector
    private var contactNumber: String = ""
    private val CHANNEL_ID = "fall_detector"

    // --- Ciclo de Vida del Servicio ---

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val actionType = intent?.getStringExtra("ACTION_TYPE") ?: "UNKNOWN"
        contactNumber = intent?.getStringExtra(KEY_PHONE) ?: ""

        Log.i("FallDetectionService", "Recibido comando: $actionType. Número: $contactNumber")

        when (actionType) {
            "START_DETECTION" -> {
                startForeground(1, createNotification())
                try {
                    detector = FallDetector(this, this)
                    detector.start()
                    Log.i("FallDetectionService", "Detector iniciado. Estado: MONITORING.")
                } catch (e: Exception) {
                    Log.e("FallDetectionService", "ERROR CRÍTICO al iniciar: ${e.message}", e)
                    FallDetectorStatus.updateStatus("ERROR CRÍTICO: ${e.message}")
                }
            }
            "RUN_TEST" -> {
                Log.i("FallDetectionService", "Iniciando prueba de alerta.")
                launchAlertActivity()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::detector.isInitialized) detector.stop()
        FallDetectorStatus.updateStatus("Detenida")
        Log.i("FallDetectionService", "Servicio detenido.")
    }

    // --- Implementación de FallDetector.FallListener (AHORA USA LIVEDATA) ---

    override fun onDetectionStateChanged(state: FallState) {
        val statusText = when (state) {
            FallState.MONITORING -> "Monitoreando (IA)"
            FallState.CANDIDATE_EVENT -> "POSIBLE CAÍDA: Analizando..."
            FallState.CONFIRMED_FALL -> "CAÍDA CONFIRMADA. Esperando acción del usuario..."
        }
        FallDetectorStatus.updateStatus(statusText)
        Log.i("FallDetectionService", "Estado de Detección Cambiado: $statusText")
    }

    override fun onSensorDataUpdated(ax: Float, ay: Float, az: Float, amag: Float) {
        val sensorData = "X: ${String.format("%.2f", ax)}, Y: ${String.format("%.2f", ay)}, Z: ${String.format("%.2f", az)}, Mag: ${String.format("%.2f", amag)}"
        FallDetectorStatus.updateSensorData(sensorData)
    }

    override fun onFallDetected() {
        launchAlertActivity()
    }

    private fun launchAlertActivity() {
        if (contactNumber.isBlank()) {
            Log.e("FallDetectionService", "No se puede iniciar la alerta: el número de contacto está vacío.")
            return
        }
        Log.w("FallDetectionService", "¡CAÍDA DETECTADA! Lanzando pantalla de alerta para confirmación.")
        val intent = Intent(this, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("EMERGENCY_CONTACT", contactNumber)
        }
        startActivity(intent)
    }

    // --- Notificación de Servicio en Primer Plano ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Fall Detector", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fall Detector Activo")
            .setContentText("Monitoreando caídas en segundo plano.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .build()
    }
}
