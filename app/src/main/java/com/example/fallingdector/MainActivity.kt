package com.example.fallingdector

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val KEY_PHONE = "emergency_phone"

class MainActivity : AppCompatActivity() {

    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var testBtn: Button
    private lateinit var phoneNumberEditText: EditText
    private lateinit var statusTextView: TextView
    private lateinit var sensorDataTextView: TextView
    private lateinit var statusCard: CardView // Nueva vista

    private val PERMISSION_REQUEST_CODE = 101
    private val PREFS_NAME = "app_prefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Inicialización de Vistas
        startBtn = findViewById(R.id.startButton)
        stopBtn = findViewById(R.id.stopButton)
        testBtn = findViewById(R.id.testButton)
        phoneNumberEditText = findViewById(R.id.phoneNumberEditText)
        statusTextView = findViewById(R.id.statusTextView)
        sensorDataTextView = findViewById(R.id.sensorDataTextView)
        statusCard = findViewById(R.id.statusCard)

        // 2. Carga de datos y permisos
        loadPhoneNumber()
        checkAndRequestPermissions()

        // 3. Configuración de Listeners para los botones
        setupButtonClickListeners()

        // 4. Observadores de LiveData para actualizar la UI en tiempo real
        setupLiveDataObservers()
    }

    private fun setupLiveDataObservers() {
        FallDetectorStatus.status.observe(this) { status ->
            statusTextView.text = status
            Log.i("MainActivity", "LiveData - Estado actualizado: $status")

            val isDetecting = status.contains("Monitoreando") || status.contains("Analizando")
            updateUI(isDetecting)

            // Lógica de cambio de color
            val cardColor = when {
                status.contains("Monitoreando") -> Color.parseColor("#4CAF50") // Verde
                status.contains("Analizando") -> Color.parseColor("#FFC107") // Ámbar
                status.contains("CAÍDA CONFIRMADA") -> Color.parseColor("#FF5722") // Naranja Intenso
                else -> Color.parseColor("#D32F2F") // Rojo para Detenida, Error, etc.
            }
            statusCard.setCardBackgroundColor(cardColor)
        }

        FallDetectorStatus.sensorData.observe(this) { data ->
            sensorDataTextView.text = data
        }
    }

    private fun setupButtonClickListeners() {
        startBtn.setOnClickListener {
            val phone = phoneNumberEditText.text.toString()
            if (phone.isBlank()) {
                Toast.makeText(this, "Por favor, ingresa un número de emergencia.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!arePermissionsGranted()) {
                Toast.makeText(this, "Permisos insuficientes. La app no puede iniciar sin ellos.", Toast.LENGTH_LONG).show()
                checkAndRequestPermissions()
                return@setOnClickListener
            }

            savePhoneNumber(phone)
            val intent = Intent(this, FallDetectionService::class.java).apply {
                putExtra("ACTION_TYPE", "START_DETECTION")
                putExtra(KEY_PHONE, phone)
            }
            ContextCompat.startForegroundService(this, intent)
            Toast.makeText(this, "Servicio de detección iniciado.", Toast.LENGTH_SHORT).show()
        }

        stopBtn.setOnClickListener {
            val intent = Intent(this, FallDetectionService::class.java)
            stopService(intent)
            Toast.makeText(this, "Servicio de detección detenido.", Toast.LENGTH_SHORT).show()
        }

        testBtn.setOnClickListener {
            val phone = phoneNumberEditText.text.toString()
            if (phone.isBlank()) {
                Toast.makeText(this, "Ingresa un número para probar la alerta.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            savePhoneNumber(phone)

            val intent = Intent(this, AlertActivity::class.java).apply {
                putExtra("EMERGENCY_CONTACT", phone)
            }
            startActivity(intent)
        }
    }

    override fun onPause() {
        super.onPause()
        if (phoneNumberEditText.text.isNotBlank()) {
            savePhoneNumber(phoneNumberEditText.text.toString())
        }
    }

    private fun savePhoneNumber(number: String) {
        val sharedPref = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) { putString(KEY_PHONE, number); apply() }
    }

    private fun loadPhoneNumber() {
        val sharedPref = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        phoneNumberEditText.setText(sharedPref.getString(KEY_PHONE, ""))
    }

    private fun arePermissionsGranted(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun checkAndRequestPermissions() {
        if (!arePermissionsGranted()) {
            val permissionsToRequest = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.SEND_SMS,
                Manifest.permission.CALL_PHONE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun updateUI(isDetecting: Boolean) {
        startBtn.isEnabled = !isDetecting
        stopBtn.isEnabled = isDetecting
        phoneNumberEditText.isEnabled = !isDetecting
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "Algunos permisos fueron denegados. La app podría no funcionar como se espera.", Toast.LENGTH_LONG).show()
        }
    }
}
