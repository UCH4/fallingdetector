package com.example.fallingdector

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

// NOTA: Las constantes ACTION_FALL_DETECTOR_UPDATE, EXTRA_STATUS y EXTRA_SENSOR_DATA
// fueron ELIMINADAS de aquí para evitar el error "Conflicting declarations"
private const val KEY_PHONE = "emergency_phone" // Se define aquí si es necesaria internamente

class FallDetectionService : Service(), FallDetector.FallListener {

    private lateinit var detector: FallDetector
    private var locationManager: LocationManager? = null
    private var contactNumber: String = ""
    private val CHANNEL_ID = "fall_detector"

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
                    // Inicializa el detector
                    detector = FallDetector(this, this)
                    detector.start()
                    Log.i("FallDetectionService", "Detector iniciado. Estado: MONITORING.")

                    // Envía el estado inicial a la MainActivity
                    sendUpdateBroadcast("Monitoreando (IA)", "")
                } catch (e: Exception) {
                    Log.e("FallDetectionService", "ERROR CRÍTICO al iniciar el detector: ${e.message}", e)
                    sendUpdateBroadcast("ERROR CRÍTICO: ${e.message}", "No disponible")
                }
            }
            "RUN_TEST" -> {
                sendAlertWithLocation(isTest = true)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::detector.isInitialized) detector.stop()
        Log.i("FallDetectionService", "Servicio detenido. Detector de caídas desactivado.")
        // Envía el estado de detención a la MainActivity
        sendUpdateBroadcast("Detenida", "Detenida")
    }

    // --- Implementación de FallDetector.FallListener (Comunicación con UI) ---

    override fun onDetectionStateChanged(state: FallState) {
        val statusText = when (state) {
            FallState.MONITORING -> "Monitoreando (IA)"
            FallState.CANDIDATE_EVENT -> "POSIBLE CAÍDA: Analizando..."
            FallState.CONFIRMED_FALL -> "CAÍDA CONFIRMADA. Alerta enviada."
        }
        sendUpdateBroadcast(statusText, "")
        Log.i("FallDetectionService", "Estado de Detección Cambiado: $statusText")
    }

    override fun onSensorDataUpdated(ax: Float, ay: Float, az: Float, amag: Float) {
        val sensorData = "X: ${String.format("%.2f", ax)}, Y: ${String.format("%.2f", ay)}, Z: ${String.format("%.2f", az)}, Mag: ${String.format("%.2f", amag)}"
        sendUpdateBroadcast("", sensorData)
    }

    override fun onFallDetected() {
        sendAlertWithLocation(isTest = false)
        Log.w("FallDetectionService", "¡CAÍDA DETECTADA! Iniciando alerta.")
    }

    // --- Función de Broadcast (Corregida) ---

    private fun sendUpdateBroadcast(status: String, sensorData: String) {
        val intent = Intent(ACTION_FALL_DETECTOR_UPDATE).apply {
            if (status.isNotEmpty()) putExtra(EXTRA_STATUS, status)
            if (sensorData.isNotEmpty()) putExtra(EXTRA_SENSOR_DATA, sensorData)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    // --- Funciones de Alerta ---

    private fun sendAlertWithLocation(isTest: Boolean = false) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            Log.e("FallDetectionService", "Permisos de ubicación no concedidos.")
            return
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        locationManager?.requestSingleUpdate(
            LocationManager.GPS_PROVIDER,
            object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val smsManager = SmsManager.getDefault()
                    val typeMessage = if (isTest) "PRUEBA DE ALERTA" else "¡POSIBLE CAÍDA DETECTADA!"
                    // Corregida la URL para que funcione correctamente
                    val url = "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
                    val message = "$typeMessage\nUbicación: $url"

                    smsManager.sendTextMessage(contactNumber, null, message, null, null)
                    Log.i("FallDetectionService", "SMS enviado a $contactNumber. Es Prueba: $isTest. URL: $url")

                    // Inicia la llamada de emergencia
                    val callIntent = Intent(Intent.ACTION_CALL).apply {
                        data = Uri.parse("tel:$contactNumber")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        startActivity(callIntent)
                        Log.i("FallDetectionService", "Llamada iniciada a $contactNumber.")
                    } else {
                        Log.e("FallDetectionService", "Permiso CALL_PHONE denegado. No se puede llamar.")
                    }

                    if (isTest) stopSelf()
                    // Remueve el listener después de la actualización (buena práctica)
                    locationManager?.removeUpdates(this)
                }

                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            },
            null
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Fall Detector", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fall Detector activo (IA)")
            .setContentText("Monitoreando caídas en segundo plano con TensorFlow Lite")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .build()
    }
}