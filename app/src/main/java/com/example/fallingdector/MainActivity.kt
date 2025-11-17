package com.example.fallingdector

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

// Constantes para la comunicación Broadcast. DEBEN ESTAR SOLO AQUÍ.
const val ACTION_FALL_DETECTOR_UPDATE = "com.example.fallingdector.UPDATE"
const val EXTRA_STATUS = "EXTRA_STATUS"
const val EXTRA_SENSOR_DATA = "EXTRA_SENSOR_DATA"
private const val KEY_PHONE = "emergency_phone"

class MainActivity : AppCompatActivity() {

    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var testBtn: Button
    private lateinit var phoneNumberEditText: EditText

    // VISTAS PARA EL ESTADO
    private lateinit var statusTextView: TextView
    private lateinit var sensorDataTextView: TextView

    private val PERMISSION_REQUEST_CODE = 101
    private val PREFS_NAME = "app_prefs"

    // RECEPTOR DE BROADCAST
    private val detectorUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(EXTRA_STATUS) ?: ""
            val sensorData = intent?.getStringExtra(EXTRA_SENSOR_DATA) ?: ""

            if (status.isNotEmpty()) {
                statusTextView.text = "Detección de caídas: $status"
                Log.i("MainActivity", "Estado de Detección Recibido: $status")

                // Actualiza los botones basado en el estado recibido
                when {
                    status.contains("Detenida") || status.contains("ERROR") -> updateUI(false)
                    else -> updateUI(true)
                }
            }

            if (sensorData.isNotEmpty()) {
                sensorDataTextView.text = "Datos del sensor: $sensorData"
                Log.d("MainActivity_Sensor", sensorData)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicialización de Vistas
        startBtn = findViewById(R.id.startButton)
        stopBtn = findViewById(R.id.stopButton)
        testBtn = findViewById(R.id.testButton)
        phoneNumberEditText = findViewById(R.id.phoneNumberEditText)
        statusTextView = findViewById(R.id.statusTextView)
        sensorDataTextView = findViewById(R.id.sensorDataTextView)

        loadPhoneNumber()
        checkAndRequestPermissions()

        // Registrar el Broadcast Receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(
            detectorUpdateReceiver, IntentFilter(ACTION_FALL_DETECTOR_UPDATE)
        )

        startBtn.setOnClickListener {
            if (phoneNumberEditText.text.isBlank()) {
                Toast.makeText(this, "Por favor, ingresa un número de emergencia.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!arePermissionsGranted()) {
                Toast.makeText(this, "Permisos insuficientes. Concede todos los permisos.", Toast.LENGTH_LONG).show()
                checkAndRequestPermissions()
                return@setOnClickListener
            }

            savePhoneNumber(phoneNumberEditText.text.toString())
            val intent = Intent(this, FallDetectionService::class.java).apply {
                putExtra("ACTION_TYPE", "START_DETECTION")
                putExtra(KEY_PHONE, phoneNumberEditText.text.toString())
            }
            ContextCompat.startForegroundService(this, intent)

            updateUI(true)
        }

        stopBtn.setOnClickListener {
            val intent = Intent(this, FallDetectionService::class.java)
            stopService(intent)
            updateUI(false)
        }

        testBtn.setOnClickListener {
            if (phoneNumberEditText.text.isBlank() || !arePermissionsGranted()) return@setOnClickListener
            savePhoneNumber(phoneNumberEditText.text.toString())

            val intent = Intent(this, FallDetectionService::class.java).apply {
                putExtra("ACTION_TYPE", "RUN_TEST")
                putExtra(KEY_PHONE, phoneNumberEditText.text.toString())
            }
            startService(intent)
            Toast.makeText(this, "Enviando SMS de prueba e iniciando llamada...", Toast.LENGTH_LONG).show()
        }

        updateUI(false)
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(detectorUpdateReceiver)
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        if (phoneNumberEditText.text.isNotBlank()) {
            savePhoneNumber(phoneNumberEditText.text.toString())
        }
    }

    // --- Funciones de Persistencia ---

    private fun savePhoneNumber(number: String) {
        val sharedPref = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with (sharedPref.edit()) {
            putString(KEY_PHONE, number)
            apply()
        }
    }

    private fun loadPhoneNumber() {
        val sharedPref = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedNumber = sharedPref.getString(KEY_PHONE, "")
        phoneNumberEditText.setText(savedNumber)
    }

    // --- Gestión de Permisos ---

    private fun checkAndRequestPermissions() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
        }
    }

    private fun arePermissionsGranted(): Boolean {
        val baseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return baseGranted && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        return baseGranted
    }

    private fun updateUI(isDetecting: Boolean) {
        startBtn.isEnabled = !isDetecting
        stopBtn.isEnabled = isDetecting
        phoneNumberEditText.isEnabled = !isDetecting
        testBtn.isEnabled = !isDetecting

        if (!isDetecting) {
            statusTextView.text = "Detección de caídas: Detenida"
            sensorDataTextView.text = "Datos del sensor: "
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // ... (Tu manejo de resultados de permisos) ...
    }
}