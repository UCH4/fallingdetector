# Falling Dector: Asistente de Detección de Caídas con IA

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Falling Dector** es una aplicación de código abierto para Android diseñada para actuar como una red de seguridad personal, detectando caídas en tiempo real mediante el análisis de datos de sensores con un modelo de Inteligencia Artificial en el dispositivo.

---

## El Problema: El Impacto Real de las Caídas

Las caídas son un problema de salud pública global y a menudo subestimado, especialmente entre los adultos mayores. Las estadísticas subrayan la urgencia de soluciones tecnológicas efectivas:

*   **Principal Causa de Muerte por Lesión:** Según la Organización Mundial de la Salud (OMS), las caídas son la **segunda causa principal de muerte por lesiones accidentales** en todo el mundo.
*   **Impacto en Adultos Mayores:** Los Centros para el Control y la Prevención de Enfermedades (CDC) informan que **una de cada cinco caídas** en adultos mayores causa una lesión grave, como una fractura de hueso o una lesión en la cabeza.
*   **El Factor Tiempo:** La rapidez con la que una persona recibe ayuda después de una caída es un factor crítico que influye directamente en la gravedad de las secuelas. La incapacidad de pedir ayuda puede llevar a complicaciones graves.

**Falling Dector** fue creado para abordar este problema, proporcionando un sistema de alerta automático, rápido y fiable que funciona incluso cuando el usuario no puede pedir ayuda por sí mismo.

---

## Diseño del Sistema y Arquitectura Técnica

La aplicación está construida sobre una arquitectura moderna, priorizando la fiabilidad, la eficiencia y la separación de responsabilidades.

### Flujo General de Detección

El sistema opera como una máquina de estados finitos, diseñada para maximizar la duración de la batería mientras se mantiene una alta sensibilidad a los eventos de caída.

```
[Usuario Inicia el Servicio en MainActivity]
              |
              v
[FallDetectionService se ejecuta en Primer Plano]
              |
              v
[FallDetector: Estado = MONITORING]
   |         ^
   |         | (Evento rechazado por IA, vuelve a monitorear)
   |         |
(Pico de Aceleración > 20.0 Gs)
   |         |
   v         |
[FallDetector: Estado = CANDIDATE_EVENT]
   |         |
   | (Recolecta 500 muestras de Acelerómetro y Giroscopio)
   |         |
   v         |
[FallClassifier.classify(datos_acelerometro)] --+
   |         
(Probabilidad de Caída > 0.4)
   |          
   v
[FallDetectionService decide cómo lanzar la alerta]
              |
              v
[AlertActivity: Muestra Cuenta Atrás de 60s y Suena Alarma Sonora]
   |                                        |
   | (Usuario pulsa CANCELAR)               | (El temporizador llega a 0)
   |                                        |
   v                                        v
[La alerta se detiene, la actividad se cierra]   [Protocolo de Emergencia: Se envía SMS y se realiza la Llamada]
```

### Desglose de Componentes

| Componente Fichero (.kt)  | Responsabilidad Principal                                                                                                                                                                                            | Puntos Clave de Diseño                                                                                                                                   |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`MainActivity`**        | Punto de entrada de la UI. Gestiona permisos, entrada de teléfono y muestra el estado del sistema.                                                                                                                   | - **Reactiva:** Usa `LiveData` para observar cambios desde `FallDetectorStatus`, desacoplando la UI del servicio.<br>- **Control de Estado:** Informa a `FallDetectorStatus` si la app está en primer plano o no. |
| **`FallDetectorStatus`**  | **Fuente Única de Verdad (Singleton)**. Centraliza el estado (`status`), los datos de sensores (`sensorData`) y si la app está visible (`isAppInForeground`).                                                | - **Seguridad de Hilos:** Usa `@Volatile` y `postValue()` en `LiveData` para una comunicación segura entre el servicio y la UI.<br>- **Arquitectura Limpia:** Reemplaza a los antiguos `BroadcastReceiver`. |
| **`FallDetectionService`**| **Servicio de Primer Plano**. Aloja el `FallDetector`. Garantiza la detección continua y decide la mejor forma de lanzar la alerta.                                                                         | - **Lógica Inteligente:** Lanza `AlertActivity` directamente si la app está abierta; usa una notificación `fullScreenIntent` si la app está en segundo plano para máxima fiabilidad. |
| **`FallDetector`**        | **El Corazón de la Detección**. Implementa la lógica de sensores y la máquina de estados (`MONITORING`, `CANDIDATE_EVENT`).                                                                                       | - **Eficiencia:** Usa un "disparador" (`trigger`) basado en un umbral de impacto para evitar el análisis constante.<br>- **Sensibilidad Ajustada:** El umbral de la IA está calibrado en `0.4` para priorizar la detección. |
| **`FallClassifier`**      | **El Cerebro de IA**. Carga el modelo `fall_detector_model.tflite` y ejecuta la inferencia sobre los datos de los sensores.                                                                               | - **Rendimiento:** Carga el modelo como un `MappedByteBuffer` para un acceso a memoria más rápido.<br>- **Preparado para el Futuro:** La firma del método `classify` ya acepta datos del giroscopio. |
| **`AlertActivity`**       | **La Red de Seguridad Final**. Muestra una alerta a pantalla completa, reproduce una alarma sonora en bucle y gestiona la cuenta atrás.                                                                     | - **Crítico:** Usa flags de `WindowManager` para aparecer sobre la pantalla de bloqueo.<br>- **Alarma Sonora:** Utiliza `RingtoneManager` para una alerta audible imposible de ignorar. |

---

## Cómo Empezar

### Prerrequisitos

*   Android Studio (Recomendado: Iguana o superior).
*   Un **dispositivo físico Android** con acelerómetro. El emulador no puede simular de forma fiable los sensores ni la alerta sonora.

### Instalación y Ejecución

1.  **Clonar el repositorio:**
    ```bash
    git clone https://github.com/UCH4/fallingdetector.git
    ```
2.  **Abrir el proyecto** en Android Studio.
3.  **Generar el APK** desde el menú `Build` > `Build Bundle(s) / APK(s)` > `Build APK(s)`.
4.  **Instalar el archivo `app-debug.apk`** en el dispositivo de prueba.
5.  Al iniciar, la aplicación solicitará varios permisos. **Es fundamental concederlos** para que el protocolo de alerta funcione.
6.  Introducir un número de teléfono de emergencia válido y pulsar **"Iniciar Detección"**.

---

## Hoja de Ruta y Posibles Mejoras

1.  **Mejora del Modelo de IA:** El paso más importante es entrenar un nuevo modelo que utilice los datos del giroscopio que ya se están recolectando para aumentar la precisión.
2.  **Ajuste de Sensibilidad:** Crear una pantalla de "Ajustes" para que el usuario pueda elegir entre niveles de sensibilidad (Baja, Media, Alta).
3.  **Historial de Eventos:** Añadir una pantalla que muestre un registro de las caídas detectadas y si fueron canceladas o confirmadas.

## Contribuciones

Las contribuciones son bienvenidas. La forma recomendada es abrir un "Issue" para discutir la propuesta antes de enviar un "Pull Request".

## Licencia

Este proyecto está distribuido bajo la Licencia MIT.
