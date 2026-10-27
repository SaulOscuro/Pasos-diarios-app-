# Pasos Diarios

Aplicación **nativa de Android** hecha con Kotlin y Jetpack Compose para convertir una meta de 10,000 pasos diarios en diez caminatas de 1,000 pasos.

## Funcionalidades de la v1.0

- **10 bloques tocables** que puedes marcar y desmarcar en cualquier orden.
- **Barra segmentada** que responde al instante y muestra el progreso de 0 a 10,000.
- **Horarios sugeridos**: 8:00, 9:30, 11:00, 12:30, 14:00, 15:30, 17:00, 18:00, 19:00 y 20:00.
- **Guardado en el dispositivo** al tocar cada bloque, incluso si cierras la app.
- **Cambio de día automático** según la fecha del teléfono. Cada día tiene sus propias casillas, así que no se sobrescriben los días anteriores.
- **Reinicio con confirmación** para evitar borrar accidentalmente el día actual.
- Diseño oscuro, sin cuentas, publicidad, internet ni permisos especiales.

> Esta versión es un **planificador manual**. No lee automáticamente el contador TriSport. Consulta `STEP`, no `TOTAL STEP`.

## Descargar el APK

1. Abre la pestaña **Actions** de este repositorio.
2. Entra al último resultado correcto de **Android APK** (marca verde).
3. Baja a **Artifacts** y descarga `pasos-diarios-debug-apk`.
4. Extrae el ZIP, copia `app-debug.apk` a tu Android e instálalo. Android puede pedir autorización para instalar APK de esa fuente.

El archivo es un APK de pruebas firmado con la clave de depuración de Android. No es una publicación en Google Play.

## Compilar localmente

Abre la carpeta en **Android Studio** con JDK 17 y Android SDK 35. Usa Gradle 8.9. En la terminal, si tienes Gradle 8.9 instalado:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

La salida estará en `app/build/outputs/apk/debug/app-debug.apk`.

## Estructura

- `app/src/main/java/com/ostwaldviz/pasosdiarios/MainActivity.kt`: diseño interactivo Compose y lógica de pantalla.
- `app/src/main/java/com/ostwaldviz/pasosdiarios/StepTracker.kt`: cálculo de bloques, persistencia local por día.
- `app/src/test/java/com/ostwaldviz/pasosdiarios/StepTrackerTest.kt`: pruebas básicas de conteo, toggles y límites.
- `.github/workflows/android.yml`: compila y publica el APK de depuración como artefacto de Actions.

## Privacidad

La aplicación no solicita permisos ni se conecta con servicios remotos. Los bloques confirmados se almacenan localmente mediante SharedPreferences. Para eliminar los datos, borra el almacenamiento de la aplicación desde los ajustes de Android.
