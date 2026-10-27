# Pasos Diarios

Aplicación **nativa de Android** hecha con Kotlin y Jetpack Compose para repartir tu meta diaria en bloques de pasos configurables. El plan inicial sigue siendo de diez bloques de 1,000 pasos.

## Funcionalidades de la v1.1

- **Meta configurable**: de 1 a 100 bloques y de 1 a 100,000 pasos por bloque. Por ejemplo, 6 × 500 = 3,000 pasos o 12 × 1,500 = 18,000 pasos.
- **Ajustar bloques y pasos** permite añadir o retirar bloques, cambiar los pasos de cada uno y revisar la meta total antes de guardar.
- **Bloques tocables** que puedes marcar y desmarcar en cualquier orden, con una barra segmentada que se adapta a la cantidad elegida.
- **Horarios sugeridos**: se mantiene el horario original al elegir diez bloques; para otras cantidades se distribuyen entre las 8:00 y las 20:00 (un solo bloque a las 8:00).
- **Guardado en el dispositivo** al tocar cada bloque, incluso si cierras la app.
- **Cambio de día automático** según la fecha del teléfono. Cada día tiene sus propias casillas, así que no se sobrescriben los días anteriores.
- **Historial** ordenado del día más reciente al más antiguo, con pasos completados, meta, porcentaje y detalle de los bloques. Hoy aparece como «En curso»; los días anteriores son de solo consulta.
- **Metas independientes por día**: modificar la configuración de hoy no recalcula ni modifica registros anteriores.
- **Reinicio con confirmación** para evitar borrar accidentalmente el día actual.
- Diseño oscuro, sin cuentas, publicidad, internet ni permisos especiales.

> Esta versión es un **planificador manual**. No lee automáticamente el contador TriSport. Consulta `STEP`, no `TOTAL STEP`.

### Guardado y medianoche

El progreso se guarda al marcar cada bloque, sin esperar a las doce. A las **00:00 de la hora local** empieza un registro vacío con la última configuración elegida, conservando el día anterior. Con la app abierta, la pantalla cambia al llegar la medianoche; al volver de segundo plano o abrirla después de varios días, carga inmediatamente la fecha actual. No necesita alarmas ni ejecutarse en segundo plano. El historial contiene los días en que se utilizó la app; no inventa registros para fechas sin uso.

### Cambiar los bloques

Los cambios se aplican a **hoy y a los próximos días**. Se mantienen las marcas de los bloques que siguen existiendo. Si retiras bloques que estaban completados, el diálogo indica cuántas marcas se quitarán. Cambiar los pasos por bloque recalcula el total de hoy según las marcas conservadas; el diálogo muestra el resultado antes de guardar. Cancelar conserva la configuración y el progreso originales.

La v1.1 recupera automáticamente los registros de diez bloques de la v1.0 si se instala como actualización conservando el almacenamiento de la app.

## Descargar el APK

1. Inicia sesión en GitHub desde el navegador y abre la pestaña **Actions** de este repositorio.
2. Entra al último resultado correcto de **Android APK** (marca verde).
3. Baja a **Artifacts** y descarga `pasos-diarios-debug-apk`.
4. Extrae el ZIP, copia `app-debug.apk` a tu Android e instálalo. Android puede pedir autorización para instalar APK de esa fuente.

El archivo es un APK de pruebas firmado con la clave de depuración de Android. No es una publicación en Google Play.

Desde la v1.1, los APKs de desarrollo usan la misma firma de prueba para que las siguientes compilaciones puedan actualizarse sin borrar datos. La clave `.github/debug-signing.p12` es **pública, solo para desarrollo**, y nunca se utiliza para builds de release. Los APKs de la v1.0 se generaban con una firma temporal por ejecución; si Android rechaza la primera actualización por firma incompatible, no desinstales la app sin antes considerar los datos que quieras conservar: desinstalar borra su almacenamiento local.

## Compilar localmente

Abre la carpeta en **Android Studio** con JDK 17 y Android SDK 35. El repositorio incluye **Gradle Wrapper 8.9**, así que no necesitas instalar Gradle por separado. En macOS/Linux ejecuta:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

En Windows ejecuta `gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.

La salida estará en `app/build/outputs/apk/debug/app-debug.apk`.

Con un emulador o teléfono de pruebas conectado, `./gradlew :app:connectedDebugAndroidTest` verifica la configuración, persistencia, historial de solo consulta, validación y planes de 100 bloques. El workflow **Android UI** ejecuta estas pruebas en Android 15 y guarda capturas y reportes como artefactos.

## Estructura

- `app/src/main/java/com/ostwaldviz/pasosdiarios/MainActivity.kt`: diseño interactivo Compose y lógica de pantalla.
- `app/src/main/java/com/ostwaldviz/pasosdiarios/StepTracker.kt`: planes configurables y progreso de un día.
- `app/src/main/java/com/ostwaldviz/pasosdiarios/DailyRepository.kt`: historial, cambio de día, migración del formato anterior y configuración persistente.
- `app/src/main/java/com/ostwaldviz/pasosdiarios/StepPreferences.kt`: almacenamiento local en SharedPreferences.
- `app/src/test/java/com/ostwaldviz/pasosdiarios/`: pruebas de conteo, límites, migración, persistencia, medianoche y conservación de metas históricas.
- `app/src/androidTest/java/com/ostwaldviz/pasosdiarios/`: pruebas de interacción y capturas en emulador.
- `.github/workflows/android.yml`: compila y publica el APK de depuración como artefacto de Actions.

## Privacidad

La aplicación no solicita permisos ni se conecta con servicios remotos. Los bloques confirmados se almacenan localmente mediante SharedPreferences. Para eliminar los datos, borra el almacenamiento de la aplicación desde los ajustes de Android.
