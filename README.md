# Ferro DLNA — Proyecto nativo Android (listo para compilar)

Este es el proyecto de Android Studio reconstruido a partir del código Kotlin/XML
que la IA generó dentro de la pestaña "Exportar a Android" de la app web.
Ese código venía como texto plano para copiar a mano — acá ya está armado
como un proyecto Gradle real, con los archivos que faltaban agregados:

- `DeviceAdapter.kt` — la IA lo usaba en `MainActivity.kt` pero nunca lo generó.
- `item_device.xml` — layout de cada fila de la lista de dispositivos.
- `bg_industrial_card.xml` y `btn_industrial_yellow.xml` — los drawables que
  usaban los layouts.
- `build.gradle` (raíz y app), `settings.gradle`, `gradle.properties` — la
  estructura que arma el proyecto completo.

## ⚠️ Importante sobre qué hace esta app

Esta es la app **nativa real** (no el simulador web). El escaneo de red usa
SSDP de verdad (sockets UDP), así que para encontrar dispositivos DLNA
necesita estar conectado a la misma red Wi-Fi que el servidor/TV.

**Ahora sí hace Cast real.** Se agregó `UpnpCastController.kt`, un cliente
UPnP/AVTransport mínimo (sin librerías pesadas, a mano con
`HttpURLConnection` + `XmlPullParser`) que:

1. Descarga el XML de descripción del dispositivo (la URL que devuelve SSDP).
2. Busca ahí el servicio `AVTransport:1` y su `controlURL`.
3. Le manda las acciones SOAP estándar `SetAVTransportURI` + `Play`.

Eso es lo mismo que hace cualquier app de "enviar a TV": no reproduce el
video en el celular, le ordena al renderer (la TV/parlante) que lo reproduzca
él, tomando el stream directo de la URL.

El escaneo ahora busca tanto `MediaServer` como `MediaRenderer` y los
distingue. Al tocar un dispositivo:
- Si es un **receptor/renderer** → podés pegar la URL de un video y tocar
  **"Transmitir (Cast)"**: se envía de verdad al dispositivo.
- Si es un **servidor** → se reproduce local en el celular con
  `PlayerActivity` (`MediaPlayer` nativo).

**Lo que todavía no incluye** (para no inflar el alcance): navegación del
catálogo de un MediaServer (acción `Browse` de `ContentDirectory`) — por eso
hay que pegar la URL del archivo a mano en vez de navegar carpetas. Si
querés, se puede sumar después.

## Cómo generar el APK (sin PC, todo desde el celular)

1. Subí esta carpeta completa a un repositorio nuevo en GitHub (podés
   hacerlo desde la app de GitHub o desde el navegador, subiendo el .zip
   y extrayéndolo, o con Termux + git si preferís línea de comandos).
2. Andá a la pestaña **Actions** del repo. El workflow `Build APK` corre
   solo al hacer push a `main`, o lo podés disparar manualmente con
   **Run workflow**.
3. Cuando termine (unos 3-5 minutos), entrá al resultado y bajá el
   artefacto `ferro-dlna-debug-apk` — es un .zip con el `.apk` adentro.
4. Descomprimilo, instalá el `.apk` en el celular (vas a tener que permitir
   "instalar apps de orígenes desconocidos" la primera vez).

## Alternativa: Android Studio en PC

1. Abrí Android Studio → `Open` → seleccioná esta carpeta.
2. Dejá que sincronice Gradle solo.
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
