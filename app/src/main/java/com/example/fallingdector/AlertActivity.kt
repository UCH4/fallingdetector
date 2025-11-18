package com.example.fallingdector

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.telephony.SmsManager
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class AlertActivity : AppCompatActivity() {

    private lateinit var countdownText: TextView
    private lateinit var cancelButton: Button
    private var timer: CountDownTimer? = null
    private var contactNumber: String = ""

    // **NUEVO**: Reproductor para la alarma sonora
    private var ringtone: Ringtone? = null

    @Volatile
    private var isAlertSent = false

    private val COUNTDOWN_SECONDS = 60L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        countdownText = findViewById(R.id.textViewCountdown)
        cancelButton = findViewById(R.id.buttonCancel)

        contactNumber = intent.getStringExtra("EMERGENCY_CONTACT") ?: ""
        if (contactNumber.isEmpty()) {
            Log.e("AlertActivity", "Error fatal: No se recibió número de contacto. Cerrando.")
            finish()
            return
        }

        cancelButton.setOnClickListener {
            stopAlarmAndFinish()
        }

        // Inicia la alarma y la cuenta atrás
        startAlarm()
        startCountdown()
    }

    private fun startCountdown() {
        timer = object : CountDownTimer(COUNTDOWN_SECONDS * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = millisUntilFinished / 1000
                countdownText.text = secondsLeft.toString()
            }

            override fun onFinish() {
                Log.w("AlertActivity", "Temporizador finalizado. Procediendo con el protocolo de alerta.")
                countdownText.text = "0"
                sendAlertProtocol()
            }
        }.start()
    }

    /**
     * **NUEVO**: Inicia la reproducción del sonido de la alarma.
     */
    private fun startAlarm() {
        try {
            // Obtiene el URI del sonido de alarma por defecto del sistema.
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            // Crea una instancia del reproductor.
            ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
            // Pone la alarma en bucle para que suene continuamente.
            ringtone?.isLooping = true
            ringtone?.play()
            Log.i("AlertActivity", "Alarma sonora iniciada.")
        } catch (e: Exception) {
            Log.e("AlertActivity", "Error al iniciar la alarma sonora.", e)
        }
    }

    /**
     * **NUEVO**: Detiene la alarma y cierra la actividad.
     */
    private fun stopAlarmAndFinish() {
        timer?.cancel()
        ringtone?.stop()
        Log.i("AlertActivity", "Alarma CANCELADA por el usuario.")
        Toast.makeText(this, "Alerta cancelada.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun sendAlertProtocol() {
        synchronized(this) {
            if (isAlertSent) {
                return
            }
            isAlertSent = true
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            sendSms(null)
            makeCall()
            finish()
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    sendSms(location)
                    makeCall()
                    finish()
                }

                override fun onProviderDisabled(provider: String) {
                     sendSms(null)
                     makeCall()
                     finish()
                }
            }, null)
        } catch (se: SecurityException) {
            sendSms(null)
            makeCall()
            finish()
        }
    }

    private fun sendSms(location: Location?) {
        val smsManager = SmsManager.getDefault()
        val message: String
        if (location != null) {
            val url = "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
            message = "¡POSIBLE CAÍDA DETECTADA!\nUbicación aproximada: $url"
        } else {
            message = "¡POSIBLE CAÍDA DETECTADA!\nNo se pudo obtener la ubicación del dispositivo."
        }
        
        try {
            smsManager.sendTextMessage(contactNumber, null, message, null, null)
        } catch (e: Exception) {
            Log.e("AlertActivity", "Error al enviar SMS.", e)
        }
    }

    private fun makeCall() {
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$contactNumber")
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            try {
                startActivity(callIntent)
            } catch (e: SecurityException) {
                Log.e("AlertActivity", "Error de seguridad al intentar realizar la llamada.", e)
            }
        } else {
            Log.e("AlertActivity", "Permiso CALL_PHONE denegado. No se puede realizar la llamada.")
        }
    }

    /**
     * `onDestroy` se llama cuando la actividad está a punto de ser destruida.
     * Es crucial detener todos los procesos aquí para liberar recursos.
     */
    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        ringtone?.stop()
        Log.d("AlertActivity", "Actividad destruida, temporizador y alarma detenidos.")
    }
}