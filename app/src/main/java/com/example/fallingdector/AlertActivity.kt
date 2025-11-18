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

/**
 * `AlertActivity` es la pantalla más crítica de la aplicación. Se lanza cuando se confirma una caída
 * y actúa como la última barrera de seguridad para evitar falsos positivos.
 * Su responsabilidad es triple:
 * 1. Mostrar una cuenta atrás visible para el usuario.
 * 2. Permitir al usuario cancelar la alerta.
 * 3. Si no se cancela, ejecutar el protocolo de emergencia: obtener ubicación, enviar SMS y llamar.
 */
class AlertActivity : AppCompatActivity() {

    private lateinit var countdownText: TextView
    private lateinit var cancelButton: Button
    private var timer: CountDownTimer? = null
    private var contactNumber: String = ""

    // **MEJORA DE ROBUSTEZ**: Este flag previene que la alerta se envíe múltiples veces
    // en el caso de que haya múltiples callbacks de ubicación o errores.
    @Volatile // `Volatile` asegura que los cambios en esta variable sean visibles para todos los hilos.
    private var isAlertSent = false

    private val COUNTDOWN_SECONDS = 60L

    /**
     * `onCreate` es el punto de entrada de la actividad.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert)

        // --- Configuración Crítica de la Ventana ---
        // Estos flags son esenciales para que la actividad aparezca inmediatamente, incluso si el teléfono
        // está bloqueado, y para que encienda la pantalla.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or      // Muestra la actividad sobre la pantalla de bloqueo.
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or    // Descarta el teclado de seguridad si está activo.
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or        // Mantiene la pantalla encendida mientras esta actividad sea visible.
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON          // Enciende la pantalla si estaba apagada.
        )

        // --- Inicialización de Vistas y Datos ---
        countdownText = findViewById(R.id.textViewCountdown)
        cancelButton = findViewById(R.id.buttonCancel)

        // Recupera el número de contacto pasado por el `FallDetectionService`.
        contactNumber = intent.getStringExtra("EMERGENCY_CONTACT") ?: ""
        // Cláusula de guarda: Si no hay número, es un error irrecuperable. Se registra y se cierra la actividad.
        if (contactNumber.isEmpty()) {
            Log.e("AlertActivity", "Error fatal: No se recibió número de contacto. Cerrando.")
            finish()
            return
        }

        // --- Lógica de Cancelación ---
        cancelButton.setOnClickListener {
            timer?.cancel() // Detiene la cuenta atrás inmediatamente.
            Log.i("AlertActivity", "Alerta CANCELADA por el usuario.")
            Toast.makeText(this, "Alerta cancelada.", Toast.LENGTH_SHORT).show()
            finish() // Cierra esta actividad y vuelve a la anterior.
        }

        // Inicia la cuenta atrás tan pronto como la actividad es creada.
        startCountdown()
    }

    /**
     * Configura e inicia el temporizador de cuenta atrás.
     */
    private fun startCountdown() {
        // `CountDownTimer` es una clase de utilidad de Android para este propósito específico.
        timer = object : CountDownTimer(COUNTDOWN_SECONDS * 1000, 1000) { // (Total ms, Intervalo de tick en ms)
            // `onTick` se llama cada segundo.
            override fun onTick(millisUntilFinished: Long) {
                countdownText.text = (millisUntilFinished / 1000).toString()
            }

            // `onFinish` se llama cuando la cuenta atrás llega a cero.
            override fun onFinish() {
                Log.w("AlertActivity", "Temporizador finalizado. Procediendo con el protocolo de alerta.")
                countdownText.text = "0"
                // Este es el punto de no retorno. Se inicia el envío de la alerta.
                sendAlertProtocol()
            }
        }.start()
    }

    /**
     * Orquesta el envío de la alerta. Primero intenta obtener la ubicación.
     */
    private fun sendAlertProtocol() {
        // **MEJORA DE ROBUSTEZ**: Se comprueba y se establece el flag para asegurar una única ejecución.
        synchronized(this) {
            if (isAlertSent) {
                Log.w("AlertActivity", "sendAlertProtocol llamado de nuevo, pero la alerta ya fue enviada. Ignorando.")
                return
            }
            isAlertSent = true
        }

        // Primero, verifica si tenemos permiso para acceder a la ubicación.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("AlertActivity", "Permiso de ubicación no concedido. Se enviará SMS sin ubicación.")
            // Plan B: Si no hay permiso, envía el SMS sin ubicación y realiza la llamada.
            sendSms(null)
            makeCall()
            finish()
            return
        }

        // Si hay permiso, intenta obtener una única actualización de la ubicación.
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // `requestSingleUpdate` es ideal aquí: es eficiente porque solo pide la ubicación una vez.
        try {
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, object : LocationListener {
                // Se encontró una ubicación.
                override fun onLocationChanged(location: Location) {
                    Log.i("AlertActivity", "Ubicación obtenida: ${location.latitude}, ${location.longitude}")
                    sendSms(location)
                    makeCall()
                    finish()
                }

                // El proveedor de ubicación (GPS) está desactivado en el teléfono.
                override fun onProviderDisabled(provider: String) {
                     Log.w("AlertActivity", "Proveedor de GPS deshabilitado. Se enviará SMS sin ubicación.")
                     sendSms(null)
                     makeCall()
                     finish()
                }
            }, null)
        } catch (se: SecurityException) {
            Log.e("AlertActivity", "Error de seguridad al pedir la ubicación. Se enviará SMS sin ubicación.", se)
            sendSms(null)
            makeCall()
            finish()
        }
    }

    /**
     * Construye y envía el SMS de alerta.
     */
    private fun sendSms(location: Location?) {
        val smsManager = SmsManager.getDefault() // API estándar de Android para SMS.
        val message: String
        if (location != null) {
            // Si tenemos ubicación, construye un enlace de Google Maps.
            val url = "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
            message = "¡POSIBLE CAÍDA DETECTADA!\nUbicación aproximada: $url"
        } else {
            // Mensaje de fallback si no se pudo obtener la ubicación.
            message = "¡POSIBLE CAÍDA DETECTADA!\nNo se pudo obtener la ubicación del dispositivo."
        }
        
        try {
            smsManager.sendTextMessage(contactNumber, null, message, null, null)
            Log.i("AlertActivity", "SMS de alerta enviado exitosamente a $contactNumber.")
        } catch (e: Exception) {
            Log.e("AlertActivity", "Error al enviar SMS.", e)
            // No se reintenta para no causar spam. El error queda registrado.
        }
    }

    /**
     * Inicia la llamada de emergencia.
     */
    private fun makeCall() {
        // `Intent.ACTION_CALL` inicia una llamada directamente. Requiere el permiso `CALL_PHONE`.
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$contactNumber")
        }
        // Verifica el permiso antes de intentar la llamada.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            try {
                startActivity(callIntent)
                Log.i("AlertActivity", "Iniciando llamada de emergencia a $contactNumber.")
            } catch (e: SecurityException) {
                Log.e("AlertActivity", "Error de seguridad al intentar realizar la llamada.", e)
            }
        } else {
            Log.e("AlertActivity", "Permiso CALL_PHONE denegado. No se puede realizar la llamada.")
        }
    }

    /**
     * `onDestroy` se llama cuando la actividad está a punto de ser destruida.
     */
    override fun onDestroy() {
        super.onDestroy()
        // Es crucial cancelar el temporizador aquí para evitar que `onFinish()` se ejecute
        // si la actividad se destruye antes de que termine la cuenta atrás (ej. si el usuario la cierra manualmente).
        timer?.cancel()
    }
}
