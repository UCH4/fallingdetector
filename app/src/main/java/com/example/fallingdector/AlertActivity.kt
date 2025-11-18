package com.example.fallingdector

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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

    private val COUNTDOWN_SECONDS = 60L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert)

        // --- Configura la ventana para que aparezca sobre la pantalla de bloqueo ---
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
            Log.e("AlertActivity", "No se recibió número de contacto. No se puede enviar alerta.")
            finish()
            return
        }

        cancelButton.setOnClickListener {
            timer?.cancel()
            Log.i("AlertActivity", "Alerta cancelada por el usuario.")
            Toast.makeText(this, "Alerta cancelada.", Toast.LENGTH_SHORT).show()
            finish()
        }

        startCountdown()
    }

    private fun startCountdown() {
        timer = object : CountDownTimer(COUNTDOWN_SECONDS * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdownText.text = (millisUntilFinished / 1000).toString()
            }

            override fun onFinish() {
                Log.w("AlertActivity", "Temporizador finalizado. Procediendo a enviar la alerta.")
                countdownText.text = "0"
                sendAlertWithLocation()
            }
        }.start()
    }

    private fun sendAlertWithLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("AlertActivity", "Permiso de ubicación no concedido. No se puede enviar ubicación.")
            // Enviar SMS sin ubicación como último recurso
            sendSms(null)
            makeCall()
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, object : LocationListener {
            override fun onLocationChanged(location: Location) {
                sendSms(location)
                makeCall()
                finish()
            }

            override fun onProviderDisabled(provider: String) {
                 Log.w("AlertActivity", "El proveedor de GPS está deshabilitado.")
                 sendSms(null)
                 makeCall()
                 finish()
            }
        }, null)
    }

    private fun sendSms(location: Location?) {
        val smsManager = SmsManager.getDefault()
        val message: String
        if (location != null) {
            val url = "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
            message = "¡POSIBLE CAÍDA DETECTADA!\nUbicación: $url"
        } else {
            message = "¡POSIBLE CAÍDA DETECTADA!\nNo se pudo obtener la ubicación."
        }
        
        try {
            smsManager.sendTextMessage(contactNumber, null, message, null, null)
            Log.i("AlertActivity", "SMS de alerta enviado a $contactNumber.")
        } catch (e: Exception) {
            Log.e("AlertActivity", "Error al enviar SMS: ${e.message}", e)
        }
    }

    private fun makeCall() {
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$contactNumber")
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            try {
                startActivity(callIntent)
                Log.i("AlertActivity", "Llamada de emergencia iniciada a $contactNumber.")
            } catch (e: SecurityException) {
                Log.e("AlertActivity", "Error de seguridad al intentar realizar la llamada: ${e.message}", e)
            }
        } else {
            Log.e("AlertActivity", "Permiso CALL_PHONE denegado. No se puede realizar la llamada.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
