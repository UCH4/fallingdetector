# Falling Dector: Asistente de Detección de Caídas con IA

**Falling Dector** es una aplicación para Android diseñada para ofrecer tranquilidad y seguridad a personas vulnerables, como los adultos mayores. Utilizando los sensores del dispositivo y un modelo de Inteligencia Artificial (TensorFlow Lite), la app detecta caídas en tiempo real y activa un protocolo de alerta para notificar a un contacto de emergencia.

La característica más importante de la aplicación es su **mecanismo de cancelación de falsas alarmas**, que da al usuario un tiempo prudencial para anular la alerta en caso de que no sea una emergencia real, evitando así preocupaciones innecesarias.

## Características Clave

*   **Detección Inteligente:** Un modelo de IA analiza los datos del acelerómetro para distinguir caídas reales de otros movimientos bruscos.
*   **Ventana de Cancelación:** Al detectar una caída, la app muestra una pantalla de alerta durante 60 segundos, permitiendo al usuario cancelar la alarma si está bien.
*   **Alertas Completas:** Si no se cancela, se envía automáticamente un SMS con la ubicación GPS del usuario (si está disponible) y se realiza una llamada al contacto de emergencia.
*   **Monitoreo Constante y Eficiente:** La detección se ejecuta en un servicio en segundo plano (`Foreground Service`), garantizando que funcione incluso si la app no está en pantalla, pero optimizado para un bajo consumo de batería.
*   **Interfaz Dinámica y Reactiva:** La pantalla principal informa al usuario en tiempo real sobre el estado del servicio (monitoreando, analizando, detenido) y muestra los datos del sensor en vivo.

## Mejoras Recientes en la Arquitectura

Recientemente, hemos implementado dos mejoras cruciales para hacer la aplicación más robusta y segura:

1.  **Centralización de Alertas con `AlertActivity` (Seguridad):**
    *   **Problema Anterior:** La lógica para enviar SMS y realizar llamadas estaba en el servicio de detección, lo que podía llevar a alertas inmediatas por falsos positivos.
    *   **Solución:** Se creó una `AlertActivity` dedicada. Ahora, el `FallDetectionService` tiene la única responsabilidad de lanzar esta actividad cuando detecta una caída. Toda la lógica de enviar alertas (SMS, llamada, obtención de ubicación) se ha movido a `AlertActivity` y solo se ejecuta si el temporizador de 60 segundos llega a cero. Esto asegura que el usuario siempre tenga la oportunidad de cancelar.

2.  **Modernización de la Comunicación con `LiveData` (Robustez):**
    *   **Problema Anterior:** La comunicación entre el servicio en segundo plano y la interfaz de usuario usaba `LocalBroadcastManager`, un mecanismo antiguo y propenso a errores.
    *   **Solución:** Se implementó un objeto singleton llamado `FallDetectorStatus`. Este objeto actúa como una "fuente única de verdad" utilizando `LiveData` para exponer el estado del servicio (`status`) y los datos del sensor (`sensorData`). El `FallDetectionService` ahora escribe sus actualizaciones en este objeto, y la `MainActivity` observa estos `LiveData` para actualizar la interfaz de forma reactiva, eliminando la necesidad de `BroadcastReceivers`.

## ¿Cómo Funciona?

1.  **Monitoreo Pasivo:** El servicio escucha constantemente el acelerómetro.
2.  **Disparador de Evento (Trigger):** Un pico brusco de aceleración activa la recolección de datos.
3.  **Recolección de Datos:** Se captura una ventana de 500 muestras del sensor.
4.  **Clasificación con IA:** El modelo de TensorFlow Lite analiza los datos y determina si ha ocurrido una caída.
5.  **Pantalla de Alerta:** Si la IA confirma una caída, se lanza `AlertActivity` a pantalla completa, mostrando una cuenta atrás de 60 segundos.
6.  **Resolución de la Alerta:**
    *   **Cancelación:** El usuario presiona "ESTOY BIEN" para detener la alerta.
    *   **Confirmación:** Si el temporizador llega a cero, la app envía el SMS y realiza la llamada de emergencia.

## Arquitectura Técnica

*   `FallDetector.kt`: Interactúa con los sensores y recolecta datos.
*   `FallClassifier.kt`: Contiene el intérprete de TensorFlow Lite para la inferencia.
*   `FallDetectionService.kt`: `Foreground Service` que aloja la detección y lanza `AlertActivity`.
*   `AlertActivity.kt`: Pantalla de alerta con cuenta atrás, responsable de enviar las notificaciones de emergencia.
*   `MainActivity.kt`: UI principal para activar/desactivar el servicio y observar su estado.
*   `FallDetectorStatus.kt`: Singleton con `LiveData` para comunicar el estado del servicio a la UI.

## Cómo Empezar

### Prerrequisitos

*   Android Studio (versión recomendada: Iguana o superior)
*   Un dispositivo físico Android con acelerómetro.

### Instalación

1.  **Clona el repositorio:**
    ```bash
    git clone https://github.com/UCH4/fallingdetector.git
    ```
2.  **Abre el proyecto en Android Studio.**
3.  **Sincroniza y Ejecuta** en tu dispositivo.

## Justificación de Permisos

*   `SEND_SMS`: Para enviar el SMS de alerta.
*   `CALL_PHONE`: Para iniciar la llamada de emergencia.
*   `ACCESS_FINE_LOCATION`: Para incluir la ubicación en el SMS.
*   `FOREGROUND_SERVICE` y `POST_NOTIFICATIONS`: Para que el servicio de detección funcione de manera fiable.

## Posibles Mejoras Futuras

*   **Incorporar Datos del Giroscopio:** Añadir datos de velocidad angular al modelo para mejorar la precisión.
*   **Umbrales Configurables:** Permitir al usuario ajustar la sensibilidad.
*   **Registro de Eventos:** Guardar un historial de caídas detectadas y canceladas.

## Contribuciones

Las contribuciones son bienvenidas. Abre un "issue" para discutir ideas o envía un "pull request".
