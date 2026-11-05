# Pasos Diarios

Aplicación **nativa de Android** hecha con Kotlin y Jetpack Compose para ajustar directamente tu meta diaria y repartirla en bloques. El plan inicial sigue siendo de diez bloques de 1,000 pasos.

## Funcionalidades de la v1.5.1

- **Meta total editable**: define directamente 10,000, 12,000, 15,000, 20,000 o hasta 10,000,000 pasos. El objetivo es el valor principal al organizar los bloques.
- **Ajustar meta, bloques y horario** permite elegir entre 1 y 100 bloques sin cambiar tu meta, editar los pasos por bloque para recalcularla y seleccionar el horario de caminatas con el reloj de Android. Cuando la meta no se divide exactamente, la app distribuye la diferencia de un paso entre bloques.
- **Bloques tocables** que puedes marcar y desmarcar en cualquier orden, con una barra segmentada que se adapta a la cantidad elegida.
- **Recordatorios locales opcionales**: activa `Recordatorios por bloque` en la pestaña Hoy y concede permiso de notificaciones. La app avisa cuando llega la hora de cada bloque todavía pendiente; el siguiente aviso se adapta automáticamente si cambias las horas, la meta o los bloques completados. Los avisos se vuelven a programar tras reiniciar Android o cambiar la zona horaria.
- **Aviso destacado v1.5.1**: notificación emergente más visible, con sonido y vibración breve, contenido expandible y visibilidad en la pantalla de bloqueo, si las preferencias de Android lo permiten. Al abrir Pasos Diarios después de recibir un aviso pendiente, aparece una tarjeta centrada que puedes cerrar con **¡Voy a caminar!** sin marcar falsamente el bloque como hecho. Usa **Configurar avisos emergentes** para comprobar los ajustes de Android. Por limitaciones de Android, la aplicación no puede dibujar por defecto una ventana centrada encima de otras apps ni forzar que aparezca justo al desbloquear el teléfono.
- **No son alarmas exactas**: para ahorrar batería y evitar permisos de alarmas especiales, Android puede retrasar avisos en reposo. La app no necesita conexión a internet ni ejecutarse continuamente.
- **Horario de inicio y fin configurable**: elige, por ejemplo, de 9:00 a. m. a 9:00 p. m. y se repartirán automáticamente los bloques entre esas horas, incluyendo el primero y último. Con un solo bloque se propone la hora de inicio. El horario debe comenzar y terminar dentro del mismo día, con la hora de fin posterior a la de inicio. Cuando hay más bloques que minutos disponibles, algunas horas se repiten.
- **Horario original conservado**: el plan inicial de diez bloques de 8:00 a 20:00 mantiene sus horas de siempre; si cambias el intervalo, se distribuyen uniformemente.
- **Guardado en el dispositivo** al tocar cada bloque, incluso si cierras la app.
- **Cambio de día automático** según la fecha del teléfono. Cada día tiene sus propias casillas, así que no se sobrescriben los días anteriores.
- **Historial editable**: entra a un día anterior para marcar o desmarcar sus bloques, o pulsa **Editar pasos exactos** e introduce el total del pedómetro, aunque supere tu meta. También puedes usar **Añadir o corregir un día anterior** para registrar fechas que habías olvidado. Al introducir un total exacto, los bloques cubiertos se marcan automáticamente; al editar una casilla, la cuenta vuelve al cálculo por bloques.
- **Metas y horarios independientes por día**: modificar la configuración de hoy no recalcula ni modifica registros anteriores.
- **Exportar / Restaurar** un archivo JSON con todas las fechas registradas, los pasos exactos, bloques, metas y horarios. Al importar, se muestra una confirmación: las fechas coincidentes se reemplazan, los demás días se conservan y la configuración predeterminada se restaura.
- **Reinicio con confirmación** para evitar borrar accidentalmente el día actual.
- Diseño oscuro, sin cuentas, publicidad ni internet. **Solo solicita notificaciones cuando activas los recordatorios.** Usa el permiso de arranque para volver a programarlos tras un reinicio.

> Esta versión es un **planificador manual**. No lee automáticamente el contador TriSport. Consulta `STEP`, no `TOTAL STEP`.

### Guardado y medianoche

El progreso se guarda al marcar cada bloque, sin esperar a las doce. A las **00:00 de la hora local** empieza un registro vacío con la última configuración elegida, conservando el día anterior. Con la app abierta, la pantalla cambia al llegar la medianoche; al volver de segundo plano o abrirla después de varios días, carga inmediatamente la fecha actual. No necesita alarmas ni ejecutarse en segundo plano. El historial contiene los días en que se utilizó la app; no inventa registros para fechas sin uso.

### Editar días pasados y respaldar tus datos

