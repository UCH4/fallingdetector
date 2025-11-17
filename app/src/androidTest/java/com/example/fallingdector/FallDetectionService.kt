package com.example.fallingdector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class FallDetectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var fallDetector: FallDetector? = null

    companion object {
        const val CHANNEL_ID = "FallDetectionServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        startForeground(1, notification)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        fallDetector = FallDetector()

        startSensorMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("FallDetectionService", "Servicio iniciado")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSensorMonitoring()
        Log.d("FallDetectionService", "Servicio detenido")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startSensorMonitoring() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            Log.d("FallDetectionService", "Monitoreo de sensor iniciado")
        }
    }

    private fun stopSensorMonitoring() {
        sensorManager.unregisterListener(this)
        Log.d("FallDetectionService", "Monitoreo de sensor detenido")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val totalAcceleration = sqrt((x * x + y * y + z * z).toDouble())

            val isFall = fallDetector?.detectFall(totalAcceleration) ?: false
            if (isFall) {
                Log.d("FallDetectionService", "¡Posible caída detectada!")
                Toast.makeText(this, "¡Posible caída detectada!", Toast.LENGTH_SHORT).show()
                val broadcastIntent = Intent("com.example.fallingdector.FALL_DETECTED")
                sendBroadcast(broadcastIntent)
                fallDetector?.reset() // Resetea el detector para evitar detecciones múltiples
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No se necesita implementación
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de Detección de Caídas",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = Context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Detección de Caídas Activa")
            .setContentText("Tu seguridad está siendo monitoreada en segundo plano.")
            .setSmallIcon(R.mipmap.ic_launcher_round) // Asegúrate de tener este icono
            .setContentIntent(pendingIntent)
            .build()
    }
}