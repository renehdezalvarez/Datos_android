<div align="center">

<img src="docs/images/icon.png" alt="Previsor de Datos Móviles" width="96"/>

# Previsor de Datos Móviles

Predice si tu paquete de datos móviles te alcanzará para el resto del ciclo,
según tu saldo restante, los días transcurridos y tu ritmo de consumo.

![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=android&logoColor=white)
![CI](https://github.com/renehdezalvarez/datos_android/actions/workflows/ci.yml/badge.svg)

</div>

---

## ✨ Características

- **Dos líneas independientes**: una línea mensual (ciclo de calendario) y una
  línea prepago de 35 días (conteo desde la compra).
- **Pronóstico de agotamiento**: calcula el ritmo de consumo diario, la cuota
  diaria segura y el día del ciclo en que se agotará el saldo.
- **Semáforo de riesgo**: `Sobrante`, `En ritmo`, `Riesgo de agotarse`,
  `Crítico`.
- **Simulador what-if**: "¿y si consumo X MB al día, me alcanza?"
- **Recarga de saldo (solo línea prepaga)**: consulta `*222*732#` una vez por
  ciclo al abrir la app, guarda la fecha en que el operador permite recargar
  (la fecha del mensaje más un día) y la muestra en la línea prepaga. Un
  recordatorio diario a las 09:00 avisa desde ese día hasta que registres un
  paquete nuevo. El aviso de "último día" del paquete sigue igual.
- **Lecturas USSD automáticas**: el botón **Consultar Saldo** de cada línea
  consulta el operador y actualiza el saldo sin abrir ningún formulario. El
  código USSD por operador y la SIM de cada línea se configuran una vez; la app
  consulta directamente con permiso de llamada y captura la respuesta del
  operador. La selección de SIM por línea evita consultar la línea equivocada
  en teléfonos de doble SIM; se guarda en la base de datos y se usa tanto en
  la API oficial como en el marcador de respaldo. Para listar las SIM se pide
  el permiso de teléfono (`READ_PHONE_STATE`), del mismo grupo que el permiso
  de llamada; si no se concede, se usa la SIM predeterminada del sistema. En Android 8+ se usa la API
  oficial de telefonía; si el operador la bloquea, el lector de accesibilidad
  queda como respaldo y solo lee la pantalla durante la consulta. El texto se
  interpreta automáticamente, detecta el saldo (GB/MB, con coma o punto:
  "4.2 GB", "2,5GB", "10 gigas", "850 megas"...) y en la línea prepago también
  los días restantes ("le quedan 20 días", "válido hasta 25/11"), actualizando
  el día del ciclo automáticamente. El formulario completo (código USSD, SIM,
  captura manual del texto) está solo en **Ajustes → Lectura USSD**, para
  cuando quieras pegarlo a mano.
- **Lectura automática al abrir**: al iniciar la app, si una línea no tiene
  lecturas registradas en el día actual, se intenta una consulta automática
  silenciosa por la API oficial de telefonía (solo cuando los permisos y la SIM
  configurada lo permiten, sin abrir el marcador).
- **Recordatorio diario de saldo**: a las 22:00, si alguna línea sigue sin
  lectura registrada en el día, aparece una notificación: *"Consulta tu saldo
  para afinar el pronóstico"*. El aviso solo abre la app: no marca el código
  USSD ni activa la captura de accesibilidad en segundo plano. Se retira solo
  al registrar las lecturas pendientes.
- **Historial de lecturas y consumo diario** por línea: lecturas con notas
  (editables/eliminables) y una vista de **consumo diario** calculada a partir
  de las diferencias entre lecturas registradas cada día.
- **Gráfico de barras de consumo por día del ciclo** en la pantalla principal de
  cada línea: muestra en MB cuánto se consumió en cada día del ciclo; la última
  lectura de un día sirve como saldo inicial del día siguiente.
- **Persistencia local** con Room (sin cuentas, sin servicios externos).

## 📲 Instalación y configuración

### Instalar

1. Descarga el APK firmado de la última
   [Release](https://github.com/renehdezalvarez/datos_android/releases)
   (archivo `PrevisorDatos_X.Y.Z.apk`). No hace falta cuenta ni Play Store.
2. La primera vez Android pedirá permiso para instalar aplicaciones desconocidas.
   Concédelo solo a **Previsor de Datos Móviles** cuando te lo pregunte. Si no
   aparece el aviso, ve a **Ajustes del teléfono → Apps → Previsor de Datos
   Móviles → Instalar aplicaciones desconocidas**. Ese mismo permiso lo necesita
   la actualización desde la app.
3. Instala el APK y abre la app. En versiones posteriores no tienes que volver a
   descargarlo: entra en **Ajustes → Buscar actualizaciones**. La descarga es
   silenciosa y Android solo pide confirmar la instalación al final.

### Configuración para que la consulta de saldo no dé error

1. **Permisos de teléfono**: al abrir la app concede **Teléfono**
   (`CALL_PHONE` y `READ_PHONE_STATE`). Sin `CALL_PHONE` no se puede marcar el
   código USSD; sin `READ_PHONE_STATE` no se pueden listar las SIM y se usa la
   predeterminada del sistema, que en un teléfono de doble SIM suele ser la
   línea equivocada. Concede también **Notificaciones** en Android 13+ para los
   avisos de saldo, vencimiento y recarga.
2. **Código USSD de cada línea**: en **Ajustes → Lectura USSD** comprueba el
   código Guardado (por defecto, Línea 1 `*111#` y Línea 2 `*222*328#`). Si tu
   operador usa otro, cámbialo y guárdalo. La consulta de fecha de recarga de la
   línea prepaga usa siempre `*222*732#`.
3. **SIM de cada línea**: en **Ajustes**, usa el selector **SIM de la línea** de
   cada tarjeta y elige el slot donde está esa línea (0 = primera SIM, 1 =
   segunda). En un teléfono de una sola SIM puedes dejar la opción
   predeterminada del sistema.
4. **Lector de respuestas USSD (respaldo)**: algunos operadores desactivan la
   API oficial de telefonía (`sendUssdRequest`). Actívalo en **Ajustes del
   teléfono → Accesibilidad → Servicios descargados → Lector de respuestas
   USSD**. Así, **Consultar Saldo** abre el marcador, marca el código y lee la
   respuesta del operador únicamente mientras dura la consulta. Si no lo
   activas, la app no puede capturar la respuesta sola: pega el texto en
   **Ajustes → Lectura USSD** para registrar el saldo manualmente.
5. **Comprueba el entorno**: la SIM debe estar activa y con servicio (sin modo
   avión) y el operador debe responder al código USSD. Si la respuesta muestra
   un menú, termínalo en el marcador o pega el texto final en el formulario
   manual.

Después de esto, **Consultar Saldo** en cualquiera de las dos líneas registra el
saldo y los días restantes automáticamente. Si todavía falla, revisa sobre todo
los puntos 1, 3 y 4: son las causas habituales de error.

## 🏗️ Arquitectura

MVVM con Jetpack Compose, organizado por capas:

```
app/src/main/java/com/example/
├── MainActivity.kt                  # Host + navegación por pestañas
├── data/
│   ├── local/       AppDatabase, DataDao        (Room)
│   ├── model/       DataPlanEntity, DataBalanceLogEntity
│   └── repository/  DataPlanRepository          (fuente única de datos)
├── domain/                          # Lógica pura, sin Android
│   ├── DataForecastCalculator       # Pronóstico, cuotas, semáforo
│   └── UssdParser                   # Parser de respuestas USSD/SMS
└── ui/
    ├── components/                  # Cards, dialogs, formatters
    ├── screens/                     # Forecast, Prepago 35d, Logs, Ajustes
    ├── theme/
    └── viewmodel/                   # DataForecastViewModel (StateFlow)
```

## 🛠️ Compilar

Requisitos: JDK 17+ y el Android SDK (API 36).

```bash
./gradlew assembleDebug          # APK debug en app/build/outputs/apk/debug/
./gradlew assembleRelease         # firma con KEYSTORE_PATH/STORE_PASSWORD/KEY_PASSWORD
```

> El primer `./gradlew` descarga automáticamente Gradle 9.3.1 vía el wrapper.

Instalar en un dispositivo conectado:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 🧪 Tests

```bash
./gradlew testDebugUnitTest
```

Cubren el motor de pronóstico (`DataForecastCalculator`) y el parser USSD
(`UssdParser`) con JUnit.

## 📦 Descargas

Los APK firmados se publican como
[Releases](https://github.com/renehdezalvarez/datos_android/releases), no en
el historial de git.

## 🔄 Actualizaciones desde la app

La app comprueba silenciosamente la última release de GitHub (cada 6 horas,
también al abrir), muestra una tarjeta en **Ajustes** cuando hay una versión
nueva y descarga el APK en segundo plano. La descarga se verifica con el
SHA-256 y con la firma de la app antes de ofrecer **Instalar**.

Android no permite que una app instalada por sideload instale silenciosamente:
el instalador del sistema siempre pide una confirmación. El único toque del
usuario es ese; si aún no concedió "instalar aplicaciones desconocidas", la app
abre el ajuste correspondiente y reanuda la instalación al volver.

Cada release publica un `update.json` como asset con `versionCode`,
`versionName`, `tag`, nombre del APK, `sha256` y tamaño. La versión se compara
por `versionCode`, así que el formato del `versionName` no afecta la detección.
El botón **Buscar actualizaciones** fuerza una comprobación; **Ahora no** omite
esa versión hasta la siguiente release. Si la versión publicada deja de ser mayor
que la instalada (por ejemplo después de instalar), la tarjeta, la descarga y la
notificación pendientes se descartan automáticamente.

## 📄 Licencia

Sin licencia definida por ahora (repositorio privado). Si decides abrirlo,
MIT/Apache-2.0 serían opciones típicas.