En **Historial** puedes añadir cualquier fecha anterior a hoy. Selecciona una fecha con el calendario e introduce los pasos reales, por ejemplo 12,345. Si ya existe, se corregirá únicamente ese registro, manteniendo su meta y su horario históricos. También puedes abrir la fecha y editar sus casillas una por una. El día de hoy se modifica desde la pestaña **Hoy**.

Para proteger el historial, abre **Historial → Exportar** y elige una carpeta de tu teléfono, Google Drive u otro proveedor disponible en el selector de archivos de Android. Obtendrás un respaldo `pasos-diarios-AAAA-MM-DD.json`, legible y portable. Después de reinstalar, pulsa **Historial → Restaurar**, selecciona el JSON y confirma la combinación de registros. No se necesitan permisos amplios de almacenamiento, internet ni una cuenta.

**Importante:** desinstalar la aplicación borra sus datos privados. Esta app mantiene `android:allowBackup=false`; no hay sincronización automática. Si todavía tienes la v1.3 instalada, intenta actualizar **sin desinstalar** para conservar los registros y poder exportarlos desde la v1.4. Solo si Android informa de una firma incompatible se necesita una reinstalación, y en ese caso los datos no se pueden recuperar del repositorio: primero anota o fotografía el historial que quieras preservar.

### Cambiar tu meta, los bloques y el horario

En **Ajustar meta, bloques y horario**, modifica la **Meta diaria (pasos)** para repartir automáticamente el total entre los bloques actuales. Si modificas **Cantidad de bloques**, la meta total permanece igual y la cantidad de pasos por bloque se ajusta. Si editas **Pasos por bloque**, se actualiza la meta total según la cantidad de bloques, como antes. Por ejemplo, 12,000 pasos en 7 bloques se distribuyen en 2 bloques de 1,715 pasos y 5 de 1,714. Cada bloque muestra su objetivo exacto.

Para ajustar el horario, toca **Desde** y **Hasta** y elige las horas con el selector nativo de Android. Los bloques se distribuyen automáticamente dentro de ese horario sin modificar el objetivo de pasos ni el progreso registrado. También puedes cambiar el horario sin editar la meta o los bloques. Por ejemplo, con diez bloques entre las 9:00 a. m. y las 9:00 p. m., el primero se propone a las 9:00 a. m. y el último a las 9:00 p. m.

Los cambios se aplican a **hoy y a los próximos días**. Se mantienen las marcas de los bloques que siguen existiendo. Si retiras bloques que estaban completados, el diálogo indica cuántas marcas se quitarán. Recalcular el plan modifica los pasos de hoy según las marcas conservadas; el diálogo muestra el resultado antes de guardar. Cancelar conserva la configuración y el progreso originales.

La v1.5 conserva los registros y metas de la v1.4, v1.3, v1.2 y v1.1, y recupera registros de diez bloques de la v1.0 si se instala como actualización conservando el almacenamiento de la app. Los días anteriores mantienen su meta y su horario original.

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

Con un emulador o teléfono de pruebas conectado, `./gradlew :app:connectedDebugAndroidTest` verifica la configuración, persistencia, edición de historial, validación y planes de 100 bloques. El workflow **Android UI** ejecuta estas pruebas en Android 15 y guarda capturas y reportes como artefactos.

## Estructura

- `app/src/main/java/com/ostwaldviz/pasosdiarios/MainActivity.kt`: diseño interactivo Compose y lógica de pantalla.
- `app/src/main/java/com/ostwaldviz/pasosdiarios/StepTracker.kt`: planes configurables y progreso de un día.
- `app/src/main/java/com/ostwaldviz/pasosdiarios/DailyRepository.kt`: historial editable, cambio de día, migración y respaldo/restauración seguros.
- `app/src/main/java/com/ostwaldviz/pasosdiarios/BackupArchive.kt`: lectura y escritura del JSON portable.
- `app/src/main/java/com/ostwaldviz/pasosdiarios/StepPreferences.kt`: almacenamiento local en SharedPreferences.
- `app/src/main/java/com/ostwaldviz/pasosdiarios/StepReminders.kt`: avisos locales Android, programación del siguiente bloque pendiente y reprogramación tras reiniciar.
- `app/src/test/java/com/ostwaldviz/pasosdiarios/`: pruebas de conteo, límites, migración, persistencia, medianoche y conservación de metas históricas.
- `app/src/androidTest/java/com/ostwaldviz/pasosdiarios/`: pruebas de interacción y capturas en emulador.
- `.github/workflows/android.yml`: compila y publica el APK de depuración como artefacto de Actions.

## Privacidad

La aplicación solo solicita permiso de notificaciones si decides activar los recordatorios y declara permiso para restaurarlos tras reiniciar. No se conecta con servicios remotos. Los bloques confirmados se almacenan localmente mediante SharedPreferences. El respaldo JSON queda bajo tu control y puedes compartirlo voluntariamente. Para eliminar los datos locales, borra el almacenamiento de la aplicación desde los ajustes de Android.
