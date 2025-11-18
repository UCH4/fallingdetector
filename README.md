# Falling Dector

**Falling Dector** es una aplicación para Android diseñada para detectar caídas en tiempo real utilizando el acelerómetro y el giroscopio del dispositivo. Una vez que se detecta una caída, la aplicación envía automáticamente un mensaje SMS con la ubicación GPS del usuario a un contacto de emergencia predefinido.

## Características Clave

*   **Detección de Caídas en Tiempo Real:** Utiliza un modelo de TensorFlow Lite para analizar los datos de los sensores y detectar caídas con precisión.
*   **Alertas por SMS:** Envía automáticamente un mensaje de texto a un contacto de emergencia después de una caída.
*   **Ubicación GPS:** Incluye las coordenadas GPS en el mensaje de alerta para una rápida localización.
*   **Servicio en Segundo Plano:** La detección de caídas se ejecuta como un servicio en segundo plano para un monitoreo constante.
*   **Fácil de Usar:** Interfaz de usuario sencilla para configurar el contacto de emergencia e iniciar/detener el servicio.

## Cómo Empezar

### Prerrequisitos

*   Android Studio
*   Un dispositivo Android con acelerómetro y giroscopio

### Instalación

1.  Clona este repositorio:
    ```bash
    git clone https://github.com/tu_usuario/falling-dector.git
    ```
2.  Abre el proyecto en Android Studio.
3.  Construye y ejecuta la aplicación en tu dispositivo.

## Cómo Usar

1.  Abre la aplicación.
2.  Ingresa el número de teléfono de tu contacto de emergencia.
3.  Presiona el botón "Iniciar Servicio" para comenzar el monitoreo.
4.  Para detener el monitoreo, presiona el botón "Detener Servicio".

## Contribuciones

Las contribuciones son bienvenidas. Si deseas contribuir a este proyecto, por favor abre un "issue" o envía un "pull request".

## Licencia

Este proyecto está bajo la Licencia MIT. Consulta el archivo `LICENSE` para más detalles.
