# Falling Dector: Asistente de Detección de Caídas con IA

**Falling Dector** es una aplicación para Android diseñada para ofrecer tranquilidad y seguridad a personas vulnerables, como los adultos mayores. Utilizando los sensores del dispositivo y un modelo de Inteligencia Artificial (TensorFlow Lite), la app detecta caídas en tiempo real y activa un protocolo de alerta para notificar a un contacto de emergencia.

La característica más importante de la aplicación es su **mecanismo de cancelación de falsas alarmas**, que da al usuario un tiempo prudencial para anular la alerta en caso de que no sea una emergencia real, evitando así preocupaciones innecesarias.

## Características Clave

*   **Detección Inteligente:** Un modelo de IA analiza los datos del acelerómetro para distinguir caídas reales de otros movimientos bruscos.
*   **Ventana de Cancelación:** Al detectar una caída, la app muestra una pantalla de alerta durante 60 segundos, permitiendo al usuario cancelar la alarma si está bien.
*   **Alertas Completas:** Si no se cancela, se envía automáticamente un SMS con la ubicación GPS del usuario (si está disponible) y se realiza una llamada al contacto de emergencia.
*   **Monitoreo Constante y Eficiente:** La detección se ejecuta en un servicio en segundo plano (`Foreground Service`), garantizando que funcione incluso si la app no está en pantalla, pero optimizado para un bajo consumo de batería.
*   **Interfaz Sencilla:** Diseñada para ser fácil de usar. Solo se necesita configurar un número de teléfono y activar el servicio.

## ¿Cómo Funciona?

El sistema opera a través de un flujo de varios pasos para maximizar la precisión y evitar falsos positivos:

1.  **Monitoreo Pasivo:** El servicio escucha constantemente el acelerómetro del dispositivo, esperando un evento de alta energía.
2.  **Disparador de Evento (Trigger):** Si la magnitud de la aceleración supera un umbral predefinido (un pico brusco), el sistema asume que ha ocurrido un "evento candidato" y pasa a la siguiente fase. Esto ahorra batería al no analizar datos constantemente.
3.  **Recolección de Datos:** Durante un breve periodo, la app recolecta una ventana de datos del sensor (500 muestras) que captura el patrón de movimiento completo del evento.
4.  **Clasificación con IA:** Esta ventana de datos se envía al modelo de TensorFlow Lite (`fall_detector_model.tflite`), que analiza el patrón y determina la probabilidad de que haya sido una caída.
5.  **Pantalla de Alerta:** Si la IA confirma una caída, en lugar de alertar inmediatamente, se lanza una actividad a pantalla completa (`AlertActivity`). Esta pantalla muestra una cuenta atrás de 60 segundos.
6.  **Resolución de la Alerta:**
    *   **Cancelación:** Si el usuario presiona el botón "ESTOY BIEN", la alerta se detiene y la app vuelve al modo de monitoreo.
    *   **Confirmación:** Si el temporizador llega a cero, la app procede a enviar el SMS y realizar la llamada de emergencia.

## Arquitectura Técnica

El proyecto se estructura en varios componentes clave:

*   `FallDetector.kt`: La clase responsable de interactuar con los sensores, implementar la lógica del disparador y la recolección de datos.
*   `FallClassifier.kt`: Contiene el intérprete de TensorFlow Lite. Carga el modelo `.tflite` desde los assets y realiza la inferencia sobre los datos del sensor.
*   `FallDetectionService.kt`: Un `Foreground Service` que aloja al `FallDetector`. Su función es iniciar la `AlertActivity` cuando se confirma una caída.
*   `AlertActivity.kt`: La pantalla de alerta con la cuenta atrás. Es la responsable final de enviar el SMS y realizar la llamada si la alarma no es cancelada.
*   `MainActivity.kt`: La interfaz de usuario principal para que el usuario ingrese el número de emergencia y active o desactive el servicio.

## Cómo Empezar

### Prerrequisitos

*   Android Studio (versión recomendada: Iguana o superior)
*   Un dispositivo físico Android con acelerómetro. El emulador no puede simular caídas.

### Instalación

1.  **Clona el repositorio:**
    ```bash
    git clone https://github.com/UCH4/fallingdetector.git
    ```
2.  **Abre el proyecto:**
    Abre Android Studio y selecciona `Open an existing project`, navegando hasta la carpeta clonada.
3.  **Sincroniza y Ejecuta:**
    Android Studio descargará automáticamente las dependencias de Gradle. Una vez sincronizado, ejecuta la aplicación en tu dispositivo conectado.

## Justificación de Permisos

La aplicación requiere varios permisos sensibles para cumplir su función. Es importante ser transparente con el usuario sobre por qué son necesarios:

*   `SEND_SMS`: Para enviar el mensaje de texto de alerta al contacto de emergencia.
*   `CALL_PHONE`: Para iniciar la llamada de emergencia automáticamente.
*   `ACCESS_FINE_LOCATION`: Para obtener las coordenadas GPS y enviarlas en el SMS de alerta.
*   `FOREGROUND_SERVICE`: Para permitir que el servicio de detección se ejecute de manera fiable en segundo plano.
*   `POST_NOTIFICATIONS`: Requerido por Android para mostrar la notificación persistente del servicio en primer plano.

## Posibles Mejoras Futuras

*   **Incorporar Datos del Giroscopio:** Añadir datos de velocidad angular al modelo de IA podría mejorar significativamente la precisión, ayudando a distinguir mejor entre un salto y una caída real.
*   **Umbrales Configurables:** Permitir al usuario ajustar la sensibilidad del disparador de eventos desde los ajustes de la app.
*   **Registro de Eventos:** Guardar un historial de caídas detectadas (y canceladas) para que el usuario o un cuidador puedan revisarlo.

## Contribuciones

Las contribuciones son bienvenidas. Si tienes ideas para mejorar la app, por favor abre un "issue" para discutirlo o envía un "pull request" con tu mejora.
