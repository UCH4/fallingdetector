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

// Clave para guardar y recuperar el número de teléfono, usada tanto en SharedPreferences como en Intents.
private const val KEY_PHONE = "emergency_phone"

/**
 * `MainActivity` es el punto de entrada principal para la interacción del usuario.
 * Sus responsabilidades son:
 * 1. Proporcionar una interfaz para que el usuario ingrese un número de emergencia.
 * 2. Iniciar y detener el `FallDetectionService`.
 * 3. Solicitar los permisos necesarios para el funcionamiento de la app.
 * 4. Mostrar el estado en tiempo real de la detección (status y datos de sensores) observando `FallDetectorStatus`.
 */
class MainActivity : AppCompatActivity() {

    // --- Declaración de Vistas de la UI ---
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var testBtn: Button // Botón para depuración
    private lateinit var phoneNumberEditText: EditText
    private lateinit var statusTextView: TextView
    private lateinit var sensorDataTextView: TextView
    private lateinit var statusCard: CardView // El contenedor visual del estado

    // --- Constantes ---
    private val PERMISSION_REQUEST_CODE = 101 // Código único para la solicitud de permisos.
    private val PREFS_NAME = "app_prefs" // Nombre del archivo de SharedPreferences.

    /**
     * `onCreate` es el primer método que se llama cuando se crea la actividad.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- Orquestación de la Configuración Inicial ---
        // La secuencia de inicialización es importante para asegurar que las vistas estén listas
        // antes de intentar cargar datos o configurar listeners.
        initializeViews()
        loadPhoneNumber() // Carga el último número guardado para comodidad del usuario.
        checkAndRequestPermissions() // Solicita permisos críticos al iniciar la app.
        setupButtonClickListeners() // Configura lo que hace cada botón.
        setupLiveDataObservers() // Conecta la UI al estado global de la app.

        Log.i("MainActivity", "Actividad creada y configurada.")
    }
    
    private fun initializeViews(){
        startBtn = findViewById(R.id.startButton)
        stopBtn = findViewById(R.id.stopButton)
        testBtn = findViewById(R.id.testButton)
        phoneNumberEditText = findViewById(R.id.phoneNumberEditText)
        statusTextView = findViewById(R.id.statusTextView)
        sensorDataTextView = findViewById(R.id.sensorDataTextView)
        statusCard = findViewById(R.id.statusCard)
    }

    /**
     * Configura los observadores de `LiveData` desde `FallDetectorStatus`.
     * ESTE ES EL CORAZÓN DE LA ARQUITECTURA REACTIVA. La MainActivity no sabe nada sobre el servicio,
     * solo reacciona a los cambios de estado publicados en `FallDetectorStatus`.
     */
    private fun setupLiveDataObservers() {
        // 1. Observador para el estado principal (ej. "Monitoreando", "Detenida").
        FallDetectorStatus.status.observe(this) { status ->
            statusTextView.text = status
            Log.d("MainActivity", "LiveData-Status: $status")

            // Basado en el texto del estado, determina si el servicio está activo.
            val isDetecting = status.contains("Monitoreando") || status.contains("Analizando")
            // Habilita/deshabilita los botones correspondientes.
            updateUiState(isDetecting)

            // Cambia el color de la tarjeta de estado para dar una retroalimentación visual clara.
            val cardColor = when {
                status.contains("Monitoreando") -> Color.parseColor("#4CAF50") // Verde
                status.contains("Analizando") -> Color.parseColor("#FFC107") // Ámbar
                status.contains("CAÍDA CONFIRMADA") -> Color.parseColor("#FF5722") // Naranja Intenso
                else -> Color.parseColor("#D32F2F") // Rojo para Detenida, Error, etc.
            }
            statusCard.setCardBackgroundColor(cardColor)
        }

        // 2. Observador para los datos crudos de los sensores.
        FallDetectorStatus.sensorData.observe(this) { data ->
            sensorDataTextView.text = data
        }
    }

