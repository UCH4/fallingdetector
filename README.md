# Falling Dector: Asistente de Detección de Caídas con IA

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Falling Dector** es una aplicación de código abierto para Android diseñada para actuar como una red de seguridad personal, detectando caídas en tiempo real mediante el análisis de datos de sensores con un modelo de Inteligencia Artificial en el dispositivo.

---

## El Problema: El Impacto Real de las Caídas

Las caídas son un problema de salud pública global y a menudo subestimado. Las estadísticas subrayan la urgencia y la necesidad de soluciones tecnológicas efectivas:

*   **Principal Causa de Muerte por Lesión:** Según la Organización Mundial de la Salud (OMS), las caídas son la **segunda causa principal de muerte por lesiones accidentales o no intencionales** en todo el mundo.
*   **Impacto en Adultos Mayores:** Los Centros para el Control y la Prevención de Enfermedades (CDC) de EE. UU. informan que cada año, millones de adultos mayores de 65 años sufren caídas, y **una de cada cinco de estas caídas causa una lesión grave**, como una fractura de hueso o una lesión en la cabeza.
*   **El Factor Tiempo:** La rapidez con la que una persona recibe ayuda después de una caída es un factor crítico que influye directamente en la gravedad de las secuelas. La incapacidad de pedir ayuda puede llevar a complicaciones graves.

**Falling Dector** fue creado para abordar este problema, proporcionando un sistema de alerta automático, rápido y fiable que funciona incluso cuando el usuario no puede pedir ayuda por sí mismo.

---

## Diseño del Sistema y Arquitectura Técnica

La aplicación está construida sobre una arquitectura robusta y moderna, priorizando la eficiencia, la fiabilidad y la separación de responsabilidades.

### Flujo General de Detección

El sistema opera como una máquina de estados finitos, diseñada para maximizar la duración de la batería mientras se mantiene una alta sensibilidad a los eventos de caída.

```
[Usuario Inicia el Servicio en MainActivity]
              |
              v
[FallDetectionService se ejecuta en Primer Plano (Notificación Persistente)]
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
[FallDetectionService lanza Notificación de Pantalla Completa]
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
| **`MainActivity`**        | Punto de entrada de la UI. Gestiona la solicitud de permisos, la entrada del número de teléfono y la visualización del estado del sistema.                                                                             | - **Reactiva:** Usa `LiveData` para observar cambios desde `FallDetectorStatus`, desacoplando la UI del servicio.<br>- **Persistencia:** Guarda el número de teléfono usando `SharedPreferences`. |
| **`FallDetectorStatus`**  | **Fuente Única de Verdad (Singleton)**. Centraliza el estado (`status`) y los datos de los sensores (`sensorData`) para toda la aplicación.                                                                     | - **Seguridad de Hilos:** Usa `postValue()` en `LiveData` para permitir actualizaciones seguras desde hilos de segundo plano.<br>- **Arquitectura Limpia:** Elimina la necesidad de `BroadcastReceiver`. |
| **`FallDetectionService`**| **Servicio de Primer Plano**. Aloja y gestiona el ciclo de vida de `FallDetector`. Garantiza que la detección continúe incluso si la app se cierra.                                                               | - **Fiabilidad:** Usa `fullScreenIntent` para mostrar la alerta sobre la pantalla de bloqueo.<br>- **Puente:** Implementa la interfaz `FallDetector.FallListener` para reaccionar a los eventos de detección. |
| **`FallDetector`**        | **El Corazón de la Detección**. Implementa la lógica de sensores y la máquina de estados (`MONITORING`, `CANDIDATE_EVENT`).                                                                                       | - **Eficiencia:** Usa un "disparador" (`trigger`) basado en un umbral de magnitud para evitar el análisis constante.<br>- **Recolección Sincronizada:** Captura datos tanto del acelerómetro como del giroscopio en búferes. |
| **`FallClassifier`**      | **El Cerebro de IA**. Carga el modelo `fall_detector_model.tflite` y ejecuta la inferencia sobre los datos de los sensores para determinar la probabilidad de una caída.                                            | - **Rendimiento:** Carga el modelo como un `MappedByteBuffer` para un acceso a memoria más rápido y eficiente.<br>- **Preparado para el Futuro:** La firma del método `classify` ya acepta datos del giroscopio. |
| **`AlertActivity`**       | **La Red de Seguridad Final**. Muestra una pantalla de alerta a pantalla completa, reproduce una alarma sonora y da al usuario la oportunidad de cancelar. Ejecuta el protocolo de emergencia.                         | - **Crítico:** Usa flags de `WindowManager` para aparecer sobre la pantalla de bloqueo.<br>- **Alarma Sonora:** Utiliza `RingtoneManager` para una alerta audible imposible de ignorar. |

---

## Cómo Empezar

### Prerrequisitos

*   Android Studio (Recomendado: Iguana o superior).
*   Un **dispositivo físico Android** con acelerómetro. El emulador no puede simular los datos de sensores necesarios para una caída real.

### Instalación y Ejecución

1.  **Clonar el repositorio:**
    ```bash
    git clone https://github.com/UCH4/fallingdetector.git
    ```
2.  **Abrir el proyecto** en Android Studio.
3.  **Ejecutar la aplicación** en el dispositivo conectado (`Shift` + `F10`).
4.  Al iniciar, la aplicación solicitará varios permisos. **Es fundamental concederlos** para que el protocolo de alerta funcione.
5.  Introducir un número de teléfono de emergencia válido y pulsar **"Iniciar Detección"**.

---

## Hoja de Ruta y Posibles Mejoras

1.  **Mejora del Modelo de IA:** El paso más importante es entrenar un nuevo modelo que utilice los datos del giroscopio que ya se están recolectando para aumentar la precisión.
2.  **Ajuste de Sensibilidad:** Crear una pantalla de "Ajustes" para que el usuario pueda elegir entre niveles de sensibilidad (Baja, Media, Alta).
3.  **Historial de Eventos:** Añadir una pantalla que muestre un registro de las caídas detectadas y si fueron canceladas o confirmadas.

---

## Contribuciones

Las contribuciones son bienvenidas. La forma recomendada de contribuir es abrir un "Issue" para discutir la propuesta antes de enviar un "Pull Request".

## Licencia

Este proyecto está distribuido bajo la Licencia MIT.