    /**
     * Asigna las acciones a cada uno de los botones de la interfaz.
     */
    private fun setupButtonClickListeners() {
        startBtn.setOnClickListener {
            val phone = phoneNumberEditText.text.toString()
            // Validaciones de entrada: verifica que el número no esté vacío y que los permisos estén concedidos.
            if (phone.isBlank()) {
                Toast.makeText(this, "Por favor, ingresa un número de emergencia.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!arePermissionsGranted()) {
                Toast.makeText(this, "Permisos insuficientes. La app no puede iniciar.", Toast.LENGTH_LONG).show()
                checkAndRequestPermissions() // Intenta pedir permisos de nuevo.
                return@setOnClickListener
            }

            savePhoneNumber(phone) // Guarda el número para futuras sesiones.
            // Crea el Intent para iniciar el servicio, pasando la acción y el número de teléfono.
            val intent = Intent(this, FallDetectionService::class.java).apply {
                putExtra("ACTION_TYPE", "START_DETECTION")
                putExtra(KEY_PHONE, phone)
            }
            // `startForegroundService` es obligatorio desde Android 8 para iniciar servicios en segundo plano.
            ContextCompat.startForegroundService(this, intent)
            Toast.makeText(this, "Servicio de detección iniciado.", Toast.LENGTH_SHORT).show()
        }

        stopBtn.setOnClickListener {
            // Para detener el servicio, simplemente se crea un Intent y se llama a `stopService()`.
            val intent = Intent(this, FallDetectionService::class.java)
            stopService(intent)
            Toast.makeText(this, "Servicio de detección detenido.", Toast.LENGTH_SHORT).show()
        }

        // Botón de depuración para probar la `AlertActivity` sin simular una caída.
        testBtn.setOnClickListener {
            val phone = phoneNumberEditText.text.toString()
            if (phone.isBlank()) {
                Toast.makeText(this, "Ingresa un número para probar la alerta.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, AlertActivity::class.java).apply {
                putExtra("EMERGENCY_CONTACT", phone)
            }
            startActivity(intent)
        }
    }

    // --- Persistencia del Número de Teléfono ---

    /**
     * Guarda el número de teléfono en `SharedPreferences`, un almacenamiento simple de clave-valor.
     * Esto permite que el número persista incluso si la aplicación se cierra y se vuelve a abrir.
     */
    private fun savePhoneNumber(number: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_PHONE, number)
            apply() // `apply()` guarda los datos de forma asíncrona en segundo plano.
        }
    }

    /**
     * Carga el número de teléfono desde `SharedPreferences` al iniciar la actividad.
     */
    private fun loadPhoneNumber() {
        val sharedPref = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        phoneNumberEditText.setText(sharedPref.getString(KEY_PHONE, ""))
    }

    // --- Gestión de Permisos ---

    /**
     * Verifica si todos los permisos críticos han sido concedidos.
     */
    private fun arePermissionsGranted(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION, // Para la ubicación en el SMS.
            Manifest.permission.SEND_SMS,             // Para enviar el SMS de alerta.
            Manifest.permission.CALL_PHONE            // Para realizar la llamada de emergencia.
        )
        // `POST_NOTIFICATIONS` es un permiso requerido solo en Android 13 (API 33) y superior
        // para poder mostrar la notificación del servicio en primer plano.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // `all` devuelve `true` solo si todos los permisos de la lista están concedidos.
        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    /**
     * Si los permisos no están concedidos, los solicita al usuario.
     */
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
            // Muestra el diálogo del sistema para solicitar permisos.
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    /**
     * Actualiza el estado de la UI (botones y campo de texto) para prevenir acciones no deseadas.
     * @param isDetecting `true` si el servicio está corriendo, `false` si está detenido.
     */
    private fun updateUiState(isDetecting: Boolean) {
        startBtn.isEnabled = !isDetecting
        stopBtn.isEnabled = isDetecting
        phoneNumberEditText.isEnabled = !isDetecting // El número no se puede cambiar mientras la detección está activa.
    }

    /**
     * Callback que se recibe después de que el usuario responde al diálogo de solicitud de permisos.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Si algún permiso fue denegado (`any`), muestra un mensaje informativo.
            if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Algunos permisos fueron denegados. La app podría no funcionar correctamente.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
